package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Day-time spawning and lifecycle management for the friendly Kudu Adept NPC role.
 *
 * <p>This is intentionally plugin-driven so it can be tuned via {@code server-config.json} without editing worldgen
 * graphs, and to keep behavior reproducible via the persistent debug log.</p>
 */
public final class KuduAdeptSpawnerSystem extends TickingSystem<EntityStore> {

    public static final String DEFAULT_ROLE_NAME = "Kudu_Adept_Magician";

    private static final int CHUNK_WIDTH_BLOCKS = ChunkUtil.SIZE;
    private static final int MAX_Y = ChunkUtil.HEIGHT_MINUS_1;
    private static final int MIN_Y = 1;

    private static final long DEBUG_INTERVAL_NANOS = 10_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final KuduAdeptSpawnState spawnState;

    private volatile long nextSpawnAtNanos = 0L;
    private volatile long nextSummaryDebugAtNanos = 0L;

    public KuduAdeptSpawnerSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull KuduAdeptSpawnState spawnState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.spawnState = spawnState;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            var external = store.getExternalData();
            World world = external != null ? external.getWorld() : null;
            if (world == null) {
                return;
            }

            if (config == null || config.kuduAdept == null || !config.kuduAdept.enabled) {
                return;
            }

            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null) {
                return;
            }

            String roleName = config.kuduAdept.roleName != null && !config.kuduAdept.roleName.isBlank()
                ? config.kuduAdept.roleName
                : DEFAULT_ROLE_NAME;
            if (!npcPlugin.hasRoleName(roleName)) {
                maybeDebugSummary(null, "KuduAdeptSpawn event=roleNotFound roleName=" + roleName + " world=" + world.getName());
                return;
            }

            WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
            if (worldTime == null) {
                return;
            }

            boolean isDay = isDay(
                worldTime,
                config.kuduAdept.spawn != null ? config.kuduAdept.spawn.daySunlightThreshold : 0.25
            );
            long nowNanos = System.nanoTime();

            cleanupExpiredAndInvalid(store, world, nowNanos);

            if (!isDay && config.kuduAdept.despawn != null && config.kuduAdept.despawn.onNight) {
                int removed = removeAllInWorld(store, world);
                if (removed > 0) {
                    maybeDebugSummary(
                        null,
                        "KuduAdeptSpawn event=nightCleanup"
                            + " removed=" + removed
                            + " world=" + world.getName()
                            + " sunlight=" + worldTime.getSunlightFactor()
                    );
                }
                return;
            }

            if (!isDay) {
                return;
            }

            long intervalNanos = secondsToNanos(
                config.kuduAdept.spawn != null ? config.kuduAdept.spawn.intervalSeconds : 30.0,
                30.0
            );
            if (nowNanos < nextSpawnAtNanos) {
                return;
            }
            nextSpawnAtNanos = nowNanos + intervalNanos;

            List<PlayerCandidate> players = snapshotPlayers(store);
            if (players.isEmpty()) {
                return;
            }

            int active = spawnState.count(world);
            int maxActive = Math.max(0, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.maxActivePerWorld : 0);
            int spawnBudget = Math.min(
                Math.max(0, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.spawnsPerInterval : 0),
                Math.max(0, maxActive - active)
            );
            if (spawnBudget <= 0) {
                maybeDebugSummary(
                    null,
                    "KuduAdeptSpawn event=spawnSkipped"
                        + " reason=maxActive"
                        + " active=" + active
                        + " maxActive=" + maxActive
                        + " world=" + world.getName()
                );
                return;
            }

            int spawned = 0;
            int attempts = 0;
            int maxAttempts = Math.max(1, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.maxAttemptsPerInterval : 1);
            while (spawned < spawnBudget && attempts < maxAttempts) {
                attempts++;
                PlayerCandidate anchor = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                SpawnAttemptResult result = trySpawnNear(store, world, npcPlugin, roleName, anchor, players, nowNanos);
                if (result.spawned) {
                    spawned++;
                }
            }

            if (spawned > 0) {
                maybeDebugSummary(
                    null,
                    "KuduAdeptSpawn event=spawned"
                        + " roleName=" + roleName
                        + " spawned=" + spawned
                        + " attempts=" + attempts
                        + " activeNow=" + spawnState.count(world)
                        + " world=" + world.getName()
                        + " sunlight=" + worldTime.getSunlightFactor()
                );
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduAdeptSpawnerSystem: tick failed.", t);
        }
    }

    private void cleanupExpiredAndInvalid(@Nonnull Store<EntityStore> store, @Nonnull World world, long nowNanos) {
        for (KuduAdeptSpawnState.ActiveKuduAdept active : spawnState.snapshot(world)) {
            if (active == null || active.uuid() == null) {
                continue;
            }
            if (active.expiresAtNanos() > 0 && nowNanos > active.expiresAtNanos()) {
                removeByUuid(store, world, active.uuid(), "expired");
                continue;
            }

            var ref = store.getExternalData() != null ? store.getExternalData().getRefFromUUID(active.uuid()) : null;
            if (ref == null || !ref.isValid()) {
                spawnState.remove(world, active.uuid());
            }
        }
    }

    private int removeAllInWorld(@Nonnull Store<EntityStore> store, @Nonnull World world) {
        int removed = 0;
        for (KuduAdeptSpawnState.ActiveKuduAdept active : spawnState.snapshot(world)) {
            if (active == null || active.uuid() == null) {
                continue;
            }
            if (removeByUuid(store, world, active.uuid(), "nightCleanup")) {
                removed++;
            }
        }
        spawnState.clearWorld(world);
        return removed;
    }

    private boolean removeByUuid(@Nonnull Store<EntityStore> store, @Nonnull World world, @Nonnull UUID uuid, @Nonnull String reason) {
        try {
            var ref = store.getExternalData() != null ? store.getExternalData().getRefFromUUID(uuid) : null;
            if (ref == null || !ref.isValid()) {
                spawnState.remove(world, uuid);
                return false;
            }
            store.removeEntity(ref, com.hypixel.hytale.component.RemoveReason.REMOVE);
            spawnState.remove(world, uuid);
            debug.traceFileOnly(null, "KuduAdeptSpawn event=despawn reason=" + reason + " uuid=" + uuid + " world=" + world.getName());
            return true;
        } catch (Throwable t) {
            spawnState.remove(world, uuid);
            debug.traceFileOnly(null, "KuduAdeptSpawn event=despawnFailed reason=" + reason + " uuid=" + uuid + " world=" + world.getName());
            return false;
        }
    }

    private SpawnAttemptResult trySpawnNear(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull NPCPlugin npcPlugin,
        @Nonnull String roleName,
        @Nonnull PlayerCandidate anchor,
        @Nonnull List<PlayerCandidate> allPlayers,
        long nowNanos
    ) {
        Vector3d anchorPos = anchor.position;
        if (anchorPos == null || !anchorPos.isFinite()) {
            return SpawnAttemptResult.fail("anchorPosInvalid");
        }

        double minRadius = Math.max(0, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.radiusMinBlocks : 0);
        double maxRadius = Math.max(minRadius, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.radiusMaxBlocks : minRadius);
        double radius = ThreadLocalRandom.current().nextDouble(minRadius, maxRadius + 0.0001);
        double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);

        int x = (int) Math.floor(anchorPos.x + Math.cos(angle) * radius);
        int z = (int) Math.floor(anchorPos.z + Math.sin(angle) * radius);

        double minDistancePlayers = Math.max(0, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.minDistanceFromPlayersBlocks : 0);
        double minDistanceSq = minDistancePlayers * minDistancePlayers;
        for (PlayerCandidate player : allPlayers) {
            Vector3d pos = player.position;
            if (pos == null || !pos.isFinite()) {
                continue;
            }
            double dx = (pos.x - x);
            double dz = (pos.z - z);
            double d2 = dx * dx + dz * dz;
            if (d2 < minDistanceSq) {
                return SpawnAttemptResult.fail("tooCloseToPlayer");
            }
        }

        WorldChunk chunk = getChunkForSpawn(world, x, z);
        if (chunk == null) {
            return SpawnAttemptResult.fail("chunkNotLoaded");
        }

        int localX = x - (chunk.getX() * CHUNK_WIDTH_BLOCKS);
        int localZ = z - (chunk.getZ() * CHUNK_WIDTH_BLOCKS);

        int surfaceY = chunk.getHeight(localX, localZ);
        int y = clampY(surfaceY + 1);

        y = findAirColumn(chunk, x, y, z);
        if (y < MIN_Y) {
            return SpawnAttemptResult.fail("noAirColumn");
        }

        Vector3d spawnPosition = new Vector3d(x + 0.5, y, z + 0.5);
        Vector3f rotation = Vector3f.ZERO;

        it.unimi.dsi.fastutil.Pair<Ref<EntityStore>, ?> spawned;
        try {
            spawned = npcPlugin.spawnNPC(store, roleName, null, spawnPosition, rotation);
        } catch (Throwable t) {
            debug.traceFileOnly(
                null,
                "KuduAdeptSpawn event=spawnException"
                    + " roleName=" + roleName
                    + " x=" + x
                    + " y=" + y
                    + " z=" + z
                    + " world=" + world.getName()
            );
            return SpawnAttemptResult.fail("spawnException");
        }

        Ref<EntityStore> npcRef = spawned != null ? spawned.left() : null;
        if (npcRef == null || !npcRef.isValid()) {
            return SpawnAttemptResult.fail("spawnFailed");
        }

        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            try {
                store.removeEntity(npcRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            } catch (Throwable ignored) {
                // Best effort.
            }
            return SpawnAttemptResult.fail("npcComponentMissing");
        }

        UUIDComponent uuidComponent = store.getComponent(npcRef, UUIDComponent.getComponentType());
        UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
        if (uuid == null) {
            try {
                store.removeEntity(npcRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            } catch (Throwable ignored) {
                // Best effort.
            }
            return SpawnAttemptResult.fail("uuidMissing");
        }

        long lifetimeNanos = secondsToNanos(
            config.kuduAdept.despawn != null ? config.kuduAdept.despawn.afterSeconds : 600.0,
            600.0
        );
        long expiresAtNanos = lifetimeNanos > 0 ? nowNanos + lifetimeNanos : 0L;
        spawnState.trackSpawn(world, uuid, nowNanos, expiresAtNanos);

        debug.traceFileOnly(
            null,
            "KuduAdeptSpawn event=spawnedOne"
                + " roleName=" + roleName
                + " uuid=" + uuid
                + " x=" + x
                + " y=" + y
                + " z=" + z
                + " anchorPlayer=" + anchor.playerUuid
                + " chunkX=" + chunk.getX()
                + " chunkZ=" + chunk.getZ()
                + " lifetimeSeconds=" + (config.kuduAdept.despawn != null ? config.kuduAdept.despawn.afterSeconds : 600.0)
                + " world=" + world.getName()
        );

        return SpawnAttemptResult.ok(uuid);
    }

    private static @Nonnull List<PlayerCandidate> snapshotPlayers(@Nonnull Store<EntityStore> store) {
        List<PlayerCandidate> out = new ArrayList<>();
        Query<EntityStore> query = Query.and(
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType()
        );
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> visitor = (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                PlayerRef ref = chunk.getComponent(i, PlayerRef.getComponentType());
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                if (ref == null || ref.getUuid() == null) {
                    continue;
                }
                Vector3d pos = transform != null ? transform.getPosition() : null;
                if (pos == null || !pos.isFinite()) {
                    continue;
                }
                out.add(new PlayerCandidate(ref.getUuid(), pos));
            }
        };
        store.forEachChunk(query, visitor);
        return out;
    }

    private @Nullable WorldChunk getChunkForSpawn(@Nonnull World world, int x, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null && config.kuduAdept.spawn != null && config.kuduAdept.spawn.allowInMemoryChunks) {
            chunk = world.getChunkIfInMemory(chunkIndex);
        }
        return chunk;
    }

    private static int findAirColumn(@Nonnull WorldChunk chunk, int x, int y, int z) {
        int base = clampY(y);
        for (int dy = 0; dy <= 4; dy++) {
            int tryY = clampY(base + dy);
            if (tryY < MIN_Y || tryY >= MAX_Y) {
                continue;
            }
            if (!isAir(chunk, x, tryY, z)) {
                continue;
            }
            if (!isAir(chunk, x, tryY + 1, z)) {
                continue;
            }
            if (isAir(chunk, x, tryY - 1, z)) {
                continue;
            }
            return tryY;
        }
        return -1;
    }

    private static boolean isAir(@Nonnull WorldChunk chunk, int x, int y, int z) {
        try {
            var type = chunk.getBlockType(x, y, z);
            return type == null
                || type.isUnknown()
                || type == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
                || type.getDrawType() == com.hypixel.hytale.protocol.DrawType.Empty;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isDay(@Nonnull WorldTimeResource worldTime, double sunlightThreshold) {
        try {
            double sunlight = worldTime.getSunlightFactor();
            double threshold = Double.isFinite(sunlightThreshold) ? sunlightThreshold : 0.25;
            return sunlight >= threshold;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long secondsToNanos(double seconds, double defaultSeconds) {
        double s = Double.isFinite(seconds) ? seconds : defaultSeconds;
        if (s < 0) {
            s = 0;
        }
        if (s > 3600) {
            s = 3600;
        }
        return (long) (s * 1_000_000_000L);
    }

    private static int clampY(int y) {
        return Math.max(MIN_Y, Math.min(MAX_Y, y));
    }

    private void maybeDebugSummary(@Nullable PlayerRef player, @Nonnull String message) {
        long now = System.nanoTime();
        long next = nextSummaryDebugAtNanos;
        if (next > 0 && now < next) {
            return;
        }
        nextSummaryDebugAtNanos = now + DEBUG_INTERVAL_NANOS;
        debug.traceFileOnly(player, message);
    }

    private record PlayerCandidate(@Nonnull UUID playerUuid, @Nonnull Vector3d position) {
    }

    private record SpawnAttemptResult(boolean spawned, @Nonnull String reason, @Nullable UUID uuid) {
        static SpawnAttemptResult ok(@Nonnull UUID uuid) {
            return new SpawnAttemptResult(true, "ok", uuid);
        }

        static SpawnAttemptResult fail(@Nonnull String reason) {
            return new SpawnAttemptResult(false, reason, null);
        }
    }
}

