package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * Best-effort: keeps Rune Knights focused on nearby players by forcing the default marked target.
 *
 * <p>This helps reliability for placeholder/variant roles where default AI sensors may be finicky.</p>
 */
public final class RuneKnightAggroSystem extends TickingSystem<EntityStore> {

    private static final long TICK_INTERVAL_NANOS = 500_000_000L;
    private static final double TARGET_HOSTILE_OVERRIDE_SECONDS = 4.0;
    private static final long DEBUG_INTERVAL_NANOS = 5_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final RuneKnightSpawnState spawnState;
    private final ConcurrentMap<UUID, Long> nextDebugAtNanosByKnight = new ConcurrentHashMap<>();

    private volatile long nextTickAtNanos = 0L;

    public RuneKnightAggroSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull RuneKnightSpawnState spawnState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.spawnState = spawnState;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            long nowNanos = System.nanoTime();
            if (nowNanos < nextTickAtNanos) {
                return;
            }
            nextTickAtNanos = nowNanos + TICK_INTERVAL_NANOS;

            if (config == null || config.runeKnight == null || !config.runeKnight.enabled) {
                return;
            }

            var external = store.getExternalData();
            World world = external != null ? external.getWorld() : null;
            if (world == null) {
                return;
            }

            List<PlayerSnapshot> players = snapshotPlayers(store);
            if (players.isEmpty()) {
                return;
            }

            double maxAggroRadius = Math.max(
                1.0,
                config.runeKnight.aggro != null ? config.runeKnight.aggro.radiusBlocks : 40.0
            );
            double maxAggroRadiusSq = maxAggroRadius * maxAggroRadius;

            for (RuneKnightSpawnState.ActiveRuneKnight active : spawnState.snapshot(world)) {
                UUID knightUuid = active != null ? active.uuid() : null;
                if (knightUuid == null) {
                    continue;
                }

                Ref<EntityStore> knightRef = external.getRefFromUUID(knightUuid);
                if (knightRef == null || !knightRef.isValid()) {
                    spawnState.remove(world, knightUuid);
                    continue;
                }

                Transform look = TargetUtil.getLook(knightRef, store);
                Vector3d knightPos = look != null ? look.getPosition() : null;
                if (knightPos == null || !knightPos.isFinite()) {
                    TransformComponent transform = store.getComponent(knightRef, TransformComponent.getComponentType());
                    knightPos = transform != null ? transform.getPosition() : null;
                }
                if (knightPos == null || !knightPos.isFinite()) {
                    continue;
                }

                PlayerSnapshot nearest = null;
                double nearestD2 = Double.POSITIVE_INFINITY;
                for (PlayerSnapshot player : players) {
                    if (player == null || player.position == null || !player.position.isFinite()) {
                        continue;
                    }
                    double d2 = distSq(knightPos, player.position);
                    if (d2 < nearestD2) {
                        nearestD2 = d2;
                        nearest = player;
                    }
                }
                if (nearest == null || nearest.playerEntityRef == null || !nearest.playerEntityRef.isValid()) {
                    continue;
                }

                double d2 = nearestD2;
                if (d2 > maxAggroRadiusSq) {
                    continue;
                }

                NPCEntity npc = store.getComponent(knightRef, NPCEntity.getComponentType());
                if (npc == null) {
                    continue;
                }
                Role role = npc.getRole();
                if (role == null || role.getMarkedEntitySupport() == null || role.getWorldSupport() == null) {
                    continue;
                }

                MarkedEntitySupport marked = role.getMarkedEntitySupport();
                Ref<EntityStore> currentTarget = marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
                if (currentTarget != null && currentTarget.isValid() && currentTarget.equals(nearest.playerEntityRef)) {
                    continue;
                }

                try {
                    role.getWorldSupport().overrideAttitude(nearest.playerEntityRef, Attitude.HOSTILE, TARGET_HOSTILE_OVERRIDE_SECONDS);
                } catch (Throwable ignored) {
                    // Best effort.
                }

                marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, nearest.playerEntityRef);
                role.getWorldSupport().requestNewPath();
                role.notifySensorMatch();

                long nextDebugAt = nextDebugAtNanosByKnight.getOrDefault(knightUuid, 0L);
                if (nextDebugAt > nowNanos) {
                    continue;
                }
                nextDebugAtNanosByKnight.put(knightUuid, nowNanos + DEBUG_INTERVAL_NANOS);

                UUIDComponent targetUuidComponent = store.getComponent(nearest.playerEntityRef, UUIDComponent.getComponentType());
                UUID targetUuid = targetUuidComponent != null ? targetUuidComponent.getUuid() : null;

                debug.traceFileOnly(
                    null,
                    "RuneKnightAggro event=retarget"
                        + " knightUuid=" + knightUuid
                        + " targetUuid=" + targetUuid
                        + " distanceBlocks=" + Math.sqrt(d2)
                        + " aggroRadiusBlocks=" + maxAggroRadius
                        + " hostileOverrideSeconds=" + TARGET_HOSTILE_OVERRIDE_SECONDS
                        + " world=" + world.getName()
                );
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "RuneKnightAggroSystem: tick failed.", t);
        }
    }

    private static @Nonnull List<PlayerSnapshot> snapshotPlayers(@Nonnull Store<EntityStore> store) {
        List<PlayerSnapshot> out = new ArrayList<>();
        Query<EntityStore> query = Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType()
        );
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> visitor = (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> playerEntityRef = chunk.getReferenceTo(i);
                if (playerEntityRef == null || !playerEntityRef.isValid()) {
                    continue;
                }
                PlayerRef playerRef = chunk.getComponent(i, PlayerRef.getComponentType());
                Player player = chunk.getComponent(i, Player.getComponentType());
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                UUID uuid = playerRef != null ? playerRef.getUuid() : null;
                Vector3d pos = transform != null ? transform.getPosition() : null;
                if (uuid == null || pos == null || !pos.isFinite()) {
                    continue;
                }

                boolean creative = false;
                try {
                    GameMode gm = player != null ? player.getGameMode() : null;
                    creative = gm == GameMode.Creative;
                } catch (Throwable ignored) {
                    creative = false;
                }
                if (creative) {
                    continue;
                }
                out.add(new PlayerSnapshot(uuid, playerEntityRef, pos));
            }
        };
        store.forEachChunk(query, visitor);
        return out;
    }

    private static double distSq(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private record PlayerSnapshot(@Nonnull UUID playerUuid, @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull Vector3d position) {
    }
}
