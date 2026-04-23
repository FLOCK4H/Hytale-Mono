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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final double DEFAULT_DENSITY_CELL_SIZE_BLOCKS = 32.0;

    private static final long DEBUG_INTERVAL_NANOS = 10_000_000_000L;
    private static final long SPAWN_EXCEPTION_BACKOFF_NANOS = 60_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final KuduAdeptSpawnState spawnState;
    private final KuduAdeptBondState bondState;

    private volatile long nextSpawnAtNanos = 0L;
    private volatile long nextSummaryDebugAtNanos = 0L;
    private volatile long nextSpawnExceptionRetryAtNanos = 0L;
    private volatile String spawnExceptionBackoffRoleName = null;

    public KuduAdeptSpawnerSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull KuduAdeptSpawnState spawnState,
        @Nonnull KuduAdeptBondState bondState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.spawnState = spawnState;
        this.bondState = bondState;
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

            boolean spawnTimeAllowed = isSpawnTimeAllowed(
                worldTime,
                config.kuduAdept.spawn != null ? config.kuduAdept.spawn.daySunlightThreshold : 0.25
            );
            long nowNanos = System.nanoTime();

            cleanupExpiredAndInvalid(store, world, nowNanos);

            if (isSpawnExceptionBackoffActive(roleName, nowNanos)) {
                maybeDebugSummary(
                    null,
                    "KuduAdeptSpawn event=spawnSkipped"
                        + " reason=spawnExceptionBackoff"
                        + " roleName=" + roleName
                        + " retryInMillis=" + Math.max(0L, (nextSpawnExceptionRetryAtNanos - nowNanos) / 1_000_000L)
                        + " world=" + world.getName()
                );
                return;
            }

            if (!spawnTimeAllowed && config.kuduAdept.despawn != null && config.kuduAdept.despawn.onNight) {
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

            if (!spawnTimeAllowed) {
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

            List<ActiveAdeptSnapshot> activeAdepts = snapshotActiveAdepts(store, roleName);
            int active = activeAdepts.size();
            int maxActive = Math.max(0, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.maxActivePerWorld : 0);
            double cellSize = getDensityCellSizeBlocks();
            int spawnChancePercent = getCellSpawnChancePercent();
            List<SpawnCellCandidate> missingCells = findMissingSpawnCells(players, activeAdepts, cellSize);
            int spawnBudget = Math.min(Math.max(0, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.spawnsPerInterval : 0), missingCells.size());
            spawnBudget = Math.min(spawnBudget, Math.max(0, maxActive - active));
            if (spawnBudget <= 0) {
                maybeDebugSummary(
                    null,
                    "KuduAdeptSpawn event=spawnSkipped"
                        + " reason=" + (active >= maxActive ? "maxActive" : "densitySatisfied")
                        + " active=" + active
                        + " maxActive=" + maxActive
                        + " missingCells=" + missingCells.size()
                        + " densityCellSizeBlocks=" + cellSize
                        + " world=" + world.getName()
                );
                return;
            }

            int spawned = 0;
            int attempts = 0;
            int maxAttempts = Math.max(1, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.maxAttemptsPerInterval : 1);
            for (SpawnCellCandidate cell : missingCells) {
                if (spawned >= spawnBudget) {
                    break;
                }
                if (spawnChancePercent < 100 && ThreadLocalRandom.current().nextInt(100) >= spawnChancePercent) {
                    attempts++;
                    debug.traceFileOnly(
                        null,
                        "KuduAdeptSpawn event=cellSkipped"
                            + " reason=chance"
                            + " chancePercent=" + spawnChancePercent
                            + " cellX=" + cell.key.x()
                            + " cellZ=" + cell.key.z()
                            + " world=" + world.getName()
                    );
                    continue;
                }
                SpawnAttemptResult result = trySpawnInCell(store, world, npcPlugin, roleName, cell, players, nowNanos, maxAttempts, cellSize);
                attempts += result.attempts;
                if (result.spawned) {
                    clearSpawnExceptionBackoff(roleName);
                    spawned++;
                } else if ("spawnException".equals(result.reason)) {
                    break;
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
                        + " activeRoleNow=" + snapshotActiveAdepts(store, roleName).size()
                        + " densityCellSizeBlocks=" + cellSize
                        + " cellSpawnChancePercent=" + spawnChancePercent
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

    private SpawnAttemptResult trySpawnInCell(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull NPCPlugin npcPlugin,
        @Nonnull String roleName,
        @Nonnull SpawnCellCandidate cell,
        @Nonnull List<PlayerCandidate> allPlayers,
        long nowNanos,
        int maxAttempts,
        double cellSize
    ) {
        SpawnAttemptResult lastFailure = SpawnAttemptResult.fail("notAttempted", 0);
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            SpawnAttemptResult result = trySpawnAtCellSample(store, world, npcPlugin, roleName, cell, allPlayers, nowNanos, cellSize, maxAttempts, attempt);
            if (result.spawned) {
                return result;
            }
            lastFailure = result;
            if ("spawnException".equals(result.reason)) {
                return result;
            }
        }
        debug.traceFileOnly(
            null,
            "KuduAdeptSpawn event=cellSpawnFailed"
                + " roleName=" + roleName
                + " cellX=" + cell.key.x()
                + " cellZ=" + cell.key.z()
                + " attempts=" + maxAttempts
                + " lastReason=" + lastFailure.reason
                + " densityCellSizeBlocks=" + cellSize
                + " world=" + world.getName()
        );
        return SpawnAttemptResult.fail(lastFailure.reason, maxAttempts);
    }

    private SpawnAttemptResult trySpawnAtCellSample(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull NPCPlugin npcPlugin,
        @Nonnull String roleName,
        @Nonnull SpawnCellCandidate cell,
        @Nonnull List<PlayerCandidate> allPlayers,
        long nowNanos,
        double cellSize,
        int maxAttempts,
        int attempt
    ) {
        PlayerCandidate anchor = cell.anchor;
        Vector3d anchorPos = anchor.position;
        if (anchorPos == null || !anchorPos.isFinite()) {
            return SpawnAttemptResult.fail("anchorPosInvalid", attempt);
        }

        double minRadius = Math.max(0, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.radiusMinBlocks : 0);
        double maxRadius = Math.max(minRadius, config.kuduAdept.spawn != null ? config.kuduAdept.spawn.radiusMaxBlocks : minRadius);
        double minRadiusSq = minRadius * minRadius;
        double maxRadiusSq = maxRadius * maxRadius;

        int x;
        int z;
        if (attempt <= Math.max(1, (int) Math.floor(Math.max(1, maxAttempts) * 0.75))) {
            double radius = ThreadLocalRandom.current().nextDouble(minRadius, maxRadius + 0.0001);
            double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
            x = (int) Math.floor(anchorPos.x + Math.cos(angle) * radius);
            z = (int) Math.floor(anchorPos.z + Math.sin(angle) * radius);
            if (!cellKeyFor(x, z, cellSize).equals(cell.key)) {
                return SpawnAttemptResult.fail("outsideDensityCell", attempt);
            }
        } else {
            NearbyCellSampleBounds sampleBounds = getNearbyCellSampleBounds(cell, anchorPos, cellSize, maxRadius);
            if (sampleBounds == null) {
                return SpawnAttemptResult.fail("noNearbyCellArea", attempt);
            }
            x = ThreadLocalRandom.current().nextInt(sampleBounds.minX(), sampleBounds.maxX() + 1);
            z = ThreadLocalRandom.current().nextInt(sampleBounds.minZ(), sampleBounds.maxZ() + 1);
            double dxAnchor = anchorPos.x - x;
            double dzAnchor = anchorPos.z - z;
            double distanceSq = dxAnchor * dxAnchor + dzAnchor * dzAnchor;
            if (distanceSq < minRadiusSq || distanceSq > maxRadiusSq) {
                return SpawnAttemptResult.fail("outsideSpawnRadius", attempt);
            }
        }

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
                return SpawnAttemptResult.fail("tooCloseToPlayer", attempt);
            }
        }

        WorldChunk chunk = getChunkForSpawn(world, x, z);
        if (chunk == null) {
            return SpawnAttemptResult.fail("chunkNotLoaded", attempt);
        }

        int localX = x - (chunk.getX() * CHUNK_WIDTH_BLOCKS);
        int localZ = z - (chunk.getZ() * CHUNK_WIDTH_BLOCKS);

        int surfaceY = chunk.getHeight(localX, localZ);
        int y = clampY(surfaceY + 1);

        y = findAirColumn(chunk, x, y, z);
        if (y < MIN_Y) {
            return SpawnAttemptResult.fail("noAirColumn", attempt);
        }

        Vector3d spawnPosition = new Vector3d(x + 0.5, y, z + 0.5);
        Vector3f rotation = Vector3f.ZERO;

        it.unimi.dsi.fastutil.Pair<Ref<EntityStore>, ?> spawned;
        try {
            spawned = npcPlugin.spawnNPC(store, roleName, null, spawnPosition, rotation);
        } catch (Throwable t) {
            noteSpawnException(roleName);
            debug.traceFileOnly(
                null,
                "KuduAdeptSpawn event=spawnException"
                    + " roleName=" + roleName
                    + " x=" + x
                    + " y=" + y
                    + " z=" + z
                    + " cellX=" + cell.key.x()
                    + " cellZ=" + cell.key.z()
                    + " errorClass=" + t.getClass().getName()
                    + " errorMessage=" + sanitizeLogValue(t.getMessage())
                    + " world=" + world.getName()
            );
            return SpawnAttemptResult.fail("spawnException", attempt);
        }

        Ref<EntityStore> npcRef = spawned != null ? spawned.left() : null;
        if (npcRef == null || !npcRef.isValid()) {
            return SpawnAttemptResult.fail("spawnFailed", attempt);
        }

        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            try {
                store.removeEntity(npcRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            } catch (Throwable ignored) {
                // Best effort.
            }
            return SpawnAttemptResult.fail("npcComponentMissing", attempt);
        }

        UUIDComponent uuidComponent = store.getComponent(npcRef, UUIDComponent.getComponentType());
        UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
        if (uuid == null) {
            try {
                store.removeEntity(npcRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            } catch (Throwable ignored) {
                // Best effort.
            }
            return SpawnAttemptResult.fail("uuidMissing", attempt);
        }

        long lifetimeNanos = secondsToNanos(
            config.kuduAdept.despawn != null ? config.kuduAdept.despawn.afterSeconds : 0.0,
            0.0
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
                + " cellX=" + cell.key.x()
                + " cellZ=" + cell.key.z()
                + " densityCellSizeBlocks=" + cellSize
                + " anchorPlayer=" + anchor.playerUuid
                + " chunkX=" + chunk.getX()
                + " chunkZ=" + chunk.getZ()
                + " lifetimeSeconds=" + (config.kuduAdept.despawn != null ? config.kuduAdept.despawn.afterSeconds : 0.0)
                + " world=" + world.getName()
        );

        return SpawnAttemptResult.ok(uuid, attempt);
    }

    private boolean isSpawnExceptionBackoffActive(@Nonnull String roleName, long nowNanos) {
        String backoffRole = spawnExceptionBackoffRoleName;
        return backoffRole != null && backoffRole.equals(roleName) && nowNanos < nextSpawnExceptionRetryAtNanos;
    }

    private void noteSpawnException(@Nonnull String roleName) {
        spawnExceptionBackoffRoleName = roleName;
        nextSpawnExceptionRetryAtNanos = System.nanoTime() + SPAWN_EXCEPTION_BACKOFF_NANOS;
    }

    private void clearSpawnExceptionBackoff(@Nonnull String roleName) {
        String backoffRole = spawnExceptionBackoffRoleName;
        if (backoffRole == null || !backoffRole.equals(roleName)) {
            return;
        }
        spawnExceptionBackoffRoleName = null;
        nextSpawnExceptionRetryAtNanos = 0L;
    }

    private @Nonnull List<ActiveAdeptSnapshot> snapshotActiveAdepts(@Nonnull Store<EntityStore> store, @Nonnull String roleName) {
        ArrayList<ActiveAdeptSnapshot> out = new ArrayList<>();
        Query<EntityStore> query = Query.and(
            NPCEntity.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> visitor = (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                String npcRoleName;
                try {
                    npcRoleName = npc != null ? npc.getRoleName() : null;
                } catch (Throwable ignored) {
                    npcRoleName = null;
                }
                if (!roleName.equals(npcRoleName)) {
                    continue;
                }

                UUIDComponent uuidComponent = chunk.getComponent(i, UUIDComponent.getComponentType());
                UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                Vector3d position = transform != null ? transform.getPosition() : null;
                if (uuid == null || position == null || !position.isFinite()) {
                    continue;
                }
                out.add(new ActiveAdeptSnapshot(uuid, position, bondState.getByAdept(uuid) != null));
            }
        };
        store.forEachChunk(query, visitor);
        return out;
    }

    private @Nonnull List<SpawnCellCandidate> findMissingSpawnCells(
        @Nonnull List<PlayerCandidate> players,
        @Nonnull List<ActiveAdeptSnapshot> activeAdepts,
        double cellSize
    ) {
        Set<CellKey> occupiedWildCells = new HashSet<>();
        for (ActiveAdeptSnapshot adept : activeAdepts) {
            if (adept == null || adept.bonded || adept.position == null || !adept.position.isFinite()) {
                continue;
            }
            occupiedWildCells.add(cellKeyFor(adept.position, cellSize));
        }

        Set<CellKey> queued = new HashSet<>();
        ArrayList<SpawnCellCandidate> out = new ArrayList<>();
        for (PlayerCandidate player : players) {
            if (player == null || player.position == null || !player.position.isFinite()) {
                continue;
            }
            CellKey key = cellKeyFor(player.position, cellSize);
            if (occupiedWildCells.contains(key) || !queued.add(key)) {
                continue;
            }
            out.add(new SpawnCellCandidate(key, player));
        }
        return out;
    }

    private double getDensityCellSizeBlocks() {
        double cellSize = config.kuduAdept.spawn != null ? config.kuduAdept.spawn.densityCellSizeBlocks : DEFAULT_DENSITY_CELL_SIZE_BLOCKS;
        if (!Double.isFinite(cellSize) || cellSize < 32.0) {
            return DEFAULT_DENSITY_CELL_SIZE_BLOCKS;
        }
        return cellSize;
    }

    private int getCellSpawnChancePercent() {
        int chance = config.kuduAdept.spawn != null ? config.kuduAdept.spawn.cellSpawnChancePercent : 100;
        return Math.max(0, Math.min(100, chance));
    }

    private static @Nonnull CellKey cellKeyFor(@Nonnull Vector3d position, double cellSize) {
        return cellKeyFor((int) Math.floor(position.x), (int) Math.floor(position.z), cellSize);
    }

    private static @Nonnull CellKey cellKeyFor(int x, int z, double cellSize) {
        double size = Double.isFinite(cellSize) && cellSize > 0 ? cellSize : DEFAULT_DENSITY_CELL_SIZE_BLOCKS;
        return new CellKey((int) Math.floor(x / size), (int) Math.floor(z / size));
    }

    private static @Nullable NearbyCellSampleBounds getNearbyCellSampleBounds(
        @Nonnull SpawnCellCandidate cell,
        @Nonnull Vector3d anchorPos,
        double cellSize,
        double maxRadius
    ) {
        double size = Double.isFinite(cellSize) && cellSize > 0 ? cellSize : DEFAULT_DENSITY_CELL_SIZE_BLOCKS;
        double radius = Double.isFinite(maxRadius) ? Math.max(0.0, maxRadius) : 0.0;
        double cellMinX = cell.key.x() * size;
        double cellMinZ = cell.key.z() * size;
        double cellMaxX = cellMinX + size;
        double cellMaxZ = cellMinZ + size;
        int minX = (int) Math.ceil(Math.max(cellMinX, anchorPos.x - radius));
        int maxX = (int) Math.floor(Math.min(Math.nextAfter(cellMaxX, Double.NEGATIVE_INFINITY), anchorPos.x + radius));
        int minZ = (int) Math.ceil(Math.max(cellMinZ, anchorPos.z - radius));
        int maxZ = (int) Math.floor(Math.min(Math.nextAfter(cellMaxZ, Double.NEGATIVE_INFINITY), anchorPos.z + radius));
        if (minX > maxX || minZ > maxZ) {
            return null;
        }
        return new NearbyCellSampleBounds(minX, maxX, minZ, maxZ);
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

    private static boolean isSpawnTimeAllowed(@Nonnull WorldTimeResource worldTime, double sunlightThreshold) {
        try {
            if (Double.isFinite(sunlightThreshold) && sunlightThreshold <= 0.0) {
                return true;
            }
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

    private static @Nonnull String sanitizeLogValue(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "<none>";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private record PlayerCandidate(@Nonnull UUID playerUuid, @Nonnull Vector3d position) {
    }

    private record ActiveAdeptSnapshot(@Nonnull UUID uuid, @Nonnull Vector3d position, boolean bonded) {
    }

    private record CellKey(int x, int z) {
    }

    private record SpawnCellCandidate(@Nonnull CellKey key, @Nonnull PlayerCandidate anchor) {
    }

    private record NearbyCellSampleBounds(int minX, int maxX, int minZ, int maxZ) {
    }

    private record SpawnAttemptResult(boolean spawned, @Nonnull String reason, @Nullable UUID uuid, int attempts) {
        static SpawnAttemptResult ok(@Nonnull UUID uuid, int attempts) {
            return new SpawnAttemptResult(true, "ok", uuid, attempts);
        }

        static SpawnAttemptResult fail(@Nonnull String reason, int attempts) {
            return new SpawnAttemptResult(false, reason, null, attempts);
        }
    }
}
