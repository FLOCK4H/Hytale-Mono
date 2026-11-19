package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;









@SuppressWarnings("deprecation")
public final class KuduAdeptBondSystem extends TickingSystem<EntityStore> {

    private static final String ARCANE_CRYSTAL_SHARD_ITEM_ID = "Axo_Arcane_Crystal_Shards";
    private static final String ARCANE_CRYSTAL_BLOCK_ITEM_ID = "Axo_Arcane_Crystal";
    private static final String ADEPT_HELD_ITEM_ID = "Weapon_Spellbook_Fire";
    private static final String BOND_PARTICLE_SYSTEM_ID = "AxoTales_Kudu_Bond";
    private static final String BOND_FALLBACK_PARTICLE_SYSTEM_ID = "Hearts";
    private static final String PICKUP_ANIMATION_ID = "Interact";
    private static final String HEALTH_STAT_NAME = "Health";
    private static final short ADEPT_HELD_HOTBAR_SLOT = 0;

    private static final Set<String> SUPPRESSED_VANILLA_SUMMON_ROLE_IDS = Set.of("Wolf_Trork_Shaman");

    private static final int CHUNK_WIDTH_BLOCKS = ChunkUtil.SIZE;
    private static final int MAX_Y = ChunkUtil.HEIGHT_MINUS_1;
    private static final int MIN_Y = 1;

    private static final long TICK_INTERVAL_NANOS = 250_000_000L;
    private static final long DEBUG_INTERVAL_NANOS = 5_000_000_000L;
    private static final long MASTER_TARGET_TIMEOUT_NANOS = 30_000_000_000L;
    private static final long CRYSTAL_DROP_OWNER_TIMEOUT_NANOS = 10_000_000_000L;

    private static final double PACIFY_FRIENDLY_SECONDS = 10.0;
    private static final double BONDED_FRIENDLY_SECONDS = 60.0;
    private static final double TARGET_HOSTILE_OVERRIDE_SECONDS = 3.0;

    private static final double PICKUP_RADIUS_BLOCKS = 4.0;
    private static final double OWNER_SEARCH_RADIUS_BLOCKS = 8.0;
    private static final double CRYSTAL_DROP_OWNER_MATCH_RADIUS_BLOCKS = 12.0;

    private static final double FOLLOW_RADIUS_BLOCKS = 24.0;
    private static final double MASTER_TARGET_MAX_DISTANCE_FROM_OWNER_BLOCKS = 48.0;

    private static final double COMBAT_FOLLOW_START_DISTANCE_BLOCKS = 8.0;
    private static final double COMBAT_FOLLOW_STOP_DISTANCE_BLOCKS = 4.0;
    private static final double COMBAT_FOLLOW_SPEED_BLOCKS_PER_SECOND = 6.0;
    private static final double OWNER_TELEPORT_DISTANCE_BLOCKS = 30.0;
    private static final double OWNER_TELEPORT_MIN_OFFSET_BLOCKS = 3.0;
    private static final double OWNER_TELEPORT_MAX_OFFSET_BLOCKS = 5.0;
    private static final int OWNER_TELEPORT_MAX_ATTEMPTS = 16;
    private static final double SUPPRESSED_SUMMON_RADIUS_BLOCKS = 18.0;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final KuduAdeptBondState bondState;
    private final ComponentType<EntityStore, KuduAdeptBondPersistedComponent> bondPersistedComponentType;

    private final ConcurrentMap<UUID, Long> nextDebugAtNanosByAdept = new ConcurrentHashMap<>();

    private final java.util.Map<Store<EntityStore>,Long> nextTickByStore=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private volatile long nextPickupSkipDebugAtNanos = 0L;

    public KuduAdeptBondSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull KuduAdeptBondState bondState,
        @Nonnull ComponentType<EntityStore, KuduAdeptBondPersistedComponent> bondPersistedComponentType
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.bondState = bondState;
        this.bondPersistedComponentType = bondPersistedComponentType;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            long nowNanos = System.nanoTime();
            long next = nextTickByStore.getOrDefault(store,0L);
            if (next > 0 && nowNanos < next) {
                return;
            }
            nextTickByStore.put(store,nowNanos + TICK_INTERVAL_NANOS);

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

