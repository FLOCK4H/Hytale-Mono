package org.example.plugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import org.bson.BsonDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drops configurable loot from Kudu Rune Knights on death.
 */
public final class RuneKnightLootSystem extends TickingSystem<EntityStore> {

    private static final String DEFAULT_ROLE_NAME = RuneKnightSpawnerSystem.DEFAULT_ROLE_NAME;
    private static final String KUDU_BOOTS_ITEM_ID = "Kudu_Boots";
    private static final String FROST_BOOK_ITEM_ID = "Book_Frost_Texture";
    private static final String ARCANE_CRYSTAL_ITEM_ID = "Ingredient_Crystal_Arcane";
    private static final String ARCANE_MATTER_ITEM_ID = "Arcane_Matter";
    private static final String CLOUD_OR_BOUNCE_DROP_LIST_ID = "Drop_AxoTales_Cloud_Or_Bounce";
    private static final int ARCANE_CRYSTAL_DROP_MIN = 1;
    private static final int ARCANE_CRYSTAL_DROP_MAX = 2;
    private static final int ARCANE_MATTER_DROP_QUANTITY = 1;
    private static final int CLOUD_OR_BOUNCE_DROP_CHANCE_PERCENT = 33;
    private static final double CLOUD_OR_BOUNCE_DROP_CHANCE = CLOUD_OR_BOUNCE_DROP_CHANCE_PERCENT / 100.0;

    private static final long TICK_INTERVAL_NANOS = 250_000_000L;
    private static final long DEATH_CLEANUP_DELAY_NANOS = 1_200_000_000L;
    private static final String DEATH_ANIMATION_ID = "DeathDrop";

    private static final float DROP_VELOCITY_HORIZONTAL_STDDEV = 0.22f;
    private static final float DROP_VELOCITY_Y = 0.25f;
    private static final float DROP_PICKUP_DELAY_SECONDS = 0.4f;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final RuneKnightSpawnState spawnState;

    private final java.util.Set<UUID> processedDeaths = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Long> removeAtNanosByUuid = new ConcurrentHashMap<>();

    private volatile long nextTickAtNanos = 0L;

    public RuneKnightLootSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull RuneKnightSpawnState spawnState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.spawnState = spawnState;
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

            if (config == null || config.runeKnight == null || !config.runeKnight.enabled) {
                return;
            }

            var external = store.getExternalData();
            World world = external != null ? external.getWorld() : null;
            if (world == null) {
                return;
            }

            runDeathCleanup(store, world, nowNanos);

            String roleName = config != null
                && config.runeKnight != null
                && config.runeKnight.roleName != null
                && !config.runeKnight.roleName.isBlank()
                ? config.runeKnight.roleName
                : DEFAULT_ROLE_NAME;

            int kuduChancePercentRaw = config != null
                && config.runeKnight != null
                && config.runeKnight.loot != null
                ? config.runeKnight.loot.kuduBootsDropChancePercent
                : 0;
            final int kuduChancePercent = Math.max(0, Math.min(100, kuduChancePercentRaw));
            final double kuduChance = kuduChancePercent / 100.0;

            int frostChancePercentRaw = config != null
                && config.runeKnight != null
                && config.runeKnight.loot != null
                ? config.runeKnight.loot.frostBookDropChancePercent
                : 0;
            final int frostChancePercent = Math.max(0, Math.min(100, frostChancePercentRaw));
            final double frostChance = frostChancePercent / 100.0;

            if (processedDeaths.size() > 4096) {
                processedDeaths.clear();
            }

            final int healthIndex = DefaultEntityStatTypes.getHealth();
            ArrayList<DeathDrop> deathDrops = new ArrayList<>();

