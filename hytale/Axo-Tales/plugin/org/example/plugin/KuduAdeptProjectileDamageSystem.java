package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
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
import java.util.UUID;

/**
 * Makes Kudu Adept firebolts deterministic and friendly-fire safe.
 *
 * <p>Adept projectiles only damage the owner's recorded master target. Everything else, including players and other
 * adepts, is cancelled.</p>
 */
public final class KuduAdeptProjectileDamageSystem extends DamageEventSystem {

    private static final float ADEPT_PROJECTILE_DAMAGE = 18f;
    private static final String ADEPT_PROJECTILE_ID = "KuduAdept_Firebolt";

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final KuduAdeptBondState bondState;

    public KuduAdeptProjectileDamageSystem(
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
            if (config == null || config.kuduAdept == null || !config.kuduAdept.enabled) {
                return;
            }

            Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
            UUID targetUuid = getTargetUuid(index, chunk, store, targetRef);
            String roleName = config.kuduAdept.roleName != null && !config.kuduAdept.roleName.isBlank()
                ? config.kuduAdept.roleName
                : KuduAdeptSpawnerSystem.DEFAULT_ROLE_NAME;
            String causeId = getCauseId(damage);

            Damage.Source source = damage.getSource();
            Ref<EntityStore> adeptRef = null;
            Ref<EntityStore> projectileRef = null;
            String sourceType;
            String projectileId = null;

            if (source instanceof Damage.ProjectileSource projectileSource) {
                sourceType = "ProjectileSource";
                projectileRef = projectileSource.getProjectile();
                if (projectileRef == null || !projectileRef.isValid()) {
                    return;
                }
                ProjectileComponent projectile = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
                projectileId = projectile != null ? projectile.getProjectileAssetName() : null;
                if (!ADEPT_PROJECTILE_ID.equals(projectileId)) {
                    return;
                }
                adeptRef = projectileSource.getRef();
            } else if (source instanceof Damage.EntitySource entitySource) {
                sourceType = "EntitySource";
                if (!"Projectile".equals(causeId)) {
                    return;
                }
                adeptRef = entitySource.getRef();
                projectileId = ADEPT_PROJECTILE_ID;
            } else {
                return;
            }

            if (adeptRef == null || !adeptRef.isValid()) {
                return;
            }

            UUID adeptUuid = getUuid(store, adeptRef);
            String attackerRoleName = getNpcRoleName(store, adeptRef);
            KuduAdeptBondState.BondedAdept bonded = adeptUuid != null ? bondState.getByAdept(adeptUuid) : null;
            if (!roleName.equals(attackerRoleName) && bonded == null) {
                return;
            }

            KuduAdeptBondState.OwnerTarget ownerTarget = bonded != null && bonded.ownerUuid() != null
                ? bondState.getOwnerTarget(bonded.ownerUuid())
                : null;
            String targetRoleName = targetRef != null && targetRef.isValid() ? getNpcRoleName(store, targetRef) : null;

            String decision;
            if (adeptUuid == null) {
                decision = "deny.adeptUuidMissing";
            } else if (bonded == null || bonded.ownerUuid() == null) {
                decision = "deny.unbondedAdept";
            } else if (targetRef == null || !targetRef.isValid()) {
                decision = "deny.invalidTarget";
            } else if (store.getComponent(targetRef, PlayerRef.getComponentType()) != null) {
                decision = "deny.playerTarget";
            } else if (store.getComponent(targetRef, ItemComponent.getComponentType()) != null) {
                decision = "deny.itemTarget";
            } else if (targetUuid == null) {
                decision = "deny.targetUuidMissing";
            } else if (bondState.getByAdept(targetUuid) != null) {
                decision = "deny.bondedAdeptTarget";
            } else if (roleName.equals(targetRoleName)) {
                decision = "deny.adeptRoleTarget";
            } else if (ownerTarget == null || ownerTarget.targetUuid() == null) {
                decision = "deny.noMasterTarget";
            } else if (!ownerTarget.targetUuid().equals(targetUuid)) {
                decision = "deny.notMasterTarget";
            } else if (damage.isCancelled()) {
                decision = "deny.cancelledUpstream";
            } else {
                decision = "allow";
            }

            boolean allow = "allow".equals(decision);
            boolean cancelledBefore = damage.isCancelled();
            float amountBefore = damage.getAmount();
            if (allow) {
                if (!(amountBefore > 0f) || !Float.isFinite(amountBefore)) {
                    damage.setAmount(ADEPT_PROJECTILE_DAMAGE);
                }
            } else {
                damage.setAmount(0f);
                damage.setCancelled(true);
            }

            UUID projectileUuid = getUuid(store, projectileRef);
            PlayerRef ownerPlayerRef = bonded != null && bonded.ownerUuid() != null
                ? resolveOwnerPlayerRef(store, bonded.ownerUuid())
                : null;

            debug.traceFileOnly(
                ownerPlayerRef,
                "KuduAdeptProjectileDamage event=Damage"
                    + " decision=" + decision
                    + " sourceType=" + sourceType
                    + (causeId != null ? " causeId=" + causeId : "")
                    + " cancelled.before=" + cancelledBefore
                    + " cancelled.after=" + damage.isCancelled()
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=" + damage.getAmount()
                    + (bonded != null && bonded.ownerUuid() != null ? " ownerUuid=" + bonded.ownerUuid() : "")
                    + (adeptUuid != null ? " adeptUuid=" + adeptUuid : "")
                    + (targetUuid != null ? " targetUuid=" + targetUuid : "")
                    + (ownerTarget != null && ownerTarget.targetUuid() != null ? " masterTargetUuid=" + ownerTarget.targetUuid() : "")
                    + (projectileUuid != null ? " projectileUuid=" + projectileUuid : "")
                    + (attackerRoleName != null ? " attacker.roleName=" + attackerRoleName : "")
                    + (targetRoleName != null ? " target.roleName=" + targetRoleName : "")
                    + " projectileId=" + projectileId
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduAdeptProjectileDamageSystem: failed to handle damage.", t);
        }
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
        @Nullable Ref<EntityStore> targetRef
    ) {
        try {
            UUIDComponent uuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
            if (uuidComponent != null && uuidComponent.getUuid() != null) {
                return uuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
        return getUuid(store, targetRef);
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

    private static @Nullable String getNpcRoleName(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        try {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            return npc != null ? npc.getRoleName() : null;
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
