package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.registry.Registration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;








public final class ArmorManaMaxBonusEffect {

    private static final float FLOAT_EPSILON = 0.0001f;
    private static final String BASELINE_MANA_MAX_MODIFIER_KEY = "axotales:mana_max_baseline_neg";


    private static final Map<String, Float> ARMOR_MANA_MAX_BONUSES_BY_ITEM_ID = Map.of(
        "Axo_Sar_Boots", 25f,
        "Axo_Sar_Diadem", 25f,
        "Axo_Sar_Armor", 25f,
        "Axo_Sar_Warfists", 25f,
        "Axo_Invisibility_Cloak", 25f
    );


    private static final Map<String, Float> INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID = Map.of(
        "Axo_Arcane_Sword", 30f
    );

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Map<UUID, List<Registration>> itemChangeRegistrationsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextDebugAtNanosByPlayer = new ConcurrentHashMap<>();

    public ArmorManaMaxBonusEffect(@Nonnull PluginErrorReporter errors, @Nonnull PluginDebugReporter debug) {
        this.errors = errors;
        this.debug = debug;
    }

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        world.execute(() -> {
            try {
                EntityStore entityStore = world.getEntityStore();
                if (entityStore == null) {
                    return;
                }

                Store<EntityStore> store = entityStore.getStore();
                if (store == null) {
                    return;
                }

                Ref<EntityStore> playerEntityRef = event.getPlayerRef();
                if (playerEntityRef == null) {
                    return;
                }

                PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }

                UUID playerUuid = playerRef.getUuid();
                if (playerUuid == null) {
                    return;
                }

                List<Registration> previous = itemChangeRegistrationsByPlayer.remove(playerUuid);
                if (previous != null) {
                    for (Registration registration : previous) {
                        if (registration != null) {
                            registration.unregister();
                        }
                    }
                }

                List<Registration> registrations = new ArrayList<>();
                ItemContainer armor = InventoryComponentAccess.armor(store, playerEntityRef);
                if (armor != null) {
                    registrations.add(armor.registerChangeEvent(changeEvent -> refresh(playerUuid)));
                }
                ItemContainer hotbar = InventoryComponentAccess.hotbar(store, playerEntityRef);
                if (hotbar != null) {
                    registrations.add(hotbar.registerChangeEvent(changeEvent -> refresh(playerUuid)));
                }
                ItemContainer storage = InventoryComponentAccess.storage(store, playerEntityRef);
                if (storage != null) {
                    registrations.add(storage.registerChangeEvent(changeEvent -> refresh(playerUuid)));
                }
                ItemContainer utility = InventoryComponentAccess.utility(store, playerEntityRef);
                if (utility != null) {
                    registrations.add(utility.registerChangeEvent(changeEvent -> refresh(playerUuid)));
                }
                ItemContainer tools = InventoryComponentAccess.tools(store, playerEntityRef);
                if (tools != null) {
                    registrations.add(tools.registerChangeEvent(changeEvent -> refresh(playerUuid)));
                }
                ItemContainer backpack = InventoryComponentAccess.backpack(store, playerEntityRef);
                if (backpack != null) {
                    registrations.add(backpack.registerChangeEvent(changeEvent -> refresh(playerUuid)));
                }

                itemChangeRegistrationsByPlayer.put(playerUuid, registrations);

                applyOrRemove(world, playerUuid);
            } catch (Throwable t) {
                errors.report((PlayerRef) null, "Failed to register ArmorManaMaxBonusEffect.", t);
            }
        });
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }

        List<Registration> registrations = itemChangeRegistrationsByPlayer.remove(playerUuid);
        if (registrations != null) {
            for (Registration registration : registrations) {
                if (registration != null) {
                    registration.unregister();
                }
            }
        }
        nextDebugAtNanosByPlayer.remove(playerUuid);
    }

    public void shutdown() {
        for (List<Registration> registrations : itemChangeRegistrationsByPlayer.values()) {
            try {
                if (registrations != null) {
                    for (Registration registration : registrations) {
                        if (registration != null) {
                            registration.unregister();
                        }
                    }
                }
            } catch (Throwable ignored) {

            }
        }
        itemChangeRegistrationsByPlayer.clear();
        nextDebugAtNanosByPlayer.clear();
    }

    private void refresh(@Nonnull UUID playerUuid) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null || !playerRef.isValid()) {
                return;
            }

            World world = Universe.get().getWorld(playerRef.getWorldUuid());
            if (world == null) {
                return;
            }

            world.execute(() -> applyOrRemove(world, playerUuid));
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "Failed to refresh ArmorManaMaxBonusEffect.", t);
        }
    }

    private void applyOrRemove(@Nonnull World world, @Nonnull UUID playerUuid) {
        try {
            EntityStore entityStore = world.getEntityStore();
            if (entityStore == null) {
                return;
            }

            Store<EntityStore> store = entityStore.getStore();
            if (store == null) {
                return;
            }

            Ref<EntityStore> playerEntityRef = world.getEntityRef(playerUuid);
            if (playerEntityRef == null) {
                playerEntityRef = entityStore.getRefFromUUID(playerUuid);
            }
            if (playerEntityRef == null) {
                return;
            }

            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) {
                return;
            }

            PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }

            ItemContainer armor = InventoryComponentAccess.armor(store, playerEntityRef);
            if (armor == null) {
                return;
            }

            EntityStatMap stats = store.getComponent(playerEntityRef, EntityStatMap.getComponentType());
            if (stats == null) {
                maybeDebug(playerRef, playerUuid, "manaMaxSkip", "statsMissing", null, null, null, null, null, null, null);
                return;
            }

            int manaIndex = DefaultEntityStatTypes.getMana();
            if (manaIndex == Integer.MIN_VALUE || manaIndex < 0) {
                maybeDebug(playerRef, playerUuid, "manaMaxSkip", "manaIndexInvalid", null, null, null, null, null, null, null);
                return;
            }

            EntityStatValue manaBefore = stats.get(manaIndex);
            if (manaBefore == null) {
                maybeDebug(playerRef, playerUuid, "manaMaxSkip", "manaStatMissing", null, null, manaIndex, null, null, null, null);
                return;
            }
            float manaCurrentBefore = manaBefore.get();
            float manaMinBefore = manaBefore.getMin();
            float manaMaxBefore = manaBefore.getMax();

            List<String> equippedArmorItemIds = readArmorItemIds(armor);
            List<String> inventoryBonusItemIds = readInventoryBonusItemIds(store, playerEntityRef);
            Set<String> inventoryBonusItemIdSet = new HashSet<>(inventoryBonusItemIds);
            float desiredBonusTotal = 0f;
            for (var entry : ARMOR_MANA_MAX_BONUSES_BY_ITEM_ID.entrySet()) {
                if (equippedArmorItemIds.contains(entry.getKey())) {
                    desiredBonusTotal += entry.getValue();
                }
            }
            for (var entry : INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID.entrySet()) {
                if (inventoryBonusItemIdSet.contains(entry.getKey())) {
                    desiredBonusTotal += entry.getValue();
                }
            }

            boolean anyChange = false;
            List<String> applied = new ArrayList<>();
            List<String> removed = new ArrayList<>();



            if (stats.removeModifier(manaIndex, BASELINE_MANA_MAX_MODIFIER_KEY) != null) {
                anyChange = true;
                removed.add(BASELINE_MANA_MAX_MODIFIER_KEY);
            }

            for (var entry : ARMOR_MANA_MAX_BONUSES_BY_ITEM_ID.entrySet()) {
                String itemId = entry.getKey();
                float bonus = entry.getValue();
                boolean wearing = equippedArmorItemIds.contains(itemId);
                String modifierKey = "axotales:mana_max_" + AxoItems.modifierId(itemId);

                boolean changed = applyOrRemoveManaMaxModifier(stats, manaIndex, modifierKey, bonus, wearing);
                if (changed) {
                    anyChange = true;
                    if (wearing) {
                        applied.add(itemId + "(+" + bonus + ")");
                    } else {
                        removed.add(itemId);
                    }
                }
            }

            for (var entry : INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID.entrySet()) {
                String itemId = entry.getKey();
                float bonus = entry.getValue();
                boolean present = inventoryBonusItemIdSet.contains(itemId);
                String modifierKey = "axotales:mana_max_" + AxoItems.modifierId(itemId);

                boolean changed = applyOrRemoveManaMaxModifier(stats, manaIndex, modifierKey, bonus, present);
                if (changed) {
                    anyChange = true;
                    if (present) {
                        applied.add(itemId + "(+" + bonus + ")");
                    } else {
                        removed.add(itemId);
                    }
                }
            }

            if (!anyChange) {
                maybeDebug(
                    playerRef,
                    playerUuid,
                    "manaMaxNoop",
                    "noChange",
                    equippedArmorItemIds,
                    inventoryBonusItemIds,
                    manaIndex,
                    manaCurrentBefore,
                    manaMinBefore,
                    manaMaxBefore,
                    null
                );
                return;
            }

            stats.update();

            EntityStatValue manaAfterUpdate = stats.get(manaIndex);
            float manaMaxAfter = manaAfterUpdate != null ? manaAfterUpdate.getMax() : Float.NaN;
            float manaCurrentAfter = manaAfterUpdate != null ? manaAfterUpdate.get() : Float.NaN;
            if (Float.isFinite(manaMaxAfter) && Float.isFinite(manaCurrentAfter) && manaCurrentAfter > manaMaxAfter + FLOAT_EPSILON) {
                stats.setStatValue(manaIndex, manaMaxAfter);
                stats.update();
                manaCurrentAfter = manaMaxAfter;
            }

            debug.traceFileOnly(
                playerRef,
                "ArmorManaMax event=manaMaxApply"
                    + " applied=" + applied
                    + " removed=" + removed
                    + " armorItemIds=" + equippedArmorItemIds
                    + " inventoryBonusItemIds=" + inventoryBonusItemIds
                    + " desiredBonusTotal=" + desiredBonusTotal
                    + " mana.index=" + manaIndex
                    + (Float.isFinite(manaCurrentBefore) ? " mana.currentBefore=" + manaCurrentBefore : "")
                    + (Float.isFinite(manaMinBefore) ? " mana.minBefore=" + manaMinBefore : "")
                    + (Float.isFinite(manaMaxBefore) ? " mana.maxBefore=" + manaMaxBefore : "")
                    + (Float.isFinite(manaMaxAfter) ? " mana.maxAfter=" + manaMaxAfter : "")
                    + (Float.isFinite(manaCurrentAfter) ? " mana.currentAfter=" + manaCurrentAfter : "")
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "ArmorManaMaxBonusEffect: apply failed.", t);
        }
    }

    private static boolean applyOrRemoveManaMaxModifier(
        @Nonnull EntityStatMap stats,
        int manaIndex,
        @Nonnull String modifierKey,
        float bonus,
        boolean active
    ) {
        if (active) {
            Modifier existing = stats.getModifier(manaIndex, modifierKey);
            if (existing instanceof StaticModifier staticModifier
                && staticModifier.getTarget() == Modifier.ModifierTarget.MAX
                && staticModifier.getCalculationType() == StaticModifier.CalculationType.ADDITIVE
                && Math.abs(staticModifier.getAmount() - bonus) < FLOAT_EPSILON) {
                return false;
            }

            stats.putModifier(
                manaIndex,
                modifierKey,
                new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, bonus)
            );
            return true;
        }

        return stats.removeModifier(manaIndex, modifierKey) != null;
    }

    @Nonnull
    private static List<String> readArmorItemIds(@Nonnull ItemContainer armor) {
        List<String> ids = new ArrayList<>();
        int capacity = armor.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = armor.getItemStack(slot);
            if (stack == null || !stack.isValid()) {
                continue;
            }
            String itemId = stack.getItemId();
            if (itemId != null) {
                ids.add(itemId);
            }
        }
        return ids;
    }

    @Nonnull
    private static List<String> readInventoryBonusItemIds(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef
    ) {
        if (INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> found = new LinkedHashSet<>();
        readRelevantItemIds(InventoryComponentAccess.hotbar(store, playerEntityRef), INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID.keySet(), found);
        readRelevantItemIds(InventoryComponentAccess.storage(store, playerEntityRef), INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID.keySet(), found);
        readRelevantItemIds(InventoryComponentAccess.utility(store, playerEntityRef), INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID.keySet(), found);
        readRelevantItemIds(InventoryComponentAccess.tools(store, playerEntityRef), INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID.keySet(), found);
        readRelevantItemIds(InventoryComponentAccess.backpack(store, playerEntityRef), INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID.keySet(), found);

        return new ArrayList<>(found);
    }

    private static void readRelevantItemIds(
        @Nullable ItemContainer container,
        @Nonnull Set<String> relevantItemIds,
        @Nonnull Set<String> foundOut
    ) {
        if (container == null || relevantItemIds.isEmpty()) {
            return;
        }

        int capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || !stack.isValid()) {
                continue;
            }

            String itemId = stack.getItemId();
            if (itemId != null && relevantItemIds.contains(itemId)) {
                foundOut.add(itemId);
            }
        }
    }

    private void maybeDebug(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID playerUuid,
        @Nonnull String event,
        @Nonnull String reason,
        @Nullable List<String> armorItemIds,
        @Nullable List<String> inventoryBonusItemIds,
        @Nullable Integer manaIndex,
        @Nullable Float manaCurrent,
        @Nullable Float manaMin,
        @Nullable Float manaMax,
        @Nullable Float manaMaxAfter
    ) {
        long nowNanos = System.nanoTime();
        long next = nextDebugAtNanosByPlayer.getOrDefault(playerUuid, 0L);
        if (next > nowNanos) {
            return;
        }
        nextDebugAtNanosByPlayer.put(playerUuid, nowNanos + 30_000_000_000L);

        debug.traceFileOnly(
            playerRef,
            "ArmorManaMax event=" + event
                + " reason=" + reason
                + " armorItemIds=" + (armorItemIds != null ? armorItemIds : "unknown")
                + " inventoryBonusItemIds=" + (inventoryBonusItemIds != null ? inventoryBonusItemIds : "unknown")
                + " manaMaxBonuses.armor=" + ARMOR_MANA_MAX_BONUSES_BY_ITEM_ID
                + " manaMaxBonuses.inventory=" + INVENTORY_MANA_MAX_BONUSES_BY_ITEM_ID
                + (manaIndex != null ? " mana.index=" + manaIndex : "")
                + (manaCurrent != null && Float.isFinite(manaCurrent) ? " mana.current=" + manaCurrent : "")
                + (manaMin != null && Float.isFinite(manaMin) ? " mana.min=" + manaMin : "")
                + (manaMax != null && Float.isFinite(manaMax) ? " mana.max=" + manaMax : "")
                + (manaMaxAfter != null && Float.isFinite(manaMaxAfter) ? " mana.maxAfter=" + manaMaxAfter : "")
        );
    }

}
