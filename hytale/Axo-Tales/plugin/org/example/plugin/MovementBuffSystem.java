package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
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
    public static final String SAR_DIADEM_ITEM_ID = "Sar_Diadem";

    private static final float SARS_SPEED_MULTIPLIER = 1.15f;
    private static final float SAR_DIADEM_SWIM_SPEED_MULTIPLIER = 1.25f;
    private static final float SAR_DIADEM_SWIM_JUMP_MULTIPLIER = 1.25f;
    private static final float SWIFT_SPEED_MULTIPLIER = 1.2f;

    private static final float BASE_JUMP_HEIGHT_BLOCKS = 2f;
    private static final float JUMP_BONUS_BLOCKS = 2f;
    private static final float EPSILON = 0.0001f;

    private static final long SWEEP_INTERVAL_NANOS = 200_000_000L;
    private static final long AQUATIC_DEBUG_INTERVAL_NANOS = 10_000_000_000L;

    private record MovementDefaults(float baseSpeed, float jumpForce, float swimJumpForce) {}

    private record BuffFlags(
        boolean wearingSarsBoots,
        boolean wearingSarDiadem,
        boolean swiftActive,
        boolean rabbitActive,
        boolean aquaticBoostActive
    ) {}

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Map<UUID, MovementDefaults> defaultsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, BuffFlags> lastFlagsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextAquaticDebugAtNanosByPlayer = new ConcurrentHashMap<>();

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
                    EffectControllerComponent.getComponentType(),
                    MovementStatesComponent.getComponentType(),
                    EntityStatMap.getComponentType()
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

                        MovementStatesComponent movementStatesComponent = chunk.getComponent(i, MovementStatesComponent.getComponentType());
                        var movementStates = movementStatesComponent != null ? movementStatesComponent.getMovementStates() : null;
                        if (movementStates == null) {
                            continue;
                        }

                        EntityStatMap stats = chunk.getComponent(i, EntityStatMap.getComponentType());
                        if (stats == null) {
                            continue;
                        }

                        boolean wearingSarsBoots = SarsBootsPassiveEffect.isWearingSarsBoots(store, ref);
                        boolean wearingSarDiadem = SAR_DIADEM_ITEM_ID.equals(
                            InventoryComponentAccess.armorItemId(store, ref, ItemArmorSlot.Head)
                        );
                        boolean swiftActive = swiftIndex >= 0 && effects.getActiveEffects().containsKey(swiftIndex);
                        boolean rabbitActive = rabbitIndex >= 0 && effects.getActiveEffects().containsKey(rabbitIndex);
                        boolean aquaticBoostActive = wearingSarDiadem && (movementStates.swimming || movementStates.inFluid);

                        BuffFlags flags = new BuffFlags(wearingSarsBoots, wearingSarDiadem, swiftActive, rabbitActive, aquaticBoostActive);
                        BuffFlags previousFlags = lastFlagsByPlayer.put(playerUuid, flags);
                        if (previousFlags == null || !previousFlags.equals(flags)) {
                            debug.traceFileOnly(
                                playerRef,
                                "MovementBuff event=buffFlagsChanged"
                                    + " boots.active=" + wearingSarsBoots
                                    + " diadem.active=" + wearingSarDiadem
                                    + " swift.active=" + swiftActive
                                    + " rabbit.active=" + rabbitActive
                                    + " aquaticBoost.active=" + aquaticBoostActive
                                    + " states.inFluid=" + movementStates.inFluid
                                    + " states.swimming=" + movementStates.swimming
                                    + " swift.effectId=" + SWIFT_EFFECT_ID
                                    + " swift.effectIndex=" + swiftIndex
                                    + " rabbit.effectId=" + RABBIT_EFFECT_ID
                                    + " rabbit.effectIndex=" + rabbitIndex
                            );
                        }

                        MovementDefaults defaults = defaultsByPlayer.computeIfAbsent(
                            playerUuid,
                            uuid -> new MovementDefaults(settings.baseSpeed, settings.jumpForce, settings.swimJumpForce)
                        );

                        float speedMultiplier = (wearingSarsBoots ? SARS_SPEED_MULTIPLIER : 1f)
                            * (swiftActive ? SWIFT_SPEED_MULTIPLIER : 1f)
                            * (aquaticBoostActive ? SAR_DIADEM_SWIM_SPEED_MULTIPLIER : 1f);
                        float targetSpeed = defaults.baseSpeed * speedMultiplier;

                        float bonusBlocks = (wearingSarsBoots ? JUMP_BONUS_BLOCKS : 0f)
                            + (rabbitActive ? JUMP_BONUS_BLOCKS : 0f);
                        float jumpMultiplier = bonusBlocks > 0f
                            ? (float) Math.sqrt((BASE_JUMP_HEIGHT_BLOCKS + bonusBlocks) / BASE_JUMP_HEIGHT_BLOCKS)
                            : 1f;
                        float targetJumpForce = defaults.jumpForce * jumpMultiplier;
                        float swimJumpMultiplier = wearingSarDiadem ? SAR_DIADEM_SWIM_JUMP_MULTIPLIER : 1f;
                        float targetSwimJumpForce = defaults.swimJumpForce * swimJumpMultiplier;

                        int oxygenIndex = DefaultEntityStatTypes.getOxygen();
                        float oxygenCurrentBefore = Float.NaN;
                        float oxygenMaxBefore = Float.NaN;
                        boolean oxygenRefilled = false;
                        if (wearingSarDiadem && oxygenIndex != Integer.MIN_VALUE && oxygenIndex >= 0) {
                            EntityStatValue oxygen = stats.get(oxygenIndex);
                            if (oxygen != null) {
                                oxygenCurrentBefore = oxygen.get();
                                oxygenMaxBefore = oxygen.getMax();
                                if (oxygenCurrentBefore + EPSILON < oxygenMaxBefore) {
                                    stats.maximizeStatValue(oxygenIndex);
                                    stats.update();
                                    oxygenRefilled = true;
                                }
                            }
                        }

                        boolean updated = false;
                        float baseSpeedBefore = settings.baseSpeed;
                        float jumpForceBefore = settings.jumpForce;
                        float swimJumpForceBefore = settings.swimJumpForce;

                        if (Math.abs(settings.baseSpeed - targetSpeed) > EPSILON) {
                            settings.baseSpeed = targetSpeed;
                            updated = true;
                        }

                        if (Math.abs(settings.jumpForce - targetJumpForce) > EPSILON) {
                            settings.jumpForce = targetJumpForce;
                            updated = true;
                        }

                        if (Math.abs(settings.swimJumpForce - targetSwimJumpForce) > EPSILON) {
                            settings.swimJumpForce = targetSwimJumpForce;
                            updated = true;
                        }

                        if (oxygenRefilled) {
                            long nextAquaticDebugAt = nextAquaticDebugAtNanosByPlayer.getOrDefault(playerUuid, 0L);
                            if (nextAquaticDebugAt <= nowNanos) {
                                nextAquaticDebugAtNanosByPlayer.put(playerUuid, nowNanos + AQUATIC_DEBUG_INTERVAL_NANOS);
                                debug.traceFileOnly(
                                    playerRef,
                                    "MovementBuff event=diademOxygenRefresh"
                                        + " diadem.active=" + wearingSarDiadem
                                        + " states.inFluid=" + movementStates.inFluid
                                        + " states.swimming=" + movementStates.swimming
                                        + " oxygen.index=" + oxygenIndex
                                        + " oxygen.currentBefore=" + oxygenCurrentBefore
                                        + " oxygen.maxBefore=" + oxygenMaxBefore
                                        + " oxygen.refilled=true"
                                );
                            }
                        }

                        if (!updated) {
                            continue;
                        }

                        movementManager.update(playerRef.getPacketHandler());
                        debug.traceFileOnly(
                            playerRef,
                            "MovementBuff event=apply"
                                + " boots.active=" + wearingSarsBoots
                                + " diadem.active=" + wearingSarDiadem
                                + " swift.active=" + swiftActive
                                + " rabbit.active=" + rabbitActive
                                + " aquaticBoost.active=" + aquaticBoostActive
                                + " states.inFluid=" + movementStates.inFluid
                                + " states.swimming=" + movementStates.swimming
                                + " defaults.baseSpeed=" + defaults.baseSpeed
                                + " defaults.jumpForce=" + defaults.jumpForce
                                + " defaults.swimJumpForce=" + defaults.swimJumpForce
                                + " speed.multiplier=" + speedMultiplier
                                + " jump.bonusBlocks=" + bonusBlocks
                                + " jump.multiplier=" + jumpMultiplier
                                + " swimJump.multiplier=" + swimJumpMultiplier
                                + " movement.baseSpeed.before=" + baseSpeedBefore
                                + " movement.baseSpeed.after=" + settings.baseSpeed
                                + " movement.jumpForce.before=" + jumpForceBefore
                                + " movement.jumpForce.after=" + settings.jumpForce
                                + " movement.swimJumpForce.before=" + swimJumpForceBefore
                                + " movement.swimJumpForce.after=" + settings.swimJumpForce
                                + " oxygen.refilled=" + oxygenRefilled
                                + (Float.isFinite(oxygenCurrentBefore) ? " oxygen.currentBefore=" + oxygenCurrentBefore : "")
                                + (Float.isFinite(oxygenMaxBefore) ? " oxygen.maxBefore=" + oxygenMaxBefore : "")
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
        nextAquaticDebugAtNanosByPlayer.remove(uuid);
    }

    public void shutdown() {
        defaultsByPlayer.clear();
        lastFlagsByPlayer.clear();
        nextAquaticDebugAtNanosByPlayer.clear();
    }
}
