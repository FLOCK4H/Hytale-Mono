package org.example.plugin;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state for Flame Book projectile impact handling.
 */
public final class FlameBookImpactTracker {

    private final Set<UUID> entityHitProjectiles = ConcurrentHashMap.newKeySet();

    public void markEntityHit(@Nonnull UUID projectileUuid) {
        entityHitProjectiles.add(projectileUuid);
    }

    public boolean hasEntityHit(@Nonnull UUID projectileUuid) {
        return entityHitProjectiles.contains(projectileUuid);
    }

    public boolean clearEntityHit(@Nonnull UUID projectileUuid) {
        return entityHitProjectiles.remove(projectileUuid);
    }

    public void clear() {
        entityHitProjectiles.clear();
    }
}
