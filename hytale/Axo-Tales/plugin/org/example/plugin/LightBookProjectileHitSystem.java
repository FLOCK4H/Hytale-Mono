package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Makes Axo's Light Book projectile a utility light: it contacts entities without damaging them.
 */
@SuppressWarnings("deprecation")
public final class LightBookProjectileHitSystem extends DamageEventSystem {

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final LightBookProjectileState projectileState;

    public LightBookProjectileHitSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull LightBookProjectileState projectileState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.projectileState = projectileState;
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
            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.ProjectileSource projectileSource)) {
                return;
            }

            Ref<EntityStore> projectileRef = projectileSource.getProjectile();
            if (projectileRef == null || !projectileRef.isValid()) {
                return;
            }

            ProjectileComponent projectileComponent = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
            if (projectileComponent == null || !LightBookProjectileSystem.LIGHT_PROJECTILE_ID.equals(projectileComponent.getProjectileAssetName())) {
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

            UUID projectileUuid = null;
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }

            PlayerRef shooterPlayerRef = null;
            Ref<EntityStore> shooterRef = projectileSource.getRef();
            if (shooterRef != null && shooterRef.isValid()) {
                shooterPlayerRef = store.getComponent(shooterRef, PlayerRef.getComponentType());
            }

            float amountBefore = damage.getAmount();
            damage.setAmount(0f);
            damage.setCancelled(true);
            boolean newlySettled = false;
            SimplePhysicsProvider physics = projectileComponent.getSimplePhysicsProvider();
            if (projectileUuid != null && physics != null) {
                TransformComponent transform = store.getComponent(projectileRef, TransformComponent.getComponentType());
                Vector3d position = transform != null && transform.getPosition() != null && transform.getPosition().isFinite()
                    ? transform.getPosition()
                    : new Vector3d(0, 0, 0);
                LightBookProjectileState.ActiveProjectile active = projectileState.getOrCreate(
                    projectileUuid,
                    position,
                    physics.getVelocity()
                );
                newlySettled = LightBookProjectileSystem.settleProjectilePhysics(physics, active);
            }

            debug.traceFileOnly(
                shooterPlayerRef,
                "LightBook event=Damage"
                    + " projectileId=" + LightBookProjectileSystem.LIGHT_PROJECTILE_ID
                    + (causeId != null ? " causeId=" + causeId : "")
                    + " cancelled=true"
                    + " projectile.settled=true"
                    + " projectile.newlySettled=" + newlySettled
                    + (projectileUuid != null ? " projectile.uuid=" + projectileUuid : "")
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=0.0"
                    + " reason=utilityLightNoDamageSettle"
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "LightBookProjectileHitSystem: failed to handle damage.", t);
        }
    }
}
