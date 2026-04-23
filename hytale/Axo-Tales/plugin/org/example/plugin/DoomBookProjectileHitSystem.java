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
import java.util.UUID;

/**
 * Applies Doom Book projectile explosion damage while preventing direct-hit double damage.
 */
public final class DoomBookProjectileHitSystem extends DamageEventSystem {

    public static final String DOOM_PROJECTILE_ID = "Doom_Ball";
    private static final float DOOM_EXPLOSION_DAMAGE = 50f;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;

    public DoomBookProjectileHitSystem(
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
            if (projectileComponent == null) {
                return;
            }

            String projectileAssetName = projectileComponent.getProjectileAssetName();
            if (!DOOM_PROJECTILE_ID.equals(projectileAssetName)) {
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

            String causeId = null;
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex >= 0) {
                DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
                if (cause != null) {
                    causeId = cause.getId();
                }
            }

            float amountBefore = damage.getAmount();
            float amountAfter = amountBefore;
            boolean cancelled = damage.isCancelled();
            String phase = "ignored";

            if (causeId != null && causeId.equalsIgnoreCase("PROJECTILE")) {
                damage.setAmount(0f);
                damage.setCancelled(true);
                amountAfter = 0f;
                cancelled = true;
                phase = "directHit";
            } else if (causeId != null && causeId.equalsIgnoreCase("ENVIRONMENT")) {
                damage.setAmount(DOOM_EXPLOSION_DAMAGE);
                amountAfter = DOOM_EXPLOSION_DAMAGE;
                cancelled = false;
                phase = "explosion";
            } else {
                return;
            }

            PlayerRef logPlayer = shooterPlayerRef != null ? shooterPlayerRef : targetPlayerRef;
            debug.traceFileOnly(
                logPlayer,
                "DoomBook event=Damage"
                    + " phase=" + phase
                    + " projectileId=" + projectileAssetName
                    + (causeId != null ? " causeId=" + causeId : "")
                    + " cancelled=" + cancelled
                    + (shooterUuid != null ? " shooter.uuid=" + shooterUuid : "")
                    + (targetUuid != null ? " target.uuid=" + targetUuid : "")
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=" + amountAfter
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "DoomBookProjectileHitSystem: failed to handle damage.", t);
        }
    }
}
