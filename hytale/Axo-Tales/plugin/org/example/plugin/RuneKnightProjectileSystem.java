package org.example.plugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.collision.CollisionMaterial;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * Plugin-driven ranged attacks for Rune Knights.
 *
 * <p>Rune Knights currently fail to execute melee attacks reliably on placeholder rigs (e.g., Mannequin), even when
 * aggro/pathing is correct. This system makes combat reproducible by firing a projectile at the current marked target.</p>
 */
public final class RuneKnightProjectileSystem extends TickingSystem<EntityStore> {

    public static final String DEFAULT_PROJECTILE_ID = "RuneKnight_Bolt";

    private static final long TICK_INTERVAL_NANOS = 250_000_000L;
    private static final long DEBUG_INTERVAL_NANOS = 15_000_000_000L;
    private static final long ACTION_CLEAR_NANOS = 800_000_000L;

    private static final double DEFAULT_RANGE_BLOCKS = 24.0;
    private static final double DEFAULT_COOLDOWN_SECONDS = 1.25;
    private static final double DEFAULT_AIM_HEIGHT_BLOCKS = 1.25;

    private static final double REQUIRE_FACING_MAX_ANGLE_DEGREES = 55.0;

    private static final Box RAY_POINT_BOX = new Box(0, 0, 0, 0.01, 0.01, 0.01);

    private static final String ACTION_ANIMATION_CAST = "CastMagic";

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final RuneKnightSpawnState spawnState;

    private final ConcurrentMap<UUID, Long> nextFireAtNanosByKnight = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> nextDebugAtNanosByKnight = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> clearActionAtNanosByKnight = new ConcurrentHashMap<>();

    private volatile long nextTickAtNanos;

