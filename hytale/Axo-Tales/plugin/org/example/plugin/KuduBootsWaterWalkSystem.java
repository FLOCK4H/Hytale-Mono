package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PositionDataComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows players wearing the Kudu Boots armor item to walk on water by placing Snow Brick blocks where water is
 * detected (fluid layer).
 *
 * <p>Debug traces are written to the persistent plugin debug log.</p>
 */
public final class KuduBootsWaterWalkSystem extends TickingSystem<EntityStore> {

    private static final String KUDU_BOOTS_ITEM_ID = "Kudu_Boots";
    private static final String SNOW_BRICK_BLOCK_ITEM_ID = "Soil_Snow_Brick";
    private static final String WATER_FLUID_KEY_PREFIX = "Fluid_Water";
    private static final String WATER_BLOCK_KEY_PREFIX = "Water";

    private static final int CHUNK_WIDTH_BLOCKS = ChunkUtil.SIZE;
    private static final double BELOW_Y_EPSILON = 0.01;
    private static final int WATER_SEARCH_DEPTH_BLOCKS = 2;
    private static final int WATER_SEARCH_UP_BLOCKS = 3;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Map<UUID, Boolean> lastActiveByPlayer = new ConcurrentHashMap<>();

    public KuduBootsWaterWalkSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug
    ) {
        this.errors = errors;
        this.debug = debug;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            var external = store.getExternalData();
            World world = external != null ? external.getWorld() : null;
            if (world == null) {
                return;
            }

            long nowNanos = System.nanoTime();
            store.forEachChunk(
                Query.and(
                    Player.getComponentType(),
                    PlayerRef.getComponentType(),
                    TransformComponent.getComponentType(),
                    MovementStatesComponent.getComponentType(),
                    PositionDataComponent.getComponentType()
                ),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int index = 0; index < chunk.size(); index++) {
                        try {
                            Ref<EntityStore> playerEntityRef = chunk.getReferenceTo(index);
                            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                                continue;
                            }

                            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
                            UUID uuid = playerRef != null ? playerRef.getUuid() : null;
                            if (uuid == null) {
                                continue;
                            }

                            Player player = chunk.getComponent(index, Player.getComponentType());
                            if (player == null) {
                                continue;
                            }

                            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
                            Vector3d position = transform != null ? transform.getPosition() : null;
                            if (position == null || !position.isFinite()) {
                                markInactive(playerRef, uuid, "positionMissingOrInvalid");
                                continue;
                            }

                            MovementStatesComponent movementStatesComponent = chunk.getComponent(index, MovementStatesComponent.getComponentType());
                            MovementStates movementStates = movementStatesComponent != null ? movementStatesComponent.getMovementStates() : null;
                            if (movementStates == null) {
                                markInactive(playerRef, uuid, "movementStatesMissing");
                                continue;
                            }

                            PositionDataComponent posData = chunk.getComponent(index, PositionDataComponent.getComponentType());
                            PositionDataSnapshot posSnapshot = PositionDataSnapshot.from(posData);

                            ArmorLegSlotSnapshot legSlot = readLegArmorSlot(player);
                            boolean wearing = KUDU_BOOTS_ITEM_ID.equals(legSlot.itemId);
                            if (!wearing) {
                                markInactive(playerRef, uuid, "notWearingKuduBoots slotLegs.itemId=" + legSlot.itemId);
                                continue;
                            }

                            if (movementStates.crouching || movementStates.forcedCrouching) {
                                markInactive(playerRef, uuid, "crouching");
                                continue;
                            }

                            Transform look = TargetUtil.getLook(playerEntityRef, store);
                            if (look == null) {
                                look = playerRef.getTransform();
                            }

                            Vector3d direction = look != null ? look.getDirection() : null;
                            Step step = stepFromDirection(direction);

                            int blockX = (int) Math.floor(position.x);
                            int blockY = (int) Math.floor(position.y - BELOW_Y_EPSILON);
                            int blockZ = (int) Math.floor(position.z);

                            BlockPlacement below = replaceWaterWithSnowBrick(world, blockX, blockY, blockZ);
                            BlockPlacement ahead = replaceWaterWithSnowBrick(world, blockX + step.dx, blockY, blockZ + step.dz);

                            boolean nearWater = below.wasWater || ahead.wasWater;
                            if (!nearWater) {
                                markInactive(
                                    playerRef,
                                    uuid,
                                    "noWaterBelowOrAhead"
                                        + " pos.inside=" + posSnapshot.insideLabel()
                                        + " pos.standingOn=" + posSnapshot.standingOnLabel()
                                        + " states.inFluid=" + movementStates.inFluid
                                        + " states.swimming=" + movementStates.swimming
                                        + " below=" + below.reason + ":" + below.blockTypeId + "@[" + below.x + "," + below.y + "," + below.z + "]"
                                        + " fluid=" + below.fluidLabel()
                                        + " ahead=" + ahead.reason + ":" + ahead.blockTypeId + "@[" + ahead.x + "," + ahead.y + "," + ahead.z + "]"
                                        + " fluid=" + ahead.fluidLabel()
                                );
                                continue;
                            }

                            markActive(playerRef, uuid, legSlot, blockX, blockY, blockZ, step, below, ahead, movementStates, posSnapshot);
                        } catch (Throwable ignoredPerEntity) {
                            // Best-effort: isolate per-entity failures.
                        }
                    }
                }
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduBootsWaterWalkSystem: tick failed.", t);
        }
    }

    public void onPlayerDisconnect(@Nullable PlayerRef playerRef) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        lastActiveByPlayer.remove(uuid);
    }

    public void shutdown() {
        lastActiveByPlayer.clear();
    }

    private void markActive(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID uuid,
        @Nonnull ArmorLegSlotSnapshot legSlot,
        int blockX,
        int blockY,
        int blockZ,
        @Nonnull Step step,
        @Nonnull BlockPlacement below,
        @Nonnull BlockPlacement ahead,
        @Nonnull MovementStates movementStates,
        @Nonnull PositionDataSnapshot posSnapshot
    ) {
        Boolean previous = lastActiveByPlayer.put(uuid, true);
        boolean transitioned = previous == null || !previous;
        if (!transitioned) {
            return;
        }

        debug.traceFileOnly(
            playerRef,
            "KuduBootsWaterWalk event=enter"
                + " allow=true"
                + " snowBlockId=" + SNOW_BRICK_BLOCK_ITEM_ID
                + " pos.inside=" + posSnapshot.insideLabel()
                + " pos.standingOn=" + posSnapshot.standingOnLabel()
                + " detectedFrom=inventory.armor[Legs]"
                + " slotLegs.itemId=" + legSlot.itemId
                + " player.block=[" + blockX + "," + blockY + "," + blockZ + "]"
                + " ahead.step=[dx=" + step.dx + ",dz=" + step.dz + "]"
                + " below=" + below.reason + ":" + below.blockTypeId + "@[" + below.x + "," + below.y + "," + below.z + "]"
                + " fluid=" + below.fluidLabel()
                + " ahead=" + ahead.reason + ":" + ahead.blockTypeId + "@[" + ahead.x + "," + ahead.y + "," + ahead.z + "]"
                + " fluid=" + ahead.fluidLabel()
                + " placed.count=" + ((below.placed ? 1 : 0) + (ahead.placed ? 1 : 0))
                + " states.onGround=" + movementStates.onGround
                + " states.inFluid=" + movementStates.inFluid
                + " states.swimming=" + movementStates.swimming
                + " states.jumping=" + movementStates.jumping
                + " states.falling=" + movementStates.falling
        );
    }

    private void markInactive(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID uuid,
        @Nonnull String reason
    ) {
        Boolean previous = lastActiveByPlayer.put(uuid, false);
        if (previous != null && previous) {
            debug.traceFileOnly(
                playerRef,
                "KuduBootsWaterWalk event=exit reason=" + reason
            );
        }
    }

    private record ArmorLegSlotSnapshot(@Nullable String itemId) {}

    private record Step(int dx, int dz) {}

    private record BlockPlacement(
        int x,
        int y,
        int z,
        @Nullable String blockTypeId,
        int fluidId,
        int fluidLevel,
        @Nullable String fluidKey,
        boolean wasWater,
        boolean placed,
        @Nonnull String reason
    ) {
        @Nonnull
        String fluidLabel() {
            String key = fluidKey != null ? fluidKey : "null";
            return key + "(id=" + fluidId + ",lvl=" + fluidLevel + ")";
        }
    }

    private record PositionDataSnapshot(
        int insideBlockTypeId,
        @Nullable String insideBlockTypeKey,
        int standingOnBlockTypeId,
        @Nullable String standingOnBlockTypeKey
    ) {
        @Nonnull
        static PositionDataSnapshot from(@Nullable PositionDataComponent component) {
            if (component == null) {
                return new PositionDataSnapshot(-1, null, -1, null);
            }

            int inside = component.getInsideBlockTypeId();
            int standing = component.getStandingOnBlockTypeId();

            BlockType insideType = inside >= 0 ? BlockType.getAssetMap().getAsset(inside) : null;
            String insideKey = insideType != null && !insideType.isUnknown() ? insideType.getId() : null;

            BlockType standingType = standing >= 0 ? BlockType.getAssetMap().getAsset(standing) : null;
            String standingKey = standingType != null && !standingType.isUnknown() ? standingType.getId() : null;

            return new PositionDataSnapshot(inside, insideKey, standing, standingKey);
        }

        @Nonnull
        String insideLabel() {
            return (insideBlockTypeKey != null ? insideBlockTypeKey : "null")
                + "(id=" + insideBlockTypeId + ")";
        }

        @Nonnull
        String standingOnLabel() {
            return (standingOnBlockTypeKey != null ? standingOnBlockTypeKey : "null")
                + "(id=" + standingOnBlockTypeId + ")";
        }
    }

    @Nonnull
    private static ArmorLegSlotSnapshot readLegArmorSlot(@Nonnull Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return new ArmorLegSlotSnapshot(null);
        }

        ItemContainer armor = inventory.getArmor();
        if (armor == null) {
            return new ArmorLegSlotSnapshot(null);
        }

        short slot = (short) ItemArmorSlot.Legs.getValue();
        if (slot < 0 || slot >= armor.getCapacity()) {
            return new ArmorLegSlotSnapshot(null);
        }

        ItemStack stack = armor.getItemStack(slot);
        if (stack == null || !stack.isValid()) {
            return new ArmorLegSlotSnapshot(null);
        }

        return new ArmorLegSlotSnapshot(stack.getItemId());
    }

    private static boolean isWaterFluidKey(@Nullable String fluidKey) {
        return fluidKey != null && (fluidKey.startsWith(WATER_FLUID_KEY_PREFIX) || fluidKey.startsWith(WATER_BLOCK_KEY_PREFIX));
    }

    private static Step stepFromDirection(@Nullable Vector3d direction) {
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new Step(0, 1);
        }

        double absX = Math.abs(direction.x);
        double absZ = Math.abs(direction.z);
        if (absX >= absZ) {
            return new Step(direction.x >= 0 ? 1 : -1, 0);
        }
        return new Step(0, direction.z >= 0 ? 1 : -1);
    }

    private BlockPlacement replaceWaterWithSnowBrick(@Nonnull World world, int x, int startingY, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfInMemory(chunkIndex);
        }
        if (chunk == null) {
            return new BlockPlacement(x, startingY, z, null, 0, 0, null, false, false, "chunkMissing");
        }

        int localX = x - (chunk.getX() * CHUNK_WIDTH_BLOCKS);
        int localZ = z - (chunk.getZ() * CHUNK_WIDTH_BLOCKS);

        BlockPlacement firstResult = null;

        for (int offset = 0; offset <= WATER_SEARCH_UP_BLOCKS; offset++) {
            int y = startingY + offset;
            BlockPlacement inspected = inspectAndMaybePlace(world, chunk, x, y, z, localX, localZ);
            if (inspected.wasWater) {
                return inspected;
            }
            if (firstResult == null) {
                firstResult = inspected;
            }
        }

        for (int depth = 1; depth <= WATER_SEARCH_DEPTH_BLOCKS; depth++) {
            int y = startingY - depth;
            BlockPlacement inspected = inspectAndMaybePlace(world, chunk, x, y, z, localX, localZ);
            if (inspected.wasWater) {
                return inspected;
            }
            if (firstResult == null) {
                firstResult = inspected;
            }
        }

        return firstResult != null
            ? firstResult
            : new BlockPlacement(x, startingY, z, null, 0, 0, null, false, false, "noCandidates");
    }

    private BlockPlacement inspectAndMaybePlace(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int x,
        int y,
        int z,
        int localX,
        int localZ
    ) {
        if (y < 1 || y > ChunkUtil.HEIGHT_MINUS_1) {
            return new BlockPlacement(x, y, z, null, 0, 0, null, false, false, "yOutOfRange");
        }

        BlockType blockType = null;
        String blockTypeId = null;
        try {
            blockType = chunk.getBlockType(x, y, z);
            if (blockType != null && !blockType.isUnknown()) {
                blockTypeId = blockType.getId();
            }
        } catch (Throwable ignored) {
            // Best-effort debug; still try fluid path.
        }

        int fluidId;
        int fluidLevel;
        FluidRead fluidRead = readFluidAt(world, chunk, x, y, z);
        fluidId = fluidRead.fluidId;
        fluidLevel = fluidRead.fluidLevel;
        if (!"ok".equals(fluidRead.reason)) {
            return new BlockPlacement(x, y, z, blockTypeId, fluidId, fluidLevel, fluidRead.fluidKey, false, false, "fluidRead." + fluidRead.reason);
        }
        if (fluidId <= 0) {
            return new BlockPlacement(x, y, z, blockTypeId, fluidId, fluidLevel, fluidRead.fluidKey, false, false, "noFluid");
        }

        String fluidKey = fluidRead.fluidKey;

        boolean isWater = isWaterFluidKey(fluidKey);
        if (!isWater) {
            return new BlockPlacement(x, y, z, blockTypeId, fluidId, fluidLevel, fluidKey, false, false, "notWaterFluid");
        }

        boolean ok;
        try {
            ok = chunk.setBlock(localX, y, localZ, SNOW_BRICK_BLOCK_ITEM_ID);
        } catch (Throwable t) {
            ok = false;
        }

        return new BlockPlacement(x, y, z, blockTypeId, fluidId, fluidLevel, fluidKey, true, ok, ok ? "placed" : "setBlockFailed");
    }

    private record FluidRead(
        int fluidId,
        int fluidLevel,
        @Nullable String fluidKey,
        @Nonnull String reason
    ) {
    }

    @Nonnull
    private static FluidRead readFluidAt(
        @Nonnull World world,
        @Nonnull WorldChunk chunk,
        int blockX,
        int blockY,
        int blockZ
    ) {
        try {
            ChunkStore chunkStore = world.getChunkStore();
            if (chunkStore == null) {
                return new FluidRead(0, 0, null, "chunkStoreMissing");
            }

            Store<ChunkStore> store = chunkStore.getStore();
            if (store == null) {
                return new FluidRead(0, 0, null, "chunkStoreStateMissing");
            }

            int sectionY = ChunkUtil.indexSection(blockY);
            var sectionRef = chunkStore.getChunkSectionReference(chunk.getX(), sectionY, chunk.getZ());
            if (sectionRef == null || !sectionRef.isValid()) {
                return new FluidRead(0, 0, null, "sectionRefMissing");
            }

            FluidSection fluidSection = store.getComponent(sectionRef, FluidSection.getComponentType());
            if (fluidSection == null) {
                return new FluidRead(0, 0, null, "fluidSectionMissing");
            }

            int fluidId = fluidSection.getFluidId(blockX, blockY, blockZ);
            int fluidLevel = Byte.toUnsignedInt(fluidSection.getFluidLevel(blockX, blockY, blockZ));

            String fluidKey = null;
            if (fluidId > 0) {
                Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
                if (fluid != null && !fluid.isUnknown()) {
                    fluidKey = fluid.getId();
                }
            }

            return new FluidRead(fluidId, fluidLevel, fluidKey, "ok");
        } catch (Throwable ignored) {
            return new FluidRead(0, 0, null, "exception");
        }
    }
}
