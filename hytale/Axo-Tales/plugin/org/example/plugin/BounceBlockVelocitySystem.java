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
 * Applies upward-only stacked launches when players touch the Axo Tales bounce block.
 */
public final class BounceBlockVelocitySystem extends TickingSystem<EntityStore> {

    public static final String BOUNCE_BLOCK_ITEM_ID = "AxoTales_Bounce_Block";

    private static final int CHUNK_WIDTH_BLOCKS = ChunkUtil.SIZE;
    private static final double PLAYER_HORIZONTAL_RADIUS_BLOCKS = 0.42;
    private static final double BASE_JUMP_HEIGHT_BLOCKS = 2.0;
    private static final double DEFAULT_BASE_TARGET_HEIGHT_BLOCKS = 4.0;
    private static final double DEFAULT_HEIGHT_GAIN_PER_BOUNCE_BLOCKS = 2.0;
    private static final double DEFAULT_MAX_TARGET_HEIGHT_BLOCKS = 18.0;
    private static final double DEFAULT_MAX_VERTICAL_SPEED = 48.0;
    private static final double DEFAULT_COOLDOWN_SECONDS = 0.20;
    private static final double DEFAULT_STREAK_RESET_SECONDS = 8.0;
    private static final long DEBUG_MISSING_BLOCK_INTERVAL_NANOS = 10_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final Map<UUID, ContactState> contactByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, BounceStreak> streakByPlayer = new ConcurrentHashMap<>();

    private volatile int bounceBlockTypeId = Integer.MIN_VALUE;
    private volatile long nextMissingBlockDebugAtNanos = 0L;

    public BounceBlockVelocitySystem(
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
                contactByPlayer.clear();
                streakByPlayer.clear();
                return;
            }

