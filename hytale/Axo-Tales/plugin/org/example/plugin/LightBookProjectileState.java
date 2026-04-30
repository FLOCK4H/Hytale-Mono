package org.example.plugin;

import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime flight data for Axo's Light Book projectiles.
 */
public final class LightBookProjectileState {

    private final Map<UUID, ActiveProjectile> activeProjectiles = new ConcurrentHashMap<>();

    public void register(@Nonnull UUID projectileUuid, @Nonnull Vector3d origin, @Nonnull Vector3d direction) {
        activeProjectiles.put(projectileUuid, new ActiveProjectile(origin, direction));
    }

    public @Nonnull ActiveProjectile getOrCreate(
        @Nonnull UUID projectileUuid,
        @Nonnull Vector3d observedPosition,
        @Nullable Vector3d observedVelocity
    ) {
        return activeProjectiles.computeIfAbsent(projectileUuid, ignored -> new ActiveProjectile(
            observedPosition,
            observedVelocity != null && observedVelocity.isFinite() && observedVelocity.squaredLength() > 1e-9
                ? observedVelocity
                : new Vector3d(0, 0, 1)
        ));
    }

    public void clear(@Nonnull UUID projectileUuid) {
        activeProjectiles.remove(projectileUuid);
    }

    public void clearAll() {
        activeProjectiles.clear();
    }

    public static final class ActiveProjectile {
        private final Vector3d origin;
        private final Vector3d direction;
        private double elapsedSeconds;
        private double settleReboundSecondsRemaining;
        private boolean cruiseLogged;
        private boolean settled;

        private ActiveProjectile(@Nonnull Vector3d origin, @Nonnull Vector3d direction) {
            this.origin = new Vector3d(origin);
            this.direction = normalized(direction);
        }

        public @Nonnull Vector3d origin() {
            return origin;
        }

        public @Nonnull Vector3d direction() {
            return direction;
        }

        public double elapsedSeconds() {
            return elapsedSeconds;
        }

        public void advance(double deltaSeconds) {
            if (Double.isFinite(deltaSeconds) && deltaSeconds > 0) {
                elapsedSeconds += deltaSeconds;
            }
        }

        public boolean isSettled() {
            return settled;
        }

        public boolean markSettled(double reboundSeconds) {
            if (settled) {
                return false;
            }
            settled = true;
            settleReboundSecondsRemaining = Double.isFinite(reboundSeconds) ? Math.max(0.0, reboundSeconds) : 0.0;
            return true;
        }

        public double advanceSettleRebound(double deltaSeconds) {
            if (Double.isFinite(deltaSeconds) && deltaSeconds > 0) {
                settleReboundSecondsRemaining = Math.max(0.0, settleReboundSecondsRemaining - deltaSeconds);
            }
            return settleReboundSecondsRemaining;
        }

        public boolean markCruiseLogged() {
            if (cruiseLogged) {
                return false;
            }
            cruiseLogged = true;
            return true;
        }

        private static @Nonnull Vector3d normalized(@Nonnull Vector3d direction) {
            if (!direction.isFinite() || direction.squaredLength() <= 1e-9) {
                return new Vector3d(0, 0, 1);
            }
            return new Vector3d(direction).normalize();
        }
    }
}