            long nowEpochMillis = System.currentTimeMillis();
            ArrayList<AdeptSnapshot> adepts = collectAdepts(store, roleName);
            syncPersistedBondState(store, nowNanos, nowEpochMillis, roleName, adepts);
            if (adepts.isEmpty()) {
                return;
            }


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
                if (adeptUuid != null && !aliveAdepts.contains(adeptUuid) && external.getRefFromUUID(adeptUuid)!=null) {
                    bondState.removeAdept(adeptUuid);
                    nextDebugAtNanosByAdept.remove(adeptUuid);
                }
            }


            for (AdeptSnapshot adept : adepts) {
                if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                    continue;
                }

                var oldHotbar=InventoryComponentAccess.hotbar(store,adept.ref);
                if(oldHotbar!=null && oldHotbar.getCapacity()>0){var oldBook=oldHotbar.getItemStack((short)0);if(!ItemStack.isEmpty(oldBook) && ADEPT_HELD_ITEM_ID.equals(oldBook.getItemId()))oldHotbar.setItemStackForSlot((short)0,ItemStack.EMPTY);}
                updateRecruitmentNameplate(store,adept.ref,bondState.getByAdept(adept.uuid)!=null);
            }

            ArrayList<ItemDropSnapshot> crystalDrops = snapshotArcaneCrystalDrops(store);
            if (!crystalDrops.isEmpty()) {
                handleCrystalPickups(store, nowNanos, roleName, players, playersByUuid, adepts, crystalDrops);
            }


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


            for (AdeptSnapshot adept : adepts) {
                if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                    continue;
                }
                if (bondState.getByAdept(adept.uuid) != null) {
                    continue;
                }
                pacifyAdept(store, nowNanos, roleName, adept, players);
            }


            for (var entry : bondedByOwner.entrySet()) {
                UUID ownerUuid = entry.getKey();
                ArrayList<AdeptSnapshot> ownerAdepts = entry.getValue();
                if (ownerUuid == null || ownerAdepts == null || ownerAdepts.isEmpty()) {
                    continue;
                }

                PlayerSnapshot owner = playersByUuid.get(ownerUuid);
                if (owner == null || owner.ref == null || !owner.ref.isValid() || owner.position == null || !owner.position.isFinite()) {
                    for (AdeptSnapshot adept : ownerAdepts) {
                        if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                            continue;
                        }
                        driveBondedAdeptWithoutOwner(store, nowNanos, roleName, adept, ownerUuid);
                    }
                    continue;
                }

                driveBondedAdepts(store, nowNanos, roleName, owner, ownerAdepts);
            }


        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduAdeptBondSystem: tick failed.", t);
        }
    }

    static void updateRecruitmentNameplate(Store<EntityStore> store,Ref<EntityStore> ref,boolean bonded){
        var type=com.hypixel.hytale.server.core.entity.nameplate.Nameplate.getComponentType();
        var current=store.getComponent(ref,type);
        String text=recruitmentNameplate(current==null?null:current.getText(),bonded);
        if(current==null)store.putComponent(ref,type,new com.hypixel.hytale.server.core.entity.nameplate.Nameplate(text));
        else if(!text.equals(current.getText()))current.setText(text);
    }
    static String recruitmentNameplate(String current,boolean bonded){
        return "Elder Mage";
    }

    private void handleCrystalPickups(
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

            AdeptSnapshot nearest = findNearestUnbondedAdept(adepts, drop.position, pickupRadiusSq);
            if (nearest == null || nearest.uuid == null || nearest.ref == null || !nearest.ref.isValid()) {
                debugPickupSkippedMaybe(nowNanos, null, roleName, "noUnbondedAdeptInRange", drop.position, null);
                continue;
            }

            ItemComponent itemComponent = store.getComponent(drop.ref, ItemComponent.getComponentType());
            if (itemComponent == null) {
                debugPickupSkippedMaybe(nowNanos, null, roleName, "missingItemComponent", drop.position, nearest.uuid);
                continue;
            }

            ItemStack stack = itemComponent.getItemStack();
            String itemId = stack != null ? stack.getItemId() : null;
            if (stack == null || !stack.isValid() || stack.isEmpty() || !isBondingItemId(itemId)) {
                debugPickupSkippedMaybe(nowNanos, null, roleName, "invalidBondingStack", drop.position, nearest.uuid);
                continue;
            }

            var offer=MageCrystalOffer.offer(store,drop.ref);
            PlayerSnapshot owner=offer==null?null:playersByUuid.get(offer.owner);
            if(offer==null || owner==null || !offer.world.equals(owner.playerRef.getWorldUuid()) || System.currentTimeMillis()>offer.expires) {
                debugPickupSkippedMaybe(nowNanos,null,roleName,"missingExactDropper",drop.position,nearest.uuid);continue;
            }
            String ownerSource="nativeDropNonce";
            KuduAdeptBondState.CrystalDropOwner dropOwner=null;

            int quantityBefore = stack.getQuantity();
            int quantityAfter = Math.max(0, quantityBefore - 1);
            boolean removedEntity = false;
            if (quantityAfter <= 0) {
                try {
                    store.removeEntity(drop.ref, RemoveReason.REMOVE);
                    removedEntity = true;
                } catch (Throwable ignored) {

                }
            } else {
                try {
                    itemComponent.setItemStack(stack.withQuantity(quantityAfter));itemComponent.setMergeRadius(0);
                } catch (Throwable ignored) {

                }
            }

            bondState.bond(nearest.uuid, owner.uuid, nowNanos);
            persistBondComponentMaybe(
                store,
                nowNanos,
                roleName,
                nearest,
                owner.uuid,
                nowEpochMillis(),
                owner.playerRef,
                "pickup"
            );

            PlayerSnapshot ownerSnapshot = playersByUuid.get(owner.uuid);
            BondFeedbackResult feedback = playBondPickupFeedback(store, nearest, ownerSnapshot, drop.position);

            debug.traceFileOnly(
                ownerSnapshot != null ? ownerSnapshot.playerRef : null,
                "KuduAdeptBond event=pickup"
                    + " roleName=" + roleName
                    + " itemId=" + itemId
                    + " acceptedIds=" + ARCANE_CRYSTAL_SHARD_ITEM_ID + "," + ARCANE_CRYSTAL_BLOCK_ITEM_ID
                    + " quantity.before=" + quantityBefore
                    + " quantity.after=" + quantityAfter
                    + " itemEntity.removed=" + removedEntity
                    + " ownerUuid=" + owner.uuid
                    + " owner.source=" + ownerSource
                    + (dropOwner != null
                        ? " dropOwner.uuid=" + dropOwner.ownerUuid()
                            + " dropOwner.itemId=" + dropOwner.itemId()
                            + " dropOwner.ageSeconds=" + ((nowNanos - dropOwner.droppedAtNanos()) / 1_000_000_000.0)
                        : " dropOwner.matched=false")
                    + " adeptUuid=" + nearest.uuid
                    + " particle.systemId=" + BOND_PARTICLE_SYSTEM_ID
                    + " particle.fallbackSystemId=" + BOND_FALLBACK_PARTICLE_SYSTEM_ID
                    + " particle.spawned=" + feedback.particleSpawned
                    + " particle.bursts=" + feedback.particleBursts
                    + " particle.reason=" + feedback.particleReason
                    + " animation.id=" + PICKUP_ANIMATION_ID
                    + " animation.played=" + feedback.animationPlayed
                    + " animation.count=" + feedback.animationCount
                    + " animation.reason=" + feedback.animationReason
            );
        }
    }

    private void syncPersistedBondState(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        long nowEpochMillis,
        @Nonnull String roleName,
        @Nonnull List<AdeptSnapshot> adepts
    ) {
        for (AdeptSnapshot adept : adepts) {
            if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                continue;
            }

            KuduAdeptBondPersistedComponent persisted = null;
            try {
                persisted = store.getComponent(adept.ref, bondPersistedComponentType);
            } catch (Throwable ignored) {

            }

            KuduAdeptBondState.BondedAdept current = bondState.getByAdept(adept.uuid);
            UUID persistedOwnerUuid = persisted != null && persisted.hasOwnerUuid() ? persisted.getOwnerUuid() : null;
            if (persistedOwnerUuid != null) {
                if (current == null || !persistedOwnerUuid.equals(current.ownerUuid())) {
                    bondState.bond(adept.uuid, persistedOwnerUuid, nowNanos);
                    debug.traceFileOnly(
                        null,
                        "KuduAdeptBond event=hydrate"
                            + " roleName=" + roleName
                            + " source=entityComponent"
                            + " adeptUuid=" + adept.uuid
                            + " ownerUuid=" + persistedOwnerUuid
                            + " bondedAtEpochMillis=" + persisted.getBondedAtEpochMillis()
                    );
                }
                continue;
            }

            if (current == null || current.ownerUuid() == null) {
                continue;
            }

            persistBondComponentMaybe(
                store,
                nowNanos,
                roleName,
                adept,
                current.ownerUuid(),
                nowEpochMillis,
                null,
                "backfill"
            );
        }
    }

    private void persistBondComponentMaybe(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nonnull UUID ownerUuid,
        long bondedAtEpochMillis,
        @Nullable PlayerRef ownerPlayerRef,
        @Nonnull String source
    ) {
        KuduAdeptBondPersistedComponent existing = null;
        try {
            existing = store.getComponent(adept.ref, bondPersistedComponentType);
        } catch (Throwable ignored) {

        }

        long persistedBondedAtEpochMillis = bondedAtEpochMillis > 0 ? bondedAtEpochMillis : nowEpochMillis();
        if(existing!=null && existing.hasOwnerUuid() && !ownerUuid.equals(existing.getOwnerUuid()))return;
        if (existing != null && ownerUuid.equals(existing.getOwnerUuid())) {
            if (existing.getBondedAtEpochMillis() <= 0 && persistedBondedAtEpochMillis > 0) {
                existing.setBondedAtEpochMillis(persistedBondedAtEpochMillis);
                store.putComponent(adept.ref, bondPersistedComponentType, existing);
                SoulSystem.dirty(store,adept.ref);
            }
            return;
        }

        store.putComponent(
            adept.ref,
            bondPersistedComponentType,
            new KuduAdeptBondPersistedComponent(ownerUuid, persistedBondedAtEpochMillis)
        );
        SoulSystem.dirty(store,adept.ref);
        CompanionLoyalty.retain(store,adept.ref);

        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
        if (nextDebugAt > nowNanos) {
            return;
        }
        nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);

        debug.traceFileOnly(
            ownerPlayerRef,
            "KuduAdeptBond event=persisted"
                + " roleName=" + roleName
                + " source=" + source
                + " adeptUuid=" + adept.uuid
                + " ownerUuid=" + ownerUuid
                + " bondedAtEpochMillis=" + persistedBondedAtEpochMillis
        );
    }

    private void debugPickupSkippedMaybe(
        long nowNanos,
        @Nullable PlayerRef playerRef,
        @Nonnull String roleName,
        @Nonnull String reason,
        @Nullable Vector3d dropPosition,
        @Nullable UUID adeptUuid
    ) {
        if (adeptUuid != null) {
            long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adeptUuid, 0L);
            if (nextDebugAt > nowNanos) {
                return;
            }
            nextDebugAtNanosByAdept.put(adeptUuid, nowNanos + DEBUG_INTERVAL_NANOS);
        } else {
            long nextDebugAt = nextPickupSkipDebugAtNanos;
            if (nextDebugAt > nowNanos) {
                return;
            }
            nextPickupSkipDebugAtNanos = nowNanos + DEBUG_INTERVAL_NANOS;
        }

        debug.traceFileOnly(
            playerRef,
            "KuduAdeptBond event=pickupSkipped"
                + " reason=" + reason
                + " roleName=" + roleName
                + " pickupRadiusBlocks=" + PICKUP_RADIUS_BLOCKS
                + (adeptUuid != null ? " adeptUuid=" + adeptUuid : "")
                + (dropPosition != null && dropPosition.isFinite()
                    ? " dropPosition=" + dropPosition.x + "," + dropPosition.y + "," + dropPosition.z
                    : "")
        );
    }

    private @Nonnull BondFeedbackResult playBondPickupFeedback(
        @Nonnull Store<EntityStore> store,
        @Nonnull AdeptSnapshot adept,
        @Nullable PlayerSnapshot owner,
        @Nullable Vector3d dropPosition
    ) {
        int particleBursts = 0;
        String particleReason = "spawned";

        if (dropPosition != null && dropPosition.isFinite()) {
            particleBursts += spawnBondParticles(store, new Vector3d(dropPosition.x, dropPosition.y + 0.45, dropPosition.z));
        }
        if (adept.position != null && adept.position.isFinite()) {
            particleBursts += spawnBondParticles(store, new Vector3d(adept.position.x, adept.position.y + 1.1, adept.position.z));
        }
        if (owner != null && owner.position != null && owner.position.isFinite()) {
            particleBursts += spawnBondParticles(store, new Vector3d(owner.position.x, owner.position.y + 1.1, owner.position.z));
        }
        if (particleBursts <= 0) {
            particleReason = "noValidPositionsOrSpawnException";
        }

        int animationCount = 0;
        String animationReason = "skipped.noNpc";
        try {
            NPCEntity npc = store.getComponent(adept.ref, NPCEntity.getComponentType());
            if (npc != null) {
                npc.playAnimation(adept.ref, AnimationSlot.Status, PICKUP_ANIMATION_ID, store);
                animationCount++;
                npc.playAnimation(adept.ref, AnimationSlot.Action, PICKUP_ANIMATION_ID, store);
                animationCount++;
                animationReason = "played.statusAndAction";
            }
        } catch (Throwable ignored) {
            animationReason = "playException";
        }

        return new BondFeedbackResult(particleBursts > 0, particleBursts, particleReason, animationCount > 0, animationCount, animationReason);
    }

    private int spawnBondParticles(@Nonnull Store<EntityStore> store, @Nonnull Vector3d position) {
        if (!position.isFinite()) {
            return 0;
        }
        int spawned = 0;
        try {
            ParticleUtil.spawnParticleEffect(BOND_PARTICLE_SYSTEM_ID, position, store);
            spawned++;
        } catch (Throwable ignored) {

        }
        try {
            ParticleUtil.spawnParticleEffect(BOND_FALLBACK_PARTICLE_SYSTEM_ID, position, store);
            spawned++;
        } catch (Throwable ignored) {

        }
        return spawned;
    }


    private void pacifyAdept(Store<EntityStore> store,long now,String roleName,AdeptSnapshot adept,List<PlayerSnapshot> players) {
        var target=nearestWild(store,adept.ref,adept.position,18);
        setNativeTask(store,adept.ref,target,target==null?"Roam":"Combat.Attack");
    }

    static void setNativeTask(Store<EntityStore> store,Ref<EntityStore> ref,Ref<EntityStore> target,String desired) {
        var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc==null || npc.getRole()==null)return;
        var marked=MarkedEntitySupport.get(ref,store);var machine=StateSupport.get(ref,store);var world=WorldSupport.get(ref,store);
        if(marked==null || machine==null || world==null)return;
        boolean changed=!java.util.Objects.equals(marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT),target);
        if(changed){if(target==null)marked.clearMarkedEntity(resolveDefaultTargetSlotIndex(marked));else marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,target);}
        String current=machine.getStateName();boolean same=desired.equals(current) || current!=null && current.startsWith(desired+".");
        if(!same){int dot=desired.indexOf('.');machine.setState(ref,dot<0?desired:desired.substring(0,dot),dot<0?null:desired.substring(dot+1),store);changed=true;}
        if(changed){npc.getRole().clearOnce(ref,store);world.requestNewPath();npc.getRole().acquireExecutionSupport(ref,store).notifySensorMatch();}
    }

    private Ref<EntityStore> nearestWild(Store<EntityStore> store,Ref<EntityStore> self,Vector3d centre,double range) {
        var marked=MarkedEntitySupport.get(self,store);var previous=marked==null?null:marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        var before=previous!=null && previous.isValid()?store.getComponent(previous,TransformComponent.getComponentType()):null;
        if(CompanionLoyalty.wildTarget(store,previous) && before!=null && before.getPosition().distanceSquared(centre)<=24*24)return previous;
        var best=new java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>>();double[] distance={range*range};
        store.forEachChunk(Query.and(NPCEntity.getComponentType(),TransformComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var ref=chunk.getReferenceTo(i);if(ref.equals(self) || !CompanionLoyalty.wildTarget(buffer,ref))continue;
                double d=chunk.getComponent(i,TransformComponent.getComponentType()).getPosition().distanceSquared(centre);
                if(d<distance[0]){best.set(ref);distance[0]=d;}
            }
        });return best.get();
    }

    private void driveBondedAdepts(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull PlayerSnapshot owner,
        @Nonnull ArrayList<AdeptSnapshot> ownerAdepts
    ) {
        Ref<EntityStore> targetRef = resolveCommandedTarget(store, nowNanos, roleName, owner);
        if(targetRef==null)targetRef=nearestHostile(store,owner);
        if (targetRef == null || !targetRef.isValid()) {
            for (AdeptSnapshot adept : ownerAdepts) {
                if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                    continue;
                }
                driveBondedAdeptIdle(store, nowNanos, roleName, adept, owner);
            }
            return;
        }

        for (AdeptSnapshot adept : ownerAdepts) {
            if (adept == null || adept.uuid == null || adept.ref == null || !adept.ref.isValid()) {
                continue;
            }
            teleportNearOwnerIfFar(store, nowNanos, roleName, adept, owner);
            driveBondedAdeptToTarget(store, nowNanos, roleName, owner, ownerAdepts, adept, targetRef);
        }
    }

    private void driveBondedAdeptIdle(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nonnull PlayerSnapshot owner
    ) {
        if(teleportNearOwnerIfFar(store,nowNanos,roleName,adept,owner))return;
        NPCEntity npc=store.getComponent(adept.ref,NPCEntity.getComponentType());
        if(npc==null || npc.getRole()==null)return;
        npc.setLeashPoint(new Vector3d(owner.position));
        var marked=MarkedEntitySupport.get(adept.ref,store);
        var previous=marked==null?null:marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        double distance=adept.position.distanceSquared(owner.position);
        boolean follow=distance>12*12 || owner.ref.equals(previous) && distance>6*6;
        setNativeTask(store,adept.ref,follow?owner.ref:null,follow?"Idle":"Roam");
    }

    private void driveBondedAdeptWithoutOwner(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nonnull UUID ownerUuid
    ) {
        clearDefaultTarget(store, nowNanos, roleName, adept, null, ownerUuid, "ownerOffline");
        setNativeTask(store,adept.ref,null,"Roam");
    }

    private void clearDefaultTarget(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nullable PlayerRef ownerPlayerRef,
        @Nullable UUID ownerUuid,
        @Nonnull String reason
    ) {
        NPCEntity npc = store.getComponent(adept.ref, NPCEntity.getComponentType());
        Role role = npc != null ? npc.getRole() : null;
        MarkedEntitySupport marked = role != null ? MarkedEntitySupport.get(adept.ref, store) : null;
        if (role == null || marked == null || WorldSupport.get(adept.ref, store) == null) {
            return;
        }

        int slot = resolveDefaultTargetSlotIndex(marked);
        boolean cleared = false;
        try {
            if (marked.hasMarkedEntityInSlot(slot)) {
                marked.clearMarkedEntity(slot);
                WorldSupport.get(adept.ref, store).requestNewPath();
                role.acquireExecutionSupport(adept.ref, store).notifySensorMatch();
                cleared = true;
            }
        } catch (Throwable ignored) {

        }
        if (!cleared) {
            return;
        }

        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
        if (nextDebugAt <= nowNanos) {
            nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);
            debug.traceFileOnly(
                ownerPlayerRef,
                "KuduAdeptIdle event=clearTarget"
                    + " reason=" + reason
                    + " roleName=" + roleName
                    + " adeptUuid=" + adept.uuid
                    + (ownerUuid != null ? " ownerUuid=" + ownerUuid : "")
            );
        }
    }

    private boolean forceIdleWanderState(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nullable PlayerRef ownerPlayerRef,
        @Nullable UUID ownerUuid
    ) {
        NPCEntity npc = store.getComponent(adept.ref, NPCEntity.getComponentType());
        Role role = npc != null ? npc.getRole() : null;
        StateSupport stateSupport = role != null ? StateSupport.get(adept.ref, store) : null;
        if (role == null || stateSupport == null || WorldSupport.get(adept.ref, store) == null) {
            return false;
        }

        String before = null;
        try {
            before = stateSupport.getStateName();
        } catch (Throwable ignored) {

        }

        if (before != null && before.startsWith("Idle.")) {
            return false;
        }

        String desiredParentState = "Idle";
        String desiredSubState = "Default";
        String desiredStateName = desiredParentState + "." + desiredSubState;
        if (desiredStateName.equals(before)) {
            return false;
        }

        try {
            stateSupport.setState(adept.ref, desiredParentState, desiredSubState, store);
            role.clearOnce(adept.ref, store);
            WorldSupport.get(adept.ref, store).requestNewPath();
            role.acquireExecutionSupport(adept.ref, store).notifySensorMatch();
        } catch (Throwable ignored) {
            debug.traceFileOnly(
                ownerPlayerRef,
                "KuduAdeptIdle event=forceState"
                    + " decision=failed"
                    + " roleName=" + roleName
                    + " adeptUuid=" + adept.uuid
                    + (ownerUuid != null ? " ownerUuid=" + ownerUuid : "")
                    + " state.desired=" + desiredStateName
                    + (before != null ? " state.before=" + before : "")
            );
            return false;
        }

        String after = null;
        try {
            after = stateSupport.getStateName();
        } catch (Throwable ignored) {

        }

        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
        if (nextDebugAt <= nowNanos) {
            nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);
            debug.traceFileOnly(
                ownerPlayerRef,
                "KuduAdeptIdle event=forceState"
                    + " decision=wander"
                    + " roleName=" + roleName
                    + " adeptUuid=" + adept.uuid
                    + (ownerUuid != null ? " ownerUuid=" + ownerUuid : "")
                    + " state.desired=" + desiredStateName
                    + (before != null ? " state.before=" + before : "")
                    + (after != null ? " state.after=" + after : "")
                    + " behavior=nativeOwnerFollow"
            );
        }
        return true;
    }

    private @Nullable Ref<EntityStore> resolveCommandedTarget(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull PlayerSnapshot owner
    ) {
        KuduAdeptBondState.OwnerTarget ownerTarget = bondState.getOwnerTarget(owner.uuid);
        if (ownerTarget == null || ownerTarget.targetUuid() == null) {
            return null;
        }

        UUID targetUuid = ownerTarget.targetUuid();
        if (ownerTarget.markedAtNanos() > 0 && nowNanos - ownerTarget.markedAtNanos() > MASTER_TARGET_TIMEOUT_NANOS) {
            clearCommandedTarget(owner, targetUuid, roleName, "expired");
            return null;
        }

        EntityStore external = store.getExternalData();
        Ref<EntityStore> targetRef = external != null ? external.getRefFromUUID(targetUuid) : null;
        if (targetRef == null || !targetRef.isValid()) {
            clearCommandedTarget(owner, targetUuid, roleName, "invalidRef");
            return null;
        }
        if (!CompanionLoyalty.wildTarget(store,targetRef)) {
            clearCommandedTarget(owner,targetUuid,roleName,"playerOrPet");return null;
        }
        if (targetRef.equals(owner.ref)) {
            clearCommandedTarget(owner, targetUuid, roleName, "owner");
            return null;
        }
        if (store.getComponent(targetRef, PlayerRef.getComponentType()) != null) {
            clearCommandedTarget(owner, targetUuid, roleName, "playerTarget");
            return null;
        }
        if (store.getComponent(targetRef, ItemComponent.getComponentType()) != null) {
            clearCommandedTarget(owner, targetUuid, roleName, "itemTarget");
            return null;
        }
        if (bondState.getByAdept(targetUuid) != null) {
            clearCommandedTarget(owner, targetUuid, roleName, "bondedAdeptTarget");
            return null;
        }
        if (isDeadOrDying(store, targetRef)) {
            clearCommandedTarget(owner, targetUuid, roleName, "dead");
            return null;
        }

        Vector3d targetPos = getEntityPosition(store, targetRef);
        if (targetPos == null || !targetPos.isFinite() || owner.position == null || !owner.position.isFinite()) {
            return null;
        }
        double maxDistanceSq = MASTER_TARGET_MAX_DISTANCE_FROM_OWNER_BLOCKS * MASTER_TARGET_MAX_DISTANCE_FROM_OWNER_BLOCKS;
        if (distSq(owner.position, targetPos) > maxDistanceSq) {
            return null;
        }
        return targetRef;
    }


    private @Nullable Ref<EntityStore> nearestHostile(Store<EntityStore> store,PlayerSnapshot owner) {
        var ref=nearestWild(store,owner.ref,owner.position,18);
        if(ref!=null){var id=store.getComponent(ref,UUIDComponent.getComponentType());if(id!=null)bondState.recordOwnerTarget(owner.uuid,id.getUuid(),System.nanoTime());}
        return ref;
    }

    private void clearCommandedTarget(
        @Nonnull PlayerSnapshot owner,
        @Nonnull UUID targetUuid,
        @Nonnull String roleName,
        @Nonnull String reason
    ) {
        bondState.clearOwnerTargetIfMatches(owner.uuid, targetUuid);
        debug.traceFileOnly(
            owner.playerRef,
            "KuduAdeptBond event=targetCleared"
                + " reason=" + reason
                + " roleName=" + roleName
                + " ownerUuid=" + owner.uuid
                + " targetUuid=" + targetUuid
        );
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
        if (role == null || MarkedEntitySupport.get(adept.ref, store) == null || WorldSupport.get(adept.ref, store) == null) {
            return;
        }

        trySetCombatTarget(store, nowNanos, roleName, adept, owner, targetRef);
        boolean forcedCombatAttack = forceCombatAttackState(store, nowNanos, roleName, adept, owner.playerRef);
        boolean combatFollowApplied = false;

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

        }

        debug.traceFileOnly(
            owner.playerRef,
            "KuduAdeptBond event=retarget"
                + " roleName=" + roleName
                + " ownerUuid=" + owner.uuid
                + " adeptUuid=" + adept.uuid
                + (targetUuid != null ? " targetUuid=" + targetUuid : "")
                + " targetSource=masterDamage"
                + " targetTimeoutSeconds=" + (MASTER_TARGET_TIMEOUT_NANOS / 1_000_000_000.0)
                + " attackMode=mageRanged"
                + " idleBehavior=nativeOwnerFollow"
                + " combatFollow=nativeEnemyStandoff"
                + " combatFollowApplied=" + combatFollowApplied
                + " combatFollowStartDistanceBlocks=" + COMBAT_FOLLOW_START_DISTANCE_BLOCKS
                + " combatFollowStopDistanceBlocks=" + COMBAT_FOLLOW_STOP_DISTANCE_BLOCKS
                + " forcedCombatAttack=" + forcedCombatAttack
        );
    }

    private boolean applyCombatFollowVelocity(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nonnull PlayerSnapshot owner,
        @Nonnull Ref<EntityStore> targetRef
    ) {
        if (adept.position == null || !adept.position.isFinite() || owner.position == null || !owner.position.isFinite()) {
            return false;
        }

        double dx = owner.position.x - adept.position.x;
        double dz = owner.position.z - adept.position.z;
        double horizontalDistanceSq = dx * dx + dz * dz;
        double startSq = COMBAT_FOLLOW_START_DISTANCE_BLOCKS * COMBAT_FOLLOW_START_DISTANCE_BLOCKS;
        if (horizontalDistanceSq <= startSq) {
            return false;
        }

        double horizontalDistance = Math.sqrt(horizontalDistanceSq);
        if (horizontalDistance <= COMBAT_FOLLOW_STOP_DISTANCE_BLOCKS) {
            return false;
        }

        Velocity velocity = store.ensureAndGetComponent(adept.ref, Velocity.getComponentType());
        if (velocity == null) {
            return false;
        }

        Vector3d currentVelocity = velocity.getVelocity();
        double vy = currentVelocity != null && Double.isFinite(currentVelocity.y) ? currentVelocity.y : 0.0;
        Vector3d followVelocity = new Vector3d(
            (dx / horizontalDistance) * COMBAT_FOLLOW_SPEED_BLOCKS_PER_SECOND,
            vy,
            (dz / horizontalDistance) * COMBAT_FOLLOW_SPEED_BLOCKS_PER_SECOND
        );
        if (!followVelocity.isFinite()) {
            return false;
        }

        try {
            velocity.addInstruction(followVelocity, null, ChangeVelocityType.Set);
        } catch (Throwable t) {
            errors.report(owner.playerRef, "KuduAdeptBondSystem: failed to apply combat follow velocity.", t);
            return false;
        }

        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
        if (nextDebugAt <= nowNanos) {
            nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);
            UUID targetUuid = getUuid(store, targetRef);
            debug.traceFileOnly(
                owner.playerRef,
                "KuduAdeptCombatFollow event=velocity"
                    + " roleName=" + roleName
                    + " adeptUuid=" + adept.uuid
                    + " ownerUuid=" + owner.uuid
                    + (targetUuid != null ? " targetUuid=" + targetUuid : "")
                    + " distanceBlocks=" + String.format(Locale.ROOT, "%.2f", horizontalDistance)
                    + " startDistanceBlocks=" + COMBAT_FOLLOW_START_DISTANCE_BLOCKS
                    + " stopDistanceBlocks=" + COMBAT_FOLLOW_STOP_DISTANCE_BLOCKS
                    + " speedBlocksPerSecond=" + COMBAT_FOLLOW_SPEED_BLOCKS_PER_SECOND
                    + " velocity=" + String.format(Locale.ROOT, "%.2f,%.2f,%.2f", followVelocity.x, followVelocity.y, followVelocity.z)
            );
        }
        return true;
    }

    private boolean forceCombatAttackState(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nullable PlayerRef ownerPlayerRef
    ) {
        NPCEntity npc = store.getComponent(adept.ref, NPCEntity.getComponentType());
        Role role = npc != null ? npc.getRole() : null;
        StateSupport stateSupport = role != null ? StateSupport.get(adept.ref, store) : null;
        if (role == null || stateSupport == null) {
            return false;
        }

        String before = null;
        try {
            before = stateSupport.getStateName();
        } catch (Throwable ignored) {

        }
        if ("Combat.Attack".equals(before)) {
            return false;
        }

        try {
            stateSupport.setState(adept.ref, "Combat", "Attack", store);
            role.clearOnce(adept.ref, store);
            role.acquireExecutionSupport(adept.ref, store).notifySensorMatch();
        } catch (Throwable ignored) {
            debug.traceFileOnly(
                ownerPlayerRef,
                "KuduAdeptMode event=state"
                    + " decision=failed"
                    + " roleName=" + roleName
                    + " adeptUuid=" + adept.uuid
                    + " state.before=" + before
            );
            return false;
        }

        String after = null;
        try {
            after = stateSupport.getStateName();
        } catch (Throwable ignored) {

        }

        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
        if (nextDebugAt <= nowNanos) {
            nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);
            debug.traceFileOnly(
                ownerPlayerRef,
                "KuduAdeptMode event=state"
                    + " decision=forced"
                    + " roleName=" + roleName
                    + " adeptUuid=" + adept.uuid
                    + " state.before=" + before
                    + (after != null ? " state.after=" + after : "")
            );
        }
        return true;
    }

    private static boolean isInheritedSummonState(@Nonnull String stateName) {
        String s = stateName.trim();
        return "Combat.Summon".equals(s)
            || "Combat.CastingEnd".equals(s)
            || "Combat.Default".equals(s)
            || "Combat.Flee".equals(s)
            || "FleeTimed".equals(s);
    }

    private boolean teleportNearOwnerIfFar(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nonnull PlayerSnapshot owner
    ) {
        if (adept.position == null || !adept.position.isFinite() || owner.position == null || !owner.position.isFinite()) {
            return false;
        }
        double distanceSq = distSq(adept.position, owner.position);
        double thresholdSq = OWNER_TELEPORT_DISTANCE_BLOCKS * OWNER_TELEPORT_DISTANCE_BLOCKS;
        if (distanceSq <= thresholdSq) {
            return false;
        }

        EntityStore external = store.getExternalData();
        World world = external != null ? external.getWorld() : null;
        if (world == null) {
            return false;
        }

        Vector3d destination = findTeleportDestinationNearOwner(world, owner.position);
        if (destination == null || !destination.isFinite()) {
            debug.traceFileOnly(
                owner.playerRef,
                "KuduAdeptFollow event=teleportSkipped"
                    + " reason=noSafeDestination"
                    + " roleName=" + roleName
                    + " adeptUuid=" + adept.uuid
                    + " ownerUuid=" + owner.uuid
                    + " distanceBlocks=" + Math.sqrt(distanceSq)
                    + " thresholdBlocks=" + OWNER_TELEPORT_DISTANCE_BLOCKS
            );
            return false;
        }

        try {
            TransformComponent transform = store.getComponent(adept.ref, TransformComponent.getComponentType());
            Rotation3f rotation = transform != null && transform.getRotation() != null ? transform.getRotation() : new Rotation3f();
            store.addComponent(adept.ref, Teleport.getComponentType(), Teleport.createExact(destination, rotation));
            debug.traceFileOnly(
                owner.playerRef,
                "KuduAdeptFollow event=teleportToOwner"
                    + " roleName=" + roleName
                    + " adeptUuid=" + adept.uuid
                    + " ownerUuid=" + owner.uuid
                    + " distanceBlocks=" + Math.sqrt(distanceSq)
                    + " thresholdBlocks=" + OWNER_TELEPORT_DISTANCE_BLOCKS
                    + " destination=" + destination.x + "," + destination.y + "," + destination.z
            );
            return true;
        } catch (Throwable t) {
            errors.report(owner.playerRef,"Elder Mage safe catch-up failed; original retained",t);return false;
        }
    }

    private @Nullable Vector3d findTeleportDestinationNearOwner(@Nonnull World world, @Nonnull Vector3d ownerPosition) {
        for (int attempt = 0; attempt < OWNER_TELEPORT_MAX_ATTEMPTS; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
            double radius = ThreadLocalRandom.current().nextDouble(OWNER_TELEPORT_MIN_OFFSET_BLOCKS, OWNER_TELEPORT_MAX_OFFSET_BLOCKS + 0.0001);
            int x = (int) Math.floor(ownerPosition.x + Math.cos(angle) * radius);
            int z = (int) Math.floor(ownerPosition.z + Math.sin(angle) * radius);

            WorldBlocks.Column chunk = getChunkForPosition(world, x, z);
            if (chunk == null) {
                continue;
            }
            int localX = x - (chunk.getX() * CHUNK_WIDTH_BLOCKS);
            int localZ = z - (chunk.getZ() * CHUNK_WIDTH_BLOCKS);
            int y = clampY(chunk.getHeight(localX, localZ) + 1);
            y = findAirColumn(chunk, localX, y, localZ);
            if (y < MIN_Y) {
                continue;
            }
            return new Vector3d(x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private @Nullable WorldBlocks.Column getChunkForPosition(@Nonnull World world, int x, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldBlocks.Column chunk = WorldBlocks.loaded(world, chunkIndex);
        return chunk;
    }

    private void suppressInheritedMageSummons(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull ArrayList<AdeptSnapshot> adepts
    ) {
        if (adepts.isEmpty()) {
            return;
        }
        ArrayList<SuppressedSummonSnapshot> summons = collectSuppressedSummons(store);
        if (summons.isEmpty()) {
            return;
        }

        double radiusSq = SUPPRESSED_SUMMON_RADIUS_BLOCKS * SUPPRESSED_SUMMON_RADIUS_BLOCKS;
        HashMap<UUID, AdeptSnapshot> bondedAdeptsByUuid = new HashMap<>();
        for (AdeptSnapshot adept : adepts) {
            if (adept == null || adept.uuid == null || adept.position == null || !adept.position.isFinite()) {
                continue;
            }
            if (bondState.getByAdept(adept.uuid) == null) {
                continue;
            }
            bondedAdeptsByUuid.put(adept.uuid, adept);
        }
        if (bondedAdeptsByUuid.isEmpty()) {
            return;
        }

        for (SuppressedSummonSnapshot summon : summons) {
            if (summon == null || summon.ref == null || !summon.ref.isValid() || summon.position == null || !summon.position.isFinite()) {
                continue;
            }

            AdeptSnapshot nearestAdept = null;
            double nearestDistanceSq = Double.POSITIVE_INFINITY;
            for (AdeptSnapshot adept : bondedAdeptsByUuid.values()) {
                double d2 = distSq(adept.position, summon.position);
                if (d2 < nearestDistanceSq) {
                    nearestDistanceSq = d2;
                    nearestAdept = adept;
                }
            }
            if (nearestAdept == null || nearestDistanceSq > radiusSq) {
                continue;
            }

            KuduAdeptBondState.BondedAdept bonded = bondState.getByAdept(nearestAdept.uuid);
            if (bonded == null || bonded.ownerUuid() == null || bondState.getOwnerTarget(bonded.ownerUuid()) == null) {
                continue;
            }

            boolean removed = false;
            try {
                store.removeEntity(summon.ref, RemoveReason.REMOVE);
                removed = true;
            } catch (Throwable ignored) {

            }

            debug.traceFileOnly(
                null,
                "KuduAdeptSummonSuppression event=removeInheritedSummon"
                    + " roleName=" + roleName
                    + " summon.roleName=" + summon.roleName
                    + " summonUuid=" + summon.uuid
                    + " adeptUuid=" + nearestAdept.uuid
                    + " ownerUuid=" + bonded.ownerUuid()
                    + " distanceBlocks=" + Math.sqrt(nearestDistanceSq)
                    + " radiusBlocks=" + SUPPRESSED_SUMMON_RADIUS_BLOCKS
                    + " removed=" + removed
            );
        }
    }

    private void trySetCombatTarget(
        @Nonnull Store<EntityStore> store,
        long nowNanos,
        @Nonnull String roleName,
        @Nonnull AdeptSnapshot adept,
        @Nonnull PlayerSnapshot owner,
        @Nonnull Ref<EntityStore> targetRef
    ) {
        NPCEntity npc = store.getComponent(adept.ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        Role role = npc.getRole();
        MarkedEntitySupport marked = role != null ? MarkedEntitySupport.get(adept.ref, store) : null;
        if (role == null || marked == null || WorldSupport.get(adept.ref, store) == null) {
            return;
        }


        Ref<EntityStore> currentTarget = marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        boolean alreadyTargeting = currentTarget != null && currentTarget.isValid() && currentTarget.equals(targetRef);
        if (!alreadyTargeting) {
            marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, targetRef);
        }
        try {
            marked.flockSetTarget(adept.ref, MarkedEntitySupport.DEFAULT_TARGET_SLOT, targetRef, store);
        } catch (Throwable ignored) {

        }
        if (alreadyTargeting) {
            return;
        }

        WorldSupport.get(adept.ref, store).requestNewPath();
        try {
            role.acquireExecutionSupport(adept.ref, store).notifySensorMatch();
        } catch (Throwable ignored) {

        }

        long nextDebugAt = nextDebugAtNanosByAdept.getOrDefault(adept.uuid, 0L);
        if (nextDebugAt <= nowNanos) {
            nextDebugAtNanosByAdept.put(adept.uuid, nowNanos + DEBUG_INTERVAL_NANOS);
            UUID targetUuid = getUuid(store, targetRef);
            debug.traceFileOnly(
                owner.playerRef,
                "KuduAdeptFollow event=combatTarget"
                    + " roleName=" + roleName
                    + " adeptUuid=" + adept.uuid
                    + " ownerUuid=" + owner.uuid
                    + (targetUuid != null ? " targetUuid=" + targetUuid : "")
                    + " hostileOverrideSeconds=" + TARGET_HOSTILE_OVERRIDE_SECONDS
            );
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
        InventoryComponent.Hotbar hotbarComponent = InventoryComponentAccess.hotbarComponent(store, adept.ref);
        if (hotbarComponent == null) {
            return;
        }
        ItemContainer hotbar = hotbarComponent.getInventory();
        if (hotbar == null) {
            return;
        }
        if (hotbar.getCapacity() <= ADEPT_HELD_HOTBAR_SLOT) {
            return;
        }

        ItemStack current = hotbar.getItemStack(ADEPT_HELD_HOTBAR_SLOT);
        boolean already = current != null && current.isValid() && !current.isEmpty() && ADEPT_HELD_ITEM_ID.equals(current.getItemId());
        boolean activeSlotAlready = InventoryComponentAccess.activeHotbarSlot(store, adept.ref) == (byte) ADEPT_HELD_HOTBAR_SLOT;
        if (already && activeSlotAlready) {
            return;
        }

        try {
            if (!already) {
                hotbar.setItemStackForSlot(ADEPT_HELD_HOTBAR_SLOT, new ItemStack(ADEPT_HELD_ITEM_ID, 1));
            }
            InventoryComponentAccess.setActiveHotbarSlot(store, adept.ref, (byte) ADEPT_HELD_HOTBAR_SLOT);
            hotbarComponent.markDirty();
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

                }
                if (!CompanionLoyalty.mage(npcRoleName,roleName)) {
                    continue;
                }
                if (isDeadOrDying(store, ref)) {
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

        }
        return out;
    }

    private static @Nonnull ArrayList<ItemDropSnapshot> snapshotArcaneCrystalDrops(@Nonnull Store<EntityStore> store) {
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

                }
                if (stack == null || !stack.isValid() || stack.isEmpty()) {
                    continue;
                }
                if (!isBondingItemId(stack.getItemId())) {
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

        }
        return out;
    }

    private static @Nonnull ArrayList<SuppressedSummonSnapshot> collectSuppressedSummons(@Nonnull Store<EntityStore> store) {
        ArrayList<SuppressedSummonSnapshot> out = new ArrayList<>();
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
                String roleName = null;
                try {
                    roleName = npc != null ? npc.getRoleName() : null;
                } catch (Throwable ignored) {

                }
                if (roleName == null || !SUPPRESSED_VANILLA_SUMMON_ROLE_IDS.contains(roleName)) {
                    continue;
                }
                UUIDComponent uuidComponent = chunk.getComponent(i, UUIDComponent.getComponentType());
                UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
                if (uuid == null) {
                    continue;
                }
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                Vector3d pos = transform != null ? transform.getPosition() : null;
                if (pos == null || !pos.isFinite()) {
                    continue;
                }
                out.add(new SuppressedSummonSnapshot(uuid, ref, roleName, pos));
            }
        };
        try {
            store.forEachChunk(query, visitor);
        } catch (Throwable ignored) {

        }
        return out;
    }

    private static boolean isBondingItemId(@Nullable String itemId) {
        return ARCANE_CRYSTAL_SHARD_ITEM_ID.equals(itemId) || ARCANE_CRYSTAL_BLOCK_ITEM_ID.equals(itemId);
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

            }
        }
        return 0;
    }

    private static boolean isDeadOrDying(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        try {
            DeathComponent death = store.getComponent(ref, DeathComponent.getComponentType());
            if (death != null) {
                return true;
            }
        } catch (Throwable ignored) {

        }

        try {
            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            int healthIndex = resolveHealthStatIndex();
            EntityStatValue health = stats != null && healthIndex >= 0 ? stats.get(healthIndex) : null;
            if (health != null) {
                float current = health.get();
                float min = health.getMin();
                return Float.isFinite(current) && Float.isFinite(min) && current <= min;
            }
        } catch (Throwable ignored) {

        }

        return false;
    }

    private static int resolveHealthStatIndex() {
        try {
            var map = EntityStatType.getAssetMap();
            if (map != null) {
                int index = map.getIndex(HEALTH_STAT_NAME);
                if (index >= 0) {
                    return index;
                }
            }
        } catch (Throwable ignored) {

        }
        return -1;
    }

    private static int findAirColumn(@Nonnull WorldBlocks.Column chunk, int x, int y, int z) {
        int base = clampY(y);
        for (int dy = 0; dy <= 4; dy++) {
            int tryY = clampY(base + dy);
            if (tryY < MIN_Y || tryY >= MAX_Y) {
                continue;
            }
            if (!isAir(chunk, x, tryY, z)) {
                continue;
            }
            if (!isAir(chunk, x, tryY + 1, z)) {
                continue;
            }
            if (isAir(chunk, x, tryY - 1, z)) {
                continue;
            }
            return tryY;
        }
        return -1;
    }

    private static boolean isAir(@Nonnull WorldBlocks.Column chunk, int x, int y, int z) {
        try {
            var type = chunk.getBlockType(x, y, z);
            return type == null
                || type.isUnknown()
                || type == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
                || type.getDrawType() == com.hypixel.hytale.protocol.DrawType.Empty;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int clampY(int y) {
        return Math.max(MIN_Y, Math.min(MAX_Y, y));
    }

    private static long nowEpochMillis() {
        return System.currentTimeMillis();
    }

    private static @Nullable Vector3d getEntityPosition(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        try {
            Transform look = TargetUtil.getLook(ref, store);
            Vector3d pos = look != null ? look.getPosition() : null;
            if (pos != null && pos.isFinite()) {
                return pos;
            }
        } catch (Throwable ignored) {

        }

        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            Vector3d pos = transform != null ? transform.getPosition() : null;
            if (pos != null && pos.isFinite()) {
                return pos;
            }
        } catch (Throwable ignored) {

        }

        return null;
    }

    private static @Nullable UUID getUuid(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        try {
            UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
            return uuidComponent != null ? uuidComponent.getUuid() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double distSq(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static @Nonnull String formatDistance(@Nullable Vector3d a, @Nullable Vector3d b) {
        if (a == null || b == null || !a.isFinite() || !b.isFinite()) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%.2f", Math.sqrt(distSq(a, b)));
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

    private record SuppressedSummonSnapshot(
        @Nonnull UUID uuid,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String roleName,
        @Nonnull Vector3d position
    ) {
    }

    private record BondFeedbackResult(
        boolean particleSpawned,
        int particleBursts,
        @Nonnull String particleReason,
        boolean animationPlayed,
        int animationCount,
        @Nonnull String animationReason
    ) {
    }
}
