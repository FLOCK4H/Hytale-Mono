package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.collision.BlockCollisionData;
import com.hypixel.hytale.server.core.modules.collision.CollisionMaterial;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Triggers the Taunt Book slam when the caster lands.
 *
 * <p>We intentionally detect landing via {@link MovementStatesComponent} (onGround transition) because fall-damage
 * events may not fire (e.g., creative mode / certain movement modes).</p>
 *
 * <p>Defensive fix: clamp overkill slam damage to target remaining health above minimum to avoid crashing server
 * builds where health underflow triggers assertions/exceptions.</p>
 */
public final class TauntBookLandingSystem extends EntityTickingSystem<EntityStore> {

    private static final Box DOWN_RAY_POINT_BOX = new Box(0, 0, 0, 0.01, 0.01, 0.01);
    private static final int GROUND_BREAK_SURFACE_SCAN_MAX_ABOVE_SOLID_BLOCKS = 8;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final TauntBookEffectState tauntState;
    private final TauntBookSlamQueue slamQueue;

    public TauntBookLandingSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull TauntBookEffectState tauntState,
        @Nonnull TauntBookSlamQueue slamQueue
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.tauntState = tauntState;
        this.slamQueue = slamQueue;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> playerEntityRef = chunk.getReferenceTo(index);
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }

        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return;
        }

        long nowNanos = System.nanoTime();
        TauntBookEffectState.ActiveTaunt active = tauntState.getIfActive(uuid, nowNanos);
        if (active == null) {
            return;
        }

        GroundState groundState = resolveGroundState(store, playerEntityRef);
        boolean onGround = groundState.onGround;
        if (!onGround) {
            active.leftGround = true;
            return;
        }

        if (!active.leftGround) {
            return;
        }

        boolean slamTriggered = tauntState.consumeSlamIfPending(uuid, nowNanos);
        if (!slamTriggered) {
            return;
        }

        SlamResult slam = performSlam(playerRef, playerEntityRef, store, commandBuffer, active);
        debug.traceFileOnly(
            playerRef,
            "TauntBookLanding event=Landing"
                + " ground.source=" + groundState.source
                + " taunt.cast.chainId=" + active.castChainId
                + " taunt.cast.interactionType=" + active.castInteractionType
                + " taunt.stackCount=" + active.stackCount
                + " taunt.active.expiresAtNanos=" + active.expiresAtNanos
                + " slam.triggered=true"
                + " slam.damageEnqueued=" + slam.damageEnqueued
                + " slam.damageEnqueueReason=" + slam.damageEnqueueReason
                + " slam.damageAmount=" + slam.damageAmount
                + " slam.radiusBlocks=" + slam.radiusBlocks
                + " slam.breakRadiusBlocks=" + slam.groundBreakRadiusBlocks
                + " slam.breakBlockBelow=" + config.tauntBook.breakBlockBelow
                + " slam.groundBreak.maxDepthBlocks=" + slam.groundBreakMaxDepthBlocks
                + " slam.groundBreak.samples=" + slam.groundBreakSamples
                + " slam.groundBreak.uniqueTargets=" + slam.groundBreakUniqueTargets
                + " slam.groundBreak.blocksBroken=" + slam.groundBreakBlocksBroken
                + " slam.groundBreak.sparedRandom=" + slam.groundBreakSparedRandom
                + " slam.groundBreak.skippedNoHit=" + slam.groundBreakSkippedNoHit
                + " slam.groundBreak.skippedDuplicate=" + slam.groundBreakSkippedDuplicate
                + " slam.groundBreak.skippedChunkNotLoaded=" + slam.groundBreakSkippedChunkNotLoaded
                + " slam.groundBreak.skippedUnbreakable=" + slam.groundBreakSkippedUnbreakable
                + " slam.groundBreak.breakFailed=" + slam.groundBreakBreakFailed
                + " slam.groundBreak.exceptions=" + slam.groundBreakExceptions
                + (slam.centerBelowHit != null ? " slam.blockBelow=(" + slam.centerBelowHit.x + "," + slam.centerBelowHit.y + "," + slam.centerBelowHit.z + ")" : "")
                + (slam.centerBlockTypeId != null ? " slam.blockBelow.blockTypeId=" + slam.centerBlockTypeId : "")
                + " slam.blockBelow.broke=" + slam.brokeCenterBlock
                + " slam.blockBelow.reason=" + slam.centerBlockBreakReason
        );
    }

    private record GroundState(boolean onGround, @Nonnull String source) {}

    private @Nonnull GroundState resolveGroundState(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef
    ) {
        try {
            MovementStatesComponent movementStatesComponent = store.getComponent(playerEntityRef, MovementStatesComponent.getComponentType());
            MovementStates states = movementStatesComponent != null ? movementStatesComponent.getMovementStates() : null;
            if (states != null) {
                return new GroundState(states.onGround, "movementStates");
            }
        } catch (Throwable ignored) {
            // Best-effort: fall back to raycast.
        }

        var external = store.getExternalData();
        World world = external != null ? external.getWorld() : null;
        if (world == null) {
            return new GroundState(false, "worldMissing");
        }

        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        Vector3d position = transform != null ? transform.getPosition() : null;
        if (position == null || !position.isFinite()) {
            return new GroundState(false, "positionMissing");
        }

        BlockCollisionData hit = raycastBlockBelow(world, position, 0.35);
        return new GroundState(hit != null, "raycast");
    }

    private record SlamResult(
        boolean damageEnqueued,
        @Nonnull String damageEnqueueReason,
        int damageAmount,
        int radiusBlocks,
        int groundBreakRadiusBlocks,
        int groundBreakMaxDepthBlocks,
        int groundBreakSamples,
        int groundBreakUniqueTargets,
        int groundBreakBlocksBroken,
        int groundBreakSparedRandom,
        int groundBreakSkippedNoHit,
        int groundBreakSkippedDuplicate,
        int groundBreakSkippedChunkNotLoaded,
        int groundBreakSkippedUnbreakable,
        int groundBreakBreakFailed,
        int groundBreakExceptions,
        boolean brokeCenterBlock,
        @Nullable String centerBlockTypeId,
        @Nonnull String centerBlockBreakReason,
        @Nullable BlockCollisionData centerBelowHit
    ) {}

    private record GroundBreakResult(
        int radiusBlocks,
        int maxDepthBlocks,
        int samples,
        int uniqueTargets,
        int blocksBroken,
        int sparedRandom,
        int skippedNoHit,
        int skippedDuplicate,
        int skippedChunkNotLoaded,
        int skippedUnbreakable,
        int breakFailed,
        int breakExceptions,
        boolean brokeCenterBlock,
        @Nullable String centerBlockTypeId,
        @Nonnull String centerBlockBreakReason,
        @Nullable BlockCollisionData centerHit
    ) {
        private static @Nonnull GroundBreakResult disabled(int radiusBlocks, int maxDepthBlocks) {
            return new GroundBreakResult(radiusBlocks, maxDepthBlocks, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, null, "disabled", null);
        }
    }

    private record SingleGroundBreakResult(boolean broke, @Nullable String blockTypeId, @Nonnull String reason) {}

    private record BlockPos(int x, int y, int z) {}

    private @Nonnull SlamResult performSlam(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TauntBookEffectState.ActiveTaunt active
    ) {
        int damageAmount = active.getEffectiveSlamDamage();
        int radiusBlocks = Math.max(0, config.tauntBook.slamRadiusBlocks);
        boolean breakBlockBelow = config.tauntBook.breakBlockBelow;
        int groundBreakRadiusBlocks = active.getGroundBreakRadiusBlocks();
        int groundBreakMaxDepthBlocks = resolveGroundBreakMaxDepthBlocks(active);

        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        Vector3d position = transform != null ? transform.getPosition() : null;
        if (position == null || !position.isFinite()) {
            return new SlamResult(
                false,
                "positionMissing",
                damageAmount,
                radiusBlocks,
                groundBreakRadiusBlocks,
                groundBreakMaxDepthBlocks,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                null,
                "positionMissing",
                null
            );
        }

        boolean damageEnqueued = false;
        String damageEnqueueReason = "disabled.damageOrRadiusZero";
        if (damageAmount > 0 && radiusBlocks > 0) {
            var external = store.getExternalData();
            World world = external != null ? external.getWorld() : null;
            long worldTick = world != null ? world.getTick() : Long.MIN_VALUE;
            if (world == null) {
                damageEnqueueReason = "worldMissing";
            } else if (worldTick < 0) {
                damageEnqueueReason = "worldTickInvalid";
            } else {
                TauntBookSlamQueue.PerWorldQueue worldQueue = slamQueue.forWorld(world);
                worldQueue.enqueue(
                    worldTick,
                    new TauntBookSlamQueue.SlamRequest(
                        world,
                        playerEntityRef,
                        playerRef.getUuid(),
                        active.castInteractionType,
                        active.castChainId,
                        active.expiresAtNanos,
                        active.stackCount,
                        groundBreakRadiusBlocks,
                        position,
                        radiusBlocks,
                        damageAmount
                    )
                );
                damageEnqueued = true;
                damageEnqueueReason = "enqueued";
            }
        }

        var external = store.getExternalData();
        World world = external != null ? external.getWorld() : null;
        if (world == null) {
            GroundBreakResult breakResult = new GroundBreakResult(
                groundBreakRadiusBlocks,
                groundBreakMaxDepthBlocks,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                null,
                "worldMissing",
                null
            );
            return toSlamResult(damageEnqueued, damageEnqueueReason, damageAmount, radiusBlocks, breakResult);
        }

        GroundBreakResult groundBreakResult = breakBlockBelow
            ? performGroundBreak(playerRef, world, position, groundBreakRadiusBlocks, groundBreakMaxDepthBlocks, active.castChainId)
            : GroundBreakResult.disabled(groundBreakRadiusBlocks, groundBreakMaxDepthBlocks);
        return toSlamResult(damageEnqueued, damageEnqueueReason, damageAmount, radiusBlocks, groundBreakResult);
    }

    private @Nonnull SlamResult toSlamResult(
        boolean damageEnqueued,
        @Nonnull String damageEnqueueReason,
        int damageAmount,
        int radiusBlocks,
        @Nonnull GroundBreakResult breakResult
    ) {
        return new SlamResult(
            damageEnqueued,
            damageEnqueueReason,
            damageAmount,
            radiusBlocks,
            breakResult.radiusBlocks,
            breakResult.maxDepthBlocks,
            breakResult.samples,
            breakResult.uniqueTargets,
            breakResult.blocksBroken,
            breakResult.sparedRandom,
            breakResult.skippedNoHit,
            breakResult.skippedDuplicate,
            breakResult.skippedChunkNotLoaded,
            breakResult.skippedUnbreakable,
            breakResult.breakFailed,
            breakResult.breakExceptions,
            breakResult.brokeCenterBlock,
            breakResult.centerBlockTypeId,
            breakResult.centerBlockBreakReason,
            breakResult.centerHit
        );
    }

    private @Nonnull GroundBreakResult performGroundBreak(
        @Nonnull PlayerRef playerRef,
        @Nonnull World world,
        @Nonnull Vector3d landingPosition,
        int breakRadiusBlocks,
        int maxDepthBlocks,
        int castChainId
    ) {
        BlockCollisionData centerHit = raycastSolidBlockBelow(world, landingPosition, 2.5);
        if (centerHit == null) {
            return new GroundBreakResult(breakRadiusBlocks, maxDepthBlocks, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, false, null, "noBlockBelowHit", null);
        }

        int sampleCount = 0;
        int uniqueTargets = 0;
        int blocksBroken = 0;
        int sparedRandom = 0;
        int skippedNoHit = 0;
        int skippedDuplicate = 0;
        int skippedChunkNotLoaded = 0;
        int skippedUnbreakable = 0;
        int breakFailed = 0;
        int breakExceptions = 0;
        boolean brokeCenterBlock = false;
        String centerBlockTypeId = null;
        String centerBlockBreakReason = "notAttempted";
        boolean centerBlockRecorded = false;
        Set<BlockPos> seen = new HashSet<>();
        double sampleOriginY = Math.max(landingPosition.y + 0.1, centerHit.y + 3.0);
        double sparingChance = resolveGroundBreakSparingChance();

        for (int offsetX = -breakRadiusBlocks; offsetX <= breakRadiusBlocks; offsetX++) {
            for (int offsetZ = -breakRadiusBlocks; offsetZ <= breakRadiusBlocks; offsetZ++) {
                sampleCount++;
                BlockCollisionData sampleHit;
                if (offsetX == 0 && offsetZ == 0) {
                    sampleHit = centerHit;
                } else {
                    Vector3d sampleOrigin = new Vector3d(
                        centerHit.x + 0.5 + offsetX,
                        sampleOriginY,
                        centerHit.z + 0.5 + offsetZ
                    );
                    sampleHit = raycastSolidBlockBelow(world, sampleOrigin, 6.0);
                }

                if (sampleHit == null) {
                    skippedNoHit++;
                    continue;
                }

                int columnDepthBlocks = computeGroundBreakColumnDepth(maxDepthBlocks, breakRadiusBlocks, offsetX, offsetZ);
                int surfaceTopY = resolveSurfaceBreakStartY(world, landingPosition, sampleHit);
                if (surfaceTopY > sampleHit.y) {
                    for (int blockY = surfaceTopY; blockY > sampleHit.y; blockY--) {
                        BlockPos blockPos = new BlockPos(sampleHit.x, blockY, sampleHit.z);
                        if (!seen.add(blockPos)) {
                            skippedDuplicate++;
                            continue;
                        }
                        uniqueTargets++;

                        SingleGroundBreakResult single = tryBreakGroundBlock(playerRef, world, sampleHit.x, blockY, sampleHit.z);
                        if (offsetX == 0 && offsetZ == 0 && !centerBlockRecorded) {
                            brokeCenterBlock = single.broke;
                            centerBlockTypeId = single.blockTypeId;
                            centerBlockBreakReason = single.reason;
                            centerBlockRecorded = true;
                        }

                        if (single.broke) {
                            blocksBroken++;
                            continue;
                        }

                        switch (single.reason) {
                            case "blockEmpty", "blockTypeMissingOrUnknown" -> {
                                // Allow sparse foliage columns and keep scanning downward toward the solid anchor.
                            }
                            case "chunkNotLoaded" -> {
                                skippedChunkNotLoaded++;
                                blockY = sampleHit.y;
                            }
                            case "blockProtected.bedrock", "blockUnbreakable.drawTypeEmpty" -> skippedUnbreakable++;
                            case "breakException" -> breakExceptions++;
                            case "breakFailed" -> breakFailed++;
                            default -> {
                                // Keep the detailed center reason, but don't double-count every surface miss.
                            }
                        }
                    }
                }

                for (int depth = 0; depth < columnDepthBlocks; depth++) {
                    int blockY = sampleHit.y - depth;
                    if (blockY < 0) {
                        skippedUnbreakable++;
                        break;
                    }

                    BlockPos blockPos = new BlockPos(sampleHit.x, blockY, sampleHit.z);
                    if (!seen.add(blockPos)) {
                        skippedDuplicate++;
                        continue;
                    }
                    uniqueTargets++;

                    boolean centerLayer = offsetX == 0 && offsetZ == 0 && depth == 0;
                    if (shouldSpareGroundBlock(
                        sampleHit.x,
                        blockY,
                        sampleHit.z,
                        castChainId,
                        offsetX,
                        offsetZ,
                        depth,
                        columnDepthBlocks,
                        breakRadiusBlocks,
                        sparingChance
                    )) {
                        sparedRandom++;
                        break;
                    }

                    SingleGroundBreakResult single = tryBreakGroundBlock(playerRef, world, sampleHit.x, blockY, sampleHit.z);
                    if (centerLayer && !centerBlockRecorded) {
                        brokeCenterBlock = single.broke;
                        centerBlockTypeId = single.blockTypeId;
                        centerBlockBreakReason = single.reason;
                        centerBlockRecorded = true;
                    }

                    if (single.broke) {
                        blocksBroken++;
                        continue;
                    }

                    switch (single.reason) {
                        case "chunkNotLoaded" -> skippedChunkNotLoaded++;
                        case "blockProtected.bedrock", "blockUnbreakable.drawTypeEmpty", "blockTypeMissingOrUnknown", "blockEmpty" -> skippedUnbreakable++;
                        case "breakException" -> breakExceptions++;
                        case "breakFailed" -> breakFailed++;
                        default -> {
                            // Count the reason on the center block only; keep the detailed reason in the trace.
                        }
                    }
                    break;
                }
            }
        }

        return new GroundBreakResult(
            breakRadiusBlocks,
            maxDepthBlocks,
            sampleCount,
            uniqueTargets,
            blocksBroken,
            sparedRandom,
            skippedNoHit,
            skippedDuplicate,
            skippedChunkNotLoaded,
            skippedUnbreakable,
            breakFailed,
            breakExceptions,
            brokeCenterBlock,
            centerBlockTypeId,
            centerBlockBreakReason,
            centerHit
        );
    }

    private int resolveGroundBreakMaxDepthBlocks(@Nonnull TauntBookEffectState.ActiveTaunt active) {
        int baseDepth = Math.max(1, config.tauntBook.groundBreakDepthBlocks);
        int depthPerStack = Math.max(0, config.tauntBook.groundBreakDepthPerStack);
        int stackedDepth = Math.max(0, active.stackCount - 1) * depthPerStack;
        return Math.min(8, baseDepth + stackedDepth);
    }

    private double resolveGroundBreakSparingChance() {
        // Taunt now clears every breakable block in the crater footprint.
        // The legacy sparing knob stays in config for compatibility, but the slam ignores it.
        return 0.0;
    }

    private static int computeGroundBreakColumnDepth(int maxDepthBlocks, int breakRadiusBlocks, int offsetX, int offsetZ) {
        if (maxDepthBlocks <= 1 || breakRadiusBlocks <= 0) {
            return Math.max(1, maxDepthBlocks);
        }

        double horizontalDistance = Math.sqrt((double) offsetX * offsetX + (double) offsetZ * offsetZ);
        double maxDistance = breakRadiusBlocks + 0.75;
        double distanceFactor = 1.0 - Math.min(1.0, horizontalDistance / Math.max(1.0, maxDistance));
        double depthWeight = 0.35 + (0.65 * distanceFactor);
        return Math.max(1, (int) Math.round(maxDepthBlocks * depthWeight));
    }

    private static boolean shouldSpareGroundBlock(
        int x,
        int y,
        int z,
        int castChainId,
        int offsetX,
        int offsetZ,
        int depth,
        int columnDepthBlocks,
        int breakRadiusBlocks,
        double baseSparingChance
    ) {
        if (!(baseSparingChance > 0.0)) {
            return false;
        }
        if (offsetX == 0 && offsetZ == 0 && depth < Math.min(2, columnDepthBlocks)) {
            return false;
        }

        double horizontalDistance = Math.sqrt((double) offsetX * offsetX + (double) offsetZ * offsetZ);
        double edgeFactor = breakRadiusBlocks <= 0 ? 0.0 : Math.min(1.0, horizontalDistance / Math.max(1.0, breakRadiusBlocks));
        double depthFactor = columnDepthBlocks <= 1 ? 0.0 : (double) depth / (double) (columnDepthBlocks - 1);
        double effectiveChance = baseSparingChance * (0.65 + (0.55 * edgeFactor) + (0.20 * depthFactor));
        effectiveChance = Math.max(0.0, Math.min(0.85, effectiveChance));
        return deterministicUnitDouble(x, y, z, castChainId) < effectiveChance;
    }

    private static double deterministicUnitDouble(int x, int y, int z, int castChainId) {
        long seed = 0xCBF29CE484222325L;
        seed = (seed ^ x) * 0x100000001B3L;
        seed = (seed ^ y) * 0x100000001B3L;
        seed = (seed ^ z) * 0x100000001B3L;
        seed = (seed ^ castChainId) * 0x100000001B3L;
        long mixed = mix64(seed);
        return ((mixed >>> 11) & ((1L << 53) - 1)) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private @Nonnull SingleGroundBreakResult tryBreakGroundBlock(
        @Nonnull PlayerRef playerRef,
        @Nonnull World world,
        int x,
        int y,
        int z
    ) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return new SingleGroundBreakResult(false, null, "chunkNotLoaded");
        }

        var blockType = chunk.getBlockType(x, y, z);
        String blockTypeId = blockType != null ? blockType.getId() : null;
        if (blockType == null || blockType.isUnknown()) {
            return new SingleGroundBreakResult(false, blockTypeId, "blockTypeMissingOrUnknown");
        }

        if (isProtectedGroundBlock(blockTypeId)) {
            return new SingleGroundBreakResult(false, blockTypeId, "blockProtected.bedrock");
        }

        if (blockType == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
            || blockType.getDrawType() == com.hypixel.hytale.protocol.DrawType.Empty) {
            return new SingleGroundBreakResult(false, blockTypeId, "blockEmpty");
        }

        try {
            boolean broke = chunk.breakBlock(x, y, z);
            return new SingleGroundBreakResult(broke, blockTypeId, broke ? "broken" : "breakFailed");
        } catch (Throwable t) {
            errors.report(
                playerRef,
                "TauntBookLandingSystem: failed to break ground block at (" + x + "," + y + "," + z + ").",
                t
            );
            return new SingleGroundBreakResult(false, blockTypeId, "breakException");
        }
    }

    private int resolveSurfaceBreakStartY(
        @Nonnull World world,
        @Nonnull Vector3d landingPosition,
        @Nonnull BlockCollisionData sampleHit
    ) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(sampleHit.x, sampleHit.z));
        if (chunk == null) {
            return sampleHit.y;
        }

        int scanTopY = Math.max(
            sampleHit.y,
            Math.min(
                sampleHit.y + GROUND_BREAK_SURFACE_SCAN_MAX_ABOVE_SOLID_BLOCKS,
                (int) Math.floor(landingPosition.y + 1.5)
            )
        );

        for (int scanY = scanTopY; scanY > sampleHit.y; scanY--) {
            var blockType = chunk.getBlockType(sampleHit.x, scanY, sampleHit.z);
            if (blockType == null || blockType.isUnknown()) {
                continue;
            }
            if (blockType == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
                || blockType.getDrawType() == com.hypixel.hytale.protocol.DrawType.Empty) {
                continue;
            }

            String blockTypeId = blockType.getId();
            if (isProtectedGroundBlock(blockTypeId)) {
                continue;
            }
            return scanY;
        }

        return sampleHit.y;
    }

    private static boolean isProtectedGroundBlock(@Nullable String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isBlank()) {
            return false;
        }
        return "Rock_Bedrock".equals(blockTypeId) || blockTypeId.endsWith("_Bedrock");
    }

    private static @Nullable BlockCollisionData raycastSolidBlockBelow(@Nonnull World world, @Nonnull Vector3d position) {
        return raycastSolidBlockBelow(world, position, 2.5);
    }

    private static @Nullable BlockCollisionData raycastSolidBlockBelow(
        @Nonnull World world,
        @Nonnull Vector3d position,
        double maxDistance
    ) {
        return raycastBlockBelow(world, position, maxDistance);
    }

    private static @Nullable BlockCollisionData raycastBlockBelow(
        @Nonnull World world,
        @Nonnull Vector3d position,
        double maxDistance
    ) {
        Vector3d origin = new Vector3d(position.x, position.y + 0.1, position.z);
        Vector3d ray = new Vector3d(0, -Math.max(0.0, maxDistance), 0);
        CollisionResult result = new CollisionResult(false, false);
        result.setCollisionByMaterial(CollisionMaterial.MATERIAL_SOLID);
        CollisionModule.findBlockCollisionsIterative(world, DOWN_RAY_POINT_BOX, origin, ray, true, result);
        return result.getFirstBlockCollision();
    }

}
