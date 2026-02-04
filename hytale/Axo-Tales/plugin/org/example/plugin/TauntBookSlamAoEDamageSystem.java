package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Applies Taunt Book slam AoE damage using normal ECS iteration, avoiding {@code Store.forEachChunk(...)} reentrancy.
 */
public final class TauntBookSlamAoEDamageSystem extends EntityTickingSystem<EntityStore> {

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final TauntBookSlamQueue slamQueue;

    private static final String HEALTH_STAT_NAME = "Health";
    private @Nullable DamageCause cachedSlamCause;
    private boolean loggedCauseMissing;

    public TauntBookSlamAoEDamageSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull TauntBookSlamQueue slamQueue
    ) {
        this.errors = errors;
        this.debug = debug;
        this.slamQueue = slamQueue;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            TransformComponent.getComponentType(),
            EntityStatMap.getComponentType()
        );
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, TauntBookLandingSystem.class));
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        var external = store.getExternalData();
        World world = external != null ? external.getWorld() : null;
        if (world == null) {
            return;
        }

        TauntBookSlamQueue.PerWorldQueue worldQueue = slamQueue.forWorld(world);
        long worldTick = world.getTick();
        worldQueue.advanceToTick(worldTick);

        if (worldQueue.getRolledOffTick() != Long.MIN_VALUE && !worldQueue.getRolledOff().isEmpty()) {
            long rolledTick = worldQueue.getRolledOffTick();
            List<TauntBookSlamQueue.SlamRequest> rolled = worldQueue.getRolledOff();
            for (TauntBookSlamQueue.SlamRequest slam : rolled) {
                PlayerRef caster = store.getComponent(slam.casterRef, PlayerRef.getComponentType());
                debug.traceFileOnly(
                    caster,
                    "TauntBookSlamSummary event=LandingAoE"
                        + " world=" + world.getName()
                        + " world.tick=" + rolledTick
                        + " caster.uuid=" + slam.casterUuid
                        + " taunt.cast.chainId=" + slam.castChainId
                        + " taunt.cast.interactionType=" + slam.castInteractionType
                        + " taunt.active.expiresAtNanos=" + slam.tauntExpiresAtNanos
                        + " slam.center=(" + slam.centerX + "," + slam.centerY + "," + slam.centerZ + ")"
                        + " slam.radiusBlocks=" + slam.radiusBlocks
                        + " slam.damageAmount=" + slam.damageAmount
                        + " slam.candidatesChecked=" + slam.candidatesChecked
                        + " slam.inRadius=" + slam.inRadius
                        + " slam.damageAttempts=" + slam.damageAttempts
                        + " slam.damagedEntities=" + slam.damagedEntities
                        + " slam.damageClamped=" + slam.damageClamped
                        + " slam.damageSkipped=" + slam.damageSkipped
                        + " slam.damageExceptions=" + slam.damageExceptions
                        + " slam.ignoredInvalidTarget=" + slam.ignoredInvalidTarget
                        + " slam.ignoredInvalidCaster=" + slam.ignoredInvalidCaster
                );
            }
            worldQueue.clearRolledOff();
        }

        List<TauntBookSlamQueue.SlamRequest> slams = worldQueue.getActive();
        if (slams.isEmpty()) {
            return;
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        TransformComponent targetTransform = chunk.getComponent(index, TransformComponent.getComponentType());
        Vector3d targetPos = targetTransform != null ? targetTransform.getPosition() : null;
        if (targetPos == null || !targetPos.isFinite()) {
            return;
        }

        EntityStatMap targetStats = chunk.getComponent(index, EntityStatMap.getComponentType());
        float predictedHealthCurrent = Float.NaN;
        float predictedHealthMin = Float.NaN;
        float predictedHealthMax = Float.NaN;
        int healthIndex = DefaultEntityStatTypes.getHealth();

        DamageCause slamCause = resolveSlamDamageCause();
        if (slamCause == null) {
            if (!loggedCauseMissing) {
                loggedCauseMissing = true;
                debug.traceFileOnly(null, "TauntBookSlamDamage event=LandingAoE slamDamageCauseMissing=true");
            }
            return;
        }

        for (TauntBookSlamQueue.SlamRequest slam : slams) {
            if (!slam.casterRef.isValid()) {
                slam.ignoredInvalidCaster++;
                continue;
            }
            if (targetRef.equals(slam.casterRef)) {
                continue;
            }

            slam.candidatesChecked++;
            double dx = targetPos.x - slam.centerX;
            double dy = targetPos.y - slam.centerY;
            double dz = targetPos.z - slam.centerZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > slam.radiusSq) {
                continue;
            }
            slam.inRadius++;

            float requestedDamage = (float) slam.damageAmount;
            float appliedDamage = requestedDamage;
            String clampReason = "asConfigured";
            float healthCurrent = predictedHealthCurrent;
            float healthMin = predictedHealthMin;
            float healthMax = predictedHealthMax;

            EntityStatValue healthStat = null;
            String healthStatSource = "missing";
            if (targetStats != null) {
                try {
                    // Prefer lookup by name so we don't depend on DefaultEntityStatTypes.update() timing.
                    healthStat = targetStats.get(HEALTH_STAT_NAME);
                    healthStatSource = "name(" + HEALTH_STAT_NAME + ")";
                } catch (Throwable ignored) {
                    healthStat = null;
                    healthStatSource = "nameLookupFailed";
                }

                // Fallback: lookup by index (useful if name lookup isn't supported / mismatched).
                if (healthStat == null && healthIndex >= 0) {
                    try {
                        healthStat = targetStats.get(healthIndex);
                        healthStatSource = "index(" + healthIndex + ")";
                    } catch (Throwable ignored) {
                        healthStat = null;
                        healthStatSource = "indexLookupFailed(" + healthIndex + ")";
                    }
                }
            }

            // Refresh snapshot for this target (per slam) only when needed.
            if (healthStat != null) {
                healthCurrent = healthStat.get();
                healthMin = healthStat.getMin();
                healthMax = healthStat.getMax();
                predictedHealthCurrent = healthCurrent;
                predictedHealthMin = healthMin;
                predictedHealthMax = healthMax;
            }

            boolean hasUsableHealth = Float.isFinite(healthCurrent)
                && Float.isFinite(healthMin)
                && Float.isFinite(healthMax)
                && (healthMax > healthMin)
                && (healthMax > 0f);

            if (hasUsableHealth) {
                float remainingAboveMin = Math.max(0f, healthCurrent - healthMin);
                if (appliedDamage > remainingAboveMin) {
                    appliedDamage = remainingAboveMin;
                    clampReason = "clampedToTargetRemainingHealth";
                    slam.damageClamped++;
                }
            } else {
                clampReason = targetStats == null ? "targetEntityStatMapMissing" : "targetHealthStatMissingOrInvalid";
            }

            boolean lethal = Float.isFinite(healthCurrent)
                && Float.isFinite(healthMin)
                && (healthCurrent - appliedDamage) <= (healthMin + 0.0001f);

            if (appliedDamage != requestedDamage || lethal || !hasUsableHealth) {
                PlayerRef caster = store.getComponent(slam.casterRef, PlayerRef.getComponentType());
                debug.traceFileOnly(
                    caster,
                    "TauntBookSlamDamage event=LandingAoE"
                        + " world=" + world.getName()
                        + " world.tick=" + worldTick
                        + " caster.uuid=" + slam.casterUuid
                        + " targetRef=" + targetRef
                        + " distanceSq=" + distSq
                        + " damage.requested=" + requestedDamage
                        + " damage.applied=" + appliedDamage
                        + " clamp.reason=" + clampReason
                        + " health.lookupSource=" + healthStatSource
                        + " health.index=" + healthIndex
                        + " target.health.current=" + healthCurrent
                        + " target.health.min=" + healthMin
                        + " target.health.max=" + healthMax
                        + " target.health.lethal=" + lethal
                );
            }

            if (!hasUsableHealth) {
                slam.damageSkipped++;
                continue;
            }

            if (!(appliedDamage > 0f) || !Float.isFinite(appliedDamage)) {
                slam.damageSkipped++;
                continue;
            }

            if (Float.isFinite(healthCurrent) && Float.isFinite(healthMin)) {
                predictedHealthCurrent = Math.max(healthMin, healthCurrent - appliedDamage);
            }

            Damage.EntitySource source = new Damage.EntitySource(slam.casterRef);
            Damage slamDamage = new Damage(source, slamCause, appliedDamage);
            slam.damageAttempts++;
            try {
                DamageSystems.executeDamage(targetRef, commandBuffer, slamDamage);
                slam.damagedEntities++;
            } catch (Throwable t) {
                slam.damageExceptions++;
                PlayerRef caster = store.getComponent(slam.casterRef, PlayerRef.getComponentType());
                errors.report(caster, "TauntBookSlamAoEDamageSystem: executeDamage failed (targetRef=" + targetRef + ", damage=" + appliedDamage + ").", t);
                debug.traceFileOnly(
                    caster,
                    "TauntBookSlamDamage event=LandingAoE"
                        + " world=" + world.getName()
                        + " world.tick=" + worldTick
                        + " caster.uuid=" + slam.casterUuid
                        + " targetRef=" + targetRef
                        + " distanceSq=" + distSq
                        + " damage.requested=" + requestedDamage
                        + " damage.applied=" + appliedDamage
                        + " clamp.reason=" + clampReason
                        + " executeDamage=exception(" + t.getClass().getSimpleName() + ")"
                        + (t.getMessage() != null ? " message=\"" + t.getMessage() + "\"" : "")
                        + (Float.isFinite(healthCurrent) ? " target.health.current=" + healthCurrent : "")
                        + (Float.isFinite(healthMin) ? " target.health.min=" + healthMin : "")
                        + (Float.isFinite(healthMax) ? " target.health.max=" + healthMax : "")
                );
            }
        }
    }

    private @Nullable DamageCause resolveSlamDamageCause() {
        if (cachedSlamCause != null) {
            return cachedSlamCause;
        }
        var map = DamageCause.getAssetMap();
        if (map == null) {
            return null;
        }

        DamageCause cause = map.getAsset("COMMAND");
        if (cause != null) {
            cachedSlamCause = cause;
            return cause;
        }
        cause = map.getAsset("Command");
        if (cause != null) {
            cachedSlamCause = cause;
            return cause;
        }
        cause = map.getAsset("PHYSICAL");
        if (cause != null) {
            cachedSlamCause = cause;
            return cause;
        }
        cause = map.getAsset("Physical");
        if (cause != null) {
            cachedSlamCause = cause;
            return cause;
        }
        return null;
    }
}
