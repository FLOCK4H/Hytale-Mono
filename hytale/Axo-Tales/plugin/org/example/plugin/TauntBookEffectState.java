package org.example.plugin;

import com.hypixel.hytale.protocol.InteractionType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks short-lived Taunt Book effects (fall damage immunity window + pending slam).
 *
 * <p>State is keyed by player UUID and is safe to access from multiple threads, but intended to be
 * mutated on the world thread.</p>
 */
public final class TauntBookEffectState {

    public static final class ActiveTaunt {
        public final long castAtNanos;
        public final long expiresAtNanos;
        public final int castChainId;
        public final @Nonnull InteractionType castInteractionType;
        public volatile boolean slamPending;
        public volatile boolean leftGround;

        private ActiveTaunt(
            long castAtNanos,
            long expiresAtNanos,
            @Nonnull InteractionType castInteractionType,
            int castChainId
        ) {
            this.castAtNanos = castAtNanos;
            this.expiresAtNanos = expiresAtNanos;
            this.castInteractionType = castInteractionType;
            this.castChainId = castChainId;
            this.slamPending = true;
            this.leftGround = false;
        }
    }

    private final Map<UUID, ActiveTaunt> activeTaunts = new ConcurrentHashMap<>();

    public void activate(
        @Nonnull UUID playerUuid,
        @Nonnull InteractionType interactionType,
        int chainId,
        long nowNanos,
        long durationNanos
    ) {
        long expiresAt = nowNanos + Math.max(0L, durationNanos);
        activeTaunts.put(playerUuid, new ActiveTaunt(nowNanos, expiresAt, interactionType, chainId));
    }

    public @Nullable ActiveTaunt getIfActive(@Nonnull UUID playerUuid, long nowNanos) {
        ActiveTaunt state = activeTaunts.get(playerUuid);
        if (state == null) {
            return null;
        }
        if (nowNanos > state.expiresAtNanos) {
            activeTaunts.remove(playerUuid);
            return null;
        }
        return state;
    }

    public boolean consumeSlamIfPending(@Nonnull UUID playerUuid, long nowNanos) {
        ActiveTaunt state = getIfActive(playerUuid, nowNanos);
        if (state == null) {
            return false;
        }
        if (!state.slamPending) {
            return false;
        }
        state.slamPending = false;
        return true;
    }

    public void clear(@Nonnull UUID playerUuid) {
        activeTaunts.remove(playerUuid);
    }
}