    public RuneKnightProjectileSystem(
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
            long next = nextTickAtNanos;
            if (next > 0 && nowNanos < next) {
                return;
            }
            nextTickAtNanos = nowNanos + TICK_INTERVAL_NANOS;

            if (config == null
                || config.runeKnight == null
                || config.runeKnight.projectiles == null
                || !config.runeKnight.projectiles.enabled) {
                return;
            }

            var external = store.getExternalData();
            World world = external != null ? external.getWorld() : null;
            if (world == null) {
                return;
            }

            TimeResource time = store.getResource(TimeResource.getResourceType());
            if (time == null) {
                return;
            }

            double rangeBlocks = Math.max(1.0, config.runeKnight.projectiles.rangeBlocks);
            double rangeSq = rangeBlocks * rangeBlocks;
            long cooldownNanos = secondsToNanos(config.runeKnight.projectiles.cooldownSeconds, DEFAULT_COOLDOWN_SECONDS);
            double aimHeight = Double.isFinite(config.runeKnight.projectiles.aimHeightBlocks)
                ? config.runeKnight.projectiles.aimHeightBlocks
                : DEFAULT_AIM_HEIGHT_BLOCKS;
            String projectileId = config.runeKnight.projectiles.projectileId != null && !config.runeKnight.projectiles.projectileId.isBlank()
                ? config.runeKnight.projectiles.projectileId
                : DEFAULT_PROJECTILE_ID;

            String roleName = config.runeKnight.roleName != null && !config.runeKnight.roleName.isBlank()
                ? config.runeKnight.roleName
                : RuneKnightSpawnerSystem.DEFAULT_ROLE_NAME;

            var knights = collectRuneKnights(store, world, roleName);
            if (knights.isEmpty()) {
                return;
            }

            var players = snapshotPlayers(store);
            final int healthIndex = DefaultEntityStatTypes.getHealth();

            for (KnightSnapshot knight : knights) {
                UUID knightUuid = knight.uuid;
                Ref<EntityStore> knightRef = knight.ref;
                if (knightUuid == null || knightRef == null || !knightRef.isValid()) {
                    continue;
                }

                maybeClearActionAnimation(store, nowNanos, knightUuid, knightRef);

                long nextFireAt = nextFireAtNanosByKnight.getOrDefault(knightUuid, 0L);
                if (nextFireAt > nowNanos) {
                    continue;
                }

                NPCEntity npc = store.getComponent(knightRef, NPCEntity.getComponentType());
                if (npc == null) {
                    continue;
                }

                DeathComponent death = null;
                try {
                    death = store.getComponent(knightRef, DeathComponent.getComponentType());
                } catch (Throwable ignored) {
                    // Best effort.
                }

                EntityStatMap stats = null;
                try {
                    stats = store.getComponent(knightRef, EntityStatMap.getComponentType());
                } catch (Throwable ignored) {
                    // Best effort.
                }
                EntityStatValue healthStat = stats != null ? stats.get(healthIndex) : null;
                float healthCurrent = healthStat != null ? healthStat.get() : Float.NaN;
                if (death != null || (healthStat != null && Float.isFinite(healthCurrent) && healthCurrent <= 0f)) {
                    spawnState.remove(world, knightUuid);
                    nextFireAtNanosByKnight.remove(knightUuid);
                    clearActionAtNanosByKnight.remove(knightUuid);
                    debugFailureMaybe(store, world, nowNanos, knightUuid, null, projectileId, "dead", null, null);
                    continue;
                }

                Role role = npc.getRole();
                if (role == null || role.getMarkedEntitySupport() == null || role.getWorldSupport() == null) {
                    continue;
                }

                String stateName = null;
                try {
                    stateName = role.getStateSupport() != null ? role.getStateSupport().getStateName() : null;
                } catch (Throwable ignored) {
                    // Best effort.
                }
                if (!isAggroState(stateName)) {
                    debugFailureMaybe(store, world, nowNanos, knightUuid, null, projectileId, "notAggro", stateName, null);
                    nextFireAtNanosByKnight.put(knightUuid, nowNanos + cooldownNanos);
                    continue;
                }

                MarkedEntitySupport marked = role.getMarkedEntitySupport();
                Ref<EntityStore> targetRef = marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
                String targetSource = "marked";

                if (targetRef != null && targetRef.isValid() && isCreativePlayer(store, targetRef)) {
                    clearDefaultTarget(role, marked);
                    targetRef = null;
                    targetSource = "clearedCreative";
                }
                if (targetRef == null || !targetRef.isValid()) {
                    PlayerSnapshot nearestPlayer = findNearestPlayer(players, store, knightRef, rangeSq);
                    if (nearestPlayer != null) {
                        targetRef = nearestPlayer.playerEntityRef;
                        targetSource = "nearestPlayer";
                        try {
                            marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, targetRef);
                            role.getWorldSupport().requestNewPath();
                            role.notifySensorMatch();
                        } catch (Throwable ignored) {
                            // Best effort.
                        }
                    }
                }
                if (targetRef == null || !targetRef.isValid()) {
                    debugFailureMaybe(store, world, nowNanos, knightUuid, targetRef, projectileId, "noTarget", stateName, targetSource);
                    nextFireAtNanosByKnight.put(knightUuid, nowNanos + cooldownNanos);
                    continue;
                }

                Vector3d targetPos = getAimPosition(store, targetRef, aimHeight);
                Vector3d origin = getAimPosition(store, knightRef, aimHeight);
                if (targetPos == null || origin == null) {
                    continue;
                }

                Vector3d deltaVec = new Vector3d(targetPos.x - origin.x, targetPos.y - origin.y, targetPos.z - origin.z);
                if (!deltaVec.isFinite()) {
                    continue;
                }

                double d2 = deltaVec.squaredLength();
                if (d2 < 1e-9 || d2 > rangeSq) {
                    continue;
                }

                TransformComponent knightTransform = store.getComponent(knightRef, TransformComponent.getComponentType());
                Vector3f knightRotation = knightTransform != null ? knightTransform.getRotation() : null;
                if (knightRotation != null && !isFacing(knightRotation, deltaVec, REQUIRE_FACING_MAX_ANGLE_DEGREES)) {
                    try {
                        role.getWorldSupport().requestNewPath();
                        role.notifySensorMatch();
                    } catch (Throwable ignored) {
                        // Best effort.
                    }
                    debugFailureMaybe(store, world, nowNanos, knightUuid, targetRef, projectileId, "notFacing", stateName, targetSource);
                    continue;
                }

                if (!hasLineOfSight(world, origin, targetPos)) {
                    debugFailureMaybe(store, world, nowNanos, knightUuid, targetRef, projectileId, "noLineOfSight", stateName, targetSource);
                    continue;
                }

                String actionAnimationId = selectActionAnimationId(Math.sqrt(d2));
                playActionAnimationMaybe(store, npc, nowNanos, knightUuid, knightRef, actionAnimationId);

                Vector3f rotation = rotationFromDirection(deltaVec);
                float yaw = rotation.getYaw();
                float pitch = rotation.getPitch();

                Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, projectileId, origin, rotation);
                if (holder == null) {
                    debugFailureMaybe(store, world, nowNanos, knightUuid, targetRef, projectileId, "projectileAssembleFailed", stateName, targetSource);
                    nextFireAtNanosByKnight.put(knightUuid, nowNanos + cooldownNanos);
                    continue;
                }

                ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
                if (projectile == null) {
                    debugFailureMaybe(store, world, nowNanos, knightUuid, targetRef, projectileId, "projectileComponentMissing", stateName, targetSource);
                    nextFireAtNanosByKnight.put(knightUuid, nowNanos + cooldownNanos);
                    continue;
                }

