package org.example.plugin;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Removes expired Horde Book summoned minions and cleans up orphaned state.
 */
public final class HordeBookMinionCleanupSystem extends TickingSystem<EntityStore> {

    private static final long CLEANUP_INTERVAL_NANOS = 1_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final HordeBookSummonState summonState;

    private volatile long nextCleanupAtNanos;

    public HordeBookMinionCleanupSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull HordeBookSummonState summonState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.summonState = summonState;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            long nowNanos = System.nanoTime();
            long next = nextCleanupAtNanos;
            if (next > 0 && nowNanos < next) {
                return;
            }
            nextCleanupAtNanos = nowNanos + CLEANUP_INTERVAL_NANOS;

            EntityStore external = store.getExternalData();
            if (external == null) {
                return;
            }

            for (HordeBookSummonState.ActiveSummon active : summonState.snapshotActive()) {
                if (active == null) {
                    continue;
                }

                if (nowNanos <= active.expiresAtNanos) {
                    continue;
                }

                int removedCount = 0;
                int total = active.minionUuids != null ? active.minionUuids.size() : 0;
                if (active.minionUuids != null) {
                    for (java.util.UUID minionUuid : active.minionUuids) {
                        if (minionUuid == null) {
                            continue;
                        }

                        var minionRef = external.getRefFromUUID(minionUuid);
                        if (minionRef == null || !minionRef.isValid()) {
                            continue;
                        }

                        try {
                            store.removeEntity(minionRef, RemoveReason.REMOVE);
                            removedCount++;
                        } catch (Throwable ignored) {
                            // Best effort: continue removing others.
                        }
                    }
                }

                summonState.clearOwner(active.ownerUuid);
                debug.traceFileOnly(
                    (PlayerRef) null,
                    "HordeBookMinionCleanup event=cleanup"
                        + " reason=expired"
                        + " ownerUuid=" + active.ownerUuid
                        + " minions.total=" + total
                        + " minions.removed=" + removedCount
                        + " cast.chainId=" + active.castChainId
                        + " cast.interactionType=" + active.castInteractionType
                );
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "HordeBookMinionCleanupSystem: tick failed.", t);
        }
    }
}
