package org.example.plugin;

import com.hypixel.hytale.protocol.InteractionType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active Horde Book summons (owner-to-minion mapping + expiry).
 *
 * <p>State is keyed by UUID and safe to access from multiple threads, but intended to be mutated on the world thread.</p>
 */
public final class HordeBookSummonState {

    public static final class ActiveSummon {
        public final @Nonnull UUID ownerUuid;
        public final @Nonnull java.util.List<UUID> minionUuids;
        public final long castAtNanos;
        public final long expiresAtNanos;
        public final int castChainId;
        public final @Nonnull InteractionType castInteractionType;

        private ActiveSummon(
            @Nonnull UUID ownerUuid,
            @Nonnull java.util.List<UUID> minionUuids,
            long castAtNanos,
            long expiresAtNanos,
            @Nonnull InteractionType castInteractionType,
            int castChainId
        ) {
            this.ownerUuid = ownerUuid;
            this.minionUuids = java.util.List.copyOf(minionUuids);
            this.castAtNanos = castAtNanos;
            this.expiresAtNanos = expiresAtNanos;
            this.castInteractionType = castInteractionType;
            this.castChainId = castChainId;
        }
    }

    private final Map<UUID, ActiveSummon> activeByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveSummon> activeByMinion = new ConcurrentHashMap<>();

    public void activate(
        @Nonnull UUID ownerUuid,
        @Nonnull java.util.List<UUID> minionUuids,
        @Nonnull InteractionType interactionType,
        int chainId,
        long nowNanos,
        long lifetimeNanos
    ) {
        long expiresAt = nowNanos + Math.max(0L, lifetimeNanos);
        ActiveSummon active = new ActiveSummon(ownerUuid, minionUuids, nowNanos, expiresAt, interactionType, chainId);
        activeByOwner.put(ownerUuid, active);
        for (UUID minionUuid : minionUuids) {
            if (minionUuid != null) {
                activeByMinion.put(minionUuid, active);
            }
        }
    }

    public @Nullable ActiveSummon getByOwnerIfActive(@Nonnull UUID ownerUuid, long nowNanos) {
        ActiveSummon active = activeByOwner.get(ownerUuid);
        if (active == null) {
            return null;
        }
        if (nowNanos > active.expiresAtNanos) {
            activeByOwner.remove(ownerUuid);
            for (UUID minionUuid : active.minionUuids) {
                activeByMinion.remove(minionUuid);
            }
            return null;
        }
        return active;
    }

    public @Nullable ActiveSummon getByMinionIfActive(@Nonnull UUID minionUuid, long nowNanos) {
        ActiveSummon active = activeByMinion.get(minionUuid);
        if (active == null) {
            return null;
        }
        if (nowNanos > active.expiresAtNanos) {
            activeByMinion.remove(minionUuid);
            activeByOwner.remove(active.ownerUuid);
            return null;
        }
        return active;
    }

    public void clearOwner(@Nonnull UUID ownerUuid) {
        ActiveSummon active = activeByOwner.remove(ownerUuid);
        if (active != null) {
            for (UUID minionUuid : active.minionUuids) {
                activeByMinion.remove(minionUuid);
            }
        }
    }

    public void clearMinion(@Nonnull UUID minionUuid) {
        ActiveSummon active = activeByMinion.remove(minionUuid);
        if (active != null) {
            activeByOwner.remove(active.ownerUuid);
        }
    }

    public @Nonnull Collection<ActiveSummon> snapshotActive() {
        return java.util.List.copyOf(activeByOwner.values());
    }
}
