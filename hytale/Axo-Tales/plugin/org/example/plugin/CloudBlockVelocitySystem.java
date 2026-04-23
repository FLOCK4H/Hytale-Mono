package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.PositionDataComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a one-shot directional vertical launch when players move through the Axo Tales cloud block.
 */
public final class CloudBlockVelocitySystem extends TickingSystem<EntityStore> {

    public static final String CLOUD_BLOCK_ITEM_ID = "AxoTales_Cloud_Block";

    private static final int CHUNK_WIDTH_BLOCKS = ChunkUtil.SIZE;
    private static final double PLAYER_HORIZONTAL_RADIUS_BLOCKS = 0.42;
    private static final double DEFAULT_TARGET_HEIGHT_BLOCKS = 6.0;
    private static final double BASE_JUMP_HEIGHT_BLOCKS = 2.0;
    private static final double DEFAULT_MAX_VERTICAL_SPEED = 32.0;
    private static final double DEFAULT_MIN_CONTACT_VELOCITY = 0.12;
    private static final double DEFAULT_COOLDOWN_SECONDS = 1.0;
    private static final double DEFAULT_CHAIN_VELOCITY_MULTIPLIER = 1.5;
    private static final double DEFAULT_CHAIN_RESET_SECONDS = 4.0;
    private static final double POSITION_DELTA_DIRECTION_THRESHOLD_BLOCKS = 0.015;
    private static final double CONTACT_GEOMETRY_DIRECTION_THRESHOLD_BLOCKS = 0.20;
    private static final long DEBUG_MISSING_BLOCK_INTERVAL_NANOS = 10_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final Map<UUID, ContactState> stateByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastPlayerYByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ChainState> chainByPlayer = new ConcurrentHashMap<>();

    private volatile int cloudBlockTypeId = Integer.MIN_VALUE;
    private volatile long nextMissingBlockDebugAtNanos = 0L;

    public CloudBlockVelocitySystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            RuntimeConfig runtime = readRuntimeConfig();
            if (!runtime.enabled) {
                stateByPlayer.clear();
                lastPlayerYByPlayer.clear();
                chainByPlayer.clear();
                return;
            }

            int blockTypeId = resolveCloudBlockTypeId();
            if (blockTypeId < 0) {
                debugMissingBlockMaybe(System.nanoTime());
                return;
            }

            EntityStore external = store.getExternalData();
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
                    PositionDataComponent.getComponentType(),
                    MovementManager.getComponentType(),
                    Velocity.getComponentType()
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

                            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
                            Vector3d position = transform != null ? transform.getPosition() : null;
                            if (position == null || !position.isFinite()) {
                                lastPlayerYByPlayer.remove(uuid);
                                markExit(playerRef, uuid, "positionMissingOrInvalid", runtime, nowNanos);
                                continue;
                            }

                            Double previousPlayerY = lastPlayerYByPlayer.put(uuid, position.y);
                            Velocity velocity = chunk.getComponent(index, Velocity.getComponentType());
                            Vector3d currentVelocity = velocity != null ? velocity.getVelocity() : null;
                            if (currentVelocity == null || !currentVelocity.isFinite()) {
                                markExit(playerRef, uuid, "velocityMissingOrInvalid", runtime, nowNanos);
                                continue;
                            }

                            PositionDataComponent positionData = chunk.getComponent(index, PositionDataComponent.getComponentType());
                            CloudContact contact = findCloudContact(world, position, positionData, blockTypeId);
                            if (contact == null) {
                                markExit(playerRef, uuid, "notTouchingCloud", runtime, nowNanos);
                                continue;
                            }

