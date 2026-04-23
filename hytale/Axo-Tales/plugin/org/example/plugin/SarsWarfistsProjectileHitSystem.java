package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
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
 * Applies Sa'r Warfists projectile on-hit effects.
 */
public final class SarsWarfistsProjectileHitSystem extends DamageEventSystem {

    public static final String WARFISTS_PROJECTILE_ID = SarsWarfistsInputInterceptor.WARFISTS_PROJECTILE_ASSET_ID;

    private static final String STUN_EFFECT_ID = "Stun";
    private static final float WARFISTS_DAMAGE = 25f;
    private static final float STUN_DURATION_SECONDS = 2f;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;

    private volatile int stunEffectIndex = -1;

    public SarsWarfistsProjectileHitSystem(
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
            Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
            if (targetRef == null || !targetRef.isValid()) {
                return;
            }

            PlayerRef targetPlayerRef = store.getComponent(targetRef, PlayerRef.getComponentType());
            UUIDComponent targetUuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
            UUID targetUuid = targetUuidComponent != null ? targetUuidComponent.getUuid() : null;
            if (targetUuid == null && targetPlayerRef != null) {
                targetUuid = targetPlayerRef.getUuid();
            }

            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.ProjectileSource projectileSource)) {
                return;
            }

            Ref<EntityStore> projectileRef = projectileSource.getProjectile();
            if (projectileRef == null || !projectileRef.isValid()) {
                return;
            }

            ProjectileComponent projectileComponent = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
            if (projectileComponent == null || !WARFISTS_PROJECTILE_ID.equals(projectileComponent.getProjectileAssetName())) {
                return;
            }

            String causeId = null;
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex >= 0) {
                DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
                if (cause != null) {
                    causeId = cause.getId();
                }
            }

            if (causeId != null && !causeId.equalsIgnoreCase("PROJECTILE")) {
                return;
            }

            Ref<EntityStore> shooterRef = projectileSource.getRef();
            PlayerRef shooterPlayerRef = shooterRef != null ? store.getComponent(shooterRef, PlayerRef.getComponentType()) : null;
            UUID shooterUuid = shooterPlayerRef != null ? shooterPlayerRef.getUuid() : null;
            if (shooterUuid == null && shooterRef != null) {
                UUIDComponent shooterUuidComponent = store.getComponent(shooterRef, UUIDComponent.getComponentType());
                if (shooterUuidComponent != null) {
                    shooterUuid = shooterUuidComponent.getUuid();
                }
            }

            UUID projectileUuid = null;
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }

            float amountBefore = damage.getAmount();
            damage.setAmount(WARFISTS_DAMAGE);

            int stunIndex = resolveStunEffectIndex();
            boolean stunApplied = false;
            String stunReason = "skip";
            if (stunIndex < 0) {
                stunReason = "effectNotFound";
            } else {
                EntityEffect stunEffect = EntityEffect.getAssetMap().getAsset(stunIndex);
                if (stunEffect == null) {
                    stunReason = "effectNull";
                } else {
                    try {
                        EffectControllerComponent effects = store.ensureAndGetComponent(targetRef, EffectControllerComponent.getComponentType());
                        if (effects == null) {
                            stunReason = "effectControllerMissing";
                        } else {
                            stunApplied = effects.addEffect(
                                targetRef,
                                stunIndex,
                                stunEffect,
                                STUN_DURATION_SECONDS,
                                OverlapBehavior.OVERWRITE,
                                store
                            );
                            stunReason = stunApplied ? "applied" : "addEffectFalse";
                        }
                    } catch (Throwable t) {
                        errors.report(shooterPlayerRef, "SarsWarfistsProjectileHitSystem: failed to apply stun.", t);
                        stunReason = "addEffectException";
                    }
                }
            }

            PlayerRef logPlayer = shooterPlayerRef != null ? shooterPlayerRef : targetPlayerRef;
            debug.traceFileOnly(
                logPlayer,
                "SarsWarfistsProjectile event=Damage"
                    + " projectileId=" + WARFISTS_PROJECTILE_ID
                    + (causeId != null ? " causeId=" + causeId : "")
                    + " cancelled=" + damage.isCancelled()
                    + (projectileUuid != null ? " projectile.uuid=" + projectileUuid : "")
                    + (shooterUuid != null ? " shooter.uuid=" + shooterUuid : "")
                    + (targetUuid != null ? " target.uuid=" + targetUuid : "")
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=" + damage.getAmount()
                    + " stun.effectId=" + STUN_EFFECT_ID
                    + " stun.effectIndex=" + stunIndex
                    + " stun.durationSeconds=" + STUN_DURATION_SECONDS
                    + " stun.applied=" + stunApplied
                    + " stun.reason=" + stunReason
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "SarsWarfistsProjectileHitSystem: failed to handle damage.", t);
        }
    }

    private int resolveStunEffectIndex() {
        int cached = stunEffectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(STUN_EFFECT_ID, -1);
        if (resolved >= 0) {
            stunEffectIndex = resolved;
        }
        return resolved;
    }
}
