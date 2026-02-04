package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Morph Book projectile effect: on hit, morph the caster into the hit entity's model.
 *
 * <p>Implemented via direct {@link ModelComponent} replacement on the caster. The projectile's damage is cancelled so
 * the Morph Book is non-lethal.</p>
 */
public final class MorphBookProjectileHitSystem extends DamageEventSystem {

    public static final String MORPH_PROJECTILE_ID = "Morph_Vortex";

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final MorphBookModelState morphBookModelState;

    public MorphBookProjectileHitSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull MorphBookModelState morphBookModelState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.morphBookModelState = morphBookModelState;
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
            if (!MORPH_PROJECTILE_ID.equals(projectileAssetName)) {
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
            if (shooterPlayerRef == null || shooterPlayerRef.getUuid() == null) {
                return;
            }

            UUID shooterUuid = shooterPlayerRef.getUuid();

            ModelComponent shooterModelComponent = store.getComponent(shooterRef, ModelComponent.getComponentType());
            Model shooterModel = shooterModelComponent != null ? shooterModelComponent.getModel() : null;
            morphBookModelState.captureBaselineIfAbsent(shooterUuid, shooterModel);
            Model baselineModel = morphBookModelState.getBaselineModel(shooterUuid);
            String baselineModelAssetId = baselineModel != null ? baselineModel.getModelAssetId() : null;

            PlayerSkinComponent shooterSkinComponent = store.getComponent(shooterRef, PlayerSkinComponent.getComponentType());
            PlayerSkin shooterSkin = shooterSkinComponent != null ? shooterSkinComponent.getPlayerSkin() : null;
            morphBookModelState.captureBaselineSkinIfAbsent(shooterUuid, shooterSkin);
            PlayerSkin baselineSkin = morphBookModelState.getBaselineSkin(shooterUuid);

            ModelComponent targetModelComponent = store.getComponent(targetRef, ModelComponent.getComponentType());
            Model targetModel = targetModelComponent != null ? targetModelComponent.getModel() : null;
            String targetModelAssetId = targetModel != null ? targetModel.getModelAssetId() : null;

            boolean morphed = false;
            String morphReason = "skip";
            if (targetModel == null || targetModelAssetId == null) {
                morphReason = "targetModelMissing";
            } else if (shooterRef.equals(targetRef)) {
                morphReason = "selfHit";
            } else {
                try {
                    store.putComponent(shooterRef, ModelComponent.getComponentType(), new ModelComponent(new Model(targetModel)));
                    morphed = true;
                    morphReason = "applied";
                } catch (Throwable t) {
                    morphed = false;
                    morphReason = "applyException";
                }
            }

            float amountBefore = damage.getAmount();
            damage.setAmount(0f);
            damage.setCancelled(true);

            debug.traceFileOnly(
                shooterPlayerRef,
                "MorphBook event=Damage"
                    + " projectileId=" + projectileAssetName
                    + (causeId != null ? " causeId=" + causeId : "")
                    + " cancelled=true"
                    + " shooter.uuid=" + shooterUuid
                    + (baselineModelAssetId != null ? " shooter.baselineModelAssetId=" + baselineModelAssetId : "")
                    + " shooter.baselineSkinPresent=" + (baselineSkin != null)
                    + (targetModelAssetId != null ? " target.modelAssetId=" + targetModelAssetId : "")
                    + " morph.applied=" + morphed
                    + " morph.reason=" + morphReason
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=0.0"
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "MorphBookProjectileHitSystem: failed to handle damage.", t);
        }
    }
}
