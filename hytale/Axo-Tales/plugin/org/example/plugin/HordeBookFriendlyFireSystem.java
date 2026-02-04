package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
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
 * Prevents Horde Book summoned minions from damaging their owner.
 */
public final class HordeBookFriendlyFireSystem extends DamageEventSystem {

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final HordeBookSummonState summonState;

    public HordeBookFriendlyFireSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull HordeBookSummonState summonState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.summonState = summonState;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), PlayerRef.getComponentType());
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
            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            if (victimRef == null || !victimRef.isValid()) {
                return;
            }

            PlayerRef victimPlayerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            UUID victimUuid = victimPlayerRef != null ? victimPlayerRef.getUuid() : null;
            if (victimUuid == null) {
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

            UUIDComponent attackerUuidComponent = store.getComponent(attackerRef, UUIDComponent.getComponentType());
            UUID attackerUuid = attackerUuidComponent != null ? attackerUuidComponent.getUuid() : null;
            if (attackerUuid == null) {
                return;
            }

            long nowNanos = System.nanoTime();
            HordeBookSummonState.ActiveSummon active = summonState.getByMinionIfActive(attackerUuid, nowNanos);
            if (active == null || !active.ownerUuid.equals(victimUuid)) {
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
                victimPlayerRef,
                "HordeBookFriendlyFire event=Damage"
                    + " causeId=" + causeId
                    + " cancelled=true"
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=0.0"
                    + " ownerUuid=" + active.ownerUuid
                    + " minions.count=" + (active.minionUuids != null ? active.minionUuids.size() : 0)
                    + " cast.chainId=" + active.castChainId
                    + " cast.interactionType=" + active.castInteractionType
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "HordeBookFriendlyFireSystem: failed to handle damage.", t);
        }
    }
}
