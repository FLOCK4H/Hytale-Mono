package org.example.plugin;

import com.hypixel.hytale.protocol.InteractionType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks short-lived Immunity Book effects (incoming damage immunity window).
 *
 * <p>State is keyed by player UUID and is safe to access from multiple threads, but intended to be
 * mutated on the world thread.</p>
 */
public final class ImmunityBookEffectState {

    public static final class ActiveImmunity {
        public final long castAtNanos;
        public final long expiresAtNanos;
        public final int castChainId;
        public final @Nonnull InteractionType castInteractionType;

        private ActiveImmunity(
            long castAtNanos,
            long expiresAtNanos,
            @Nonnull InteractionType castInteractionType,
            int castChainId
        ) {
            this.castAtNanos = castAtNanos;
            this.expiresAtNanos = expiresAtNanos;
            this.castInteractionType = castInteractionType;
            this.castChainId = castChainId;
        }
    }

    private final Map<UUID, ActiveImmunity> activeImmunities = new ConcurrentHashMap<>();

    public void activate(
        @Nonnull UUID playerUuid,
        @Nonnull InteractionType interactionType,
        int chainId,
        long nowNanos,
        long durationNanos
    ) {
        long expiresAt = nowNanos + Math.max(0L, durationNanos);
        activeImmunities.put(playerUuid, new ActiveImmunity(nowNanos, expiresAt, interactionType, chainId));
    }

    public @Nullable ActiveImmunity getIfActive(@Nonnull UUID playerUuid, long nowNanos) {
        ActiveImmunity state = activeImmunities.get(playerUuid);
        if (state == null) {
            return null;
        }
        if (nowNanos > state.expiresAtNanos) {
            activeImmunities.remove(playerUuid);
            return null;
        }
        return state;
    }

    public void clear(@Nonnull UUID playerUuid) {
        activeImmunities.remove(playerUuid);
    }
}

