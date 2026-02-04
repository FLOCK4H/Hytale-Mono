package org.example.plugin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Continuously steers Horde Book minions to attack nearby entities while never attacking their owner.
 *
 * <p>Mechanics:
 * <ul>
 *   <li>Refresh owner/minion friendly attitude overrides.</li>
 *   <li>Select nearby non-owner, non-minion targets and set {@link MarkedEntitySupport#DEFAULT_TARGET_SLOT}.</li>
 *   <li>Force a short-lived {@link Attitude#HOSTILE} override for the selected target to increase reliability.</li>
 * </ul>
 */
public final class HordeBookMinionAggroSystem extends TickingSystem<EntityStore> {

    private static final long TICK_INTERVAL_NANOS = 500_000_000L;
    private static final double AGGRO_RADIUS_BLOCKS = 24.0;
    private static final double TARGET_HOSTILE_OVERRIDE_SECONDS = 3.0;
    private static final int MAX_ENTITIES_CONSIDERED = 64;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final HordeBookSummonState summonState;
    private final ConcurrentMap<UUID, Long> nextDebugAtNanosByOwner = new ConcurrentHashMap<>();

    private volatile long nextTickAtNanos;

    public HordeBookMinionAggroSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull HordeBookSummonState summonState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.summonState = summonState;
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

            EntityStore external = store.getExternalData();
            if (external == null) {
                return;
            }

            for (HordeBookSummonState.ActiveSummon active : summonState.snapshotActive()) {
                if (active == null) {
                    continue;
                }
                if (nowNanos > active.expiresAtNanos) {
                    continue;
                }
                UUID ownerUuid = active.ownerUuid;
                if (ownerUuid == null) {
                    continue;
                }

                var ownerRef = external.getRefFromUUID(ownerUuid);
                if (ownerRef == null || !ownerRef.isValid()) {
                    summonState.clearOwner(ownerUuid);
                    continue;
                }

                Transform look = TargetUtil.getLook(ownerRef, store);
                if (look == null || look.getPosition() == null || !look.getPosition().isFinite()) {
                    continue;
                }
                Vector3d ownerPos = look.getPosition();

                List<com.hypixel.hytale.component.Ref<EntityStore>> minionRefs = new ArrayList<>();
                if (active.minionUuids != null) {
                    for (UUID minionUuid : active.minionUuids) {
                        if (minionUuid == null) {
                            continue;
                        }
                        var minionRef = external.getRefFromUUID(minionUuid);
                        if (minionRef == null || !minionRef.isValid()) {
                            continue;
                        }
                        minionRefs.add(minionRef);
                    }
                }
                if (minionRefs.isEmpty()) {
                    summonState.clearOwner(ownerUuid);
                    continue;
                }

                List<com.hypixel.hytale.component.Ref<EntityStore>> nearby = TargetUtil.getAllEntitiesInSphere(ownerPos, AGGRO_RADIUS_BLOCKS, store);
                if (nearby == null || nearby.isEmpty()) {
                    continue;
                }

                Set<com.hypixel.hytale.component.Ref<EntityStore>> minionRefSet = new HashSet<>(minionRefs);
                List<com.hypixel.hytale.component.Ref<EntityStore>> candidates = new ArrayList<>();
                for (com.hypixel.hytale.component.Ref<EntityStore> ref : nearby) {
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    if (ref.equals(ownerRef)) {
                        continue;
                    }
                    if (minionRefSet.contains(ref)) {
                        continue;
                    }
                    candidates.add(ref);
                    if (candidates.size() >= MAX_ENTITIES_CONSIDERED) {
                        break;
                    }
                }
                if (candidates.isEmpty()) {
                    continue;
                }

                Set<com.hypixel.hytale.component.Ref<EntityStore>> usedTargets = new HashSet<>();
                int changed = 0;
                List<String> targetUuids = new ArrayList<>();
                double ownerFriendlySeconds = Math.max(0, config.hordeBook.ownerFriendlySeconds);

                for (com.hypixel.hytale.component.Ref<EntityStore> minionRef : minionRefs) {
                    NPCEntity npc = store.getComponent(minionRef, NPCEntity.getComponentType());
                    if (npc == null) {
                        continue;
                    }
                    Role role = npc.getRole();
                    if (role == null || role.getMarkedEntitySupport() == null || role.getWorldSupport() == null) {
                        continue;
                    }

                    try {
                        role.getWorldSupport().overrideAttitude(ownerRef, Attitude.FRIENDLY, ownerFriendlySeconds);
                        for (com.hypixel.hytale.component.Ref<EntityStore> otherMinionRef : minionRefs) {
                            if (otherMinionRef == null || !otherMinionRef.isValid() || otherMinionRef.equals(minionRef)) {
                                continue;
                            }
                            role.getWorldSupport().overrideAttitude(otherMinionRef, Attitude.FRIENDLY, ownerFriendlySeconds);
                        }
                    } catch (Throwable ignored) {
                        // Best effort.
                    }

                    MarkedEntitySupport marked = role.getMarkedEntitySupport();
                    var currentTarget = marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
                    if (currentTarget != null && currentTarget.isValid() && !currentTarget.equals(ownerRef) && !minionRefSet.contains(currentTarget)) {
                        usedTargets.add(currentTarget);
                        continue;
                    }

                    com.hypixel.hytale.component.Ref<EntityStore> chosen = null;
                    for (var candidate : candidates) {
                        if (candidate == null || !candidate.isValid()) {
                            continue;
                        }
                        if (usedTargets.contains(candidate)) {
                            continue;
                        }
                        chosen = candidate;
                        break;
                    }
                    if (chosen == null) {
                        chosen = candidates.get(0);
                    }

                    usedTargets.add(chosen);
                    try {
                        role.getWorldSupport().overrideAttitude(chosen, Attitude.HOSTILE, TARGET_HOSTILE_OVERRIDE_SECONDS);
                    } catch (Throwable ignored) {
                        // Best effort.
                    }

                    marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, chosen);
                    role.getWorldSupport().requestNewPath();
                    changed++;

                    UUIDComponent targetUuidComponent = store.getComponent(chosen, UUIDComponent.getComponentType());
                    UUID targetUuid = targetUuidComponent != null ? targetUuidComponent.getUuid() : null;
                    if (targetUuid != null) {
                        targetUuids.add(targetUuid.toString());
                    }
                }

                if (changed <= 0) {
                    continue;
                }

                long nextDebug = nextDebugAtNanosByOwner.getOrDefault(ownerUuid, 0L);
                if (nextDebug > nowNanos) {
                    continue;
                }
                nextDebugAtNanosByOwner.put(ownerUuid, nowNanos + 2_000_000_000L);

                PlayerRef ownerPlayerRef = store.getComponent(ownerRef, PlayerRef.getComponentType());
                debug.traceFileOnly(
                    ownerPlayerRef,
                    "HordeBookAggro event=retarget"
                        + " ownerUuid=" + ownerUuid
                        + " radiusBlocks=" + AGGRO_RADIUS_BLOCKS
                        + " hostileOverrideSeconds=" + TARGET_HOSTILE_OVERRIDE_SECONDS
                        + " candidates.considered=" + candidates.size()
                        + " minions.count=" + minionRefs.size()
                        + " minions.changed=" + changed
                        + (targetUuids.isEmpty() ? "" : " targetUuids=" + targetUuids)
                        + " cast.chainId=" + active.castChainId
                        + " cast.interactionType=" + active.castInteractionType
                );
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "HordeBookMinionAggroSystem: tick failed.", t);
        }
    }
}

