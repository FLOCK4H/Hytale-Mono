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
                + " taunt.active.expiresAtNanos=" + active.expiresAtNanos
                + " slam.triggered=true"
                + " slam.damageEnqueued=" + slam.damageEnqueued
                + " slam.damageEnqueueReason=" + slam.damageEnqueueReason
                + " slam.damageAmount=" + slam.damageAmount
                + " slam.radiusBlocks=" + slam.radiusBlocks
                + " slam.breakBlockBelow=" + config.tauntBook.breakBlockBelow
                + (slam.belowHit != null ? " slam.blockBelow=(" + slam.belowHit.x + "," + slam.belowHit.y + "," + slam.belowHit.z + ")" : "")
                + (slam.blockTypeId != null ? " slam.blockBelow.blockTypeId=" + slam.blockTypeId : "")
                + " slam.blockBelow.broke=" + slam.brokeBlockBelow
                + " slam.blockBelow.reason=" + slam.blockBreakReason
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
        boolean brokeBlockBelow,
        @Nullable String blockTypeId,
        @Nonnull String blockBreakReason,
        @Nullable BlockCollisionData belowHit
    ) {}

    private @Nonnull SlamResult performSlam(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TauntBookEffectState.ActiveTaunt active
    ) {
        int damageAmount = Math.max(0, config.tauntBook.slamDamage);
        int radiusBlocks = Math.max(0, config.tauntBook.slamRadiusBlocks);
        boolean breakBlockBelow = config.tauntBook.breakBlockBelow;

        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        Vector3d position = transform != null ? transform.getPosition() : null;
        if (position == null || !position.isFinite()) {
            return new SlamResult(false, "positionMissing", damageAmount, radiusBlocks, false, null, "positionMissing", null);
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
                        position,
                        radiusBlocks,
                        damageAmount
                    )
                );
                damageEnqueued = true;
                damageEnqueueReason = "enqueued";
            }
        }

        if (!breakBlockBelow) {
            return new SlamResult(damageEnqueued, damageEnqueueReason, damageAmount, radiusBlocks, false, null, "disabled", null);
        }

        var external = store.getExternalData();
        World world = external != null ? external.getWorld() : null;
        if (world == null) {
            return new SlamResult(damageEnqueued, damageEnqueueReason, damageAmount, radiusBlocks, false, null, "worldMissing", null);
        }

        BlockCollisionData belowHit = raycastSolidBlockBelow(world, position);
        if (belowHit == null) {
            return new SlamResult(damageEnqueued, damageEnqueueReason, damageAmount, radiusBlocks, false, null, "noBlockBelowHit", null);
        }

        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(belowHit.x, belowHit.z));
        if (chunk == null) {
            return new SlamResult(damageEnqueued, damageEnqueueReason, damageAmount, radiusBlocks, false, null, "chunkNotLoaded", belowHit);
        }

        var blockType = chunk.getBlockType(belowHit.x, belowHit.y, belowHit.z);
        String blockTypeId = blockType != null ? blockType.getId() : null;
        if (blockType == null || blockType.isUnknown()) {
            return new SlamResult(damageEnqueued, damageEnqueueReason, damageAmount, radiusBlocks, false, blockTypeId, "blockTypeMissingOrUnknown", belowHit);
        }

        if (blockType == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY || blockType.getDrawType() == com.hypixel.hytale.protocol.DrawType.Empty) {
            return new SlamResult(damageEnqueued, damageEnqueueReason, damageAmount, radiusBlocks, false, blockTypeId, "blockUnbreakable.drawTypeEmpty", belowHit);
        }

        boolean broke = chunk.breakBlock(belowHit.x, belowHit.y, belowHit.z);
        return new SlamResult(damageEnqueued, damageEnqueueReason, damageAmount, radiusBlocks, broke, blockTypeId, broke ? "broken" : "breakFailed", belowHit);
    }

    private static @Nullable BlockCollisionData raycastSolidBlockBelow(@Nonnull World world, @Nonnull Vector3d position) {
        return raycastBlockBelow(world, position, 2.5);
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
