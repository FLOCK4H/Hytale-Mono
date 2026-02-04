package org.example.plugin;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
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
 * Chunk-time worldgen for Arcane Matter ore blocks.
 *
 * <p>Runs during {@link ChunkPreLoadProcessEvent} and replaces existing host rock blocks with the configured ore
 * blocks. By default, it can also process already-generated chunks (configurable) because worlds often have many
 * chunks generated before the plugin finishes loading.</p>
 */
public final class ArcaneMatterOreWorldgen {

    private static final int LOCAL_MIN = 1;
    private static final int LOCAL_MAX = ChunkUtil.SIZE_MINUS_1 - 1;
    private static final int WORLD_MIN_Y = 1;
    private static final int WORLD_MAX_Y = ChunkUtil.HEIGHT_MINUS_1;
    private static final long SEED_SALT = 0x4A72_69B1_0C2D_5E13L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig serverConfig;

    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>> processedChunksByWorldAndChunk = new ConcurrentHashMap<>();
    private final AtomicInteger eventLogBudget = new AtomicInteger(100);
    private final AtomicInteger placementLogBudget = new AtomicInteger(200);
    private final AtomicInteger errorLogBudget = new AtomicInteger(100);

    public ArcaneMatterOreWorldgen(
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

        AxoTalesServerConfig.Worldgen.ArcaneMatterOres config = getConfig();
        boolean newlyGenerated = event.isNewlyGenerated();

        if (eventLogBudget.getAndDecrement() > 0) {
            debug.traceFileOnly(
                null,
                "Worldgen.ArcaneMatter event=ChunkPreLoadProcess"
                    + " newlyGenerated=" + newlyGenerated
                    + " chunkX=" + chunk.getX()
                    + " chunkZ=" + chunk.getZ()
                    + " world=" + world.getName()
                    + " enabled=" + (config != null && config.enabled)
                    + " processExistingChunks=" + (config != null && config.processExistingChunks)
            );
        }

        if (config == null || !config.enabled) {
            return;
        }
        if (!newlyGenerated && !config.processExistingChunks) {
            return;
        }

        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);

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

                PlacementAttempt stone = attemptStoneOres(world, current, config);
                PlacementAttempt volcanic = attemptVolcanicOres(world, current, config);

