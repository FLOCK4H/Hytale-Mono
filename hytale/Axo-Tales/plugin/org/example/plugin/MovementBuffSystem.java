package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies movement buffs that stack across armor and temporary effects.
 *
 * <p>Currently supports:</p>
 * <ul>
 *   <li>Sa'r Boots: +15% speed and +2 blocks jump height</li>
 *   <li>Swift Potion: +20% speed</li>
 *   <li>Rabbit Potion: +2 blocks jump height</li>
 * </ul>
 */
public final class MovementBuffSystem extends TickingSystem<EntityStore> {

    public static final String SWIFT_EFFECT_ID = "AxoTales_Swift";
    public static final String RABBIT_EFFECT_ID = "AxoTales_Rabbit";

    private static final float SARS_SPEED_MULTIPLIER = 1.15f;
    private static final float SWIFT_SPEED_MULTIPLIER = 1.2f;

    private static final float BASE_JUMP_HEIGHT_BLOCKS = 2f;
    private static final float JUMP_BONUS_BLOCKS = 2f;
    private static final float EPSILON = 0.0001f;

    private static final long SWEEP_INTERVAL_NANOS = 200_000_000L;

    private record MovementDefaults(float baseSpeed, float jumpForce) {}

    private record BuffFlags(boolean wearingSarsBoots, boolean swiftActive, boolean rabbitActive) {}

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Map<UUID, MovementDefaults> defaultsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, BuffFlags> lastFlagsByPlayer = new ConcurrentHashMap<>();

    private volatile int swiftEffectIndex = -1;
    private volatile int rabbitEffectIndex = -1;
    private long nextSweepAtNanos = 0L;

    public MovementBuffSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug
    ) {
        this.errors = errors;
        this.debug = debug;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            long nowNanos = System.nanoTime();
            if (nextSweepAtNanos > nowNanos) {
                return;
            }
            nextSweepAtNanos = nowNanos + SWEEP_INTERVAL_NANOS;

            int swiftIndex = resolveSwiftEffectIndex();
            int rabbitIndex = resolveRabbitEffectIndex();

            store.forEachChunk(
                Query.and(
                    PlayerRef.getComponentType(),
                    Player.getComponentType(),
                    MovementManager.getComponentType(),
                    EffectControllerComponent.getComponentType()
                ),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> ref = chunk.getReferenceTo(i);
                        if (ref == null || !ref.isValid()) {
                            continue;
                        }

                        PlayerRef playerRef = chunk.getComponent(i, PlayerRef.getComponentType());
                        UUID playerUuid = playerRef != null ? playerRef.getUuid() : null;
                        if (playerUuid == null) {
                            continue;
                        }

                        Player player = chunk.getComponent(i, Player.getComponentType());
                        if (player == null) {
                            continue;
                        }

                        MovementManager movementManager = chunk.getComponent(i, MovementManager.getComponentType());
                        if (movementManager == null) {
                            continue;
                        }

                        MovementSettings settings = movementManager.getSettings();
                        if (settings == null) {
                            continue;
                        }

                        EffectControllerComponent effects = chunk.getComponent(i, EffectControllerComponent.getComponentType());
                        if (effects == null) {
                            continue;
                        }

                        boolean wearingSarsBoots = SarsBootsPassiveEffect.isWearingSarsBoots(player);
                        boolean swiftActive = swiftIndex >= 0 && effects.getActiveEffects().containsKey(swiftIndex);
                        boolean rabbitActive = rabbitIndex >= 0 && effects.getActiveEffects().containsKey(rabbitIndex);

                        BuffFlags flags = new BuffFlags(wearingSarsBoots, swiftActive, rabbitActive);
                        BuffFlags previousFlags = lastFlagsByPlayer.put(playerUuid, flags);
                        if (previousFlags == null || !previousFlags.equals(flags)) {
                            debug.traceFileOnly(
                                playerRef,
                                "MovementBuff event=buffFlagsChanged"
                                    + " boots.active=" + wearingSarsBoots
                                    + " swift.active=" + swiftActive
                                    + " rabbit.active=" + rabbitActive
                                    + " swift.effectId=" + SWIFT_EFFECT_ID
                                    + " swift.effectIndex=" + swiftIndex
                                    + " rabbit.effectId=" + RABBIT_EFFECT_ID
                                    + " rabbit.effectIndex=" + rabbitIndex
                            );
                        }

                        MovementDefaults defaults = defaultsByPlayer.computeIfAbsent(
                            playerUuid,
                            uuid -> new MovementDefaults(settings.baseSpeed, settings.jumpForce)
                        );

                        float speedMultiplier = (wearingSarsBoots ? SARS_SPEED_MULTIPLIER : 1f)
                            * (swiftActive ? SWIFT_SPEED_MULTIPLIER : 1f);
                        float targetSpeed = defaults.baseSpeed * speedMultiplier;

                        float bonusBlocks = (wearingSarsBoots ? JUMP_BONUS_BLOCKS : 0f)
                            + (rabbitActive ? JUMP_BONUS_BLOCKS : 0f);
                        float jumpMultiplier = bonusBlocks > 0f
                            ? (float) Math.sqrt((BASE_JUMP_HEIGHT_BLOCKS + bonusBlocks) / BASE_JUMP_HEIGHT_BLOCKS)
                            : 1f;
                        float targetJumpForce = defaults.jumpForce * jumpMultiplier;

                        boolean updated = false;
                        float baseSpeedBefore = settings.baseSpeed;
                        float jumpForceBefore = settings.jumpForce;

                        if (Math.abs(settings.baseSpeed - targetSpeed) > EPSILON) {
                            settings.baseSpeed = targetSpeed;
                            updated = true;
                        }

                        if (Math.abs(settings.jumpForce - targetJumpForce) > EPSILON) {
                            settings.jumpForce = targetJumpForce;
                            updated = true;
                        }

                        if (!updated) {
                            continue;
                        }

                        movementManager.update(playerRef.getPacketHandler());
                        debug.traceFileOnly(
                            playerRef,
                            "MovementBuff event=apply"
                                + " boots.active=" + wearingSarsBoots
                                + " swift.active=" + swiftActive
                                + " rabbit.active=" + rabbitActive
                                + " defaults.baseSpeed=" + defaults.baseSpeed
                                + " defaults.jumpForce=" + defaults.jumpForce
                                + " speed.multiplier=" + speedMultiplier
                                + " jump.bonusBlocks=" + bonusBlocks
                                + " jump.multiplier=" + jumpMultiplier
                                + " movement.baseSpeed.before=" + baseSpeedBefore
                                + " movement.baseSpeed.after=" + settings.baseSpeed
                                + " movement.jumpForce.before=" + jumpForceBefore
                                + " movement.jumpForce.after=" + settings.jumpForce
                        );
                    }
                }
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "MovementBuffSystem: tick failed.", t);
        }
    }

    private int resolveSwiftEffectIndex() {
        int cached = swiftEffectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(SWIFT_EFFECT_ID, -1);
        if (resolved >= 0) {
            swiftEffectIndex = resolved;
        }
        return resolved;
    }

    private int resolveRabbitEffectIndex() {
        int cached = rabbitEffectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(RABBIT_EFFECT_ID, -1);
        if (resolved >= 0) {
            rabbitEffectIndex = resolved;
        }
        return resolved;
    }

    public void onPlayerDisconnect(@Nullable PlayerRef playerRef) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        defaultsByPlayer.remove(uuid);
        lastFlagsByPlayer.remove(uuid);
    }

    public void shutdown() {
        defaultsByPlayer.clear();
        lastFlagsByPlayer.clear();
    }
}
