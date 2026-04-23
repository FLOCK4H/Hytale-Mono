package org.example.plugin;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chunk-time worldgen for the Axo Tales surface Arcane Crystal block.
 *
 * <p>Crystals are persistent resource nodes. They are generated above the terrain surface in new chunks and, when
 * enabled by config, in already-generated chunks as they are pre-loaded.</p>
 */
public final class CustomPlaceholderBlockWorldgen {

    public static final String BLOCK_ITEM_ID = "Rock_Crystal_Arcane_Large";

    private static final int CHUNK_WIDTH_BLOCKS = ChunkUtil.SIZE;
    private static final int LOCAL_MIN = 1;
    private static final int LOCAL_MAX = ChunkUtil.SIZE_MINUS_1 - 1;
    private static final int WORLD_MIN_Y = 1;
    private static final int WORLD_MAX_Y = ChunkUtil.HEIGHT_MINUS_1;
    private static final int SURFACE_Y_OFFSET = 1;
    private static final int PILLAR_HEIGHT_BLOCKS = 1;
    private static final long SEED_SALT = 0xA0B0_C0D0_E0F0_1234L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig serverConfig;
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>> processedChunksByWorldAndChunk = new ConcurrentHashMap<>();
    private final AtomicInteger eventDebugBudget = new AtomicInteger(100);
    private final AtomicInteger placementDebugBudget = new AtomicInteger(200);
    private final AtomicInteger errorDebugBudget = new AtomicInteger(50);

    public CustomPlaceholderBlockWorldgen(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig serverConfig
    ) {
        this.errors = errors;
        this.debug = debug;
        this.serverConfig = serverConfig;
    }

    public void onChunkPreLoad(@Nonnull ChunkPreLoadProcessEvent event) {
        WorldChunk chunk = event.getChunk();
        if (chunk == null) {
            return;
        }

        World world = chunk.getWorld();
        if (world == null) {
            return;
        }

        boolean newlyGenerated = event.isNewlyGenerated();
        boolean shouldGenerate = newlyGenerated || processExistingChunks();
        boolean shouldPruneLegacy = !newlyGenerated && pruneLegacyClusters();
        if (!shouldGenerate && !shouldPruneLegacy) {
            return;
        }

        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);

        if (eventDebugBudget.getAndDecrement() > 0) {
            debug.traceFileOnly(
                null,
                "Worldgen.ArcaneCrystal event=ChunkPreLoadProcess"
                    + " newlyGenerated=" + newlyGenerated
                    + " processExistingChunks=" + processExistingChunks()
                    + " pruneLegacyClusters=" + pruneLegacyClusters()
                    + " chancePerNewChunk=" + chancePerNewChunk()
                    + " placementsPerChunk=" + placementsPerChunk()
                    + " densityRadiusBlocks=" + densityRadiusBlocks()
                    + " maxPlacementsPerRadius=" + maxPlacementsPerRadius()
                    + " chunkX=" + chunkX
                    + " chunkZ=" + chunkZ
                    + " world=" + world.getName()
            );
        }

        Runnable work = () -> {
            try {
                WorldChunk current = chunk;
                String chunkRef = "event";

                WorldChunk resolved = world.getChunkIfLoaded(chunkIndex);
                if (resolved == null) {
                    resolved = world.getChunkIfInMemory(chunkIndex);
                }
                if (resolved != null) {
                    current = resolved;
                    chunkRef = "world";
                }

                if (!markChunkProcessed(world, chunkIndex)) {
                    return;
                }

                int prunedLegacy = shouldPruneLegacy ? pruneLegacyCrystalClusters(world, current) : 0;
                PlacementSummary summary = shouldGenerate
                    ? maybePlaceInChunk(world, current, newlyGenerated)
                    : PlacementSummary.skipped(
                        "generationDisabled",
                        chancePerNewChunk(),
                        placementsPerChunk(),
                        densityRadiusBlocks(),
                        maxPlacementsPerRadius(),
                        newlyGenerated
                    );
                summary = summary.withPrunedLegacy(prunedLegacy);
                maybeLogPlacement(world, current, chunkRef, summary);
            } catch (Throwable t) {
                errors.report(
                    (com.hypixel.hytale.server.core.universe.PlayerRef) null,
                    "CustomPlaceholderBlockWorldgen: failed for chunk x=" + chunkX + " z=" + chunkZ,
                    t
                );
            }
        };

