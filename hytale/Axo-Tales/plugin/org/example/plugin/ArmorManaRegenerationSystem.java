package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight mana regeneration based on wearing Axo Tales armor pieces.
 *
 * <p>Rule: for each configured Axo Tales armor piece currently worn, add
 * {@value #MANA_PER_ITEM_PER_INTERVAL} mana every {@value #INTERVAL_SECONDS} seconds.</p>
 */
public final class ArmorManaRegenerationSystem extends TickingSystem<EntityStore> {

    private static final long INTERVAL_SECONDS = 2L;
    private static final long INTERVAL_NANOS = INTERVAL_SECONDS * 1_000_000_000L;
    private static final float MANA_PER_ITEM_PER_INTERVAL = 2f;
    private static final float FLOAT_EPSILON = 0.0001f;
    private static final Set<String> MANA_REGEN_ARMOR_ITEM_IDS = Set.of(
        "Sars_Legs",
        "Sar_Diadem",
        "Sar_Chest",
        "Sar_Warfists"
    );

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Map<UUID, Long> lastRegenAtNanosByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextDebugAtNanosByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastWornCountByPlayer = new ConcurrentHashMap<>();

    public ArmorManaRegenerationSystem(@Nonnull PluginErrorReporter errors, @Nonnull PluginDebugReporter debug) {
        this.errors = errors;
        this.debug = debug;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            int manaIndex = DefaultEntityStatTypes.getMana();
            if (manaIndex == Integer.MIN_VALUE || manaIndex < 0) {
                return;
            }

            long nowNanos = System.nanoTime();
            store.forEachChunk(
                Query.and(
                    Player.getComponentType(),
                    PlayerRef.getComponentType(),
                    EntityStatMap.getComponentType()
                ),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int index = 0; index < chunk.size(); index++) {
                        try {
                            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
                            UUID playerUuid = playerRef != null ? playerRef.getUuid() : null;
                            if (playerUuid == null) {
                                continue;
                            }

                            Player player = chunk.getComponent(index, Player.getComponentType());
                            if (player == null) {
                                continue;
                            }

                            RegenArmorSnapshot snapshot = countWornRegenArmor(player);
                            if (snapshot.count <= 0) {
                                Integer previousCount = lastWornCountByPlayer.remove(playerUuid);
                                lastRegenAtNanosByPlayer.remove(playerUuid);
                                nextDebugAtNanosByPlayer.remove(playerUuid);

                                if (previousCount != null && previousCount > 0) {
                                    debug.traceFileOnly(
                                        playerRef,
                                        "ArmorManaRegen event=manaRegenStop"
                                            + " reason=noRegenArmor"
                                            + " worn.count=" + snapshot.count
                                            + " worn.itemIds=" + snapshot.itemIds
                                            + " itemIdsConfigured=" + MANA_REGEN_ARMOR_ITEM_IDS
                                            + " intervalSeconds=" + INTERVAL_SECONDS
                                            + " amountPerItem=" + MANA_PER_ITEM_PER_INTERVAL
                                            + " mana.index=" + manaIndex
                                    );
                                }
                                continue;
                            }

                            Integer previousCount = lastWornCountByPlayer.put(playerUuid, snapshot.count);
                            if (previousCount == null || previousCount <= 0) {
                                debug.traceFileOnly(
                                    playerRef,
                                    "ArmorManaRegen event=manaRegenStart"
                                        + " reason=hasRegenArmor"
                                        + " worn.count=" + snapshot.count
                                        + " worn.itemIds=" + snapshot.itemIds
                                        + " itemIdsConfigured=" + MANA_REGEN_ARMOR_ITEM_IDS
                                        + " intervalSeconds=" + INTERVAL_SECONDS
                                        + " amountPerItem=" + MANA_PER_ITEM_PER_INTERVAL
                                        + " mana.index=" + manaIndex
                                );
                            }

                            long lastRegenAtNanos = lastRegenAtNanosByPlayer.getOrDefault(playerUuid, 0L);
                            if (lastRegenAtNanos > 0L && nowNanos - lastRegenAtNanos < INTERVAL_NANOS) {
                                continue;
                            }
                            lastRegenAtNanosByPlayer.put(playerUuid, nowNanos);

                            EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
                            if (stats == null) {
                                maybeDebug(playerRef, playerUuid, nowNanos, "manaRegenSkip", "statsMissing", manaIndex, Float.NaN, Float.NaN, Float.NaN, Float.NaN, snapshot);
                                continue;
                            }

                            EntityStatValue mana = stats.get(manaIndex);
                            if (mana == null) {
                                maybeDebug(playerRef, playerUuid, nowNanos, "manaRegenSkip", "manaStatMissing", manaIndex, Float.NaN, Float.NaN, Float.NaN, Float.NaN, snapshot);
                                continue;
                            }

                            float current = mana.get();
                            float max = mana.getMax();
                            float min = mana.getMin();
                            float amount = MANA_PER_ITEM_PER_INTERVAL * (float) snapshot.count;
                            float updated = Math.min(max, Math.max(min, current + amount));
                            if (updated <= current + FLOAT_EPSILON) {
                                continue;
                            }

                            stats.setStatValue(manaIndex, updated);
                            stats.update();
                        } catch (Throwable ignoredPerEntity) {
                            // Best-effort regen; isolate per-entity issues.
                        }
                    }
                }
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "ArmorManaRegenerationSystem: tick failed.", t);
        }
    }

    private void maybeDebug(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID playerUuid,
        long nowNanos,
        @Nonnull String event,
        @Nonnull String reason,
        int manaIndex,
        float manaCurrent,
        float manaMin,
        float manaMax,
        float manaUpdated,
        @Nonnull RegenArmorSnapshot snapshot
    ) {
        long next = nextDebugAtNanosByPlayer.getOrDefault(playerUuid, 0L);
        if (next > nowNanos) {
            return;
        }

        nextDebugAtNanosByPlayer.put(playerUuid, nowNanos + 30_000_000_000L);
        debug.traceFileOnly(
            playerRef,
            "ArmorManaRegen event=" + event
                + " reason=" + reason
                + " worn.count=" + snapshot.count
                + " worn.itemIds=" + snapshot.itemIds
                + " itemIdsConfigured=" + MANA_REGEN_ARMOR_ITEM_IDS
                + " intervalSeconds=" + INTERVAL_SECONDS
                + " amountPerItem=" + MANA_PER_ITEM_PER_INTERVAL
                + " mana.index=" + manaIndex
                + (Float.isFinite(manaCurrent) ? " mana.current=" + manaCurrent : "")
                + (Float.isFinite(manaMin) ? " mana.min=" + manaMin : "")
                + (Float.isFinite(manaMax) ? " mana.max=" + manaMax : "")
                + (Float.isFinite(manaUpdated) ? " mana.updated=" + manaUpdated : "")
        );
    }

    private record RegenArmorSnapshot(int count, @Nonnull java.util.List<String> itemIds) {}

    @Nonnull
    private static RegenArmorSnapshot countWornRegenArmor(@Nonnull Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return new RegenArmorSnapshot(0, java.util.List.of());
        }

        ItemContainer armor = inventory.getArmor();
        if (armor == null) {
            return new RegenArmorSnapshot(0, java.util.List.of());
        }

        java.util.ArrayList<String> worn = new java.util.ArrayList<>();
        int capacity = armor.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = armor.getItemStack(slot);
            if (stack == null || !stack.isValid()) {
                continue;
            }

            String itemId = stack.getItemId();
            if (itemId != null && MANA_REGEN_ARMOR_ITEM_IDS.contains(itemId)) {
                worn.add(itemId);
            }
        }

        return new RegenArmorSnapshot(worn.size(), worn);
    }
}
