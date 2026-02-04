package org.example.plugin;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Very simple chunk-time worldgen for a modded crystal marker block.
 *
 * <p>Runs only when a chunk is {@link ChunkPreLoadProcessEvent#isNewlyGenerated()} and places a visible crystal block
 * above the terrain surface. The placement is tracked so we can revert it during plugin shutdown.</p>
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

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig serverConfig;
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, ConcurrentLinkedQueue<Placement>>> placementsByWorldAndChunk = new ConcurrentHashMap<>();
    private final AtomicInteger placementDebugBudget = new AtomicInteger(25);
    private final AtomicInteger errorDebugBudget = new AtomicInteger(25);

    public CustomPlaceholderBlockWorldgen(@Nonnull PluginErrorReporter errors, @Nonnull PluginDebugReporter debug, @Nonnull AxoTalesServerConfig serverConfig) {
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

        if (!event.isNewlyGenerated()) {
            return;
        }

        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);

        Runnable work = () -> {
            try {
                WorldChunk current = world.getChunkIfLoaded(chunkIndex);
                if (current == null) {
                    current = world.getChunkIfInMemory(chunkIndex);
                }
                if (current == null) {
                    return;
                }

                int placed = maybePlaceInChunk(world, current);
                if (placed > 0) {
                    maybeLogPlacement(world, current, placed);
                }
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

    private int maybePlaceInChunk(@Nonnull World world, @Nonnull WorldChunk chunk) {
        SplittableRandom random = new SplittableRandom(seedFor(world, chunk));
        if (random.nextDouble() > chancePerNewChunk()) {
            return 0;
        }

        int range = Math.max(1, LOCAL_MAX - LOCAL_MIN + 1);
        int localX = LOCAL_MIN + random.nextInt(range);
        int localZ = LOCAL_MIN + random.nextInt(range);

        return placeMarkerColumn(world, chunk, localX, localZ, null, "chunk-gen");
    }

    private void maybeLogPlacement(@Nonnull World world, @Nonnull WorldChunk chunk, int placed) {
        if (placementDebugBudget.getAndDecrement() <= 0) {
            return;
        }

        debug.traceFileOnly(
            null,
            "Worldgen.ArcaneCrystal"
                + " placed=" + placed
                + " blockId=" + BLOCK_ITEM_ID
                + " chancePerNewChunk=" + chancePerNewChunk()
                + " surfaceYOffset=" + SURFACE_Y_OFFSET
                + " pillarHeightBlocks=" + PILLAR_HEIGHT_BLOCKS
                + " chunkX=" + chunk.getX()
                + " chunkZ=" + chunk.getZ()
                + " world=" + world.getName()
        );
    }

    private double chancePerNewChunk() {
        if (serverConfig == null || serverConfig.worldgen == null) {
            return 0.25 / 3.0;
        }
        return serverConfig.worldgen.arcaneCrystalChancePerNewChunk;
    }

    /**
     * Forces a visible marker placement at the specified local column in a loaded chunk.
     *
     * <p>Intended for debugging when you can’t find any naturally generated placeholder markers.</p>
     *
     * <p>Must be called on the world thread.</p>
     */
    public int placeMarkerAtPlayerColumn(@Nonnull World world, @Nonnull WorldChunk chunk, int localX, int localZ, @Nonnull PlayerRef player) {
        return placeMarkerColumn(world, chunk, localX, localZ, player, "command");
    }

    private int placeMarkerColumn(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int localX,
        int localZ,
        @Nullable PlayerRef player,
        @Nonnull String reason
    ) {
        // Heightmap is per-chunk column; place above surface so it’s visible (not blended into dirt/grass).
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
        int baseY = clampY(surfaceY + SURFACE_Y_OFFSET);
        int globalX = chunk.getX() * CHUNK_WIDTH_BLOCKS + safeLocalX;
        int globalZ = chunk.getZ() * CHUNK_WIDTH_BLOCKS + safeLocalZ;

        int placed = 0;
        for (int i = 0; i < PILLAR_HEIGHT_BLOCKS; i++) {
            int y = clampY(baseY + i);
            int previousBlockId;
            boolean ok;
            try {
                previousBlockId = chunk.getBlock(safeLocalX, y, safeLocalZ);
                ok = chunk.setBlock(safeLocalX, y, safeLocalZ, BLOCK_ITEM_ID);
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

            int placedBlockId;
            try {
                placedBlockId = chunk.getBlock(safeLocalX, y, safeLocalZ);
            } catch (Throwable t) {
                placedBlockId = -1;
            }
            placementsByWorldAndChunk
                .computeIfAbsent(worldKey(world), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(ChunkUtil.indexChunk(chunk.getX(), chunk.getZ()), ignored -> new ConcurrentLinkedQueue<>())
                .add(new Placement(safeLocalX, y, safeLocalZ, globalX, globalZ, previousBlockId, placedBlockId));
            placed++;
        }

        if (placed > 0) {
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

    /**
     * Best-effort cleanup: reverts tracked placeholder placements for a chunk about to unload.
     *
     * <p>This runs before the chunk is evicted from memory, helping ensure the world does not persist the placeholder
     * block if the mod is later removed.</p>
     */
    public void onChunkUnload(@Nonnull ChunkUnloadEvent event) {
        WorldChunk chunk = event.getChunk();
        if (chunk == null) {
            return;
        }

        World world = chunk.getWorld();
        if (world == null) {
            return;
        }

        String key = worldKey(world);
        long chunkIndex = ChunkUtil.indexChunk(chunk.getX(), chunk.getZ());

        ConcurrentHashMap<Long, ConcurrentLinkedQueue<Placement>> byChunk = placementsByWorldAndChunk.get(key);
        if (byChunk == null) {
            return;
        }

        ConcurrentLinkedQueue<Placement> placements = byChunk.remove(chunkIndex);
        if (placements == null || placements.isEmpty()) {
            if (byChunk.isEmpty()) {
                placementsByWorldAndChunk.remove(key, byChunk);
            }
            return;
        }

        Runnable restore = () -> {
            int total = 0;
            int restored = 0;
            int skipped = 0;

            for (Placement placement : placements) {
                total++;
                int current = chunk.getBlock(placement.localX, placement.y, placement.localZ);
                if (current != placement.placedBlockId) {
                    skipped++;
                    continue;
                }
                chunk.setBlock(placement.localX, placement.y, placement.localZ, placement.previousBlockId);
                restored++;
            }

            debug.traceFileOnly(
                null,
                "Worldgen.ArcaneCrystal cleanup chunk-unload"
                    + " world=" + world.getName()
                    + " chunkX=" + chunk.getX()
                    + " chunkZ=" + chunk.getZ()
                    + " total=" + total
                    + " restored=" + restored
                    + " skipped=" + skipped
            );
        };

        if (world.isInThread()) {
            restore.run();
        } else {
            world.execute(restore);
        }

        if (byChunk.isEmpty()) {
            placementsByWorldAndChunk.remove(key, byChunk);
        }
    }

    /**
     * Best-effort cleanup: reverts tracked placeholder placements for the given world.
     *
     * <p>Only reverts blocks that are still the placeholder block at shutdown time, so player edits are not clobbered.</p>
     */
    public void restoreTrackedBlocksInWorld(@Nonnull World world) {
        String key = worldKey(world);
        if (!placementsByWorldAndChunk.containsKey(key)) {
            return;
        }

        Runnable restore = () -> {
            ConcurrentHashMap<Long, ConcurrentLinkedQueue<Placement>> byChunk = placementsByWorldAndChunk.remove(key);
            if (byChunk == null || byChunk.isEmpty()) {
                return;
            }

            int total = 0;
            int restored = 0;
            int skipped = 0;
            int missingChunks = 0;

            for (var entry : byChunk.entrySet()) {
                long chunkIndex = entry.getKey();
                WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                if (chunk == null) {
                    chunk = world.getChunkIfInMemory(chunkIndex);
                }
                if (chunk == null) {
                    missingChunks++;
                    continue;
                }

                for (Placement placement : entry.getValue()) {
                    total++;
                    int current = chunk.getBlock(placement.localX, placement.y, placement.localZ);
                    if (current != placement.placedBlockId) {
                        skipped++;
                        continue;
                    }
                    chunk.setBlock(placement.localX, placement.y, placement.localZ, placement.previousBlockId);
                    restored++;
                }
            }

            debug.traceFileOnly(
                null,
                "Worldgen.ArcaneCrystal cleanup"
                    + " world=" + world.getName()
                    + " total=" + total
                    + " restored=" + restored
                    + " skipped=" + skipped
                    + " missingChunks=" + missingChunks
            );
        };

        if (world.isInThread()) {
            restore.run();
        } else {
            world.execute(restore);
        }
    }

    private static int clampY(int y) {
        return Math.max(WORLD_MIN_Y, Math.min(WORLD_MAX_Y, y));
    }

    private static int clampLocal(int local) {
        return Math.max(LOCAL_MIN, Math.min(LOCAL_MAX, local));
    }

    private static long seedFor(@Nonnull World world, @Nonnull WorldChunk chunk) {
        long seed = 0xA0B0_C0D0_E0F0_1234L;

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

    private record Placement(int localX, int y, int localZ, int globalX, int globalZ, int previousBlockId, int placedBlockId) {
    }
}