                            Player player = chunk.getComponent(index, Player.getComponentType());
                            MovementManager movementManager = chunk.getComponent(index, MovementManager.getComponentType());
                            handleCloudContact(
                                playerRef,
                                uuid,
                                player,
                                movementManager,
                                velocity,
                                currentVelocity,
                                position,
                                previousPlayerY,
                                positionData,
                                contact,
                                runtime,
                                nowNanos
                            );
                        } catch (Throwable ignoredPerPlayer) {
                            // Keep one malformed entity from breaking cloud handling for every player.
                        }
                    }
                }
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "CloudBlockVelocitySystem: tick failed.", t);
        }
    }

    public void onPlayerDisconnect(@Nullable PlayerRef playerRef) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        stateByPlayer.remove(playerRef.getUuid());
        lastPlayerYByPlayer.remove(playerRef.getUuid());
        chainByPlayer.remove(playerRef.getUuid());
    }

    public void shutdown() {
        stateByPlayer.clear();
        lastPlayerYByPlayer.clear();
        chainByPlayer.clear();
    }

    private void handleCloudContact(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID uuid,
        @Nullable Player player,
        @Nullable MovementManager movementManager,
        @Nonnull Velocity velocity,
        @Nonnull Vector3d currentVelocity,
        @Nonnull Vector3d position,
        @Nullable Double previousPlayerY,
        @Nullable PositionDataComponent positionData,
        @Nonnull CloudContact contact,
        @Nonnull RuntimeConfig runtime,
        long nowNanos
    ) {
        double velocityY = currentVelocity.y;
        DirectionDecision directionDecision = resolveLaunchDirection(position, previousPlayerY, contact, velocityY, runtime.minContactVelocity);
        int direction = directionDecision.direction;
        ContactState previous = stateByPlayer.get(uuid);
        String contactKey = contact.key();
        boolean sameCloudVolume = previous != null;
        boolean blockChanged = previous == null || !contactKey.equals(previous.contactKey);
        boolean directionChanged = previous == null || previous.direction != direction;

        if (direction == 0) {
            stateByPlayer.put(uuid, ContactState.seen(contactKey, 0, previous, nowNanos));
            if (previous == null || blockChanged || previous.direction != 0) {
                debug.traceFileOnly(
                    playerRef,
                    "CloudBlockVelocity event=contact"
                        + " allow=false"
                        + " reason=verticalVelocityTooSmall"
                        + contactTrace(contact, position, positionData)
                        + " velocity.y=" + format(velocityY)
                        + directionTrace(directionDecision)
                        + " minContactVelocity=" + format(runtime.minContactVelocity)
                );
            }
            return;
        }

        if (previous != null && previous.launched && !blockChanged) {
            stateByPlayer.put(uuid, ContactState.seen(contactKey, direction, previous, nowNanos));
            if (directionChanged || blockChanged) {
                debug.traceFileOnly(
                    playerRef,
                    "CloudBlockVelocity event=launch"
                        + " allow=false"
                        + " reason=alreadyLaunchedThisContact"
                        + contactTrace(contact, position, positionData)
                        + " direction=" + directionLabel(direction)
                        + directionTrace(directionDecision)
                        + " targetHeightBlocks=" + format(runtime.targetHeightBlocks)
                        + " rearmSeconds=" + format(runtime.cooldownSeconds)
                        + " block.changed=" + blockChanged
                        + " direction.changed=" + directionChanged
                );
            }
            return;
        }

        ChainStep chainStep = nextChainStep(uuid, direction, runtime, nowNanos);
        LaunchVelocity launchVelocityY = computeLaunchVelocityY(movementManager, runtime.targetHeightBlocks, direction);
        double chainedVelocityY = launchVelocityY.velocityY * chainStep.multiplier;
        double targetVelocityY = direction > 0
            ? Math.min(runtime.maxVerticalSpeed, Math.max(currentVelocity.y, chainedVelocityY))
            : Math.max(-runtime.maxVerticalSpeed, Math.min(currentVelocity.y, chainedVelocityY));
        Vector3d targetVelocity = new Vector3d(currentVelocity.x, targetVelocityY, currentVelocity.z);
        if (!targetVelocity.isFinite()) {
            stateByPlayer.put(uuid, ContactState.seen(contactKey, direction, previous, nowNanos));
            debug.traceFileOnly(
                playerRef,
                "CloudBlockVelocity event=launch"
                    + " allow=false"
                    + " reason=targetVelocityInvalid"
                    + contactTrace(contact, position, positionData)
                    + " direction=" + directionLabel(direction)
                    + directionTrace(directionDecision)
                    + " targetHeightBlocks=" + format(runtime.targetHeightBlocks)
                    + " chain.index=" + chainStep.index
                    + " chain.multiplier=" + format(chainStep.multiplier)
                    + " chain.velocityMultiplier=" + format(runtime.chainVelocityMultiplier)
                    + " launch.source=" + launchVelocityY.source
                    + " launch.jumpForce=" + format(launchVelocityY.jumpForce)
                    + " launch.chainedVelocityY=" + format(chainedVelocityY)
                    + " velocity.before=" + Vector3d.formatShortString(currentVelocity)
            );
            return;
        }

        velocity.addInstruction(targetVelocity, null, ChangeVelocityType.Set);
        if (player != null) {
            player.setCurrentFallDistance(0.0);
        }
        stateByPlayer.put(uuid, new ContactState(contactKey, direction, true, nowNanos, nowNanos));
        chainByPlayer.put(uuid, new ChainState(direction, chainStep.index, nowNanos));

        debug.traceFileOnly(
            playerRef,
            "CloudBlockVelocity event=launch"
                + " allow=true"
                + " reason=applied"
                + contactTrace(contact, position, positionData)
                + " direction=" + directionLabel(direction)
                + directionTrace(directionDecision)
                + " changeVelocityType=Set"
                + " targetHeightBlocks=" + format(runtime.targetHeightBlocks)
                + " chain.index=" + chainStep.index
                + " chain.multiplier=" + format(chainStep.multiplier)
                + " chain.velocityMultiplier=" + format(runtime.chainVelocityMultiplier)
                + " chain.resetSeconds=" + format(runtime.chainResetSeconds)
                + " launch.source=" + launchVelocityY.source
                + " launch.jumpForce=" + format(launchVelocityY.jumpForce)
                + " launch.unclampedVelocityY=" + format(launchVelocityY.velocityY)
                + " launch.chainedVelocityY=" + format(chainedVelocityY)
                + " maxVerticalSpeed=" + format(runtime.maxVerticalSpeed)
                + " rearmSeconds=" + format(runtime.cooldownSeconds)
                + " sameCloudVolume=" + sameCloudVolume
                + " block.changed=" + blockChanged
                + " direction.changed=" + directionChanged
                + " velocity.before=" + Vector3d.formatShortString(currentVelocity)
                + " velocity.after=" + Vector3d.formatShortString(targetVelocity)
                + " velocityY.before=" + format(velocityY)
                + " velocityY.after=" + format(targetVelocityY)
        );
    }

    private @Nonnull ChainStep nextChainStep(
        @Nonnull UUID uuid,
        int direction,
        @Nonnull RuntimeConfig runtime,
        long nowNanos
    ) {
        ChainState previous = chainByPlayer.get(uuid);
        int index = 0;
        long resetNanos = secondsToNanos(runtime.chainResetSeconds);
        if (previous != null
            && previous.direction == direction
            && (resetNanos <= 0L || nowNanos - previous.lastLaunchAtNanos <= resetNanos)) {
            index = Math.max(0, previous.index + 1);
        }

        double multiplier = Math.pow(runtime.chainVelocityMultiplier, index);
        if (!Double.isFinite(multiplier) || multiplier < 1.0) {
            multiplier = 1.0;
        }
        return new ChainStep(index, multiplier);
    }

    private void markExit(
        @Nullable PlayerRef playerRef,
        @Nonnull UUID uuid,
        @Nonnull String reason,
        @Nonnull RuntimeConfig runtime,
        long nowNanos
    ) {
        ContactState previous = stateByPlayer.get(uuid);
        if (previous == null) {
            return;
        }

        long rearmNanos = secondsToNanos(runtime.cooldownSeconds);
        long unseenNanos = nowNanos - previous.lastSeenAtNanos;
        if (previous.launched && unseenNanos >= 0L && unseenNanos < rearmNanos) {
            return;
        }

        stateByPlayer.remove(uuid);
        debug.traceFileOnly(
            playerRef,
            "CloudBlockVelocity event=exit"
                + " reason=" + reason
                + " previous.contactKey=" + previous.contactKey
                + " previous.direction=" + directionLabel(previous.direction)
                + " previous.launched=" + previous.launched
                + " unseenSeconds=" + format(Math.max(0.0, unseenNanos / 1_000_000_000.0))
                + " rearmSeconds=" + format(runtime.cooldownSeconds)
        );
    }

    private @Nullable CloudContact findCloudContact(
        @Nonnull World world,
        @Nonnull Vector3d position,
        @Nullable PositionDataComponent positionData,
        int cloudBlockTypeId
    ) {
        int baseX = floor(position.x);
        int baseY = floor(position.y);
        int baseZ = floor(position.z);

        int[] xs = new int[] {
            floor(position.x - PLAYER_HORIZONTAL_RADIUS_BLOCKS),
            baseX,
            floor(position.x + PLAYER_HORIZONTAL_RADIUS_BLOCKS)
        };
        int[] ys = new int[] {
            baseY - 1,
            baseY,
            baseY + 1,
            baseY + 2
        };
        int[] zs = new int[] {
            floor(position.z - PLAYER_HORIZONTAL_RADIUS_BLOCKS),
            baseZ,
            floor(position.z + PLAYER_HORIZONTAL_RADIUS_BLOCKS)
        };

        for (int y : ys) {
            if (y < 1 || y > ChunkUtil.HEIGHT_MINUS_1) {
                continue;
            }
            for (int x : xs) {
                for (int z : zs) {
                    CloudContact contact = inspectCloudBlock(world, x, y, z, cloudBlockTypeId, "scan");
                    if (contact != null) {
                        return contact;
                    }
                }
            }
        }

        if (positionData != null && positionData.getInsideBlockTypeId() == cloudBlockTypeId) {
            return new CloudContact(baseX, baseY, baseZ, CLOUD_BLOCK_ITEM_ID, cloudBlockTypeId, "positionData.inside");
        }
        if (positionData != null && positionData.getStandingOnBlockTypeId() == cloudBlockTypeId) {
            return new CloudContact(baseX, baseY - 1, baseZ, CLOUD_BLOCK_ITEM_ID, cloudBlockTypeId, "positionData.standingOn");
        }
        return null;
    }

    private @Nullable CloudContact inspectCloudBlock(
        @Nonnull World world,
        int blockX,
        int blockY,
        int blockZ,
        int cloudBlockTypeId,
        @Nonnull String source
    ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfInMemory(chunkIndex);
        }
        if (chunk == null) {
            return null;
        }

        int localX = blockX - (chunk.getX() * CHUNK_WIDTH_BLOCKS);
        int localZ = blockZ - (chunk.getZ() * CHUNK_WIDTH_BLOCKS);
        if (localX < 0 || localX >= CHUNK_WIDTH_BLOCKS || localZ < 0 || localZ >= CHUNK_WIDTH_BLOCKS) {
            return null;
        }

        int blockTypeId;
        try {
            blockTypeId = chunk.getBlock(localX, blockY, localZ);
        } catch (Throwable ignored) {
            return null;
        }
        if (blockTypeId != cloudBlockTypeId) {
            return null;
        }

        BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeId);
        String blockId = blockType != null && !blockType.isUnknown() ? blockType.getId() : CLOUD_BLOCK_ITEM_ID;
        return new CloudContact(blockX, blockY, blockZ, blockId, blockTypeId, source);
    }

    private int resolveCloudBlockTypeId() {
        int cached = cloudBlockTypeId;
        if (cached >= 0) {
            return cached;
        }

        int resolved = BlockType.getAssetMap().getIndexOrDefault(CLOUD_BLOCK_ITEM_ID, -1);
        if (resolved >= 0) {
            cloudBlockTypeId = resolved;
        }
        return resolved;
    }

    private void debugMissingBlockMaybe(long nowNanos) {
        long next = nextMissingBlockDebugAtNanos;
        if (next > nowNanos) {
            return;
        }
        nextMissingBlockDebugAtNanos = nowNanos + DEBUG_MISSING_BLOCK_INTERVAL_NANOS;
        debug.traceFileOnly(
            null,
            "CloudBlockVelocity event=resolveBlock allow=false reason=blockTypeMissing blockId=" + CLOUD_BLOCK_ITEM_ID
        );
    }

    private static @Nonnull LaunchVelocity computeLaunchVelocityY(
        @Nullable MovementManager movementManager,
        double targetHeightBlocks,
        int direction
    ) {
        double magnitude = targetHeightBlocks;
        double jumpForce = Double.NaN;
        String source = "fallbackHeightAsVelocity";

        if (movementManager != null && movementManager.getSettings() != null) {
            jumpForce = movementManager.getSettings().jumpForce;
            if (Double.isFinite(jumpForce) && jumpForce > 0.0) {
                double heightMultiplier = Math.sqrt(Math.max(0.0, targetHeightBlocks) / BASE_JUMP_HEIGHT_BLOCKS);
                magnitude = jumpForce * heightMultiplier;
                source = "scaledFromJumpForce";
            }
        }

        double signedVelocity = direction > 0 ? magnitude : -magnitude;
        return new LaunchVelocity(signedVelocity, source, jumpForce);
    }

    private static @Nonnull DirectionDecision resolveLaunchDirection(
        @Nonnull Vector3d position,
        @Nullable Double previousPlayerY,
        @Nonnull CloudContact contact,
        double velocityY,
        double minContactVelocity
    ) {
        double deltaY = Double.NaN;
        double contactOffsetY = contactOffsetY(position, contact);
        if (previousPlayerY != null && Double.isFinite(previousPlayerY)) {
            deltaY = position.y - previousPlayerY;
            int deltaDirection = directionFromSignedValue(deltaY, POSITION_DELTA_DIRECTION_THRESHOLD_BLOCKS);
            if (deltaDirection != 0) {
                return new DirectionDecision(deltaDirection, "positionDelta", deltaY, velocityY, contactOffsetY);
            }
        }

        int geometryDirection = directionFromContactGeometry(contactOffsetY, CONTACT_GEOMETRY_DIRECTION_THRESHOLD_BLOCKS);
        int velocityDirection = directionFromVelocity(velocityY, minContactVelocity);
        if (geometryDirection > 0 && velocityDirection < 0) {
            return new DirectionDecision(geometryDirection, "contactGeometryBelowCloud", deltaY, velocityY, contactOffsetY);
        }
        if (velocityDirection != 0) {
            return new DirectionDecision(velocityDirection, "velocity", deltaY, velocityY, contactOffsetY);
        }

        if (geometryDirection != 0) {
            return new DirectionDecision(geometryDirection, "contactGeometry", deltaY, velocityY, contactOffsetY);
        }

        return new DirectionDecision(0, "none", deltaY, velocityY, contactOffsetY);
    }

    private RuntimeConfig readRuntimeConfig() {
        AxoTalesServerConfig.CloudBlock cloud = config != null ? config.cloudBlock : null;
        if (cloud == null) {
            return new RuntimeConfig(
                true,
                DEFAULT_TARGET_HEIGHT_BLOCKS,
                DEFAULT_MAX_VERTICAL_SPEED,
                DEFAULT_MIN_CONTACT_VELOCITY,
                DEFAULT_COOLDOWN_SECONDS,
                DEFAULT_CHAIN_VELOCITY_MULTIPLIER,
                DEFAULT_CHAIN_RESET_SECONDS
            );
        }

        double targetHeightBlocks = finiteOrDefault(cloud.targetHeightBlocks, DEFAULT_TARGET_HEIGHT_BLOCKS);
        double maxSpeed = finiteOrDefault(cloud.maxVerticalSpeed, DEFAULT_MAX_VERTICAL_SPEED);
        double minContactVelocity = finiteOrDefault(cloud.minContactVelocity, DEFAULT_MIN_CONTACT_VELOCITY);
        double cooldownSeconds = finiteOrDefault(cloud.cooldownSeconds, DEFAULT_COOLDOWN_SECONDS);
        double chainVelocityMultiplier = finiteOrDefault(cloud.chainVelocityMultiplier, DEFAULT_CHAIN_VELOCITY_MULTIPLIER);
        double chainResetSeconds = finiteOrDefault(cloud.chainResetSeconds, DEFAULT_CHAIN_RESET_SECONDS);

        targetHeightBlocks = clamp(targetHeightBlocks, 0.25, 64.0);
        maxSpeed = clamp(maxSpeed, 0.1, 80.0);
        minContactVelocity = clamp(minContactVelocity, 0.0, 10.0);
        cooldownSeconds = clamp(cooldownSeconds, 0.0, 5.0);
        chainVelocityMultiplier = clamp(chainVelocityMultiplier, 1.0, 5.0);
        chainResetSeconds = clamp(chainResetSeconds, 0.0, 30.0);
        return new RuntimeConfig(
            cloud.enabled,
            targetHeightBlocks,
            maxSpeed,
            minContactVelocity,
            cooldownSeconds,
            chainVelocityMultiplier,
            chainResetSeconds
        );
    }

    private static int directionFromVelocity(double velocityY, double minContactVelocity) {
        return directionFromSignedValue(velocityY, minContactVelocity);
    }

    private static int directionFromSignedValue(double value, double threshold) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        if (value > threshold) {
            return 1;
        }
        if (value < -threshold) {
            return -1;
        }
        return 0;
    }

    private static int directionFromContactGeometry(double contactOffsetY, double threshold) {
        if (!Double.isFinite(contactOffsetY)) {
            return 0;
        }
        if (contactOffsetY < -threshold) {
            return 1;
        }
        if (contactOffsetY > threshold) {
            return -1;
        }
        return 0;
    }

    private static double contactOffsetY(@Nonnull Vector3d position, @Nonnull CloudContact contact) {
        return position.y - (contact.y + 0.5);
    }

    private static String contactTrace(
        @Nonnull CloudContact contact,
        @Nonnull Vector3d position,
        @Nullable PositionDataComponent positionData
    ) {
        int insideId = positionData != null ? positionData.getInsideBlockTypeId() : -1;
        int standingId = positionData != null ? positionData.getStandingOnBlockTypeId() : -1;
        return " blockId=" + contact.blockId
            + " blockTypeId=" + contact.blockTypeId
            + " block=[" + contact.x + "," + contact.y + "," + contact.z + "]"
            + " detectedFrom=" + contact.source
            + " player.position=" + Vector3d.formatShortString(position)
            + " positionData.insideBlockTypeId=" + insideId
            + " positionData.standingOnBlockTypeId=" + standingId;
    }

    private static long secondsToNanos(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0.0) {
            return 0L;
        }
        return (long) (seconds * 1_000_000_000L);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static double finiteOrDefault(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String directionLabel(int direction) {
        if (direction > 0) {
            return "up";
        }
        if (direction < 0) {
            return "down";
        }
        return "none";
    }

    private static String directionTrace(@Nonnull DirectionDecision decision) {
        return " direction.source=" + decision.source
            + " position.deltaY=" + format(decision.deltaY)
            + " contact.offsetY=" + format(decision.contactOffsetY)
            + " velocity.y.sample=" + format(decision.velocityY);
    }

    private record RuntimeConfig(
        boolean enabled,
        double targetHeightBlocks,
        double maxVerticalSpeed,
        double minContactVelocity,
        double cooldownSeconds,
        double chainVelocityMultiplier,
        double chainResetSeconds
    ) {
    }

    private record LaunchVelocity(double velocityY, @Nonnull String source, double jumpForce) {
    }

    private record ChainStep(int index, double multiplier) {
    }

    private record ChainState(int direction, int index, long lastLaunchAtNanos) {
    }

    private record DirectionDecision(
        int direction,
        @Nonnull String source,
        double deltaY,
        double velocityY,
        double contactOffsetY
    ) {
    }

    private record ContactState(@Nonnull String contactKey, int direction, boolean launched, long lastLaunchAtNanos, long lastSeenAtNanos) {
        static ContactState seen(@Nonnull String contactKey, int direction, @Nullable ContactState previous, long nowNanos) {
            boolean sameContact = previous != null && contactKey.equals(previous.contactKey);
            return new ContactState(
                contactKey,
                direction,
                sameContact && previous.launched,
                sameContact ? previous.lastLaunchAtNanos : 0L,
                nowNanos
            );
        }
    }

    private record CloudContact(
        int x,
        int y,
        int z,
        @Nonnull String blockId,
        int blockTypeId,
        @Nonnull String source
    ) {
        @Nonnull
        String key() {
            return x + "," + y + "," + z;
        }
    }
}