                if (!projectile.initialize()) {
                    debugFailureMaybe(store, world, nowNanos, knightUuid, targetRef, projectileId, "projectileAssetNotFound", stateName, targetSource);
                    nextFireAtNanosByKnight.put(knightUuid, nowNanos + cooldownNanos);
                    continue;
                }

                try {
                    projectile.shoot(holder, knightUuid, origin.x, origin.y, origin.z, yaw, pitch);
                } catch (Throwable t) {
                    errors.report((PlayerRef) null, "RuneKnightProjectile: projectile.shoot failed (assetId=" + projectileId + ").", t);
                    debugFailureMaybe(store, world, nowNanos, knightUuid, targetRef, projectileId, "projectileShootException", stateName, targetSource);
                    nextFireAtNanosByKnight.put(knightUuid, nowNanos + cooldownNanos);
                    continue;
                }

                Ref<EntityStore> projectileRef;
                try {
                    projectileRef = store.addEntity(holder, AddReason.SPAWN);
                } catch (Throwable t) {
                    errors.report((PlayerRef) null, "RuneKnightProjectile: store.addEntity failed (assetId=" + projectileId + ").", t);
                    debugFailureMaybe(store, world, nowNanos, knightUuid, targetRef, projectileId, "projectileSpawnException", stateName, targetSource);
                    nextFireAtNanosByKnight.put(knightUuid, nowNanos + cooldownNanos);
                    continue;
                }

                UUID projectileUuid = null;
                try {
                    UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
                    if (projectileUuidComponent != null) {
                        projectileUuid = projectileUuidComponent.getUuid();
                    }
                } catch (Throwable ignored) {
                    // Best effort debug info.
                }

                nextFireAtNanosByKnight.put(knightUuid, nowNanos + cooldownNanos);