        if (world.isInThread()) {
            work.run();
        } else {
            world.execute(work);
        }
    }

    private @Nonnull PlacementSummary maybePlaceInChunk(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        boolean newlyGenerated
    ) {
        double chance = chancePerNewChunk();
        int target = placementsPerChunk();
        int radiusBlocks = densityRadiusBlocks();
        int maxPerRadius = maxPlacementsPerRadius();

        if (chance <= 0.0 || target <= 0 || radiusBlocks <= 0 || maxPerRadius <= 0) {
            return PlacementSummary.skipped("configNoop", chance, target, radiusBlocks, maxPerRadius, newlyGenerated);
        }

        int crystalTypeId = crystalTypeId();
        if (crystalTypeId < 0) {
            return PlacementSummary.skipped("missingBlockType", chance, target, radiusBlocks, maxPerRadius, newlyGenerated);
        }

        if (maxPerRadius == 1) {
            return maybePlaceSingleDensityCell(world, chunk, chance, target, radiusBlocks, maxPerRadius, newlyGenerated, crystalTypeId);
        }

        SplittableRandom random = new SplittableRandom(seedFor(world, chunk));
        if (random.nextDouble() > chance) {
            return PlacementSummary.skipped("chanceSkip", chance, target, radiusBlocks, maxPerRadius, newlyGenerated);
        }

        int existing = countExistingCrystalColumnsNearSurface(chunk, crystalTypeId);
        int remaining = Math.max(0, Math.min(target, maxPerRadius) - existing);
        if (remaining <= 0) {
            return new PlacementSummary("enoughExisting", chance, target, radiusBlocks, maxPerRadius, newlyGenerated, existing, 0, 0, 0, 0, 0);
        }

        int placed = 0;
        int attempts = 0;
        int failedPlacements = 0;
        int densityBlocked = 0;
        int maxAttempts = Math.min(128, Math.max(remaining, remaining * 16));
        for (int attempt = 0; attempt < maxAttempts && placed < remaining; attempt++) {
            attempts++;
            int localX = randomLocal(random);
            int localZ = randomLocal(random);

            int surfaceY;
            try {
                surfaceY = chunk.getHeight(localX, localZ);
            } catch (Throwable ignored) {
                failedPlacements++;
                continue;
            }

            int globalX = chunk.getX() * CHUNK_WIDTH_BLOCKS + localX;
            int globalZ = chunk.getZ() * CHUNK_WIDTH_BLOCKS + localZ;
            int nearbyCrystals = countExistingCrystalColumnsInRadius(world, globalX, globalZ, radiusBlocks, crystalTypeId);
            if (nearbyCrystals >= maxPerRadius) {
                densityBlocked++;
                continue;
            }

            int placedInColumn = placeMarkerColumn(world, chunk, localX, localZ, null, "chunk-gen", crystalTypeId);
            if (placedInColumn > 0) {
                placed += placedInColumn;
            } else {
                failedPlacements++;
            }
        }

        return new PlacementSummary(
            "ok",
            chance,
            target,
            radiusBlocks,
            maxPerRadius,
            newlyGenerated,
            existing,
            placed,
            attempts,
            failedPlacements,
            densityBlocked,
            0
        );
    }

    private @Nonnull PlacementSummary maybePlaceSingleDensityCell(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        double chance,
        int target,
        int radiusBlocks,
        int maxPerRadius,
        boolean newlyGenerated,
        int crystalTypeId
    ) {
        int cellSize = Math.max(CHUNK_WIDTH_BLOCKS, radiusBlocks);
        int chunkMinX = chunk.getX() * CHUNK_WIDTH_BLOCKS;
        int chunkMinZ = chunk.getZ() * CHUNK_WIDTH_BLOCKS;
        int chunkMaxX = chunkMinX + CHUNK_WIDTH_BLOCKS - 1;
        int chunkMaxZ = chunkMinZ + CHUNK_WIDTH_BLOCKS - 1;
        int minCellX = Math.floorDiv(chunkMinX, cellSize);
        int maxCellX = Math.floorDiv(chunkMaxX, cellSize);
        int minCellZ = Math.floorDiv(chunkMinZ, cellSize);
        int maxCellZ = Math.floorDiv(chunkMaxZ, cellSize);

        int cellsChecked = 0;
        int chanceSkipped = 0;
        int notOwner = 0;
        int densityBlocked = 0;
        int failedPlacements = 0;
        int placed = 0;

        for (int cellX = minCellX; cellX <= maxCellX && placed < target; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ && placed < target; cellZ++) {
                cellsChecked++;
                SplittableRandom cellRandom = new SplittableRandom(seedForCell(world, cellX, cellZ));
                if (cellRandom.nextDouble() > chance) {
                    chanceSkipped++;
                    continue;
                }

                int globalX = candidateCoordinateForCell(cellX, cellSize);
                int globalZ = candidateCoordinateForCell(cellZ, cellSize);
                if (globalX < chunkMinX || globalX > chunkMaxX || globalZ < chunkMinZ || globalZ > chunkMaxZ) {
                    notOwner++;
                    continue;
                }

                int nearbyCrystals = countExistingCrystalColumnsInRadius(world, globalX, globalZ, radiusBlocks, crystalTypeId);
                if (nearbyCrystals >= maxPerRadius) {
                    densityBlocked++;
                    continue;
                }

                int localX = Math.floorMod(globalX, CHUNK_WIDTH_BLOCKS);
                int localZ = Math.floorMod(globalZ, CHUNK_WIDTH_BLOCKS);
                int placedInColumn = placeMarkerColumn(world, chunk, localX, localZ, null, "density-cell", crystalTypeId);
                if (placedInColumn > 0) {
                    placed += placedInColumn;
                } else {
                    failedPlacements++;
                }
            }
        }

        String reason;
        if (placed > 0) {
            reason = "okDensityCell";
        } else if (densityBlocked > 0) {
            reason = "densityBlocked";
        } else if (chanceSkipped > 0 && cellsChecked == chanceSkipped) {
            reason = "chanceSkipDensityCell";
        } else if (notOwner > 0) {
            reason = "notDensityCellOwner";
        } else {
            reason = "noDensityCellCandidate";
        }

        return new PlacementSummary(
            reason,
            chance,
            target,
            radiusBlocks,
            maxPerRadius,
            newlyGenerated,
            0,
            placed,
            cellsChecked,
            failedPlacements,
            densityBlocked,
            0
        );
    }

    private void maybeLogPlacement(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        @Nonnull String chunkRef,
        @Nonnull PlacementSummary summary
    ) {
        if (placementDebugBudget.getAndDecrement() <= 0) {
            return;
        }

        debug.traceFileOnly(
            null,
            "Worldgen.ArcaneCrystal"
                + " event=processChunk"
                + " chunkRef=" + chunkRef
                + " blockId=" + BLOCK_ITEM_ID
                + " surfaceYOffset=" + SURFACE_Y_OFFSET
                + " pillarHeightBlocks=" + PILLAR_HEIGHT_BLOCKS
                + " chunkX=" + chunk.getX()
                + " chunkZ=" + chunk.getZ()
                + " world=" + world.getName()
                + " " + summary.summarize()
        );
    }

    private boolean processExistingChunks() {
        if (serverConfig == null || serverConfig.worldgen == null) {
            return false;
        }
        return serverConfig.worldgen.arcaneCrystalProcessExistingChunks;
    }

    private boolean pruneLegacyClusters() {
        if (serverConfig == null || serverConfig.worldgen == null) {
            return true;
        }
        return serverConfig.worldgen.arcaneCrystalPruneLegacyClusters;
    }

    private double chancePerNewChunk() {
        if (serverConfig == null || serverConfig.worldgen == null) {
            return 0.33;
        }
        return serverConfig.worldgen.arcaneCrystalChancePerNewChunk;
    }

    private int placementsPerChunk() {
        if (serverConfig == null || serverConfig.worldgen == null) {
            return 1;
        }
        return serverConfig.worldgen.arcaneCrystalPlacementsPerChunk;
    }

    private int densityRadiusBlocks() {
        if (serverConfig == null || serverConfig.worldgen == null) {
            return 64;
        }
        return serverConfig.worldgen.arcaneCrystalDensityRadiusBlocks;
    }

    private int maxPlacementsPerRadius() {
        if (serverConfig == null || serverConfig.worldgen == null) {
            return 1;
        }
        return serverConfig.worldgen.arcaneCrystalMaxPlacementsPerRadius;
    }

    /**
     * Forces a visible marker placement at the specified local column in a loaded chunk.
     *
     * <p>Intended for debugging when you cannot find any naturally generated crystal markers.</p>
     *
     * <p>Must be called on the world thread.</p>
     */
    public int placeMarkerAtPlayerColumn(@Nonnull World world, @Nonnull WorldChunk chunk, int localX, int localZ, @Nonnull PlayerRef player) {
        int crystalTypeId = crystalTypeId();
        if (crystalTypeId < 0) {
            debug.traceFileOnly(player, "Worldgen.ArcaneCrystal command failed reason=missingBlockType blockId=" + BLOCK_ITEM_ID);
            return 0;
        }
        return placeMarkerColumn(world, chunk, localX, localZ, player, "command", crystalTypeId);
    }

    private int placeMarkerColumn(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int localX,
        int localZ,
        @Nullable PlayerRef player,
        @Nonnull String reason,
        int crystalTypeId
    ) {
        // Heightmap is per-chunk column; place above surface so the crystal is visible.
        // Avoid chunk borders to prevent accidental out-of-range neighbor math in engine internals.
        int safeLocalX = clampLocal(localX);
        int safeLocalZ = clampLocal(localZ);

        int surfaceY;
        try {
            surfaceY = chunk.getHeight(safeLocalX, safeLocalZ);
        } catch (Throwable t) {
            if (errorDebugBudget.getAndDecrement() > 0) {
                debug.traceFileOnly(
                    player,
                    "Worldgen.ArcaneCrystal failed"
                        + " reason=" + reason
                        + " error=getHeightException"
                        + " localX=" + safeLocalX
                        + " localZ=" + safeLocalZ
                        + " chunkX=" + chunk.getX()
                        + " chunkZ=" + chunk.getZ()
                        + " world=" + world.getName()
                        + " exception=" + t.getClass().getSimpleName()
                        + " message=" + (t.getMessage() != null ? t.getMessage() : "null")
                );
            }
            return 0;
        }

        if (columnHasCrystalNearSurface(chunk, safeLocalX, safeLocalZ, surfaceY, crystalTypeId)) {
            return 0;
        }

        int baseY = clampY(surfaceY + SURFACE_Y_OFFSET);
        int globalX = chunk.getX() * CHUNK_WIDTH_BLOCKS + safeLocalX;
        int globalZ = chunk.getZ() * CHUNK_WIDTH_BLOCKS + safeLocalZ;

        int placed = 0;
        for (int i = 0; i < PILLAR_HEIGHT_BLOCKS; i++) {
            int y = clampY(baseY + i);
            boolean ok;
            try {
                ok = chunk.setBlock(safeLocalX, y, safeLocalZ, crystalTypeId);
            } catch (Throwable t) {
                if (errorDebugBudget.getAndDecrement() > 0) {
                    debug.traceFileOnly(
                        player,
                        "Worldgen.ArcaneCrystal failed"
                            + " reason=" + reason
                            + " error=setBlockException"
                            + " localX=" + safeLocalX
                            + " localZ=" + safeLocalZ
                            + " y=" + y
                            + " blockId=" + BLOCK_ITEM_ID
                            + " blockTypeId=" + crystalTypeId
                            + " chunkX=" + chunk.getX()
                            + " chunkZ=" + chunk.getZ()
                            + " world=" + world.getName()
                            + " exception=" + t.getClass().getSimpleName()
                            + " message=" + (t.getMessage() != null ? t.getMessage() : "null")
                    );
                }
                break;
            }
            if (!ok) {
                break;
            }
            placed++;
        }

        if (placed > 0 && player != null) {
            debug.traceFileOnly(
                player,
                "Worldgen.ArcaneCrystal placed"
                    + " reason=" + reason
                    + " placed=" + placed
                    + " blockId=" + BLOCK_ITEM_ID
                    + " x=" + globalX
                    + " y=" + baseY
                    + " z=" + globalZ
                    + " chunkX=" + chunk.getX()
                    + " chunkZ=" + chunk.getZ()
                    + " world=" + world.getName()
            );
        }

        return placed;
    }

    private int countExistingCrystalColumnsNearSurface(@Nonnull WorldChunk chunk, int crystalTypeId) {
        int found = 0;
        for (int localX = LOCAL_MIN; localX <= LOCAL_MAX; localX++) {
            for (int localZ = LOCAL_MIN; localZ <= LOCAL_MAX; localZ++) {
                int surfaceY;
                try {
                    surfaceY = chunk.getHeight(localX, localZ);
                } catch (Throwable ignored) {
                    continue;
                }
                if (columnHasCrystalNearSurface(chunk, localX, localZ, surfaceY, crystalTypeId)) {
                    found++;
                }
            }
        }
        return found;
    }

    private int countExistingCrystalColumnsInRadius(
        @Nonnull World world,
        int centerX,
        int centerZ,
        int radiusBlocks,
        int crystalTypeId
    ) {
        int radius = Math.max(1, radiusBlocks);
        int radiusSquared = radius * radius;
        int chunkRadius = Math.max(1, (radius + CHUNK_WIDTH_BLOCKS - 1) / CHUNK_WIDTH_BLOCKS);
        int centerChunkX = Math.floorDiv(centerX, CHUNK_WIDTH_BLOCKS);
        int centerChunkZ = Math.floorDiv(centerZ, CHUNK_WIDTH_BLOCKS);
        int found = 0;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
                WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                if (chunk == null) {
                    chunk = world.getChunkIfInMemory(chunkIndex);
                }
                if (chunk == null) {
                    continue;
                }

                for (int localX = LOCAL_MIN; localX <= LOCAL_MAX; localX++) {
                    int globalX = chunk.getX() * CHUNK_WIDTH_BLOCKS + localX;
                    int dx = globalX - centerX;
                    if (Math.abs(dx) > radius) {
                        continue;
                    }
                    for (int localZ = LOCAL_MIN; localZ <= LOCAL_MAX; localZ++) {
                        int globalZ = chunk.getZ() * CHUNK_WIDTH_BLOCKS + localZ;
                        int dz = globalZ - centerZ;
                        if (Math.abs(dz) > radius || dx * dx + dz * dz > radiusSquared) {
                            continue;
                        }

                        int surfaceY;
                        try {
                            surfaceY = chunk.getHeight(localX, localZ);
                        } catch (Throwable ignored) {
                            continue;
                        }
                        if (columnHasCrystalNearSurface(chunk, localX, localZ, surfaceY, crystalTypeId)) {
                            found++;
                        }
                    }
                }
            }
        }

        return found;
    }

    private boolean columnHasCrystalNearSurface(@Nonnull WorldChunk chunk, int localX, int localZ, int surfaceY, int crystalTypeId) {
        int fromY = clampY(surfaceY - 4);
        int toY = clampY(surfaceY + 2);
        for (int y = fromY; y <= toY; y++) {
            try {
                if (chunk.getBlock(localX, y, localZ) == crystalTypeId) {
                    return true;
                }
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private int pruneLegacyCrystalClusters(@Nonnull World world, @Nonnull WorldChunk chunk) {
        int crystalTypeId = crystalTypeId();
        if (crystalTypeId < 0 || maxPlacementsPerRadius() != 1) {
            return 0;
        }

        int radiusBlocks = Math.max(1, densityRadiusBlocks());
        int cellSize = Math.max(CHUNK_WIDTH_BLOCKS, radiusBlocks);
        double chance = chancePerNewChunk();
        int pruned = 0;

        for (int localX = LOCAL_MIN; localX <= LOCAL_MAX; localX++) {
            for (int localZ = LOCAL_MIN; localZ <= LOCAL_MAX; localZ++) {
                int surfaceY;
                try {
                    surfaceY = chunk.getHeight(localX, localZ);
                } catch (Throwable ignored) {
                    continue;
                }

                int globalX = chunk.getX() * CHUNK_WIDTH_BLOCKS + localX;
                int globalZ = chunk.getZ() * CHUNK_WIDTH_BLOCKS + localZ;
                if (isAllowedDensityCellSite(world, globalX, globalZ, cellSize, chance)) {
                    continue;
                }

                int fromY = clampY(surfaceY - 4);
                int toY = clampY(surfaceY + 2);
                for (int y = fromY; y <= toY; y++) {
                    try {
                        if (chunk.getBlock(localX, y, localZ) == crystalTypeId
                            && chunk.setBlock(localX, y, localZ, BlockType.EMPTY_ID)) {
                            pruned++;
                        }
                    } catch (Throwable ignored) {
                        // Best-effort legacy cleanup.
                    }
                }
            }
        }

        return pruned;
    }

    private static boolean isAllowedDensityCellSite(
        @Nonnull World world,
        int globalX,
        int globalZ,
        int cellSize,
        double chance
    ) {
        int cellX = Math.floorDiv(globalX, cellSize);
        int cellZ = Math.floorDiv(globalZ, cellSize);
        int expectedX = candidateCoordinateForCell(cellX, cellSize);
        int expectedZ = candidateCoordinateForCell(cellZ, cellSize);
        if (globalX != expectedX || globalZ != expectedZ) {
            return false;
        }

        SplittableRandom cellRandom = new SplittableRandom(seedForCell(world, cellX, cellZ));
        return cellRandom.nextDouble() <= chance;
    }

    private static int candidateCoordinateForCell(int cell, int cellSize) {
        int coordinate = cell * cellSize + (cellSize / 2);
        int local = Math.floorMod(coordinate, CHUNK_WIDTH_BLOCKS);
        if (local < LOCAL_MIN) {
            coordinate += LOCAL_MIN - local;
        } else if (local > LOCAL_MAX) {
            coordinate -= local - LOCAL_MAX;
        }
        return coordinate;
    }

    private boolean markChunkProcessed(@Nonnull World world, long chunkIndex) {
        String key = worldKey(world);
        return processedChunksByWorldAndChunk
            .computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
            .putIfAbsent(chunkIndex, Boolean.TRUE) == null;
    }

    private static int crystalTypeId() {
        return BlockType.getAssetMap().getIndexOrDefault(BLOCK_ITEM_ID, -1);
    }

    private static int randomLocal(@Nonnull SplittableRandom random) {
        int range = Math.max(1, LOCAL_MAX - LOCAL_MIN + 1);
        return LOCAL_MIN + random.nextInt(range);
    }

    private static int clampY(int y) {
        return Math.max(WORLD_MIN_Y, Math.min(WORLD_MAX_Y, y));
    }

    private static int clampLocal(int local) {
        return Math.max(LOCAL_MIN, Math.min(LOCAL_MAX, local));
    }

    private static long seedFor(@Nonnull World world, @Nonnull WorldChunk chunk) {
        long seed = SEED_SALT;

        UUID worldUuid = worldUuid(world);
        if (worldUuid != null) {
            seed ^= worldUuid.getMostSignificantBits();
            seed ^= worldUuid.getLeastSignificantBits();
        } else {
            seed ^= world.getName().hashCode();
        }

        seed ^= (long) chunk.getX() * 0x9E3779B97F4A7C15L;
        seed ^= (long) chunk.getZ() * 0xC2B2AE3D27D4EB4FL;
        return seed;
    }

    private static long seedForCell(@Nonnull World world, int cellX, int cellZ) {
        long seed = SEED_SALT ^ 0x5DEECE66DL;

        UUID worldUuid = worldUuid(world);
        if (worldUuid != null) {
            seed ^= worldUuid.getMostSignificantBits();
            seed ^= worldUuid.getLeastSignificantBits();
        } else {
            seed ^= world.getName().hashCode();
        }

        seed ^= (long) cellX * 0x9E3779B97F4A7C15L;
        seed ^= (long) cellZ * 0xC2B2AE3D27D4EB4FL;
        return seed;
    }

    @Nullable
    private static UUID worldUuid(@Nonnull World world) {
        try {
            var config = world.getWorldConfig();
            return config != null ? config.getUuid() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String worldKey(@Nonnull World world) {
        UUID uuid = worldUuid(world);
        return uuid != null ? uuid.toString() : world.getName();
    }

    private record PlacementSummary(
        @Nonnull String reason,
        double chancePerNewChunk,
        int targetPlacementsPerChunk,
        int densityRadiusBlocks,
        int maxPlacementsPerRadius,
        boolean newlyGenerated,
        int existingCrystalColumns,
        int placed,
        int attempts,
        int failedPlacements,
        int densityBlocked,
        int prunedLegacyCrystalBlocks
    ) {
        static @Nonnull PlacementSummary skipped(
            @Nonnull String reason,
            double chancePerNewChunk,
            int targetPlacementsPerChunk,
            int densityRadiusBlocks,
            int maxPlacementsPerRadius,
            boolean newlyGenerated
        ) {
            return new PlacementSummary(
                reason,
                chancePerNewChunk,
                targetPlacementsPerChunk,
                densityRadiusBlocks,
                maxPlacementsPerRadius,
                newlyGenerated,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }

        @Nonnull
        PlacementSummary withPrunedLegacy(int prunedLegacyCrystalBlocks) {
            return new PlacementSummary(
                reason,
                chancePerNewChunk,
                targetPlacementsPerChunk,
                densityRadiusBlocks,
                maxPlacementsPerRadius,
                newlyGenerated,
                existingCrystalColumns,
                placed,
                attempts,
                failedPlacements,
                densityBlocked,
                prunedLegacyCrystalBlocks
            );
        }

        @Nonnull
        String summarize() {
            return "reason=" + reason
                + " newlyGenerated=" + newlyGenerated
                + " chancePerNewChunk=" + chancePerNewChunk
                + " targetPlacementsPerChunk=" + targetPlacementsPerChunk
                + " densityRadiusBlocks=" + densityRadiusBlocks
                + " maxPlacementsPerRadius=" + maxPlacementsPerRadius
                + " existingCrystalColumns=" + existingCrystalColumns
                + " placed=" + placed
                + " attempts=" + attempts
                + " failedPlacements=" + failedPlacements
                + " densityBlocked=" + densityBlocked
                + " prunedLegacyCrystalBlocks=" + prunedLegacyCrystalBlocks;
        }
    }
}
