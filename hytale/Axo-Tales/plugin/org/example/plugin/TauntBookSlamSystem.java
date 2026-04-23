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
 * Cancels fall damage while the Taunt Book immunity window is active.
 *
 * <p>Landing slam damage is handled by {@link TauntBookLandingSystem} (onGround transition).</p>
 */
public final class TauntBookSlamSystem extends DamageEventSystem {

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final TauntBookEffectState tauntState;

    public TauntBookSlamSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull TauntBookEffectState tauntState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.tauntState = tauntState;
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
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex < 0) {
                return;
            }

            DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
            if (cause == null || cause.getId() == null || !cause.getId().equalsIgnoreCase("FALL")) {
                return;
            }

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
            TauntBookEffectState.ActiveTaunt active = tauntState.getIfActive(uuid, nowNanos);
            if (active == null) {
                return;
            }

            float amountBefore = damage.getAmount();
            damage.setAmount(0f);
            damage.setCancelled(true);

            debug.traceFileOnly(
                playerRef,
                "TauntBookFallImmunity event=Damage(FALL)"
                    + " cancelled=true"
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=0.0"
                    + " taunt.cast.chainId=" + active.castChainId
                    + " taunt.cast.interactionType=" + active.castInteractionType
                    + " taunt.stackCount=" + active.stackCount
                    + " slam.damageAmount=" + active.getEffectiveSlamDamage()
                    + " taunt.active.expiresAtNanos=" + active.expiresAtNanos
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "TauntBookSlamSystem: failed to handle fall damage.", t);
        }
    }
}
