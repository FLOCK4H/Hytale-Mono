package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Keeps Axo's Light Book projectiles bright and forces their fast-start/slow-cruise flight profile.
 */
@SuppressWarnings("deprecation")
public final class LightBookProjectileSystem extends TickingSystem<EntityStore> {

    public static final String LIGHT_PROJECTILE_ID = "Light_Ball";

    private static final double DEFAULT_MAX_DISTANCE_BLOCKS = 100.0;
    private static final double DEFAULT_INITIAL_SPEED_BLOCKS_PER_SECOND = 55.0;
    private static final double DEFAULT_CRUISE_SPEED_BLOCKS_PER_SECOND = 0.75;
    private static final double DEFAULT_SLOWDOWN_SECONDS = 1.2;
    static final double SETTLE_REBOUND_SECONDS = 0.18;
    static final double SETTLE_REBOUND_SPEED_BLOCKS_PER_SECOND = 0.65;
    private static final int DEFAULT_LIGHT_RADIUS = 1;
    private static final int DEFAULT_LIGHT_RED = 32;
    private static final int DEFAULT_LIGHT_GREEN = 24;
    private static final int DEFAULT_LIGHT_BLUE = 16;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final LightBookProjectileState state;

    public LightBookProjectileSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull LightBookProjectileState state
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.state = state;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            ArrayList<Ref<EntityStore>> removeRefs = new ArrayList<>();
            ArrayList<Ref<EntityStore>> lightRefs = new ArrayList<>();

