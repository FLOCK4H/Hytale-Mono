package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Queues Taunt Book slams for processing on the world thread without calling {@code Store.forEachChunk(...)} from within
 * an ECS system (which can trigger "Store is currently processing!" crashes).
 *
 * <p>Queues are tracked per {@link World} instance to avoid cross-world interference.</p>
 */
public final class TauntBookSlamQueue {

    public static final class SlamRequest {
        public final @Nonnull World world;
        public final @Nonnull Ref<EntityStore> casterRef;
        public final @Nonnull UUID casterUuid;
        public final int castChainId;
        public final @Nonnull InteractionType castInteractionType;
        public final long tauntExpiresAtNanos;
        public final double centerX;
        public final double centerY;
        public final double centerZ;
        public final int radiusBlocks;
        public final double radiusSq;
        public final int damageAmount;

        public int candidatesChecked;
        public int inRadius;
        public int damageAttempts;
        public int damagedEntities;
        public int damageClamped;
        public int damageSkipped;
        public int damageExceptions;
        public int ignoredInvalidTarget;
        public int ignoredInvalidCaster;

        public SlamRequest(
            @Nonnull World world,
            @Nonnull Ref<EntityStore> casterRef,
            @Nonnull UUID casterUuid,
            @Nonnull InteractionType castInteractionType,
            int castChainId,
            long tauntExpiresAtNanos,
            @Nonnull Vector3d center,
            int radiusBlocks,
            int damageAmount
        ) {
            this.world = world;
            this.casterRef = casterRef;
            this.casterUuid = casterUuid;
            this.castInteractionType = castInteractionType;
            this.castChainId = castChainId;
            this.tauntExpiresAtNanos = tauntExpiresAtNanos;
            this.centerX = center.x;
            this.centerY = center.y;
            this.centerZ = center.z;
            this.radiusBlocks = radiusBlocks;
            this.radiusSq = (double) radiusBlocks * (double) radiusBlocks;
            this.damageAmount = damageAmount;
        }
    }

    public static final class PerWorldQueue {
        private long activeTick = Long.MIN_VALUE;
        private long rolledOffTick = Long.MIN_VALUE;
        private List<SlamRequest> active = new ArrayList<>();
        private List<SlamRequest> rolledOff = new ArrayList<>();

        public void advanceToTick(long tick) {
            if (tick == activeTick) {
                return;
            }
            this.rolledOffTick = this.activeTick;
            List<SlamRequest> tmp = this.rolledOff;
            this.rolledOff = this.active;
            this.active = tmp;
            this.active.clear();
            this.activeTick = tick;
        }

        public void enqueue(long tick, @Nonnull SlamRequest request) {
            advanceToTick(tick);
            active.add(request);
        }

        public long getActiveTick() {
            return activeTick;
        }

        public @Nonnull List<SlamRequest> getActive() {
            return active;
        }

        public long getRolledOffTick() {
            return rolledOffTick;
        }

        public @Nonnull List<SlamRequest> getRolledOff() {
            return rolledOff;
        }

        public void clearRolledOff() {
            rolledOff.clear();
            rolledOffTick = Long.MIN_VALUE;
        }
    }

    private final Map<World, PerWorldQueue> byWorld = new ConcurrentHashMap<>();

    public @Nonnull PerWorldQueue forWorld(@Nonnull World world) {
        return byWorld.computeIfAbsent(world, ignored -> new PerWorldQueue());
    }
}

