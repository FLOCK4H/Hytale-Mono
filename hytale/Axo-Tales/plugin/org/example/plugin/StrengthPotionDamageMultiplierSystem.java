package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Doubles outgoing damage while the Strength Potion effect is active.
 */
public final class StrengthPotionDamageMultiplierSystem extends DamageEventSystem {

    public static final String STRENGTH_EFFECT_ID = "AxoTales_Strength";
    private static final float MULTIPLIER = 2.0f;
    private static final long DEBUG_COOLDOWN_NANOS = 10_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Map<UUID, Long> nextDebugAtNanosByAttacker = new ConcurrentHashMap<>();
    private final AtomicBoolean loggedMissingEffect = new AtomicBoolean(false);
    private volatile int strengthEffectIndex = -1;

    public StrengthPotionDamageMultiplierSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug
    ) {
        this.errors = errors;
        this.debug = debug;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        try {
            float amountBefore = damage.getAmount();
            if (!(amountBefore > 0f)) {
                return;
            }

            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.EntitySource entitySource)) {
                return;
            }

            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef == null || !attackerRef.isValid()) {
                return;
            }

            PlayerRef attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
            UUID attackerUuid = attackerPlayerRef != null ? attackerPlayerRef.getUuid() : null;
            if (attackerUuid == null) {
                UUIDComponent attackerUuidComponent = store.getComponent(attackerRef, UUIDComponent.getComponentType());
                attackerUuid = attackerUuidComponent != null ? attackerUuidComponent.getUuid() : null;
            }
            if (attackerUuid == null) {
                return;
            }

            long nowNanos = System.nanoTime();

            EffectControllerComponent effectController = store.getComponent(attackerRef, EffectControllerComponent.getComponentType());
            if (effectController == null) {
                return;
            }

            int effectIndex = resolveStrengthEffectIndex();
            if (effectIndex < 0) {
                maybeLogMissingEffectOnce(attackerPlayerRef, attackerUuid, effectIndex);
                return;
            }

            boolean active = effectController.getActiveEffects().containsKey(effectIndex);
            if (!active) {
                return;
            }

            float amountAfter = amountBefore * MULTIPLIER;
            damage.setAmount(amountAfter);
            maybeDebug(attackerPlayerRef, attackerUuid, nowNanos, effectIndex, amountBefore, amountAfter);
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "StrengthPotionDamageMultiplierSystem: failed to handle damage.", t);
        }
    }

    private int resolveStrengthEffectIndex() {
        int cached = strengthEffectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(STRENGTH_EFFECT_ID, -1);
        if (resolved >= 0) {
            strengthEffectIndex = resolved;
        }
        return resolved;
    }

    private void maybeDebug(
        @Nullable PlayerRef attackerPlayerRef,
        @Nonnull UUID attackerUuid,
        long nowNanos,
        int effectIndex,
        float amountBefore,
        float amountAfter
    ) {
        long next = nextDebugAtNanosByAttacker.getOrDefault(attackerUuid, 0L);
        if (next > nowNanos) {
            return;
        }

        nextDebugAtNanosByAttacker.put(attackerUuid, nowNanos + DEBUG_COOLDOWN_NANOS);
        debug.traceFileOnly(
            attackerPlayerRef,
            "StrengthPotionDamageBoost event=Damage"
                + " attacker.uuid=" + attackerUuid
                + " strengthEffectId=" + STRENGTH_EFFECT_ID
                + " strengthEffectIndex=" + effectIndex
                + " decision=allow"
                + " multiplier=" + MULTIPLIER
                + " damage.amount.before=" + amountBefore
                + " damage.amount.after=" + amountAfter
        );
    }

    private void maybeLogMissingEffectOnce(
        @Nullable PlayerRef attackerPlayerRef,
        @Nonnull UUID attackerUuid,
        int effectIndex
    ) {
        if (!loggedMissingEffect.compareAndSet(false, true)) {
            return;
        }
        debug.traceFileOnly(
            attackerPlayerRef,
            "StrengthPotionDamageBoost event=disabled"
                + " reason=missingEffect"
                + " attacker.uuid=" + attackerUuid
                + " strengthEffectId=" + STRENGTH_EFFECT_ID
                + " strengthEffectIndex=" + effectIndex
        );
    }
}