            store.forEachChunk(
                Query.and(
                    NPCEntity.getComponentType(),
                    UUIDComponent.getComponentType(),
                    TransformComponent.getComponentType(),
                    EntityStatMap.getComponentType()
                ),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> npcRef = chunk.getReferenceTo(i);
                        if (npcRef == null || !npcRef.isValid()) {
                            continue;
                        }

                        NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                        if (npc == null) {
                            continue;
                        }

                        String npcRoleName;
                        try {
                            npcRoleName = npc.getRoleName();
                        } catch (Throwable ignored) {
                            continue;
                        }
                        if (npcRoleName == null || !npcRoleName.equals(roleName)) {
                            continue;
                        }

                        UUIDComponent uuidComponent = chunk.getComponent(i, UUIDComponent.getComponentType());
                        UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
                        if (uuid == null) {
                            continue;
                        }

                        DeathComponent death = null;
                        try {
                            death = store.getComponent(npcRef, DeathComponent.getComponentType());
                        } catch (Throwable ignored) {
                            // Best effort.
                        }

                        EntityStatMap stats = chunk.getComponent(i, EntityStatMap.getComponentType());
                        EntityStatValue healthStat = stats != null ? stats.get(healthIndex) : null;
                        float healthCurrent = healthStat != null ? healthStat.get() : Float.NaN;
                        float healthMax = healthStat != null ? healthStat.getMax() : Float.NaN;
                        float healthMin = healthStat != null ? healthStat.getMin() : Float.NaN;
                        boolean deadByHealth = healthStat != null && Float.isFinite(healthCurrent) && healthCurrent <= 0f;
                        boolean isDead = death != null || deadByHealth;
                        if (!isDead) {
                            continue;
                        }

                        if (!processedDeaths.add(uuid)) {
                            continue;
                        }

                        spawnState.remove(world, uuid);

                        TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                        Vector3d pos = transform != null ? transform.getPosition() : null;
                        if (pos == null || !pos.isFinite()) {
                            debug.traceFileOnly(
                                (PlayerRef) null,
                                "RuneKnightLoot event=death"
                                    + " roleName=" + roleName
                                    + " npc.uuid=" + uuid
                                    + " dead.by=" + (death != null ? "DeathComponent" : "health<=0")
                                    + " health.current=" + healthCurrent
                                    + " health.max=" + healthMax
                                    + " health.min=" + healthMin
                                    + " kudu.itemId=" + KUDU_BOOTS_ITEM_ID
                                    + " kudu.chancePercent=" + kuduChancePercent
                                    + " kudu.drop=false"
                                    + " frost.itemId=" + FROST_BOOK_ITEM_ID
                                    + " frost.chancePercent=" + frostChancePercent
                                    + " frost.drop=false"
                                    + " arcaneCrystal.itemId=" + ARCANE_CRYSTAL_ITEM_ID
                                    + " arcaneCrystal.quantityRange=" + ARCANE_CRYSTAL_DROP_MIN + "-" + ARCANE_CRYSTAL_DROP_MAX
                                    + " arcaneCrystal.drop=false"
                                    + " arcaneMatter.itemId=" + ARCANE_MATTER_ITEM_ID
                                    + " arcaneMatter.quantity=" + ARCANE_MATTER_DROP_QUANTITY
                                    + " arcaneMatter.drop=false"
                                    + " platformBlock.dropListId=" + CLOUD_OR_BOUNCE_DROP_LIST_ID
                                    + " platformBlock.chancePercent=" + CLOUD_OR_BOUNCE_DROP_CHANCE_PERCENT
                                    + " platformBlock.drop=false"
                                    + " reason=positionInvalid"
                                    + " world=" + world.getName()
                            );
                            continue;
                        }

                        double kuduRoll = ThreadLocalRandom.current().nextDouble();
                        boolean kuduDrop = kuduRoll < kuduChance;
                        double frostRoll = ThreadLocalRandom.current().nextDouble();
                        boolean frostDrop = frostRoll < frostChance;
                        double platformBlockRoll = ThreadLocalRandom.current().nextDouble();
                        boolean platformBlockDrop = platformBlockRoll < CLOUD_OR_BOUNCE_DROP_CHANCE;

                        deathDrops.add(
                            new DeathDrop(
                                uuid,
                                npcRef,
                                new Vector3d(pos),
                                death != null,
                                healthCurrent,
                                healthMax,
                                healthMin,
                                kuduRoll,
                                kuduDrop,
                                frostRoll,
                                frostDrop,
                                platformBlockRoll,
                                platformBlockDrop
                            )
                        );
                    }
                }
            );

            for (DeathDrop drop : deathDrops) {
                int arcaneCrystalQuantity = ThreadLocalRandom.current().nextInt(
                    ARCANE_CRYSTAL_DROP_MIN,
                    ARCANE_CRYSTAL_DROP_MAX + 1
                );
                int arcaneCrystalSpawned = spawnItemDrop(
                    store,
                    new ItemStack(ARCANE_CRYSTAL_ITEM_ID, arcaneCrystalQuantity),
                    drop.position
                );
                int arcaneMatterSpawned = spawnItemDrop(
                    store,
                    new ItemStack(ARCANE_MATTER_ITEM_ID, ARCANE_MATTER_DROP_QUANTITY),
                    drop.position
                );

                int kuduSpawned = 0;
                if (drop.kuduDrop) {
                    kuduSpawned = spawnItemDrop(store, new ItemStack(KUDU_BOOTS_ITEM_ID, 1), drop.position);
                }

                int frostSpawned = 0;
                if (drop.frostDrop) {
                    frostSpawned = spawnItemDrop(store, new ItemStack(FROST_BOOK_ITEM_ID, 1), drop.position);
                }

                DropListSpawnResult platformBlockDrop = DropListSpawnResult.empty();
                if (drop.platformBlockDrop) {
                    platformBlockDrop = spawnDropList(store, CLOUD_OR_BOUNCE_DROP_LIST_ID, drop.position);
                }

                boolean deathAnimPlayed = playDeathAnimationMaybe(store, drop.npcRef);
                removeAtNanosByUuid.put(drop.uuid, nowNanos + DEATH_CLEANUP_DELAY_NANOS);

                debug.traceFileOnly(
                    (PlayerRef) null,
                    "RuneKnightLoot event=death"
                        + " roleName=" + roleName
                        + " npc.uuid=" + drop.uuid
                        + " dead.by=" + (drop.deadByDeathComponent ? "DeathComponent" : "health<=0")
                        + " health.current=" + drop.healthCurrent
                        + " health.max=" + drop.healthMax
                        + " health.min=" + drop.healthMin
                        + " kudu.itemId=" + KUDU_BOOTS_ITEM_ID
                        + " kudu.chancePercent=" + kuduChancePercent
                        + " kudu.roll=" + String.format(java.util.Locale.ROOT, "%.4f", drop.kuduRoll)
                        + " kudu.drop=" + drop.kuduDrop
                        + " kudu.spawned=" + kuduSpawned
                        + " frost.itemId=" + FROST_BOOK_ITEM_ID
                        + " frost.chancePercent=" + frostChancePercent
                        + " frost.roll=" + String.format(java.util.Locale.ROOT, "%.4f", drop.frostRoll)
                        + " frost.drop=" + drop.frostDrop
                        + " frost.spawned=" + frostSpawned
                        + " arcaneCrystal.itemId=" + ARCANE_CRYSTAL_ITEM_ID
                        + " arcaneCrystal.quantity=" + arcaneCrystalQuantity
                        + " arcaneCrystal.spawned=" + arcaneCrystalSpawned
                        + " arcaneMatter.itemId=" + ARCANE_MATTER_ITEM_ID
                        + " arcaneMatter.quantity=" + ARCANE_MATTER_DROP_QUANTITY
                        + " arcaneMatter.spawned=" + arcaneMatterSpawned
                        + " platformBlock.dropListId=" + CLOUD_OR_BOUNCE_DROP_LIST_ID
                        + " platformBlock.chancePercent=" + CLOUD_OR_BOUNCE_DROP_CHANCE_PERCENT
                        + " platformBlock.roll=" + String.format(java.util.Locale.ROOT, "%.4f", drop.platformBlockRoll)
                        + " platformBlock.drop=" + drop.platformBlockDrop
                        + " platformBlock.itemId=" + platformBlockDrop.itemId
                        + " platformBlock.spawned=" + platformBlockDrop.entitiesSpawned
                        + " death.anim=" + DEATH_ANIMATION_ID
                        + " death.animPlayed=" + deathAnimPlayed
                        + " cleanup.delaySeconds=" + (DEATH_CLEANUP_DELAY_NANOS / 1_000_000_000.0)
                        + " world=" + world.getName()
                );
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "RuneKnightLootSystem: tick failed.", t);
        }
    }

    private void runDeathCleanup(@Nonnull Store<EntityStore> store, @Nonnull World world, long nowNanos) {
        if (removeAtNanosByUuid.isEmpty()) {
            return;
        }

        var external = store.getExternalData();
        if (external == null) {
            return;
        }

        if (removeAtNanosByUuid.size() > 4096) {
            removeAtNanosByUuid.clear();
            return;
        }

        for (var entry : removeAtNanosByUuid.entrySet()) {
            UUID uuid = entry.getKey();
            long removeAt = entry.getValue();
            if (uuid == null || removeAt <= 0 || nowNanos < removeAt) {
                continue;
            }

            removeAtNanosByUuid.remove(uuid);

            Ref<EntityStore> ref = external.getRefFromUUID(uuid);
            if (ref == null || !ref.isValid()) {
                continue;
            }

            try {
                store.removeEntity(ref, com.hypixel.hytale.component.RemoveReason.REMOVE);
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
    }

    private static boolean playDeathAnimationMaybe(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }

        NPCEntity npc;
        try {
            npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        } catch (Throwable ignored) {
            return false;
        }
        if (npc == null) {
            return false;
        }

        try {
            npc.playAnimation(npcRef, AnimationSlot.Movement, DEATH_ANIMATION_ID, store);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static @Nonnull DropListSpawnResult spawnDropList(
        @Nonnull Store<EntityStore> store,
        @Nonnull String dropListId,
        @Nonnull Vector3d entityPosition
    ) {
        ItemDropList dropList = ItemDropList.getAssetMap().getAsset(dropListId);
        if (dropList == null) {
            return DropListSpawnResult.empty();
        }

        var container = dropList.getContainer();
        if (container == null) {
            return DropListSpawnResult.empty();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemDrop> drops = new ArrayList<>();
        container.populateDrops(drops, random::nextDouble, dropListId);

        int entitiesSpawned = 0;
        int itemsTotal = 0;
        String firstItemId = "none";
        for (ItemDrop drop : drops) {
            if (drop == null) {
                continue;
            }

            String itemId = drop.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }

            int quantity = Math.max(0, drop.getRandomQuantity(random));
            if (quantity <= 0) {
                continue;
            }

            BsonDocument metadata = drop.getMetadata();
            ItemStack stack = metadata != null ? new ItemStack(itemId, quantity, metadata) : new ItemStack(itemId, quantity);
            int spawned = spawnItemDrop(store, stack, entityPosition);
            if (spawned <= 0) {
                continue;
            }

            if ("none".equals(firstItemId)) {
                firstItemId = itemId;
            }
            entitiesSpawned += spawned;
            itemsTotal += quantity;
        }

        return new DropListSpawnResult(firstItemId, entitiesSpawned, itemsTotal);
    }

    private static int spawnItemDrop(@Nonnull Store<EntityStore> store, @Nonnull ItemStack stack, @Nonnull Vector3d entityPosition) {
        if (!stack.isValid()) {
            return 0;
        }

        Vector3d position = new Vector3d(entityPosition.x, entityPosition.y + 0.5, entityPosition.z);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float vx = (float) (random.nextGaussian() * DROP_VELOCITY_HORIZONTAL_STDDEV);
        float vz = (float) (random.nextGaussian() * DROP_VELOCITY_HORIZONTAL_STDDEV);

        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
            store,
            stack,
            position,
            Vector3f.ZERO,
            vx,
            DROP_VELOCITY_Y,
            vz
        );
        if (holder == null) {
            return 0;
        }

        ItemComponent itemComponent = holder.getComponent(ItemComponent.getComponentType());
        if (itemComponent != null) {
            itemComponent.setPickupDelay(DROP_PICKUP_DELAY_SECONDS);
        }

        store.addEntity(holder, AddReason.SPAWN);
        return 1;
    }

    private record DeathDrop(
        @Nonnull UUID uuid,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Vector3d position,
        boolean deadByDeathComponent,
        float healthCurrent,
        float healthMax,
        float healthMin,
        double kuduRoll,
        boolean kuduDrop,
        double frostRoll,
        boolean frostDrop,
        double platformBlockRoll,
        boolean platformBlockDrop
    ) {
    }

    private record DropListSpawnResult(@Nonnull String itemId, int entitiesSpawned, int itemsTotal) {
        private static @Nonnull DropListSpawnResult empty() {
            return new DropListSpawnResult("none", 0, 0);
        }
    }
}
