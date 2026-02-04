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
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * When a Horde Book owner is damaged by an entity, retarget the active minion towards the attacker.
 */
public final class HordeBookRetaliationTargetingSystem extends DamageEventSystem {

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final HordeBookSummonState summonState;

    public HordeBookRetaliationTargetingSystem(
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
            if (damage.isCancelled()) {
                return;
            }
            float amount = damage.getAmount();
            if (!(amount > 0f) || !Float.isFinite(amount)) {
                return;
            }

            Ref<EntityStore> ownerRef = chunk.getReferenceTo(index);
            if (ownerRef == null || !ownerRef.isValid()) {
                return;
            }

            PlayerRef ownerPlayerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            UUID ownerUuid = ownerPlayerRef != null ? ownerPlayerRef.getUuid() : null;
            if (ownerUuid == null) {
                return;
            }

            long nowNanos = System.nanoTime();
            HordeBookSummonState.ActiveSummon active = summonState.getByOwnerIfActive(ownerUuid, nowNanos);
            if (active == null) {
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
            if (attackerRef.equals(ownerRef)) {
                return;
            }

            EntityStore external = store.getExternalData();
            if (external == null) {
                return;
            }

            java.util.List<Ref<EntityStore>> minionRefs = new java.util.ArrayList<>();
            if (active.minionUuids != null) {
                for (UUID minionUuid : active.minionUuids) {
                    if (minionUuid == null) {
                        continue;
                    }
                    Ref<EntityStore> minionRef = external.getRefFromUUID(minionUuid);
                    if (minionRef == null || !minionRef.isValid()) {
                        continue;
                    }
                    if (minionRef.equals(attackerRef) || minionRef.equals(ownerRef)) {
                        continue;
                    }
                    minionRefs.add(minionRef);
                }
            }

            if (minionRefs.isEmpty()) {
                summonState.clearOwner(ownerUuid);
                return;
            }

            boolean anyMinionTargetChanged = false;
            for (Ref<EntityStore> minionRef : minionRefs) {
                NPCEntity minionNpc = store.getComponent(minionRef, NPCEntity.getComponentType());
                if (minionNpc == null) {
                    continue;
                }

                Role role = minionNpc.getRole();
                MarkedEntitySupport marked = role != null ? role.getMarkedEntitySupport() : null;
                if (marked == null) {
                    continue;
                }

                Ref<EntityStore> previousMinionTarget = marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
                if (previousMinionTarget != null && previousMinionTarget.isValid() && previousMinionTarget.equals(attackerRef)) {
                    continue;
                }

                try {
                    if (role != null && role.getWorldSupport() != null) {
                        role.getWorldSupport().overrideAttitude(attackerRef, com.hypixel.hytale.server.core.asset.type.attitude.Attitude.HOSTILE, 3.0);
                    }
                } catch (Throwable ignored) {
                    // Best effort.
                }

                marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, attackerRef);
                if (role != null && role.getWorldSupport() != null) {
                    role.getWorldSupport().requestNewPath();
                }
                anyMinionTargetChanged = true;
            }

            boolean attackerTargetedToMinion = false;
            String attackerTargetReason = "skipped.notNpc";
            NPCEntity attackerNpc = store.getComponent(attackerRef, NPCEntity.getComponentType());
            if (attackerNpc != null) {
                Role attackerRole = attackerNpc.getRole();
                MarkedEntitySupport attackerMarked = attackerRole != null ? attackerRole.getMarkedEntitySupport() : null;
                if (attackerMarked != null) {
                    Ref<EntityStore> preferredMinionRef = minionRefs.get(0);
                    Ref<EntityStore> previousAttackerTarget = attackerMarked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
                    if (!(previousAttackerTarget != null && previousAttackerTarget.isValid() && previousAttackerTarget.equals(preferredMinionRef))) {
                        attackerMarked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, preferredMinionRef);
                        attackerMarked.flockSetTarget(MarkedEntitySupport.DEFAULT_TARGET_SLOT, preferredMinionRef, store);
                        if (attackerRole != null && attackerRole.getWorldSupport() != null) {
                            attackerRole.getWorldSupport().requestNewPath();
                        }
                        attackerTargetedToMinion = true;
                        attackerTargetReason = "ok";
                    } else {
                        attackerTargetReason = "alreadyTargetingMinion";
                    }
                } else {
                    attackerTargetReason = "skipped.noMarkedEntitySupport";
                }
            }

            String causeId = null;
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex >= 0) {
                DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
                if (cause != null) {
                    causeId = cause.getId();
                }
            }

            UUIDComponent attackerUuidComponent = store.getComponent(attackerRef, UUIDComponent.getComponentType());
            UUID attackerUuid = attackerUuidComponent != null ? attackerUuidComponent.getUuid() : null;

            debug.traceFileOnly(
                ownerPlayerRef,
                "HordeBookRetaliation event=Damage"
                    + " cancelled=false"
                    + " causeId=" + causeId
                    + " damage.amount=" + amount
                    + " ownerUuid=" + ownerUuid
                    + " minions.count=" + (active.minionUuids != null ? active.minionUuids.size() : 0)
                    + (attackerUuid != null ? " attackerUuid=" + attackerUuid : "")
                    + " target.slot=" + MarkedEntitySupport.DEFAULT_TARGET_SLOT
                    + " minionTarget.changed=" + anyMinionTargetChanged
                    + " attackerRetargetedToMinion=" + attackerTargetedToMinion
                    + " attackerRetarget.reason=" + attackerTargetReason
                    + " cast.chainId=" + active.castChainId
                    + " cast.interactionType=" + active.castInteractionType
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "HordeBookRetaliationTargetingSystem: failed to retarget minion.", t);
        }
    }
}
