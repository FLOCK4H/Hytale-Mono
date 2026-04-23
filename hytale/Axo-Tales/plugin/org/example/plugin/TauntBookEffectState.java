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

    public static final double STACK_DAMAGE_MULTIPLIER = 1.5d;
    public static final int STACK_DAMAGE_CAP = 2000;

    public static final class ActiveTaunt {
        public final long castAtNanos;
        public final long expiresAtNanos;
        public final int castChainId;
        public final @Nonnull InteractionType castInteractionType;
        public final int stackCount;
        public final double slamDamage;
        public volatile boolean slamPending;
        public volatile boolean leftGround;

        private ActiveTaunt(
            long castAtNanos,
            long expiresAtNanos,
            @Nonnull InteractionType castInteractionType,
            int castChainId,
            int stackCount,
            double slamDamage
        ) {
            this.castAtNanos = castAtNanos;
            this.expiresAtNanos = expiresAtNanos;
            this.castInteractionType = castInteractionType;
            this.castChainId = castChainId;
            this.stackCount = Math.max(1, stackCount);
            this.slamDamage = sanitizeSlamDamage(slamDamage);
            this.slamPending = true;
            this.leftGround = false;
        }

        public int getEffectiveSlamDamage() {
            return (int) Math.min(STACK_DAMAGE_CAP, Math.round(slamDamage));
        }

        public int getGroundBreakRadiusBlocks() {
            return Math.max(0, stackCount - 1);
        }
    }

    private final Map<UUID, ActiveTaunt> activeTaunts = new ConcurrentHashMap<>();

    public @Nonnull ActiveTaunt activate(
        @Nonnull UUID playerUuid,
        @Nonnull InteractionType interactionType,
        int chainId,
        long nowNanos,
        long durationNanos,
        int baseSlamDamage
    ) {
        long expiresAt = nowNanos + Math.max(0L, durationNanos);
        ActiveTaunt next = activeTaunts.compute(
            playerUuid,
            (ignored, current) -> {
                if (canStack(current, nowNanos)) {
                    int nextStackCount = current.stackCount + 1;
                    double nextDamage = Math.min(STACK_DAMAGE_CAP, current.slamDamage * STACK_DAMAGE_MULTIPLIER);
                    ActiveTaunt stacked = new ActiveTaunt(
                        nowNanos,
                        Math.max(expiresAt, current.expiresAtNanos),
                        interactionType,
                        chainId,
                        nextStackCount,
                        nextDamage
                    );
                    stacked.leftGround = current.leftGround;
                    stacked.slamPending = current.slamPending;
                    return stacked;
                }
                return new ActiveTaunt(
                    nowNanos,
                    expiresAt,
                    interactionType,
                    chainId,
                    1,
                    sanitizeSlamDamage(baseSlamDamage)
                );
            }
        );
        return next != null ? next : new ActiveTaunt(nowNanos, expiresAt, interactionType, chainId, 1, sanitizeSlamDamage(baseSlamDamage));
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

    private static boolean canStack(@Nullable ActiveTaunt current, long nowNanos) {
        return current != null
            && current.slamPending
            && nowNanos <= current.expiresAtNanos;
    }

    private static double sanitizeSlamDamage(double damage) {
        if (!Double.isFinite(damage) || damage <= 0d) {
            return 0d;
        }
        return Math.min(STACK_DAMAGE_CAP, damage);
    }
}
