package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * Drives Kudu Adept behavior:
 * <ul>
 *   <li>Unbonded adepts are pacified and forced-friendly to players (no targeting).</li>
 *   <li>When an Arcane Crystal Shard item drop is nearby, an adept can "pick it up" and become bonded to a player.</li>
 *   <li>Bonded adepts follow their owner and fight nearby enemies.</li>
 * </ul>
 */
public final class KuduAdeptBondSystem extends TickingSystem<EntityStore> {

    private static final String ARCANE_SHARD_ITEM_ID = "Ingredient_Crystal_Arcane";
    private static final String ADEPT_HELD_ITEM_ID = "Book_Flame_Texture";
    private static final short ADEPT_HELD_HOTBAR_SLOT = 0;

    private static final long TICK_INTERVAL_NANOS = 250_000_000L;
    private static final long DEBUG_INTERVAL_NANOS = 5_000_000_000L;

    private static final double PACIFY_FRIENDLY_SECONDS = 10.0;
    private static final double BONDED_FRIENDLY_SECONDS = 60.0;
    private static final double TARGET_HOSTILE_OVERRIDE_SECONDS = 3.0;

    private static final double PICKUP_RADIUS_BLOCKS = 2.5;
    private static final double OWNER_SEARCH_RADIUS_BLOCKS = 8.0;

    private static final double FOLLOW_RADIUS_BLOCKS = 24.0;
    private static final double ATTACK_RADIUS_BLOCKS = 24.0;
    private static final int MAX_ENTITIES_CONSIDERED = 96;

    private static final double FOLLOW_REPATH_DISTANCE_BLOCKS = 3.0;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final KuduAdeptBondState bondState;

    private final ConcurrentMap<UUID, Long> nextDebugAtNanosByAdept = new ConcurrentHashMap<>();

    private volatile long nextTickAtNanos = 0L;

    public KuduAdeptBondSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull KuduAdeptBondState bondState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.bondState = bondState;
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

            if (config == null || config.kuduAdept == null || !config.kuduAdept.enabled) {
                return;
            }

            EntityStore external = store.getExternalData();
            if (external == null) {
                return;
            }
            World world = external.getWorld();
            if (world == null) {
                return;
            }

            String roleName = config.kuduAdept.roleName != null && !config.kuduAdept.roleName.isBlank()
                ? config.kuduAdept.roleName
                : KuduAdeptSpawnerSystem.DEFAULT_ROLE_NAME;

            List<PlayerSnapshot> players = snapshotPlayers(store);
            HashMap<UUID, PlayerSnapshot> playersByUuid = new HashMap<>(players.size());
            for (PlayerSnapshot p : players) {
                if (p != null && p.uuid != null) {
                    playersByUuid.put(p.uuid, p);
                }
            }

            ArrayList<AdeptSnapshot> adepts = collectAdepts(store, roleName);
            if (adepts.isEmpty()) {
                return;
            }

            // Cleanup: remove bonds for adepts that no longer exist (despawned/removed) so state doesn't grow forever.
            HashSet<UUID> aliveAdepts = new HashSet<>(adepts.size());
            for (AdeptSnapshot adept : adepts) {
                if (adept != null && adept.uuid != null) {
                    aliveAdepts.add(adept.uuid);
                }
            }
            for (KuduAdeptBondState.BondedAdept bonded : bondState.snapshotAll()) {
                if (bonded == null) {
                    continue;
                }
                UUID adeptUuid = bonded.adeptUuid();
                if (adeptUuid != null && !aliveAdepts.contains(adeptUuid)) {
                    bondState.removeAdept(adeptUuid);
                    nextDebugAtNanosByAdept.remove(adeptUuid);
                }
            }

