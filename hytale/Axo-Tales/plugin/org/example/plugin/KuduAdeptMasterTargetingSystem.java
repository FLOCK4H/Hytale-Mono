package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Records the bonded owner's current commanded target.
 *
 * <p>Command sources:
 * <ul>
 *   <li>the owner damages a non-player entity, or</li>
 *   <li>the owner / one of the owner's bonded adepts is damaged by a non-player attacker.</li>
 * </ul>
 *
 * <p>Bonded adepts consume this target from {@link KuduAdeptBondSystem}; they do not independently scan nearby
 * entities for aggression.</p>
 */
public final class KuduAdeptMasterTargetingSystem extends DamageEventSystem {

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final KuduAdeptBondState bondState;

    public KuduAdeptMasterTargetingSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull KuduAdeptBondState bondState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.bondState = bondState;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.or(UUIDComponent.getComponentType(), PlayerRef.getComponentType());
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
            if (config == null || config.kuduAdept == null || !config.kuduAdept.enabled) {
                return;
            }

            String roleName = config.kuduAdept.roleName != null && !config.kuduAdept.roleName.isBlank()
                ? config.kuduAdept.roleName
                : KuduAdeptSpawnerSystem.DEFAULT_ROLE_NAME;

            Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
            if (targetRef == null || !targetRef.isValid()) {
                return;
            }

            Damage.Source source = damage.getSource();
            Ref<EntityStore> attackerRef;
            String sourceType;
            if (source instanceof Damage.EntitySource entitySource) {
                attackerRef = entitySource.getRef();
                sourceType = "EntitySource";
            } else if (source instanceof Damage.ProjectileSource projectileSource) {
                attackerRef = projectileSource.getRef();
                sourceType = "ProjectileSource";
            } else {
                return;
            }
            if (attackerRef == null || !attackerRef.isValid() || attackerRef.equals(targetRef)) {
                return;
            }

            UUID targetUuid = getTargetUuid(index, chunk, store, targetRef);
            UUID attackerUuid = getUuid(store, attackerRef);
            if (attackerUuid == null) {
                return;
            }

            PlayerRef attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
            boolean targetIsPlayer = store.getComponent(targetRef, PlayerRef.getComponentType()) != null;
            String attackerRoleName = getNpcRoleName(store, attackerRef);
            String targetRoleName = getNpcRoleName(store, targetRef);

            UUID ownerUuid = null;
            UUID commandedTargetUuid = null;
            PlayerRef logPlayerRef = null;
            String decision = null;

            if (attackerPlayerRef != null) {
                UUID attackingOwnerUuid = attackerPlayerRef.getUuid();
                if (attackingOwnerUuid != null
                    && shouldAcceptCommandTarget(store, targetRef, targetUuid, targetRoleName, roleName)
                    && !bondState.snapshotForOwner(attackingOwnerUuid).isEmpty()) {
                    ownerUuid = attackingOwnerUuid;
                    commandedTargetUuid = targetUuid;
                    logPlayerRef = attackerPlayerRef;
                    decision = "recorded.ownerDamage";
                }
            }

            if (ownerUuid == null) {
                UUID damagedOwnerUuid = resolveDamagedOwnerUuid(store, targetRef, targetUuid);
                if (damagedOwnerUuid != null
                    && !bondState.snapshotForOwner(damagedOwnerUuid).isEmpty()
                    && shouldAcceptAttackerAsCommandTarget(store, attackerRef, attackerUuid, attackerRoleName, roleName)) {
                    ownerUuid = damagedOwnerUuid;
                    commandedTargetUuid = attackerUuid;
                    logPlayerRef = resolveOwnerPlayerRef(store, damagedOwnerUuid);
                    decision = targetIsPlayer
                        ? "recorded.ownerRetaliation"
                        : "recorded.adeptRetaliation";
                }
            }

            if (ownerUuid == null || commandedTargetUuid == null || decision == null) {
                return;
            }

            long nowNanos = System.nanoTime();
            bondState.recordOwnerTarget(ownerUuid, commandedTargetUuid, nowNanos);