            int blockTypeId = resolveBounceBlockTypeId();
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
                                markExit(playerRef, uuid, "positionMissingOrInvalid", runtime, nowNanos);
                                continue;
                            }

                            Velocity velocity = chunk.getComponent(index, Velocity.getComponentType());
                            Vector3d currentVelocity = velocity != null ? velocity.getVelocity() : null;
                            if (currentVelocity == null || !currentVelocity.isFinite()) {
                                markExit(playerRef, uuid, "velocityMissingOrInvalid", runtime, nowNanos);
                                continue;
                            }

                            PositionDataComponent positionData = chunk.getComponent(index, PositionDataComponent.getComponentType());
                            BounceContact contact = findBounceContact(world, position, positionData, blockTypeId);
                            if (contact == null) {
                                markExit(playerRef, uuid, "notTouchingBounceBlock", runtime, nowNanos);
                                continue;
                            }

                            Player player = chunk.getComponent(index, Player.getComponentType());
                            MovementManager movementManager = chunk.getComponent(index, MovementManager.getComponentType());
                            handleBounceContact(
                                playerRef,
                                uuid,
                                player,
                                movementManager,
                                velocity,
                                currentVelocity,
                                position,
                                positionData,
                                contact,
                                runtime,
                                nowNanos
                            );
                        } catch (Throwable ignoredPerPlayer) {
                            // Keep one malformed entity from breaking bounce handling for every player.
                        }
                    }
                }
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "BounceBlockVelocitySystem: tick failed.", t);
        }
    }

    public void onPlayerDisconnect(@Nullable PlayerRef playerRef) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        contactByPlayer.remove(playerRef.getUuid());
        streakByPlayer.remove(playerRef.getUuid());
    }

    public void shutdown() {
        contactByPlayer.clear();
        streakByPlayer.clear();
    }

    private void handleBounceContact(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID uuid,
        @Nullable Player player,
        @Nullable MovementManager movementManager,
        @Nonnull Velocity velocity,
        @Nonnull Vector3d currentVelocity,
        @Nonnull Vector3d position,
        @Nullable PositionDataComponent positionData,
        @Nonnull BounceContact contact,
        @Nonnull RuntimeConfig runtime,
        long nowNanos
    ) {
        String contactKey = contact.key();
        ContactState previous = contactByPlayer.get(uuid);
        if (previous != null && previous.launched) {
            contactByPlayer.put(uuid, ContactState.seen(contactKey, previous, nowNanos));
            return;
        }

        BounceStep step = nextBounceStep(uuid, runtime, nowNanos);
        LaunchVelocity launchVelocityY = computeLaunchVelocityY(movementManager, step.targetHeightBlocks);
        double targetVelocityY = Math.max(currentVelocity.y, Math.min(runtime.maxVerticalSpeed, launchVelocityY.velocityY));
        Vector3d targetVelocity = new Vector3d(currentVelocity.x, targetVelocityY, currentVelocity.z);
        if (!targetVelocity.isFinite()) {
            contactByPlayer.put(uuid, ContactState.seen(contactKey, previous, nowNanos));
            debug.traceFileOnly(
                playerRef,
                "BounceBlockVelocity event=bounce"
                    + " allow=false"
                    + " reason=targetVelocityInvalid"
                    + contactTrace(contact, position, positionData)
                    + " bounce.count=" + step.count
                    + " targetHeightBlocks=" + format(step.targetHeightBlocks)
                    + " launch.source=" + launchVelocityY.source
                    + " launch.jumpForce=" + format(launchVelocityY.jumpForce)
                    + " velocity.before=" + Vector3d.formatShortString(currentVelocity)
            );
            return;
        }

        velocity.addInstruction(targetVelocity, null, ChangeVelocityType.Set);
        if (player != null) {
            player.setCurrentFallDistance(0.0);
        }
        contactByPlayer.put(uuid, new ContactState(contactKey, true, nowNanos, nowNanos));
        streakByPlayer.put(uuid, new BounceStreak(step.count + 1, nowNanos));

        debug.traceFileOnly(
            playerRef,
            "BounceBlockVelocity event=bounce"
                + " allow=true"
                + " reason=applied"
                + contactTrace(contact, position, positionData)
                + " direction=up"
                + " changeVelocityType=Set"
                + " bounce.count=" + step.count
                + " targetHeightBlocks=" + format(step.targetHeightBlocks)
                + " heightGainPerBounceBlocks=" + format(runtime.heightGainPerBounceBlocks)
                + " maxTargetHeightBlocks=" + format(runtime.maxTargetHeightBlocks)
                + " launch.source=" + launchVelocityY.source
                + " launch.jumpForce=" + format(launchVelocityY.jumpForce)
                + " launch.unclampedVelocityY=" + format(launchVelocityY.velocityY)
                + " maxVerticalSpeed=" + format(runtime.maxVerticalSpeed)
                + " rearmSeconds=" + format(runtime.cooldownSeconds)
                + " velocity.before=" + Vector3d.formatShortString(currentVelocity)
                + " velocity.after=" + Vector3d.formatShortString(targetVelocity)
        );
    }

    private void markExit(
        @Nullable PlayerRef playerRef,
        @Nonnull UUID uuid,
        @Nonnull String reason,
        @Nonnull RuntimeConfig runtime,
        long nowNanos
    ) {
        ContactState previous = contactByPlayer.get(uuid);
        if (previous == null) {
            return;
        }

        long rearmNanos = secondsToNanos(runtime.cooldownSeconds);
        long unseenNanos = nowNanos - previous.lastSeenAtNanos;
        if (previous.launched && unseenNanos >= 0L && unseenNanos < rearmNanos) {
            return;
        }

        contactByPlayer.remove(uuid);
        debug.traceFileOnly(
            playerRef,
            "BounceBlockVelocity event=exit"
                + " reason=" + reason
                + " previous.contactKey=" + previous.contactKey
                + " previous.launched=" + previous.launched
                + " unseenSeconds=" + format(Math.max(0.0, unseenNanos / 1_000_000_000.0))
                + " rearmSeconds=" + format(runtime.cooldownSeconds)
        );
    }

    private @Nullable BounceContact findBounceContact(
        @Nonnull World world,
        @Nonnull Vector3d position,
        @Nullable PositionDataComponent positionData,
        int bounceBlockTypeId
    ) {
        int baseX = floor(position.x);
        int baseY = floor(position.y);
        int baseZ = floor(position.z);

        if (positionData != null && positionData.getStandingOnBlockTypeId() == bounceBlockTypeId) {
            return new BounceContact(baseX, baseY - 1, baseZ, BOUNCE_BLOCK_ITEM_ID, bounceBlockTypeId, "positionData.standingOn");
        }
        if (positionData != null && positionData.getInsideBlockTypeId() == bounceBlockTypeId) {
            return new BounceContact(baseX, baseY, baseZ, BOUNCE_BLOCK_ITEM_ID, bounceBlockTypeId, "positionData.inside");
        }

        int[] xs = new int[] {
            floor(position.x - PLAYER_HORIZONTAL_RADIUS_BLOCKS),
            baseX,
            floor(position.x + PLAYER_HORIZONTAL_RADIUS_BLOCKS)
        };
        int[] ys = new int[] {
            baseY - 1,
            baseY,
            baseY + 1
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
                    BounceContact contact = inspectBounceBlock(world, x, y, z, bounceBlockTypeId, "scan");
                    if (contact != null) {
                        return contact;
                    }
                }
            }
        }

        return null;
    }

    private @Nullable BounceContact inspectBounceBlock(
        @Nonnull World world,
        int blockX,
        int blockY,
        int blockZ,
        int bounceBlockTypeId,
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
        if (blockTypeId != bounceBlockTypeId) {
            return null;
        }

        BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeId);
        String blockId = blockType != null && !blockType.isUnknown() ? blockType.getId() : BOUNCE_BLOCK_ITEM_ID;
        return new BounceContact(blockX, blockY, blockZ, blockId, blockTypeId, source);
    }

    private int resolveBounceBlockTypeId() {
        int cached = bounceBlockTypeId;
        if (cached >= 0) {
            return cached;
        }

        int resolved = BlockType.getAssetMap().getIndexOrDefault(BOUNCE_BLOCK_ITEM_ID, -1);
        if (resolved >= 0) {
            bounceBlockTypeId = resolved;
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
            "BounceBlockVelocity event=resolveBlock allow=false reason=blockTypeMissing blockId=" + BOUNCE_BLOCK_ITEM_ID
        );
    }

    private BounceStep nextBounceStep(@Nonnull UUID uuid, @Nonnull RuntimeConfig runtime, long nowNanos) {
        BounceStreak previous = streakByPlayer.get(uuid);
        int count = 0;
        long resetNanos = secondsToNanos(runtime.streakResetSeconds);
        if (previous != null && (resetNanos <= 0L || nowNanos - previous.lastBounceAtNanos <= resetNanos)) {
            count = Math.max(0, previous.count);
        }

        double targetHeight = runtime.baseTargetHeightBlocks + (count * runtime.heightGainPerBounceBlocks);
        targetHeight = clamp(targetHeight, runtime.baseTargetHeightBlocks, runtime.maxTargetHeightBlocks);
        return new BounceStep(count, targetHeight);
    }

    private static @Nonnull LaunchVelocity computeLaunchVelocityY(
        @Nullable MovementManager movementManager,
        double targetHeightBlocks
    ) {
        double velocityY = targetHeightBlocks;
        double jumpForce = Double.NaN;
        String source = "fallbackHeightAsVelocity";

        if (movementManager != null && movementManager.getSettings() != null) {
            jumpForce = movementManager.getSettings().jumpForce;
            if (Double.isFinite(jumpForce) && jumpForce > 0.0) {
                double heightMultiplier = Math.sqrt(Math.max(0.0, targetHeightBlocks) / BASE_JUMP_HEIGHT_BLOCKS);
                velocityY = jumpForce * heightMultiplier;
                source = "scaledFromJumpForce";
            }
        }

        return new LaunchVelocity(Math.max(0.0, velocityY), source, jumpForce);
    }

    private RuntimeConfig readRuntimeConfig() {
        AxoTalesServerConfig.BounceBlock bounce = config != null ? config.bounceBlock : null;
        if (bounce == null) {
            return new RuntimeConfig(
                true,
                DEFAULT_BASE_TARGET_HEIGHT_BLOCKS,
                DEFAULT_HEIGHT_GAIN_PER_BOUNCE_BLOCKS,
                DEFAULT_MAX_TARGET_HEIGHT_BLOCKS,
                DEFAULT_MAX_VERTICAL_SPEED,
                DEFAULT_COOLDOWN_SECONDS,
                DEFAULT_STREAK_RESET_SECONDS
            );
        }

        double baseTargetHeight = finiteOrDefault(bounce.baseTargetHeightBlocks, DEFAULT_BASE_TARGET_HEIGHT_BLOCKS);
        double heightGain = finiteOrDefault(bounce.heightGainPerBounceBlocks, DEFAULT_HEIGHT_GAIN_PER_BOUNCE_BLOCKS);
        double maxTargetHeight = finiteOrDefault(bounce.maxTargetHeightBlocks, DEFAULT_MAX_TARGET_HEIGHT_BLOCKS);
        double maxSpeed = finiteOrDefault(bounce.maxVerticalSpeed, DEFAULT_MAX_VERTICAL_SPEED);
        double cooldownSeconds = finiteOrDefault(bounce.cooldownSeconds, DEFAULT_COOLDOWN_SECONDS);
        double streakResetSeconds = finiteOrDefault(bounce.streakResetSeconds, DEFAULT_STREAK_RESET_SECONDS);

        baseTargetHeight = clamp(baseTargetHeight, 0.25, 64.0);
        heightGain = clamp(heightGain, 0.0, 64.0);
        maxTargetHeight = clamp(maxTargetHeight, baseTargetHeight, 128.0);
        maxSpeed = clamp(maxSpeed, 0.1, 120.0);
        cooldownSeconds = clamp(cooldownSeconds, 0.0, 5.0);
        streakResetSeconds = clamp(streakResetSeconds, 0.0, 60.0);
        return new RuntimeConfig(bounce.enabled, baseTargetHeight, heightGain, maxTargetHeight, maxSpeed, cooldownSeconds, streakResetSeconds);
    }

    private static String contactTrace(
        @Nonnull BounceContact contact,
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

    private record RuntimeConfig(
        boolean enabled,
        double baseTargetHeightBlocks,
        double heightGainPerBounceBlocks,
        double maxTargetHeightBlocks,
        double maxVerticalSpeed,
        double cooldownSeconds,
        double streakResetSeconds
    ) {
    }

    private record LaunchVelocity(double velocityY, @Nonnull String source, double jumpForce) {
    }

    private record BounceStep(int count, double targetHeightBlocks) {
    }

    private record BounceStreak(int count, long lastBounceAtNanos) {
    }

    private record ContactState(@Nonnull String contactKey, boolean launched, long lastLaunchAtNanos, long lastSeenAtNanos) {
        static ContactState seen(@Nonnull String contactKey, @Nullable ContactState previous, long nowNanos) {
            return new ContactState(
                contactKey,
                previous != null && previous.launched,
                previous != null ? previous.lastLaunchAtNanos : 0L,
                nowNanos
            );
        }
    }

    private record BounceContact(
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