            store.forEachChunk(
                Query.and(
                    ProjectileComponent.getComponentType(),
                    TransformComponent.getComponentType(),
                    UUIDComponent.getComponentType()
                ),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        ProjectileComponent projectile = chunk.getComponent(i, ProjectileComponent.getComponentType());
                        if (projectile == null || !LIGHT_PROJECTILE_ID.equals(projectile.getProjectileAssetName())) {
                            continue;
                        }

                        Ref<EntityStore> projectileRef = chunk.getReferenceTo(i);
                        if (projectileRef == null || !projectileRef.isValid()) {
                            continue;
                        }

                        UUIDComponent uuidComponent = chunk.getComponent(i, UUIDComponent.getComponentType());
                        UUID projectileUuid = uuidComponent != null ? uuidComponent.getUuid() : null;
                        if (projectileUuid == null) {
                            continue;
                        }

                        TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                        Vector3d position = transform != null ? transform.getPosition() : null;
                        if (position == null || !position.isFinite()) {
                            state.clear(projectileUuid);
                            removeRefs.add(projectileRef);
                            continue;
                        }

                        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
                        if (physics == null) {
                            state.clear(projectileUuid);
                            removeRefs.add(projectileRef);
                            continue;
                        }

                        LightBookProjectileState.ActiveProjectile active = state.getOrCreate(
                            projectileUuid,
                            position,
                            physics.getVelocity()
                        );
                        active.advance(Math.min(Math.max(delta, 0.0f), 0.25f));
                        if (active.isSettled()) {
                            applySettledMotion(physics, active, delta);
                            lightRefs.add(projectileRef);
                            continue;
                        }

                        if (physics.isImpacted()) {
                            settleProjectilePhysics(physics, active);
                            debug.traceFileOnly(
                                (PlayerRef) null,
                                "LightBookProjectile event=settle"
                                    + " projectile.uuid=" + projectileUuid
                                    + " reason=impact"
                                    + " position=" + Vector3d.formatShortString(position)
                                    + " reboundSeconds=" + String.format("%.3f", SETTLE_REBOUND_SECONDS)
                                    + " reboundSpeedBlocksPerSecond=" + String.format("%.3f", SETTLE_REBOUND_SPEED_BLOCKS_PER_SECOND)
                            );
                            lightRefs.add(projectileRef);
                            continue;
                        }

                        double distance = new Vector3d(position).subtract(active.origin()).length();
                        double maxDistance = getMaxDistanceBlocks();
                        if (Double.isFinite(distance) && distance >= maxDistance) {
                            debug.traceFileOnly(
                                (PlayerRef) null,
                                "LightBookProjectile event=remove"
                                    + " projectile.uuid=" + projectileUuid
                                    + " reason=maxDistance"
                                    + " distanceBlocks=" + String.format("%.2f", distance)
                                    + " maxDistanceBlocks=" + String.format("%.2f", maxDistance)
                                    + " position=" + Vector3d.formatShortString(position)
                            );
                            state.clear(projectileUuid);
                            removeRefs.add(projectileRef);
                            continue;
                        }

                        double speed = computeSpeed(active.elapsedSeconds());
                        physics.setVelocity(new Vector3d(active.direction()).scale(speed));
                        lightRefs.add(projectileRef);

                        if (active.elapsedSeconds() >= getSlowdownSeconds() && active.markCruiseLogged()) {
                            debug.traceFileOnly(
                                (PlayerRef) null,
                                "LightBookProjectile event=cruise"
                                    + " projectile.uuid=" + projectileUuid
                                    + " elapsedSeconds=" + String.format("%.3f", active.elapsedSeconds())
                                    + " speedBlocksPerSecond=" + String.format("%.3f", speed)
                                    + " maxDistanceBlocks=" + String.format("%.2f", maxDistance)
                                    + " dynamicLight=true"
                            );
                        }
                    }
                }
            );

            for (Ref<EntityStore> ref : lightRefs) {
                ensureDynamicLight(store, ref);
            }
            for (Ref<EntityStore> ref : removeRefs) {
                removeProjectileBestEffort(store, ref);
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "LightBookProjectileSystem: tick failed.", t);
        }
    }

    public void shutdown() {
        state.clearAll();
    }

    public static @Nonnull DynamicLight createDynamicLight(@Nonnull AxoTalesServerConfig config) {
        return new DynamicLight(createColorLight(config));
    }

    private static @Nonnull ColorLight createColorLight(@Nonnull AxoTalesServerConfig config) {
        AxoTalesServerConfig.LightBook lightBook = config.lightBook;
        int radius = lightBook != null ? lightBook.dynamicLightRadius : DEFAULT_LIGHT_RADIUS;
        int red = lightBook != null ? lightBook.dynamicLightRed : DEFAULT_LIGHT_RED;
        int green = lightBook != null ? lightBook.dynamicLightGreen : DEFAULT_LIGHT_GREEN;
        int blue = lightBook != null ? lightBook.dynamicLightBlue : DEFAULT_LIGHT_BLUE;
        return new ColorLight(
            (byte) clampInt(radius, 1, 32),
            (byte) clampInt(red, 0, 255),
            (byte) clampInt(green, 0, 255),
            (byte) clampInt(blue, 0, 255)
        );
    }

    private void ensureDynamicLight(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> projectileRef) {
        if (!projectileRef.isValid()) {
            return;
        }
        try {
            DynamicLight dynamicLight = store.getComponent(projectileRef, DynamicLight.getComponentType());
            if (dynamicLight == null) {
                store.putComponent(projectileRef, DynamicLight.getComponentType(), createDynamicLight(config));
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "LightBookProjectileSystem: failed to ensure projectile dynamic light.", t);
        }
    }

    static boolean settleProjectilePhysics(
        @Nonnull SimplePhysicsProvider physics,
        @Nonnull LightBookProjectileState.ActiveProjectile active
    ) {
        boolean newlySettled = active.markSettled(SETTLE_REBOUND_SECONDS);
        physics.setImpacted(false);
        physics.setResting(false);
        physics.setVelocity(new Vector3d(active.direction()).scale(-SETTLE_REBOUND_SPEED_BLOCKS_PER_SECOND));
        return newlySettled;
    }

    private static void applySettledMotion(
        @Nonnull SimplePhysicsProvider physics,
        @Nonnull LightBookProjectileState.ActiveProjectile active,
        float delta
    ) {
        physics.setImpacted(false);
        double remaining = active.advanceSettleRebound(Math.min(Math.max(delta, 0.0f), 0.25f));
        if (remaining > 0.0) {
            double scale = Math.max(0.0, Math.min(1.0, remaining / SETTLE_REBOUND_SECONDS));
            physics.setResting(false);
            physics.setVelocity(new Vector3d(active.direction()).scale(-SETTLE_REBOUND_SPEED_BLOCKS_PER_SECOND * scale));
            return;
        }

        physics.setVelocity(new Vector3d(0, 0, 0));
        physics.setResting(true);
    }

    private static void removeProjectileBestEffort(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> projectileRef) {
        try {
            store.removeEntity(projectileRef, RemoveReason.REMOVE);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private double computeSpeed(double elapsedSeconds) {
        double initial = getInitialSpeedBlocksPerSecond();
        double cruise = getCruiseSpeedBlocksPerSecond();
        double slowdownSeconds = getSlowdownSeconds();
        if (elapsedSeconds >= slowdownSeconds) {
            return cruise;
        }

        double t = Math.max(0.0, Math.min(1.0, elapsedSeconds / slowdownSeconds));
        double remaining = Math.pow(1.0 - t, 4.0);
        return cruise + ((initial - cruise) * remaining);
    }

    private double getMaxDistanceBlocks() {
        AxoTalesServerConfig.LightBook lightBook = config != null ? config.lightBook : null;
        double value = lightBook != null ? lightBook.maxDistanceBlocks : DEFAULT_MAX_DISTANCE_BLOCKS;
        return finiteOrDefault(value, DEFAULT_MAX_DISTANCE_BLOCKS);
    }

    private double getInitialSpeedBlocksPerSecond() {
        AxoTalesServerConfig.LightBook lightBook = config != null ? config.lightBook : null;
        double value = lightBook != null ? lightBook.initialSpeedBlocksPerSecond : DEFAULT_INITIAL_SPEED_BLOCKS_PER_SECOND;
        return finiteOrDefault(value, DEFAULT_INITIAL_SPEED_BLOCKS_PER_SECOND);
    }

    private double getCruiseSpeedBlocksPerSecond() {
        AxoTalesServerConfig.LightBook lightBook = config != null ? config.lightBook : null;
        double value = lightBook != null ? lightBook.cruiseSpeedBlocksPerSecond : DEFAULT_CRUISE_SPEED_BLOCKS_PER_SECOND;
        return finiteOrDefault(value, DEFAULT_CRUISE_SPEED_BLOCKS_PER_SECOND);
    }

    private double getSlowdownSeconds() {
        AxoTalesServerConfig.LightBook lightBook = config != null ? config.lightBook : null;
        double value = lightBook != null ? lightBook.slowdownSeconds : DEFAULT_SLOWDOWN_SECONDS;
        return finiteOrDefault(value, DEFAULT_SLOWDOWN_SECONDS);
    }

    private static double finiteOrDefault(double value, double defaultValue) {
        return Double.isFinite(value) ? value : defaultValue;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
