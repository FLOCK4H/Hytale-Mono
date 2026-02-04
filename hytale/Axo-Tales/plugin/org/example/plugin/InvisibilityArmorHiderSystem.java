package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * While the custom Axo Tales invisibility effect is active on a player, hide all armor visuals so
 * the player is fully invisible (no lingering cloak/armor rendering).
 *
 * <p>This uses {@link PlayerSettings} hide flags and forces an equipment network invalidation so the
 * change is replicated to observers.</p>
 *
 * <p>Debug traces are written to the persistent plugin debug log.</p>
 */
public final class InvisibilityArmorHiderSystem extends TickingSystem<EntityStore> {

    private static final String INVISIBILITY_EFFECT_ID = InvisibilityPotionEffectDebugSystem.INVISIBILITY_EFFECT_ID;
    private static final long SWEEP_INTERVAL_NANOS = 500_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;

    private final Map<UUID, HideBaseline> baselineByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lastActiveByPlayer = new ConcurrentHashMap<>();

    private volatile int effectIndex = -1;
    private long nextSweepAtNanos = 0L;

    private record HideBaseline(
        boolean hideHelmet,
        boolean hideCuirass,
        boolean hideGauntlets,
        boolean hidePants
    ) {
    }

    public InvisibilityArmorHiderSystem(
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

            int invisIndex = resolveEffectIndex();
            if (invisIndex < 0) {
                return;
            }

            store.forEachChunk(
                Query.and(
                    PlayerRef.getComponentType(),
                    Player.getComponentType(),
                    PlayerSettings.getComponentType(),
                    EffectControllerComponent.getComponentType()
                ),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                        if (entityRef == null || !entityRef.isValid()) {
                            continue;
                        }

                        PlayerRef playerRef = chunk.getComponent(i, PlayerRef.getComponentType());
                        if (playerRef == null || playerRef.getUuid() == null) {
                            continue;
                        }
                        UUID uuid = playerRef.getUuid();

                        Player player = chunk.getComponent(i, Player.getComponentType());
                        if (player == null) {
                            continue;
                        }

                        PlayerSettings settings = chunk.getComponent(i, PlayerSettings.getComponentType());
                        if (settings == null) {
                            continue;
                        }

                        EffectControllerComponent effects = chunk.getComponent(i, EffectControllerComponent.getComponentType());
                        if (effects == null) {
                            continue;
                        }

                        boolean active = effects.getActiveEffects().containsKey(invisIndex);
                        Boolean lastActive = lastActiveByPlayer.put(uuid, active);

                        if (active) {
                            baselineByPlayer.computeIfAbsent(
                                uuid,
                                ignored -> new HideBaseline(
                                    settings.hideHelmet(),
                                    settings.hideCuirass(),
                                    settings.hideGauntlets(),
                                    settings.hidePants()
                                )
                            );

                            if (settings.hideHelmet() && settings.hideCuirass() && settings.hideGauntlets() && settings.hidePants()) {
                                continue;
                            }

                            PlayerSettings updated = withHideFlags(settings, true, true, true, true);
                            commandBuffer.putComponent(entityRef, PlayerSettings.getComponentType(), updated);
                            player.invalidateEquipmentNetwork();

                            debug.traceFileOnly(
                                playerRef,
                                "InvisibilityArmorHider event=applyHide"
                                    + " effectId=" + INVISIBILITY_EFFECT_ID
                                    + " effectIndex=" + invisIndex
                                    + " active=" + active
                                    + " changedFromActive=" + (lastActive != null ? lastActive : "null")
                                    + " hide.before=[helmet=" + settings.hideHelmet()
                                    + ",chest=" + settings.hideCuirass()
                                    + ",hands=" + settings.hideGauntlets()
                                    + ",legs=" + settings.hidePants() + "]"
                                    + " hide.after=[helmet=true,chest=true,hands=true,legs=true]"
                            );
                            continue;
                        }

                        HideBaseline baseline = baselineByPlayer.remove(uuid);
                        if (baseline == null) {
                            continue;
                        }

                        lastActiveByPlayer.put(uuid, false);

                        boolean needsRestore =
                            settings.hideHelmet() != baseline.hideHelmet
                                || settings.hideCuirass() != baseline.hideCuirass
                                || settings.hideGauntlets() != baseline.hideGauntlets
                                || settings.hidePants() != baseline.hidePants;
                        if (!needsRestore) {
                            continue;
                        }

                        PlayerSettings restored = withHideFlags(
                            settings,
                            baseline.hideHelmet,
                            baseline.hideCuirass,
                            baseline.hideGauntlets,
                            baseline.hidePants
                        );
                        commandBuffer.putComponent(entityRef, PlayerSettings.getComponentType(), restored);
                        player.invalidateEquipmentNetwork();

                        debug.traceFileOnly(
                            playerRef,
                            "InvisibilityArmorHider event=restoreHide"
                                + " effectId=" + INVISIBILITY_EFFECT_ID
                                + " effectIndex=" + invisIndex
                                + " active=" + active
                                + " changedFromActive=" + (lastActive != null ? lastActive : "null")
                                + " hide.before=[helmet=" + settings.hideHelmet()
                                + ",chest=" + settings.hideCuirass()
                                + ",hands=" + settings.hideGauntlets()
                                + ",legs=" + settings.hidePants() + "]"
                                + " hide.after=[helmet=" + baseline.hideHelmet
                                + ",chest=" + baseline.hideCuirass
                                + ",hands=" + baseline.hideGauntlets
                                + ",legs=" + baseline.hidePants + "]"
                        );
                    }
                }
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "InvisibilityArmorHiderSystem: tick failed.", t);
        }
    }

    private int resolveEffectIndex() {
        int cached = effectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(INVISIBILITY_EFFECT_ID, -1);
        if (resolved >= 0) {
            effectIndex = resolved;
        }
        return resolved;
    }

    private static PlayerSettings withHideFlags(
        @Nonnull PlayerSettings settings,
        boolean hideHelmet,
        boolean hideCuirass,
        boolean hideGauntlets,
        boolean hidePants
    ) {
        return new PlayerSettings(
            settings.showEntityMarkers(),
            settings.armorItemsPreferredPickupLocation(),
            settings.weaponAndToolItemsPreferredPickupLocation(),
            settings.usableItemsItemsPreferredPickupLocation(),
            settings.solidBlockItemsPreferredPickupLocation(),
            settings.miscItemsPreferredPickupLocation(),
            settings.creativeSettings(),
            hideHelmet,
            hideCuirass,
            hideGauntlets,
            hidePants
        );
    }

    public void onPlayerDisconnect(@Nullable PlayerRef playerRef) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        baselineByPlayer.remove(uuid);
        lastActiveByPlayer.remove(uuid);
    }
}