            String causeId = getCauseId(damage);
            List<KuduAdeptBondState.BondedAdept> ownerAdepts = bondState.snapshotForOwner(ownerUuid);
            debug.traceFileOnly(
                logPlayerRef,
                "KuduAdeptMasterTarget event=Damage"
                    + " decision=" + decision
                    + " cancelled=false"
                    + " sourceType=" + sourceType
                    + (causeId != null ? " causeId=" + causeId : "")
                    + " damage.amount=" + amount
                    + " ownerUuid=" + ownerUuid
                    + (targetUuid != null ? " damagedEntityUuid=" + targetUuid : "")
                    + " commandedTargetUuid=" + commandedTargetUuid
                    + (attackerUuid != null ? " attackerUuid=" + attackerUuid : "")
                    + (attackerRoleName != null ? " attacker.roleName=" + attackerRoleName : "")
                    + (targetRoleName != null ? " damaged.roleName=" + targetRoleName : "")
                    + " bondedAdepts.count=" + ownerAdepts.size()
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduAdeptMasterTargetingSystem: failed to record owner target.", t);
        }
    }

    private @Nullable UUID resolveDamagedOwnerUuid(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef,
        @Nullable UUID targetUuid
    ) {
        PlayerRef targetPlayerRef = store.getComponent(targetRef, PlayerRef.getComponentType());
        if (targetPlayerRef != null && targetPlayerRef.getUuid() != null) {
            return targetPlayerRef.getUuid();
        }
        if (targetUuid == null) {
            return null;
        }
        KuduAdeptBondState.BondedAdept bonded = bondState.getByAdept(targetUuid);
        return bonded != null ? bonded.ownerUuid() : null;
    }

    private boolean shouldAcceptCommandTarget(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef,
        @Nullable UUID targetUuid,
        @Nullable String targetRoleName,
        @Nonnull String adeptRoleName
    ) {
        if (store.getComponent(targetRef, PlayerRef.getComponentType()) != null) {
            return false;
        }
        if (store.getComponent(targetRef, ItemComponent.getComponentType()) != null) {
            return false;
        }
        if (targetUuid == null) {
            return false;
        }
        if (bondState.getByAdept(targetUuid) != null) {
            return false;
        }
        return !adeptRoleName.equals(targetRoleName);
    }

    private boolean shouldAcceptAttackerAsCommandTarget(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> attackerRef,
        @Nonnull UUID attackerUuid,
        @Nullable String attackerRoleName,
        @Nonnull String adeptRoleName
    ) {
        if (store.getComponent(attackerRef, PlayerRef.getComponentType()) != null) {
            return false;
        }
        if (store.getComponent(attackerRef, ItemComponent.getComponentType()) != null) {
            return false;
        }
        if (bondState.getByAdept(attackerUuid) != null) {
            return false;
        }
        return !adeptRoleName.equals(attackerRoleName);
    }

    private static @Nullable PlayerRef resolveOwnerPlayerRef(@Nonnull Store<EntityStore> store, @Nonnull UUID ownerUuid) {
        try {
            EntityStore external = store.getExternalData();
            Ref<EntityStore> ownerRef = external != null ? external.getRefFromUUID(ownerUuid) : null;
            return ownerRef != null && ownerRef.isValid() ? store.getComponent(ownerRef, PlayerRef.getComponentType()) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable UUID getTargetUuid(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef
    ) {
        try {
            UUIDComponent uuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
            if (uuidComponent != null && uuidComponent.getUuid() != null) {
                return uuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
        try {
            UUIDComponent uuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());
            if (uuidComponent != null) {
                return uuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
        return null;
    }

    private static @Nullable String getNpcRoleName(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        try {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            return npc != null ? npc.getRoleName() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable UUID getUuid(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        try {
            UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
            return uuidComponent != null ? uuidComponent.getUuid() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable String getCauseId(@Nonnull Damage damage) {
        try {
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex < 0) {
                return null;
            }
            DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
            return cause != null ? cause.getId() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