                UUID targetUuid = null;
                try {
                    UUIDComponent targetUuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());
                    if (targetUuidComponent != null) {
                        targetUuid = targetUuidComponent.getUuid();
                    }
                } catch (Throwable ignored) {
                    // Best effort debug info.
                }

                debug.traceFileOnly(
                    null,
                    "RuneKnightRanged event=shoot"
                        + " knightUuid=" + knightUuid
                        + (targetUuid != null ? " targetUuid=" + targetUuid : "")
                        + (projectileUuid != null ? " projectileUuid=" + projectileUuid : "")
                        + " projectileId=" + projectileId
                        + " anim=" + actionAnimationId
                        + " targetSource=" + targetSource
                        + (stateName != null ? " state=" + stateName : "")
                        + " distanceBlocks=" + Math.sqrt(d2)
                        + " rangeBlocks=" + rangeBlocks
                        + " cooldownSeconds=" + (cooldownNanos / 1_000_000_000.0)
                        + " yaw=" + yaw
                        + " pitch=" + pitch
                        + " world=" + world.getName()
                );
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "RuneKnightProjectileSystem: tick failed.", t);
        }
    }

    private void maybeClearActionAnimation(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull UUID knightUuid,
        @Nonnull Ref<EntityStore> knightRef
    ) {
        long clearAt = clearActionAtNanosByKnight.getOrDefault(knightUuid, 0L);
        if (clearAt <= 0 || nowNanos < clearAt) {
            return;
        }
        clearActionAtNanosByKnight.remove(knightUuid);
        try {
            AnimationUtils.stopAnimation(knightRef, AnimationSlot.Action, store);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private static @Nonnull String selectActionAnimationId(double distanceBlocks) {
        return ACTION_ANIMATION_CAST;
    }

    private static boolean hasLineOfSight(@Nonnull World world, @Nonnull Vector3d origin, @Nonnull Vector3d targetPos) {
        Vector3d delta = new Vector3d(targetPos.x - origin.x, targetPos.y - origin.y, targetPos.z - origin.z);
        double maxDistance = Math.sqrt(delta.squaredLength());
        if (!Double.isFinite(maxDistance) || maxDistance <= 0) {
            return false;
        }

        Vector3d ray = new Vector3d(delta).normalize().scale(maxDistance);
        CollisionResult result = new CollisionResult(false, false);
        result.setCollisionByMaterial(CollisionMaterial.MATERIAL_SOLID);
        CollisionModule.findBlockCollisionsIterative(world, RAY_POINT_BOX, origin, ray, true, result);
        return result.getFirstBlockCollision() == null;
    }

    private void playActionAnimationMaybe(
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npc,
        long nowNanos,
        @Nonnull UUID knightUuid,
        @Nonnull Ref<EntityStore> knightRef,
        @Nonnull String animationId
    ) {
        if (animationId.isBlank()) {
            return;
        }

        try {
            npc.playAnimation(knightRef, AnimationSlot.Action, animationId, store);
            clearActionAtNanosByKnight.put(knightUuid, nowNanos + ACTION_CLEAR_NANOS);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private @Nonnull ArrayList<KnightSnapshot> collectRuneKnights(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull String roleName
    ) {
        var external = store.getExternalData();
        if (external == null) {
            return new ArrayList<>();
        }

        HashMap<UUID, Ref<EntityStore>> byUuid = new HashMap<>();

        try {
            for (RuneKnightSpawnState.ActiveRuneKnight active : spawnState.snapshot(world)) {
                UUID uuid = active != null ? active.uuid() : null;
                if (uuid == null) {
                    continue;
                }
                Ref<EntityStore> ref = external.getRefFromUUID(uuid);
                if (ref != null && ref.isValid()) {
                    byUuid.put(uuid, ref);
                }
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        Query<EntityStore> query = Query.and(
            NPCEntity.getComponentType(),
            UUIDComponent.getComponentType()
        );
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> visitor = (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                if (npc == null) {
                    continue;
                }
                String npcRoleName = null;
                try {
                    npcRoleName = npc.getRoleName();
                } catch (Throwable ignored) {
                    // Best effort.
                }
                if (npcRoleName == null || !npcRoleName.equals(roleName)) {
                    continue;
                }
                UUIDComponent uuidComponent = chunk.getComponent(i, UUIDComponent.getComponentType());
                UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
                if (uuid == null) {
                    continue;
                }
                byUuid.put(uuid, ref);
            }
        };
        try {
            store.forEachChunk(query, visitor);
        } catch (Throwable ignored) {
            // Best effort.
        }

        ArrayList<KnightSnapshot> out = new ArrayList<>(byUuid.size());
        for (var entry : byUuid.entrySet()) {
            out.add(new KnightSnapshot(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private static @Nonnull ArrayList<PlayerSnapshot> snapshotPlayers(@Nonnull Store<EntityStore> store) {
        ArrayList<PlayerSnapshot> out = new ArrayList<>();
        Query<EntityStore> query = Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType()
        );
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> visitor = (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) {
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
                out.add(new PlayerSnapshot(uuid, ref, pos));
            }
        };
        try {
            store.forEachChunk(query, visitor);
        } catch (Throwable ignored) {
            // Best effort.
        }
        return out;
    }

    private static @Nullable PlayerSnapshot findNearestPlayer(
        @Nonnull ArrayList<PlayerSnapshot> players,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> knightRef,
        double maxDistanceSq
    ) {
        Vector3d knightPos = getAimPosition(store, knightRef, DEFAULT_AIM_HEIGHT_BLOCKS);
        if (knightPos == null) {
            return null;
        }

        PlayerSnapshot nearest = null;
        double nearestD2 = Double.POSITIVE_INFINITY;
        for (PlayerSnapshot p : players) {
            if (p == null || p.position == null || !p.position.isFinite()) {
                continue;
            }
            double dx = p.position.x - knightPos.x;
            double dy = p.position.y - knightPos.y;
            double dz = p.position.z - knightPos.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < nearestD2) {
                nearestD2 = d2;
                nearest = p;
            }
        }
        if (nearest == null) {
            return null;
        }
        return nearestD2 <= maxDistanceSq ? nearest : null;
    }

    private void debugFailureMaybe(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        long nowNanos,
        @Nonnull UUID knightUuid,
        @Nullable Ref<EntityStore> targetRef,
        @Nonnull String projectileId,
        @Nonnull String reason,
        @Nullable String stateName,
        @Nullable String targetSource
    ) {
        // Too noisy with many idle NPCs; only log actionable failures.
        if ("notAggro".equals(reason) || "noTarget".equals(reason) || "dead".equals(reason)) {
            return;
        }

        long nextDebugAt = nextDebugAtNanosByKnight.getOrDefault(knightUuid, 0L);
        if (nextDebugAt > nowNanos) {
            return;
        }
        nextDebugAtNanosByKnight.put(knightUuid, nowNanos + DEBUG_INTERVAL_NANOS);

        UUID targetUuid = null;
        try {
            UUIDComponent targetUuidComponent = targetRef != null ? store.getComponent(targetRef, UUIDComponent.getComponentType()) : null;
            if (targetUuidComponent != null) {
                targetUuid = targetUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        debug.traceFileOnly(
            null,
            "RuneKnightRanged event=shootSkipped"
                + " reason=" + reason
                + " knightUuid=" + knightUuid
                + (targetUuid != null ? " targetUuid=" + targetUuid : "")
                + " projectileId=" + projectileId
                + (targetSource != null ? " targetSource=" + targetSource : "")
                + (stateName != null ? " state=" + stateName : "")
                + " world=" + world.getName()
        );
    }

    private static boolean isAggroState(@Nullable String stateName) {
        if (stateName == null || stateName.isBlank()) {
            return false;
        }
        String s = stateName.trim().toLowerCase();
        return !(
            s.contains("idle")
                || s.contains("patrol")
                || s.contains("wander")
                || s.contains("roam")
                || s.contains("sleep")
        );
    }

    private static @Nullable Vector3d getAimPosition(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        double fallbackHeightBlocks
    ) {
        try {
            Transform look = TargetUtil.getLook(ref, store);
            Vector3d pos = look != null ? look.getPosition() : null;
            if (pos != null && pos.isFinite()) {
                return pos;
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            Vector3d pos = transform != null ? transform.getPosition() : null;
            if (pos != null && pos.isFinite()) {
                double height = Double.isFinite(fallbackHeightBlocks) ? fallbackHeightBlocks : DEFAULT_AIM_HEIGHT_BLOCKS;
                return new Vector3d(pos.x, pos.y + height, pos.z);
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        return null;
    }

    private static @Nonnull Vector3f rotationFromDirection(@Nonnull Vector3d direction) {
        double lenSq = direction.squaredLength();
        if (!Double.isFinite(lenSq) || lenSq < 1e-9) {
            return Vector3f.ZERO;
        }

        double len = Math.sqrt(lenSq);
        double dx = direction.x / len;
        double dy = direction.y / len;
        double dz = direction.z / len;

        double clampedY = Math.max(-1.0, Math.min(1.0, dy));
        float pitch = (float) Math.asin(clampedY);
        float yaw = (float) Math.atan2(-dx, -dz);

        return new Vector3f(pitch, yaw, 0f);
    }

    private static long secondsToNanos(double seconds, double defaultSeconds) {
        double s = Double.isFinite(seconds) ? seconds : defaultSeconds;
        if (s < 0) {
            s = 0;
        }
        if (s > 60) {
            s = 60;
        }
        return (long) (s * 1_000_000_000L);
    }

    private record KnightSnapshot(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> ref) {
    }

    private record PlayerSnapshot(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull Vector3d position) {
    }

    private static boolean isCreativePlayer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return false;
            }
            return player.getGameMode() == GameMode.Creative;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void clearDefaultTarget(@Nonnull Role role, @Nonnull MarkedEntitySupport marked) {
        int slot = resolveDefaultTargetSlotIndex(marked);
        try {
            if (marked.hasMarkedEntityInSlot(slot)) {
                marked.clearMarkedEntity(slot);
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
        try {
            if (role.getWorldSupport() != null) {
                role.getWorldSupport().requestNewPath();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private static int resolveDefaultTargetSlotIndex(@Nonnull MarkedEntitySupport marked) {
        int slots = 0;
        try {
            slots = marked.getMarkedEntitySlotCount();
        } catch (Throwable ignored) {
            return 0;
        }
        for (int i = 0; i < slots; i++) {
            try {
                String name = marked.getSlotName(i);
                if (MarkedEntitySupport.DEFAULT_TARGET_SLOT.equals(name)) {
                    return i;
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
        return 0;
    }

    private static boolean isFacing(@Nonnull Vector3f rotation, @Nonnull Vector3d toTarget, double maxAngleDegrees) {
        double lenSq = toTarget.squaredLength();
        if (!Double.isFinite(lenSq) || lenSq < 1e-9) {
            return false;
        }
        double len = Math.sqrt(lenSq);
        double dirX = toTarget.x / len;
        double dirY = toTarget.y / len;
        double dirZ = toTarget.z / len;

        double yaw = rotation.getYaw();
        double pitch = rotation.getPitch();
        double cosPitch = Math.cos(pitch);
        double forwardX = -Math.sin(yaw) * cosPitch;
        double forwardY = Math.sin(pitch);
        double forwardZ = -Math.cos(yaw) * cosPitch;

        double dot = (forwardX * dirX) + (forwardY * dirY) + (forwardZ * dirZ);
        double threshold = Math.cos(Math.toRadians(maxAngleDegrees));
        return Double.isFinite(dot) && dot >= threshold;
    }
}
