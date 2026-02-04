package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Cancels incoming damage while the Immunity Book window is active.
 */
public final class ImmunityBookDamageImmunitySystem extends DamageEventSystem {

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final ImmunityBookEffectState immunityState;

    public ImmunityBookDamageImmunitySystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull ImmunityBookEffectState immunityState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.immunityState = immunityState;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
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
            Ref<EntityStore> playerEntityRef = chunk.getReferenceTo(index);
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return;
            }

            PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
            UUID uuid = playerRef != null ? playerRef.getUuid() : null;
            if (uuid == null) {
                return;
            }

            long nowNanos = System.nanoTime();
            ImmunityBookEffectState.ActiveImmunity active = immunityState.getIfActive(uuid, nowNanos);
            if (active == null) {
                return;
            }

            float amountBefore = damage.getAmount();
            damage.setAmount(0f);
            damage.setCancelled(true);

            String causeId = null;
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex >= 0) {
                DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
                if (cause != null) {
                    causeId = cause.getId();
                }
            }

            debug.traceFileOnly(
                playerRef,
                "ImmunityBookDamageImmunity event=Damage"
                    + " causeId=" + causeId
                    + " cancelled=true"
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=0.0"
                    + " immunity.cast.chainId=" + active.castChainId
                    + " immunity.cast.interactionType=" + active.castInteractionType
                    + " immunity.active.expiresAtNanos=" + active.expiresAtNanos
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "ImmunityBookDamageImmunitySystem: failed to handle damage.", t);
        }
    }
}