                if (placementLogBudget.getAndDecrement() > 0) {
                    debug.traceFileOnly(
                        null,
                        "Worldgen.ArcaneMatter"
                            + " event=processChunk"
                            + " newlyGenerated=" + newlyGenerated
                            + " chunkRef=" + chunkRef
                            + " chunkX=" + current.getX()
                            + " chunkZ=" + current.getZ()
                            + " world=" + world.getName()
                            + " stone=" + stone.summarize()
                            + " volcanic=" + volcanic.summarize()
                    );
                }
            } catch (Throwable t) {
                errors.report(
                    (com.hypixel.hytale.server.core.universe.PlayerRef) null,
                    "ArcaneMatterOreWorldgen: failed for chunk x=" + chunkX + " z=" + chunkZ,
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

    private @Nonnull PlacementAttempt attemptStoneOres(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        @Nonnull AxoTalesServerConfig.Worldgen.ArcaneMatterOres config
    ) {
        AxoTalesServerConfig.Worldgen.ArcaneMatterOres.Stone stone = config.stone;
        if (stone == null || !stone.enabled) {
            return PlacementAttempt.skipped("stone", config.stoneOreBlockId, "disabled", true, true);
        }

        if (stone.chancePerNewChunk <= 0.0 || stone.targetPlacementsPerChunk <= 0 || stone.maxAttemptsPerChunk <= 0) {
            return PlacementAttempt.skipped("stone", config.stoneOreBlockId, "configNoop", true, stone.requireAdjacentAir);
        }

        SplittableRandom random = new SplittableRandom(seedFor(world, chunk) ^ 0xC41A_1C2FL);
        if (random.nextDouble() > clamp01(stone.chancePerNewChunk)) {
            return PlacementAttempt.skipped("stone", config.stoneOreBlockId, "chanceSkip", stone.matchAnyRock, stone.requireAdjacentAir);
        }

        int oreTypeId = BlockType.getAssetMap().getIndexOrDefault(config.stoneOreBlockId, -1);
        if (oreTypeId < 0) {
            if (errorLogBudget.getAndDecrement() > 0) {
                debug.traceFileOnly(null, "Worldgen.ArcaneMatter missingOreBlockId variant=stone id=" + config.stoneOreBlockId);
            }
            return PlacementAttempt.skipped("stone", config.stoneOreBlockId, "missingOreBlockId", stone.matchAnyRock, stone.requireAdjacentAir);
        }

        int[] hostIds = stone.matchAnyRock ? new int[0] : resolveHostBlockTypeIds("stone", stone.hostBlockIds);
        if (!stone.matchAnyRock && hostIds.length == 0) {
            return PlacementAttempt.skipped("stone", config.stoneOreBlockId, "noHostIdsResolved", false, stone.requireAdjacentAir);
        }

        int yMin = clampY(stone.minY);
        int yMax = clampY(stone.maxY);
        if (yMax < yMin) {
            int tmp = yMin;
            yMin = yMax;
            yMax = tmp;
        }

        int placed = 0;
        int attempts = 0;
        int hostHits = 0;
        int airHits = 0;
        int setBlockFalse = 0;
        int getBlockExceptions = 0;
        int setBlockExceptions = 0;
        String sampleBlockKey = null;

        int target = Math.min(stone.targetPlacementsPerChunk, stone.maxAttemptsPerChunk);
        for (int attempt = 0; attempt < stone.maxAttemptsPerChunk && placed < target; attempt++) {
            attempts++;
            int localX = randomLocal(random);
            int localZ = randomLocal(random);
            int y = random.nextInt(yMax - yMin + 1) + yMin;

            int current;
            try {
                current = chunk.getBlock(localX, y, localZ);
            } catch (Throwable t) {
                getBlockExceptions++;
                continue;
            }

            String key = null;
            if (stone.matchAnyRock) {
                BlockType type = current >= 0 ? BlockType.getAssetMap().getAsset(current) : null;
                key = type != null && !type.isUnknown() ? type.getId() : null;
                if (sampleBlockKey == null) {
                    sampleBlockKey = key;
                }
                if (key == null || !key.startsWith("Rock_") || key.startsWith("Rock_Volcanic")) {
                    continue;
                }
            } else {
                if (sampleBlockKey == null) {
                    BlockType type = current >= 0 ? BlockType.getAssetMap().getAsset(current) : null;
                    sampleBlockKey = type != null && !type.isUnknown() ? type.getId() : null;
                }
                if (!isInHostSet(current, hostIds)) {
                    continue;
                }
            }

            hostHits++;

            if (stone.requireAdjacentAir) {
                boolean hasAir = hasAdjacentAir(chunk, localX, y, localZ);
                if (!hasAir) {
                    continue;
                }
                airHits++;
            }

            try {
                if (!chunk.setBlock(localX, y, localZ, oreTypeId)) {
                    setBlockFalse++;
                    continue;
                }
            } catch (Throwable ignored) {
                setBlockExceptions++;
                continue;
            }

            placed++;
        }

        return new PlacementAttempt(
            "stone",
            config.stoneOreBlockId,
            oreTypeId,
            stone.matchAnyRock,
            stone.requireAdjacentAir,
            "ok",
            placed,
            attempts,
            hostHits,
            airHits,
            setBlockFalse,
            getBlockExceptions,
            setBlockExceptions,
            sampleBlockKey
        );
    }

    private @Nonnull PlacementAttempt attemptVolcanicOres(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        @Nonnull AxoTalesServerConfig.Worldgen.ArcaneMatterOres config
    ) {
        AxoTalesServerConfig.Worldgen.ArcaneMatterOres.Volcanic volcanic = config.volcanic;
        if (volcanic == null || !volcanic.enabled) {
            return PlacementAttempt.skipped("volcanic", config.volcanicOreBlockId, "disabled", true, true);
        }

        if (volcanic.chancePerNewChunk <= 0.0 || volcanic.targetPlacementsPerChunk <= 0 || volcanic.maxAttemptsPerChunk <= 0) {
            return PlacementAttempt.skipped("volcanic", config.volcanicOreBlockId, "configNoop", volcanic.matchAnyVolcanicRock, volcanic.requireAdjacentAir);
        }

        SplittableRandom random = new SplittableRandom(seedFor(world, chunk) ^ 0x0B1C_4A72L);
        if (random.nextDouble() > clamp01(volcanic.chancePerNewChunk)) {
            return PlacementAttempt.skipped("volcanic", config.volcanicOreBlockId, "chanceSkip", volcanic.matchAnyVolcanicRock, volcanic.requireAdjacentAir);
        }

        int oreTypeId = BlockType.getAssetMap().getIndexOrDefault(config.volcanicOreBlockId, -1);
        if (oreTypeId < 0) {
            if (errorLogBudget.getAndDecrement() > 0) {
                debug.traceFileOnly(null, "Worldgen.ArcaneMatter missingOreBlockId variant=volcanic id=" + config.volcanicOreBlockId);
            }
            return PlacementAttempt.skipped("volcanic", config.volcanicOreBlockId, "missingOreBlockId", volcanic.matchAnyVolcanicRock, volcanic.requireAdjacentAir);
        }

        int[] hostIds = volcanic.matchAnyVolcanicRock ? new int[0] : resolveHostBlockTypeIds("volcanic", volcanic.hostBlockIds);
        if (!volcanic.matchAnyVolcanicRock && hostIds.length == 0) {
            return PlacementAttempt.skipped("volcanic", config.volcanicOreBlockId, "noHostIdsResolved", false, volcanic.requireAdjacentAir);
        }

        int yMin = clampY(volcanic.minY);
        int yMax = clampY(volcanic.maxY);
        if (yMax < yMin) {
            int tmp = yMin;
            yMin = yMax;
            yMax = tmp;
        }

        int placed = 0;
        int attempts = 0;
        int hostHits = 0;
        int airHits = 0;
        int setBlockFalse = 0;
        int getBlockExceptions = 0;
        int setBlockExceptions = 0;
        String sampleBlockKey = null;

        int target = Math.min(volcanic.targetPlacementsPerChunk, volcanic.maxAttemptsPerChunk);
        for (int attempt = 0; attempt < volcanic.maxAttemptsPerChunk && placed < target; attempt++) {
            attempts++;
            int localX = randomLocal(random);
            int localZ = randomLocal(random);
            int y = random.nextInt(yMax - yMin + 1) + yMin;

            int current;
            try {
                current = chunk.getBlock(localX, y, localZ);
            } catch (Throwable t) {
                getBlockExceptions++;
                continue;
            }

            String key = null;
            if (volcanic.matchAnyVolcanicRock) {
                BlockType type = current >= 0 ? BlockType.getAssetMap().getAsset(current) : null;
                key = type != null && !type.isUnknown() ? type.getId() : null;
                if (sampleBlockKey == null) {
                    sampleBlockKey = key;
                }
                if (key == null || !key.startsWith("Rock_Volcanic")) {
                    continue;
                }
            } else {
                if (sampleBlockKey == null) {
                    BlockType type = current >= 0 ? BlockType.getAssetMap().getAsset(current) : null;
                    sampleBlockKey = type != null && !type.isUnknown() ? type.getId() : null;
                }
                if (!isInHostSet(current, hostIds)) {
                    continue;
                }
            }

            hostHits++;

            if (volcanic.requireAdjacentAir) {
                boolean hasAir = hasAdjacentAir(chunk, localX, y, localZ);
                if (!hasAir) {
                    continue;
                }
                airHits++;
            }

            try {
                if (!chunk.setBlock(localX, y, localZ, oreTypeId)) {
                    setBlockFalse++;
                    continue;
                }
            } catch (Throwable ignored) {
                setBlockExceptions++;
                continue;
            }

            placed++;
        }

        return new PlacementAttempt(
            "volcanic",
            config.volcanicOreBlockId,
            oreTypeId,
            volcanic.matchAnyVolcanicRock,
            volcanic.requireAdjacentAir,
            "ok",
            placed,
            attempts,
            hostHits,
            airHits,
            setBlockFalse,
            getBlockExceptions,
            setBlockExceptions,
            sampleBlockKey
        );
    }

    private AxoTalesServerConfig.Worldgen.ArcaneMatterOres getConfig() {
        if (serverConfig == null || serverConfig.worldgen == null) {
            return null;
        }
        return serverConfig.worldgen.arcaneMatterOres;
    }

    private int[] resolveHostBlockTypeIds(@Nonnull String label, String[] hostBlockIds) {
        if (hostBlockIds == null || hostBlockIds.length == 0) {
            if (errorLogBudget.getAndDecrement() > 0) {
                debug.traceFileOnly(null, "Worldgen.ArcaneMatter disabled reason=emptyHostBlockIds type=" + label);
            }
            return new int[0];
        }

        int[] ids = new int[hostBlockIds.length];
        int size = 0;
        for (String id : hostBlockIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            int resolved = BlockType.getAssetMap().getIndexOrDefault(id, -1);
            if (resolved < 0) {
                if (errorLogBudget.getAndDecrement() > 0) {
                    debug.traceFileOnly(null, "Worldgen.ArcaneMatter missingHostBlockId type=" + label + " id=" + id);
                }
                continue;
            }
            ids[size++] = resolved;
        }

        if (size == 0) {
            return new int[0];
        }
        if (size == ids.length) {
            return ids;
        }
        int[] trimmed = new int[size];
        System.arraycopy(ids, 0, trimmed, 0, size);
        return trimmed;
    }

    private static boolean isInHostSet(int blockTypeId, int[] hostIds) {
        for (int id : hostIds) {
            if (blockTypeId == id) {
                return true;
            }
        }
        return false;
    }

    private boolean markChunkProcessed(@Nonnull World world, long chunkIndex) {
        String key = worldKey(world);
        return processedChunksByWorldAndChunk
            .computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
            .putIfAbsent(chunkIndex, Boolean.TRUE) == null;
    }

    private static boolean hasAdjacentAir(@Nonnull WorldChunk chunk, int localX, int y, int localZ) {
        int safeX = clampLocal(localX);
        int safeZ = clampLocal(localZ);
        int safeY = clampY(y);

        if (isAir(chunk, safeX - 1, safeY, safeZ)) {
            return true;
        }
        if (isAir(chunk, safeX + 1, safeY, safeZ)) {
            return true;
        }
        if (isAir(chunk, safeX, safeY, safeZ - 1)) {
            return true;
        }
        if (isAir(chunk, safeX, safeY, safeZ + 1)) {
            return true;
        }
        if (safeY > WORLD_MIN_Y && isAir(chunk, safeX, safeY - 1, safeZ)) {
            return true;
        }
        return safeY < WORLD_MAX_Y && isAir(chunk, safeX, safeY + 1, safeZ);
    }

    private static boolean isAir(@Nonnull WorldChunk chunk, int localX, int y, int localZ) {
        try {
            BlockType type = chunk.getBlockType(localX, y, localZ);
            return type == null
                || type.isUnknown()
                || type == BlockType.EMPTY
                || type.getDrawType() == DrawType.Empty;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int randomLocal(@Nonnull SplittableRandom random) {
        int range = Math.max(1, LOCAL_MAX - LOCAL_MIN + 1);
        return LOCAL_MIN + random.nextInt(range);
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) {
            return 0;
        }
        return Math.max(0.0, Math.min(1.0, v));
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

    private record PlacementAttempt(
        @Nonnull String variant,
        @Nonnull String oreBlockId,
        int oreBlockTypeId,
        boolean matchAnyHost,
        boolean requireAdjacentAir,
        @Nonnull String reason,
        int placed,
        int attempts,
        int hostHits,
        int adjacentAirHits,
        int setBlockFalse,
        int getBlockExceptions,
        int setBlockExceptions,
        @Nullable String sampleBlockKey
    ) {
        static @Nonnull PlacementAttempt skipped(
            @Nonnull String variant,
            @Nullable String oreBlockId,
            @Nonnull String reason,
            boolean matchAnyHost,
            boolean requireAdjacentAir
        ) {
            return new PlacementAttempt(
                variant,
                oreBlockId != null ? oreBlockId : "null",
                -1,
                matchAnyHost,
                requireAdjacentAir,
                reason,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null
            );
        }

        @Nonnull
        String summarize() {
            return "{"
                + "reason=" + reason
                + ",oreId=" + oreBlockId
                + ",oreTypeId=" + oreBlockTypeId
                + ",matchAnyHost=" + matchAnyHost
                + ",requireAdjacentAir=" + requireAdjacentAir
                + ",placed=" + placed
                + ",attempts=" + attempts
                + ",hostHits=" + hostHits
                + ",airHits=" + adjacentAirHits
                + ",setFalse=" + setBlockFalse
                + ",getEx=" + getBlockExceptions
                + ",setEx=" + setBlockExceptions
                + ",sample=" + (sampleBlockKey != null ? sampleBlockKey : "null")
                + "}";
        }
    }
}
