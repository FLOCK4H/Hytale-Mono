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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * Applies custom potion splash effects by intercepting the explosion-damage pass emitted by legacy projectile explosions.
 *
 * <p>We intentionally cancel direct-hit (PROJECTILE) damage for these potion projectiles so they behave like splash
 * potions and don't double-hit (direct hit + explosion).</p>
 */
public final class PotionSplashEffectSystem extends DamageEventSystem {

    public static final String POTION_EMPTY_PROJECTILE_ID = "Potion_Empty_Projectile";
    public static final String POTION_CURSE_PROJECTILE_ID = "Potion_Curse_Projectile";

    private static final Set<String> POTION_PROJECTILE_IDS = Set.of(
        POTION_EMPTY_PROJECTILE_ID,
        POTION_CURSE_PROJECTILE_ID
    );

    private static final float CURSE_POTION_DAMAGE = 25f;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;

    public PotionSplashEffectSystem(
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

            UUIDComponent targetUuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
            PlayerRef targetPlayerRef = store.getComponent(targetRef, PlayerRef.getComponentType());
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
            if (projectileComponent == null) {
                return;
            }

            String projectileAssetName = projectileComponent.getProjectileAssetName();
            if (projectileAssetName == null || !POTION_PROJECTILE_IDS.contains(projectileAssetName)) {
                return;
            }

            long nowNanos = System.nanoTime();
            String causeId = null;
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex >= 0) {
                DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
                if (cause != null) {
                    causeId = cause.getId();
                }
            }

            boolean isProjectileDamage = causeId != null && causeId.equalsIgnoreCase("PROJECTILE");
            if (isProjectileDamage) {
                // Splash potions: prevent direct-hit damage so we don't double-hit (direct + explosion).
                float amountBefore = damage.getAmount();
                damage.setAmount(0f);
                damage.setCancelled(true);
                debug.traceFileOnly(
                    targetPlayerRef,
                    "PotionSplash event=Damage"
                        + " phase=directHit"
                        + " projectileId=" + projectileAssetName
                        + " causeId=" + causeId
                        + " cancelled=true"
                        + (targetUuid != null ? " target.uuid=" + targetUuid : "")
                        + " damage.amount.before=" + amountBefore
                        + " damage.amount.after=0.0"
                );
                return;
            }

            boolean isExplosionDamage = causeId != null && causeId.equalsIgnoreCase("ENVIRONMENT");
            if (!isExplosionDamage) {
                return;
            }

            float amountBefore = damage.getAmount();
            float amountAfter = amountBefore;
            boolean cancelled = false;

            if (POTION_CURSE_PROJECTILE_ID.equals(projectileAssetName)) {
                amountAfter = CURSE_POTION_DAMAGE;
                damage.setAmount(amountAfter);
            } else if (POTION_EMPTY_PROJECTILE_ID.equals(projectileAssetName)) {
                // Defensive: empty bottle shouldn't meaningfully affect entities.
                amountAfter = 0f;
                cancelled = true;
                damage.setAmount(0f);
                damage.setCancelled(true);
            }

            debug.traceFileOnly(
                targetPlayerRef,
                "PotionSplash event=Damage"
                    + " phase=explosion"
                    + " projectileId=" + projectileAssetName
                    + " causeId=" + causeId
                    + " cancelled=" + cancelled
                    + (targetUuid != null ? " target.uuid=" + targetUuid : "")
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=" + amountAfter
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "PotionSplashEffectSystem: failed to handle damage.", t);
        }
    }
}