            // Keep the adept's held book consistent for visuals (best effort).
            for (AdeptSnapshot adept : adepts) {
                if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                    continue;
                }
                ensureAdeptHeldItem(store, nowNanos, roleName, adept);
            }

            ArrayList<ItemDropSnapshot> shardDrops = snapshotArcaneShardDrops(store);
            if (!shardDrops.isEmpty()) {
                handleShardPickups(store, nowNanos, roleName, players, playersByUuid, adepts, shardDrops);
            }

            // Build owner -> bonded adepts mapping for this tick.
            HashMap<UUID, ArrayList<AdeptSnapshot>> bondedByOwner = new HashMap<>();
            for (AdeptSnapshot adept : adepts) {
                if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                    continue;
                }
                KuduAdeptBondState.BondedAdept bonded = bondState.getByAdept(adept.uuid);
                if (bonded == null) {
                    continue;
                }
                bondedByOwner.computeIfAbsent(bonded.ownerUuid(), ignored -> new ArrayList<>()).add(adept);
            }

            // Pacify all unbonded adepts (never target players).
            for (AdeptSnapshot adept : adepts) {
                if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                    continue;
                }
                if (bondState.getByAdept(adept.uuid) != null) {
                    continue;
                }
                pacifyAdept(store, nowNanos, roleName, adept, players);
            }

            // Drive bonded adepts to follow + fight for their owner.
            for (var entry : bondedByOwner.entrySet()) {
                UUID ownerUuid = entry.getKey();
                ArrayList<AdeptSnapshot> ownerAdepts = entry.getValue();
                if (ownerUuid == null || ownerAdepts == null || ownerAdepts.isEmpty()) {
                    continue;
                }

                PlayerSnapshot owner = playersByUuid.get(ownerUuid);
                if (owner == null || owner.ref == null || !owner.ref.isValid() || owner.position == null || !owner.position.isFinite()) {
                    // Owner not present/valid: unbond these adepts so they revert to pacified behavior.
                    for (AdeptSnapshot adept : ownerAdepts) {
                        if (adept != null && adept.uuid != null) {
                            bondState.removeAdept(adept.uuid);
                        }
                    }
                    continue;
                }

                driveBondedAdepts(store, nowNanos, roleName, owner, ownerAdepts, bondedByOwner);
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduAdeptBondSystem: tick failed.", t);
        }
    }

    private void handleShardPickups(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull List<PlayerSnapshot> players,
        @Nonnull HashMap<UUID, PlayerSnapshot> playersByUuid,
        @Nonnull ArrayList<AdeptSnapshot> adepts,
        @Nonnull ArrayList<ItemDropSnapshot> shardDrops
    ) {
        double pickupRadiusSq = PICKUP_RADIUS_BLOCKS * PICKUP_RADIUS_BLOCKS;
        double ownerRadiusSq = OWNER_SEARCH_RADIUS_BLOCKS * OWNER_SEARCH_RADIUS_BLOCKS;

        for (ItemDropSnapshot drop : shardDrops) {
            if (drop == null || drop.ref == null || !drop.ref.isValid() || drop.position == null || !drop.position.isFinite()) {
                continue;
            }

            PlayerSnapshot owner = findNearestPlayer(players, drop.position, ownerRadiusSq);
            if (owner == null || owner.uuid == null) {
                continue;
            }

            AdeptSnapshot nearest = findNearestUnbondedAdept(adepts, drop.position, pickupRadiusSq);
            if (nearest == null || nearest.uuid == null || nearest.ref == null || !nearest.ref.isValid()) {
                continue;
            }

            ItemComponent itemComponent = store.getComponent(drop.ref, ItemComponent.getComponentType());
            if (itemComponent == null) {
                continue;
            }

            ItemStack stack = itemComponent.getItemStack();
            if (stack == null || !stack.isValid() || stack.isEmpty() || !ARCANE_SHARD_ITEM_ID.equals(stack.getItemId())) {
                continue;
            }

            int quantityBefore = stack.getQuantity();
            int quantityAfter = Math.max(0, quantityBefore - 1);
            boolean removedEntity = false;
            if (quantityAfter <= 0) {
                try {
                    store.removeEntity(drop.ref, RemoveReason.REMOVE);
                    removedEntity = true;
                } catch (Throwable ignored) {
                    // Best effort.
                }
            } else {
                try {
                    itemComponent.setItemStack(stack.withQuantity(quantityAfter));
                } catch (Throwable ignored) {
                    // Best effort.
                }
            }

            bondState.bond(nearest.uuid, owner.uuid, nowNanos);

            PlayerSnapshot ownerSnapshot = playersByUuid.get(owner.uuid);
            if (ownerSnapshot != null && ownerSnapshot.ref != null && ownerSnapshot.ref.isValid()) {
                trySetFollowTarget(store, nowNanos, roleName, nearest, ownerSnapshot);
            }

            debug.traceFileOnly(
                ownerSnapshot != null ? ownerSnapshot.playerRef : null,
                "KuduAdeptBond event=pickup"
                    + " roleName=" + roleName
                    + " itemId=" + ARCANE_SHARD_ITEM_ID
                    + " quantity.before=" + quantityBefore
                    + " quantity.after=" + quantityAfter
                    + " itemEntity.removed=" + removedEntity
                    + " ownerUuid=" + owner.uuid
                    + " adeptUuid=" + nearest.uuid
            );
        }
    }

    private void pacifyAdept(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nonnull List<PlayerSnapshot> players
    ) {
        NPCEntity npc = store.getComponent(adept.ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        Role role = npc.getRole();
        if (role == null || role.getMarkedEntitySupport() == null || role.getWorldSupport() == null) {
            return;
        }

        // Force-friendly to players so the template doesn't treat them as enemies.
        try {
            for (PlayerSnapshot player : players) {
                if (player == null || player.ref == null || !player.ref.isValid() || player.position == null || !player.position.isFinite()) {
                    continue;
                }
                if (adept.position != null && adept.position.isFinite()) {
                    double d2 = distSq(adept.position, player.position);
                    if (d2 > (FOLLOW_RADIUS_BLOCKS * FOLLOW_RADIUS_BLOCKS)) {
                        continue;
                    }
                }
                role.getWorldSupport().overrideAttitude(player.ref, Attitude.FRIENDLY, PACIFY_FRIENDLY_SECONDS);
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        MarkedEntitySupport marked = role.getMarkedEntitySupport();
        int slot = resolveDefaultTargetSlotIndex(marked);
        boolean cleared = false;
        try {
            if (marked.hasMarkedEntityInSlot(slot)) {
                marked.clearMarkedEntity(slot);
                role.getWorldSupport().requestNewPath();
                cleared = true;
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        if (!cleared) {
            return;
        }

        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
        if (nextDebugAt > nowNanos) {
            return;
        }
        nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);

        debug.traceFileOnly(
            null,
            "KuduAdeptPacify event=clearTarget"
                + " roleName=" + roleName
                + " adeptUuid=" + adept.uuid
        );
    }

    private void driveBondedAdepts(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull PlayerSnapshot owner,
        @Nonnull ArrayList<AdeptSnapshot> ownerAdepts,
        @Nonnull HashMap<UUID, ArrayList<AdeptSnapshot>> bondedByOwner
    ) {
        List<Ref<EntityStore>> nearby = TargetUtil.getAllEntitiesInSphere(owner.position, ATTACK_RADIUS_BLOCKS, store);
        List<Ref<EntityStore>> candidates = filterAttackCandidates(store, owner, bondedByOwner, nearby);
        if (candidates.isEmpty()) {
            for (AdeptSnapshot adept : ownerAdepts) {
                if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                    continue;
                }
                trySetFollowTarget(store, nowNanos, roleName, adept, owner);
            }
            return;
        }

        Set<Ref<EntityStore>> usedTargets = new HashSet<>();
        for (AdeptSnapshot adept : ownerAdepts) {
            if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                continue;
            }

            Ref<EntityStore> chosen = null;
            for (Ref<EntityStore> candidate : candidates) {
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

            driveBondedAdeptToTarget(store, nowNanos, roleName, owner, ownerAdepts, adept, chosen);
        }
    }

    private void driveBondedAdeptToTarget(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull PlayerSnapshot owner,
        @Nonnull ArrayList<AdeptSnapshot> ownerAdepts,
        @Nonnull AdeptSnapshot adept,
        @Nonnull Ref<EntityStore> targetRef
    ) {
        NPCEntity npc = store.getComponent(adept.ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }

        Role role = npc.getRole();
        if (role == null || role.getMarkedEntitySupport() == null || role.getWorldSupport() == null) {
            return;
        }

        try {
            role.getWorldSupport().overrideAttitude(owner.ref, Attitude.FRIENDLY, BONDED_FRIENDLY_SECONDS);
            for (AdeptSnapshot other : ownerAdepts) {
                if (other == null || other.ref == null || !other.ref.isValid() || other.ref.equals(adept.ref)) {
                    continue;
                }
                role.getWorldSupport().overrideAttitude(other.ref, Attitude.FRIENDLY, BONDED_FRIENDLY_SECONDS);
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        MarkedEntitySupport marked = role.getMarkedEntitySupport();
        Ref<EntityStore> currentTarget = marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        if (currentTarget != null && currentTarget.isValid() && currentTarget.equals(targetRef)) {
            return;
        }

        try {
            role.getWorldSupport().overrideAttitude(targetRef, Attitude.HOSTILE, TARGET_HOSTILE_OVERRIDE_SECONDS);
        } catch (Throwable ignored) {
            // Best effort.
        }

        marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, targetRef);
        role.getWorldSupport().requestNewPath();

        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
        if (nextDebugAt > nowNanos) {
            return;
        }
        nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);

        UUID targetUuid = null;
        try {
            UUIDComponent uuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());
            if (uuidComponent != null) {
                targetUuid = uuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }

        debug.traceFileOnly(
            owner.playerRef,
            "KuduAdeptBond event=retarget"
                + " roleName=" + roleName
                + " ownerUuid=" + owner.uuid
                + " adeptUuid=" + adept.uuid
                + (targetUuid != null ? " targetUuid=" + targetUuid : "")
                + " radiusBlocks=" + ATTACK_RADIUS_BLOCKS
                + " hostileOverrideSeconds=" + TARGET_HOSTILE_OVERRIDE_SECONDS
        );
    }

    private void trySetFollowTarget(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nonnull PlayerSnapshot owner
    ) {
        NPCEntity npc = store.getComponent(adept.ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        Role role = npc.getRole();
        MarkedEntitySupport marked = role != null ? role.getMarkedEntitySupport() : null;
        if (role == null || marked == null || role.getWorldSupport() == null) {
            return;
        }

        try {
            role.getWorldSupport().overrideAttitude(owner.ref, Attitude.FRIENDLY, BONDED_FRIENDLY_SECONDS);
        } catch (Throwable ignored) {
            // Best effort.
        }

        Ref<EntityStore> currentTarget = marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        boolean alreadyTargetingOwner = currentTarget != null && currentTarget.isValid() && currentTarget.equals(owner.ref);

        if (!alreadyTargetingOwner) {
            marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, owner.ref);
        }
        try {
            // Refresh the flock target even if it hasn't changed so the adept reacts quickly as the owner moves.
            marked.flockSetTarget(MarkedEntitySupport.DEFAULT_TARGET_SLOT, owner.ref, store);
        } catch (Throwable ignored) {
            // Best effort.
        }

        // Only request a new path when the owner is far enough that we expect the adept to move.
        boolean shouldRepath = !alreadyTargetingOwner;
        if (!shouldRepath && adept.position != null && adept.position.isFinite() && owner.position != null && owner.position.isFinite()) {
            double d2 = distSq(adept.position, owner.position);
            shouldRepath = d2 > (FOLLOW_REPATH_DISTANCE_BLOCKS * FOLLOW_REPATH_DISTANCE_BLOCKS);
        }
        if (shouldRepath) {
            role.getWorldSupport().requestNewPath();
            try {
                role.notifySensorMatch();
            } catch (Throwable ignored) {
                // Best effort.
            }

            long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
            if (nextDebugAt <= nowNanos) {
                nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);
                debug.traceFileOnly(
                    owner.playerRef,
                    "KuduAdeptFollow event=repath"
                        + " roleName=" + roleName
                        + " adeptUuid=" + adept.uuid
                        + " ownerUuid=" + owner.uuid
                        + " distanceThresholdBlocks=" + FOLLOW_REPATH_DISTANCE_BLOCKS
                );
            }
        }
    }

    private void ensureAdeptHeldItem(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept
    ) {
        NPCEntity npc = store.getComponent(adept.ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        Inventory inventory;
        try {
            inventory = npc.getInventory();
        } catch (Throwable ignored) {
            return;
        }
        if (inventory == null) {
            return;
        }
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) {
            return;
        }
        if (hotbar.getCapacity() <= ADEPT_HELD_HOTBAR_SLOT) {
            return;
        }

        ItemStack current = hotbar.getItemStack(ADEPT_HELD_HOTBAR_SLOT);
        boolean already = current != null && current.isValid() && !current.isEmpty() && ADEPT_HELD_ITEM_ID.equals(current.getItemId());
        boolean activeSlotAlready = inventory.getActiveHotbarSlot() == (byte) ADEPT_HELD_HOTBAR_SLOT;
        if (already && activeSlotAlready) {
            return;
        }

        try {
            if (!already) {
                hotbar.setItemStackForSlot(ADEPT_HELD_HOTBAR_SLOT, new ItemStack(ADEPT_HELD_ITEM_ID, 1));
            }
            inventory.setActiveHotbarSlot((byte) ADEPT_HELD_HOTBAR_SLOT);
            npc.invalidateEquipmentNetwork();
        } catch (Throwable ignored) {
            return;
        }

        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
        if (nextDebugAt > nowNanos) {
            return;
        }
        nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);

        debug.traceFileOnly(
            null,
            "KuduAdeptEquip event=setHeldItem"
                + " roleName=" + roleName
                + " adeptUuid=" + adept.uuid
                + " itemId=" + ADEPT_HELD_ITEM_ID
                + " slot=" + ADEPT_HELD_HOTBAR_SLOT
        );
    }

    private static @Nonnull ArrayList<AdeptSnapshot> collectAdepts(
        @Nonnull Store<EntityStore> store,
        @Nonnull String roleName
    ) {
        ArrayList<AdeptSnapshot> out = new ArrayList<>();
        Query<EntityStore> query = Query.and(
            NPCEntity.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> visitor = (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                if (npc == null) {
                    continue;
                }
                String npcRoleName = null;
                try {
                    npcRoleName = npc.getRoleName();
                } catch (Throwable ignored) {
                    // Best effort.
                }
                if (npcRoleName == null || !npcRoleName.equals(roleName)) {
                    continue;
                }
                UUIDComponent uuidComponent = chunk.getComponent(i, UUIDComponent.getComponentType());
                UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
                if (uuid == null) {
                    continue;
                }

                Vector3d pos = null;
                try {
                    Transform look = TargetUtil.getLook(ref, store);
                    pos = look != null ? look.getPosition() : null;
                } catch (Throwable ignored) {
                    // Best effort.
                }
                if (pos == null || !pos.isFinite()) {
                    TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                    pos = transform != null ? transform.getPosition() : null;
                }

                out.add(new AdeptSnapshot(uuid, ref, pos));
            }
        };
        try {
            store.forEachChunk(query, visitor);
        } catch (Throwable ignored) {
            // Best effort.
        }
        return out;
    }

    private static @Nonnull List<PlayerSnapshot> snapshotPlayers(@Nonnull Store<EntityStore> store) {
        List<PlayerSnapshot> out = new ArrayList<>();
        Query<EntityStore> query = Query.and(
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType()
        );
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> visitor = (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                PlayerRef playerRef = chunk.getComponent(i, PlayerRef.getComponentType());
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                UUID uuid = playerRef != null ? playerRef.getUuid() : null;
                Vector3d pos = transform != null ? transform.getPosition() : null;
                if (uuid == null || pos == null || !pos.isFinite()) {
                    continue;
                }
                out.add(new PlayerSnapshot(uuid, ref, playerRef, pos));
            }
        };
        try {
            store.forEachChunk(query, visitor);
        } catch (Throwable ignored) {
            // Best effort.
        }
        return out;
    }

    private static @Nonnull ArrayList<ItemDropSnapshot> snapshotArcaneShardDrops(@Nonnull Store<EntityStore> store) {
        ArrayList<ItemDropSnapshot> out = new ArrayList<>();
        Query<EntityStore> query = Query.and(
            ItemComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> visitor = (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                ItemComponent item = chunk.getComponent(i, ItemComponent.getComponentType());
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                if (item == null || transform == null) {
                    continue;
                }
                ItemStack stack = null;
                try {
                    stack = item.getItemStack();
                } catch (Throwable ignored) {
                    // Best effort.
                }
                if (stack == null || !stack.isValid() || stack.isEmpty()) {
                    continue;
                }
                if (!ARCANE_SHARD_ITEM_ID.equals(stack.getItemId())) {
                    continue;
                }
                Vector3d pos = transform.getPosition();
                if (pos == null || !pos.isFinite()) {
                    continue;
                }
                out.add(new ItemDropSnapshot(ref, pos));
                if (out.size() >= 256) {
                    return;
                }
            }
        };
        try {
            store.forEachChunk(query, visitor);
        } catch (Throwable ignored) {
            // Best effort.
        }
        return out;
    }

    private static @Nullable PlayerSnapshot findNearestPlayer(
        @Nonnull List<PlayerSnapshot> players,
        @Nonnull Vector3d position,
        double maxDistanceSq
    ) {
        PlayerSnapshot nearest = null;
        double nearestD2 = Double.POSITIVE_INFINITY;
        for (PlayerSnapshot p : players) {
            if (p == null || p.position == null || !p.position.isFinite()) {
                continue;
            }
            double d2 = distSq(position, p.position);
            if (d2 < nearestD2) {
                nearestD2 = d2;
                nearest = p;
            }
        }
        return nearest != null && nearestD2 <= maxDistanceSq ? nearest : null;
    }

    private @Nullable AdeptSnapshot findNearestUnbondedAdept(
        @Nonnull ArrayList<AdeptSnapshot> adepts,
        @Nonnull Vector3d position,
        double maxDistanceSq
    ) {
        AdeptSnapshot nearest = null;
        double nearestD2 = Double.POSITIVE_INFINITY;
        for (AdeptSnapshot a : adepts) {
            if (a == null || a.uuid == null || bondState.getByAdept(a.uuid) != null) {
                continue;
            }
            if (a.position == null || !a.position.isFinite()) {
                continue;
            }
            double d2 = distSq(position, a.position);
            if (d2 < nearestD2) {
                nearestD2 = d2;
                nearest = a;
            }
        }
        return nearest != null && nearestD2 <= maxDistanceSq ? nearest : null;
    }

    private static @Nonnull List<Ref<EntityStore>> filterAttackCandidates(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerSnapshot owner,
        @Nonnull HashMap<UUID, ArrayList<AdeptSnapshot>> bondedByOwner,
        @Nullable List<Ref<EntityStore>> nearby
    ) {
        if (nearby == null || nearby.isEmpty()) {
            return List.of();
        }

        Set<Ref<EntityStore>> bondedAdeptRefs = new HashSet<>();
        for (var entry : bondedByOwner.entrySet()) {
            ArrayList<AdeptSnapshot> list = entry.getValue();
            if (list == null) {
                continue;
            }
            for (AdeptSnapshot adept : list) {
                if (adept != null && adept.ref != null && adept.ref.isValid()) {
                    bondedAdeptRefs.add(adept.ref);
                }
            }
        }

        ArrayList<Ref<EntityStore>> candidates = new ArrayList<>();
        for (Ref<EntityStore> ref : nearby) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            if (ref.equals(owner.ref)) {
                continue;
            }
            if (bondedAdeptRefs.contains(ref)) {
                continue;
            }
            if (store.getComponent(ref, PlayerRef.getComponentType()) != null) {
                continue;
            }
            if (store.getComponent(ref, ItemComponent.getComponentType()) != null) {
                continue;
            }
            candidates.add(ref);
            if (candidates.size() >= MAX_ENTITIES_CONSIDERED) {
                break;
            }
        }
        return candidates;
    }

    private static int resolveDefaultTargetSlotIndex(@Nonnull MarkedEntitySupport marked) {
        int slots = 0;
        try {
            slots = marked.getMarkedEntitySlotCount();
        } catch (Throwable ignored) {
            return 0;
        }
        for (int i = 0; i < slots; i++) {
            try {
                String name = marked.getSlotName(i);
                if (MarkedEntitySupport.DEFAULT_TARGET_SLOT.equals(name)) {
                    return i;
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
        return 0;
    }

    private static double distSq(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private record PlayerSnapshot(
        @Nonnull UUID uuid,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull Vector3d position
    ) {
    }

    private record AdeptSnapshot(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> ref, @Nullable Vector3d position) {
    }

    private record ItemDropSnapshot(@Nonnull Ref<EntityStore> ref, @Nonnull Vector3d position) {
    }
}
