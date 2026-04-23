package org.example.plugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.collision.CollisionMaterial;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Plugin-driven fire-like ranged attacks for bonded Kudu Adepts.
 *
 * <p>Bonding/target selection is handled by {@link KuduAdeptBondSystem}; this system just fires a projectile at the
 * current marked target (excluding the owner).</p>
 */
public final class KuduAdeptProjectileSystem extends TickingSystem<EntityStore> {

    public static final String DEFAULT_PROJECTILE_ID = "KuduAdept_Firebolt";

    private static final long TICK_INTERVAL_NANOS = 150_000_000L;
    private static final long DEBUG_INTERVAL_NANOS = 10_000_000_000L;
    private static final long ACTION_CLEAR_NANOS = 800_000_000L;

    private static final double RANGE_BLOCKS = 24.0;
    private static final long COOLDOWN_NANOS = (long) (1.35 * 1_000_000_000L);
    private static final double AIM_HEIGHT_BLOCKS = 1.25;

    private static final Box RAY_POINT_BOX = new Box(0, 0, 0, 0.01, 0.01, 0.01);

    private static final String ACTION_ANIMATION_CAST = "CastMagic";

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final KuduAdeptBondState bondState;

    private final ConcurrentMap<UUID, Long> nextFireAtNanosByAdept = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> nextDebugAtNanosByAdept = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> clearActionAtNanosByAdept = new ConcurrentHashMap<>();

    private volatile long nextTickAtNanos;

    public KuduAdeptProjectileSystem(
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

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            long nowNanos = System.nanoTime();
            long next = nextTickAtNanos;
            if (next > 0 && nowNanos < next) {
                return;
            }
            nextTickAtNanos = nowNanos + TICK_INTERVAL_NANOS;

            if (config == null || config.kuduAdept == null || !config.kuduAdept.enabled) {
                return;
            }

            var external = store.getExternalData();
            World world = external != null ? external.getWorld() : null;
            if (external == null || world == null) {
                return;
            }

            TimeResource time = store.getResource(TimeResource.getResourceType());
            if (time == null) {
                return;
            }

            String roleName = config.kuduAdept.roleName != null && !config.kuduAdept.roleName.isBlank()
                ? config.kuduAdept.roleName
                : KuduAdeptSpawnerSystem.DEFAULT_ROLE_NAME;

            double rangeSq = RANGE_BLOCKS * RANGE_BLOCKS;
            String projectileId = DEFAULT_PROJECTILE_ID;

            for (KuduAdeptBondState.BondedAdept bonded : bondState.snapshotAll()) {
                if (bonded == null) {
                    continue;
                }

                UUID adeptUuid = bonded.adeptUuid();
                UUID ownerUuid = bonded.ownerUuid();
                if (adeptUuid == null || ownerUuid == null) {
                    continue;
                }

                Ref<EntityStore> adeptRef = external.getRefFromUUID(adeptUuid);
                if (adeptRef == null || !adeptRef.isValid()) {
                    nextFireAtNanosByAdept.remove(adeptUuid);
                    clearActionAtNanosByAdept.remove(adeptUuid);
                    continue;
                }

                maybeClearActionAnimation(store, nowNanos, adeptUuid, adeptRef);

                long nextFireAt = nextFireAtNanosByAdept.getOrDefault(adeptUuid, 0L);
                if (nextFireAt > nowNanos) {
                    continue;
                }

                NPCEntity npc = store.getComponent(adeptRef, NPCEntity.getComponentType());
                if (npc == null) {
                    continue;
                }

                String npcRoleName = null;
                try {
                    npcRoleName = npc.getRoleName();
                } catch (Throwable ignored) {
                    // Best effort.
                }
                if (npcRoleName == null || !npcRoleName.equals(roleName)) {
                    continue;
                }

                KuduAdeptBondState.OwnerTarget ownerTarget = bondState.getOwnerTarget(ownerUuid);
                if (ownerTarget == null || ownerTarget.targetUuid() == null) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, null, projectileId, "noMasterTarget");
                    continue;
                }

