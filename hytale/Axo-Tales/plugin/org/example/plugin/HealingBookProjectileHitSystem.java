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
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Healing Book projectile effect: on hit, fully heal the target.
 *
 * <p>The projectile's damage is cancelled so the Healing Book is non-lethal.</p>
 */
public final class HealingBookProjectileHitSystem extends DamageEventSystem {

    public static final String HEALING_PROJECTILE_ID = "Healing_Bolt";

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;

    public HealingBookProjectileHitSystem(
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

            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.ProjectileSource projectileSource)) {
                return;
            }

            Ref<EntityStore> projectileRef = projectileSource.getProjectile();
            if (projectileRef == null || !projectileRef.isValid()) {
                return;
            }

            ProjectileComponent projectileComponent = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
            if (projectileComponent == null) {
                return;
            }

            String projectileAssetName = projectileComponent.getProjectileAssetName();
            if (!HEALING_PROJECTILE_ID.equals(projectileAssetName)) {
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
            if (shooterRef == null || !shooterRef.isValid()) {
                return;
            }

            PlayerRef shooterPlayerRef = store.getComponent(shooterRef, PlayerRef.getComponentType());
            UUID shooterUuid = shooterPlayerRef != null ? shooterPlayerRef.getUuid() : null;

            int healthIndex = DefaultEntityStatTypes.getHealth();
            boolean healed = false;
            String healReason = "skip";
            float healthBefore = Float.NaN;
            float healthMin = Float.NaN;
            float healthMax = Float.NaN;
            float healthAfter = Float.NaN;

            EntityStatMap targetStats = store.getComponent(targetRef, EntityStatMap.getComponentType());
            if (targetStats == null) {
                healReason = "targetEntityStatMapMissing";
            } else if (healthIndex == Integer.MIN_VALUE || healthIndex < 0) {
                healReason = "healthIndexInvalid";
            } else {
                EntityStatValue healthStat = targetStats.get(healthIndex);
                if (healthStat == null) {
                    healReason = "targetHealthStatMissing";
                } else {
                    healthBefore = healthStat.get();
                    healthMin = healthStat.getMin();
                    healthMax = healthStat.getMax();
                    healthAfter = healthMax;
                    try {
                        targetStats.setStatValue(healthIndex, healthAfter);
                        targetStats.update();
                        healed = true;
                        healReason = "applied";
                    } catch (Throwable ignored) {
                        healed = false;
                        healReason = "applyException";
                    }
                }
            }

            float amountBefore = damage.getAmount();
            damage.setAmount(0f);
            damage.setCancelled(true);

            debug.traceFileOnly(
                shooterPlayerRef,
                "HealingBook event=Damage"
                    + " projectileId=" + projectileAssetName
                    + (causeId != null ? " causeId=" + causeId : "")
                    + " cancelled=true"
                    + (shooterUuid != null ? " shooter.uuid=" + shooterUuid : "")
                    + " heal.applied=" + healed
                    + " heal.reason=" + healReason
                    + " health.index=" + healthIndex
                    + (Float.isFinite(healthBefore) ? " health.before=" + healthBefore : "")
                    + (Float.isFinite(healthMin) ? " health.min=" + healthMin : "")
                    + (Float.isFinite(healthMax) ? " health.max=" + healthMax : "")
                    + (Float.isFinite(healthAfter) ? " health.after=" + healthAfter : "")
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=0.0"
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "HealingBookProjectileHitSystem: failed to handle damage.", t);
        }
    }
}
