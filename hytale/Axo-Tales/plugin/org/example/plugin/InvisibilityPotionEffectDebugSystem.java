package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs when the custom Invisibility potion effect becomes active/ends on players.
 *
 * <p>This is intentionally best-effort and exists to make issues reproducible from the persistent debug log.</p>
 */
public final class InvisibilityPotionEffectDebugSystem extends TickingSystem<EntityStore> {

    public static final String INVISIBILITY_EFFECT_ID = "AxoTales_Invisibility";
    private static final long SWEEP_INTERVAL_NANOS = 1_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Map<UUID, Boolean> lastActiveByPlayer = new ConcurrentHashMap<>();

    private volatile int effectIndex = -1;
    private long nextSweepAtNanos = 0L;

    public InvisibilityPotionEffectDebugSystem(
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
                Query.and(PlayerRef.getComponentType(), EffectControllerComponent.getComponentType()),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> ref = chunk.getReferenceTo(i);
                        if (ref == null || !ref.isValid()) {
                            continue;
                        }

                        PlayerRef playerRef = chunk.getComponent(i, PlayerRef.getComponentType());
                        if (playerRef == null || playerRef.getUuid() == null) {
                            continue;
                        }

                        UUID uuid = playerRef.getUuid();
                        EffectControllerComponent effectController = chunk.getComponent(i, EffectControllerComponent.getComponentType());
                        if (effectController == null) {
                            continue;
                        }

                        boolean active = effectController.getActiveEffects().containsKey(invisIndex);
                        Boolean last = lastActiveByPlayer.put(uuid, active);
                        if (last != null && last == active) {
                            continue;
                        }

                        ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
                        Model model = modelComponent != null ? modelComponent.getModel() : null;

                        debug.traceFileOnly(
                            playerRef,
                            "InvisibilityPotionEffect event=effectChange"
                                + " effectId=" + INVISIBILITY_EFFECT_ID
                                + " effectIndex=" + invisIndex
                                + " active=" + active
                                + " model.assetId=" + (model != null ? model.getModelAssetId() : "null")
                                + " model.model=" + (model != null ? model.getModel() : "null")
                                + " model.texture=" + (model != null ? model.getTexture() : "null")
                        );
                    }
                }
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "InvisibilityPotionEffectDebugSystem: tick failed.", t);
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

    public void onPlayerDisconnect(@Nullable PlayerRef playerRef) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        lastActiveByPlayer.remove(playerRef.getUuid());
    }
}