                Ref<EntityStore> targetRef = external.getRefFromUUID(ownerTarget.targetUuid());
                if (targetRef == null || !targetRef.isValid()) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, null, projectileId, "masterTargetInvalidRef");
                    continue;
                }

                Ref<EntityStore> ownerRef = external.getRefFromUUID(ownerUuid);
                if (ownerRef != null && ownerRef.isValid() && targetRef.equals(ownerRef)) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "ownerTarget");
                    continue;
                }

                // Safety: never shoot players.
                if (store.getComponent(targetRef, PlayerRef.getComponentType()) != null) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "playerTarget");
                    continue;
                }

                UUID targetUuid = null;
                try {
                    UUIDComponent targetUuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());
                    if (targetUuidComponent != null) {
                        targetUuid = targetUuidComponent.getUuid();
                    }
                } catch (Throwable ignored) {
                    // Best effort.
                }
                if (targetUuid == null) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "targetUuidMissing");
                    continue;
                }
                if (bondState.getByAdept(targetUuid) != null) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "bondedAdeptTarget");
                    continue;
                }

                Vector3d targetPos = getAimPosition(store, targetRef, AIM_HEIGHT_BLOCKS);
                Vector3d origin = getAimPosition(store, adeptRef, AIM_HEIGHT_BLOCKS);
                if (targetPos == null || origin == null) {
                    continue;
                }

                Vector3d deltaVec = new Vector3d(targetPos.x - origin.x, targetPos.y - origin.y, targetPos.z - origin.z);
                if (!deltaVec.isFinite()) {
                    continue;
                }
                double d2 = deltaVec.squaredLength();
                if (d2 < 1e-9 || d2 > rangeSq) {
                    continue;
                }

                if (!hasLineOfSight(world, origin, targetPos)) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "noLineOfSight");
                    continue;
                }

                playActionAnimationMaybe(store, npc, nowNanos, adeptUuid, adeptRef, ACTION_ANIMATION_CAST);

                Vector3f rotation = rotationFromDirection(deltaVec);
                float yaw = rotation.getYaw();
                float pitch = rotation.getPitch();

                Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, projectileId, origin, rotation);
                if (holder == null) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "projectileAssembleFailed");
                    nextFireAtNanosByAdept.put(adeptUuid, nowNanos + COOLDOWN_NANOS);
                    continue;
                }

                ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
                if (projectile == null) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "projectileComponentMissing");
                    nextFireAtNanosByAdept.put(adeptUuid, nowNanos + COOLDOWN_NANOS);
                    continue;
                }

                if (!projectile.initialize()) {
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "projectileAssetNotFound");
                    nextFireAtNanosByAdept.put(adeptUuid, nowNanos + COOLDOWN_NANOS);
                    continue;
                }

                try {
                    projectile.shoot(holder, adeptUuid, origin.x, origin.y, origin.z, yaw, pitch);
                } catch (Throwable t) {
                    errors.report((PlayerRef) null, "KuduAdeptProjectile: projectile.shoot failed (assetId=" + projectileId + ").", t);
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "projectileShootException");
                    nextFireAtNanosByAdept.put(adeptUuid, nowNanos + COOLDOWN_NANOS);
                    continue;
                }

                Ref<EntityStore> projectileRef;
                try {
                    projectileRef = store.addEntity(holder, AddReason.SPAWN);
                } catch (Throwable t) {
                    errors.report((PlayerRef) null, "KuduAdeptProjectile: store.addEntity failed (assetId=" + projectileId + ").", t);
                    debugFailureMaybe(store, world, nowNanos, adeptUuid, targetRef, projectileId, "projectileSpawnException");
                    nextFireAtNanosByAdept.put(adeptUuid, nowNanos + COOLDOWN_NANOS);
                    continue;
                }

                UUID projectileUuid = null;
                try {
                    UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
                    if (projectileUuidComponent != null) {
                        projectileUuid = projectileUuidComponent.getUuid();
                    }
                } catch (Throwable ignored) {
                    // Best effort.
                }

                nextFireAtNanosByAdept.put(adeptUuid, nowNanos + COOLDOWN_NANOS);

                debug.traceFileOnly(
                    null,
                    "KuduAdeptRanged event=shoot"
                        + " adeptUuid=" + adeptUuid
                        + (targetUuid != null ? " targetUuid=" + targetUuid : "")
                        + (projectileUuid != null ? " projectileUuid=" + projectileUuid : "")
                        + " projectileId=" + projectileId
                        + " anim=" + ACTION_ANIMATION_CAST
                        + " distanceBlocks=" + Math.sqrt(d2)
                        + " rangeBlocks=" + RANGE_BLOCKS
                        + " cooldownSeconds=" + (COOLDOWN_NANOS / 1_000_000_000.0)
                        + " world=" + world.getName()
                );
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduAdeptProjectileSystem: tick failed.", t);
        }
    }

    private void maybeClearActionAnimation(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull UUID adeptUuid,
        @Nonnull Ref<EntityStore> adeptRef
    ) {
        long clearAt = clearActionAtNanosByAdept.getOrDefault(adeptUuid, 0L);
        if (clearAt <= 0 || nowNanos < clearAt) {
            return;
        }
        clearActionAtNanosByAdept.remove(adeptUuid);
        try {
            com.hypixel.hytale.server.core.entity.AnimationUtils.stopAnimation(adeptRef, AnimationSlot.Action, store);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private void playActionAnimationMaybe(
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npc,
        long nowNanos,
        @Nonnull UUID adeptUuid,
        @Nonnull Ref<EntityStore> adeptRef,
        @Nonnull String animationId
    ) {
        if (animationId.isBlank()) {
            return;
        }
        try {
            npc.playAnimation(adeptRef, AnimationSlot.Action, animationId, store);
            clearActionAtNanosByAdept.put(adeptUuid, nowNanos + ACTION_CLEAR_NANOS);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private void debugFailureMaybe(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        long nowNanos,
        @Nonnull UUID adeptUuid,
        @Nullable Ref<EntityStore> targetRef,
        @Nonnull String projectileId,
        @Nonnull String reason
    ) {
        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adeptUuid, 0L);
        if (nextDebugAt > nowNanos) {
            return;
        }
        nextDebugAtNanosByAdept.put(adeptUuid, nowNanos + DEBUG_INTERVAL_NANOS);

        UUID targetUuid = null;
        try {
            UUIDComponent targetUuidComponent = targetRef != null ? store.getComponent(targetRef, UUIDComponent.getComponentType()) : null;
            if (targetUuidComponent != null) {
                targetUuid = targetUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        debug.traceFileOnly(
            null,
            "KuduAdeptRanged event=shootSkipped"
                + " reason=" + reason
                + " adeptUuid=" + adeptUuid
                + (targetUuid != null ? " targetUuid=" + targetUuid : "")
                + " projectileId=" + projectileId
                + " world=" + world.getName()
        );
    }

    private static boolean hasLineOfSight(@Nonnull World world, @Nonnull Vector3d origin, @Nonnull Vector3d targetPos) {
        Vector3d delta = new Vector3d(targetPos.x - origin.x, targetPos.y - origin.y, targetPos.z - origin.z);
        double maxDistance = Math.sqrt(delta.squaredLength());
        if (!Double.isFinite(maxDistance) || maxDistance <= 0) {
            return false;
        }

        Vector3d ray = new Vector3d(delta).normalize().scale(maxDistance);
        CollisionResult result = new CollisionResult(false, false);
        result.setCollisionByMaterial(CollisionMaterial.MATERIAL_SOLID);
        CollisionModule.findBlockCollisionsIterative(world, RAY_POINT_BOX, origin, ray, true, result);
        return result.getFirstBlockCollision() == null;
    }

    private static @Nullable Vector3d getAimPosition(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        double fallbackHeightBlocks
    ) {
        try {
            Transform look = TargetUtil.getLook(ref, store);
            Vector3d pos = look != null ? look.getPosition() : null;
            if (pos != null && pos.isFinite()) {
                return pos;
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            Vector3d pos = transform != null ? transform.getPosition() : null;
            if (pos != null && pos.isFinite()) {
                double height = Double.isFinite(fallbackHeightBlocks) ? fallbackHeightBlocks : AIM_HEIGHT_BLOCKS;
                return new Vector3d(pos.x, pos.y + height, pos.z);
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        return null;
    }

    private static @Nonnull Vector3f rotationFromDirection(@Nonnull Vector3d direction) {
        double lenSq = direction.squaredLength();
        if (!Double.isFinite(lenSq) || lenSq < 1e-9) {
            return Vector3f.ZERO;
        }

        double len = Math.sqrt(lenSq);
        double dx = direction.x / len;
        double dy = direction.y / len;
        double dz = direction.z / len;

        double clampedY = Math.max(-1.0, Math.min(1.0, dy));
        float pitch = (float) Math.asin(clampedY);
        float yaw = (float) Math.atan2(-dx, -dz);

        return new Vector3f(pitch, yaw, 0f);
    }
}
