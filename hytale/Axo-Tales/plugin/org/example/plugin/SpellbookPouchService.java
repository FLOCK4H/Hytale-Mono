package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.registry.Registration;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemStackContainerWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemStackItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonString;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class SpellbookPouchService {
    static final String ID_KEY = "AxoTalesPouchId";
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final Map<UUID, Long> lastMenuAt = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.LinkedHashSet<Integer>> menuChains = new ConcurrentHashMap<>();

    public SpellbookPouchService(PluginDebugReporter debug, AxoTalesServerConfig config) {
        this.debug = debug;
        this.config = config;
    }

    public String manaCost(SpellbookCatalog spell) {
        return switch (spell) {
            case LIGHTNING, METEORAIN -> String.valueOf(LegendaryStormSystem.MANA_COST);
            case BLOODY -> "0";
            case LEECH -> "10 / second";
            case LIFE -> "All current";
            case SILVER -> "0";
            case HEAL -> String.valueOf(config.healingBook.manaCost);
            case TELEPORT -> String.valueOf(config.teleportBook.manaCost);
            case MINING -> String.valueOf(config.miningBook.manaCost);
            case IMMUNITY -> String.valueOf(config.immunityBook.manaCost);
            case LIGHT -> String.valueOf(config.lightBook.manaCost);
            case FROST -> String.valueOf(config.frostBook.manaCost);
            case FLAME -> String.valueOf(config.flameBook.manaCost);
            case HORDE -> String.valueOf(config.hordeBook.manaCost);
            case MORPH -> String.valueOf(config.morphBook.manaCost);
            case TAUNT -> String.valueOf(config.tauntBook.manaCost);
            case DOOM -> String.valueOf(config.doomBook.manaCost);
        };
    }

    public static List<ItemContainer> inventorySections(Store<EntityStore> store, Ref<EntityStore> ref) {
        List<ItemContainer> result = new ArrayList<>();
        for (ItemContainer section : new ItemContainer[] {
                InventoryComponentAccess.hotbar(store, ref), InventoryComponentAccess.storage(store, ref),
                InventoryComponentAccess.backpack(store, ref), InventoryComponentAccess.utility(store, ref),
                InventoryComponentAccess.tools(store, ref)}) {
            if (section != null && !result.contains(section)) result.add(section);
        }
        return result;
    }

    public List<ItemStackItemContainer> pouches(Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        List<ItemStackItemContainer> result = new ArrayList<>();
        for (ItemContainer section : inventorySections(store, ref)) {
            for (short slot = 0; slot < section.getCapacity(); slot++) {
                ItemStack item = section.getItemStack(slot);
                if (!ItemStack.isEmpty(item) && SpellbookCatalog.POUCH_ID.equals(item.getItemId())) {
                    result.add(container(player, section, slot));
                }
            }
        }
        return result;
    }

    private ItemStackItemContainer container(Player player, ItemContainer parent, short slot) {
        ItemStack item = parent.getItemStack(slot);
        if (pouchId(item) == null) {
            parent.setItemStackForSlot(slot, item.withMetadata(ID_KEY, new BsonString(UUID.randomUUID().toString())));
            item = parent.getItemStack(slot);
        }
        if (player != null) {
            for (var window : player.getWindowManager().getWindows()) {
                if (window instanceof ItemContainerWindow inventoryWindow
                        && inventoryWindow.getItemContainer() instanceof ItemStackItemContainer open
                        && open.getParentContainer() == parent && open.getItemStackSlot() == slot
                        && samePouch(open)) return open;
            }
        }
        var bag = ItemStackItemContainer.ensureConfiguredContainer(parent, slot, item.getItem().getItemStackContainerConfig());
        configureFilters(bag);
        return bag;
    }

    static void configureFilters(ItemStackItemContainer bag) {
        for (short slot = 0; slot < bag.getCapacity(); slot++) {
            bag.setSlotFilter(FilterActionType.ADD, slot, (action, container, index, stack) ->
                samePouch(bag) && !ItemStack.isEmpty(stack) && SpellbookCatalog.fromBook(stack.getItemId()) != null);
            bag.setSlotFilter(FilterActionType.REMOVE, slot, (action, container, index, stack) -> samePouch(bag));
            bag.setSlotFilter(FilterActionType.DROP, slot, (action, container, index, stack) -> samePouch(bag));
        }
    }

    static String pouchId(ItemStack item) {
        return ItemStack.isEmpty(item) ? null : item.getFromMetadataOrNull(ID_KEY, com.hypixel.hytale.codec.Codec.STRING);
    }

    static boolean samePouch(ItemStackItemContainer bag) {
        String expected = pouchId(bag.getOriginalItemStack());
        ItemStack current = bag.getParentContainer().getItemStack(bag.getItemStackSlot());
        return expected != null && !ItemStack.isEmpty(current)
            && SpellbookCatalog.POUCH_ID.equals(current.getItemId()) && expected.equals(pouchId(current));
    }

    public EnumSet<SpellbookCatalog> available(Store<EntityStore> store, Ref<EntityStore> ref) {
        EnumSet<SpellbookCatalog> result = EnumSet.noneOf(SpellbookCatalog.class);
        for (var section : inventorySections(store, ref)) addBooks(section, result);
        for (var bag : pouches(store, ref)) {
            addBooks(bag, result);
        }
        return result;
    }

    static void addBooks(ItemContainer container, EnumSet<SpellbookCatalog> result) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack book = container.getItemStack(slot);
            var spell = ItemStack.isEmpty(book) ? null : SpellbookCatalog.fromBook(book.getItemId());
            if (spell != null) result.add(spell);
        }
    }

    public boolean owns(Store<EntityStore> store, Ref<EntityStore> ref, SpellbookCatalog spell) {
        return available(store, ref).contains(spell);
    }

    public ItemStack collect(Store<EntityStore> store, Ref<EntityStore> ref, ItemStack books) {
        ItemStack remainder = books;
        if (SpellbookCatalog.fromBook(books.getItemId()) == null) return books;
        for (var bag : pouches(store, ref)) {
            remainder = bag.addItemStack(remainder).getRemainder();
            if (ItemStack.isEmpty(remainder)) break;
        }
        return remainder;
    }


    public void stowAddedBooks(Store<EntityStore> store, Ref<EntityStore> ref, ItemContainer source,
            List<ItemStackSlotTransaction> additions) {
        int moved = stowAddedBooks(source, additions, pouches(store, ref));
        if (moved > 0) trace(store.getComponent(ref, PlayerRef.getComponentType()), "autoStow", true, "books=" + moved);
    }


    static int stowAddedBooks(ItemContainer source, List<ItemStackSlotTransaction> additions,
            List<ItemStackItemContainer> bags) {
        int moved = 0;
        for (var addition : additions) {
            if (!addition.succeeded()) continue;
            short slot = addition.getSlot();
            if (slot < 0 || slot >= source.getCapacity()) continue;
            ItemStack book = source.getItemStack(slot);
            if (ItemStack.isEmpty(book) || !book.equals(addition.getSlotAfter())
                    || SpellbookCatalog.fromBook(book.getItemId()) == null) continue;
            ItemStack before = addition.getSlotBefore();
            int amount = book.getQuantity() - (ItemStack.isEmpty(before) ? 0 : before.getQuantity());
            if (amount <= 0) continue;
            for (var bag : bags) {
                if (source.moveItemStackFromSlot(slot, amount, bag).succeeded()) {
                    moved += amount;
                    break;
                }
            }
        }
        return moved;
    }

    public void openPouch(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        if (!menuDebounce(playerRef)) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        ItemContainer hotbar = InventoryComponentAccess.hotbar(store, ref);
        short slot = InventoryComponentAccess.activeHotbarSlot(store, ref);
        if (player == null || hotbar == null || slot < 0) return;
        ItemStack item = hotbar.getItemStack(slot);
        if (ItemStack.isEmpty(item) || !SpellbookCatalog.POUCH_ID.equals(item.getItemId())) return;
        var bag = container(player, hotbar, slot);
        boolean opened = player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, new PouchWindow(bag));
        trace(playerRef, "openPouch", opened, SpellbookCatalog.POUCH_ID + " slots=" + bag.getCapacity());
    }

    public void openSelection(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        if (!menuDebounce(playerRef)) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) player.getPageManager().openCustomPage(ref, store, new WandSpellSelectionPage(playerRef, this));
    }

    private boolean menuDebounce(PlayerRef player) {
        long now = System.nanoTime();
        Long previous = lastMenuAt.put(player.getUuid(), now);
        return previous == null || now - previous >= 400_000_000L;
    }

    public synchronized void suppressMenuChain(PlayerRef player, int chainId) {
        var chains = menuChains.computeIfAbsent(player.getUuid(), key -> new java.util.LinkedHashSet<>());
        chains.add(chainId);
        while (chains.size() > 16) chains.remove(chains.iterator().next());
    }

    public synchronized boolean isMenuChain(PlayerRef player, int chainId) {
        var chains = menuChains.get(player.getUuid());
        return chains != null && chains.contains(chainId);
    }

    public void disconnect(PlayerRef player) {
        lastMenuAt.remove(player.getUuid());
        menuChains.remove(player.getUuid());
    }

    public boolean select(Store<EntityStore> store, Ref<EntityStore> ref, SpellbookCatalog spell) {
        if (!owns(store, ref, spell)) return false;
        ItemContainer hotbar = InventoryComponentAccess.hotbar(store, ref);
        short slot = InventoryComponentAccess.activeHotbarSlot(store, ref);
        if (hotbar == null || slot < 0) return false;
        ItemStack wand = hotbar.getItemStack(slot);
        if (ItemStack.isEmpty(wand) || SpellbookCatalog.fromWand(wand.getItemId()) == null) return false;
        if (!hotbar.setItemStackForSlot(slot, reidentifyWand(wand, spell.wandId())).succeeded()) return false;
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        player.sendMessage(Message.raw("Wand selected: " + spell.title + ". " + spell.primaryHint.replace("LMB:", "Crouch+RMB:")));
        trace(player, "selectSpell", true, spell.itemId());
        return true;
    }

    @SuppressWarnings("deprecation")
    static ItemStack reidentifyWand(ItemStack original, String variant) {
        return new ItemStack(variant, 1, original.getMetadata() == null ? null : original.getMetadata().clone());
    }

    void trace(PlayerRef player, String event, boolean allow, String itemId) {
        debug.traceFileOnly(player, "SpellbookPouch event=" + event + " cancelled=false itemSource=serverInventory"
            + " itemId=" + itemId + " allow=" + allow);
    }


    private static final class PouchWindow extends ItemStackContainerWindow {
        private final ItemStackItemContainer bag;
        private Registration identityRegistration;
        PouchWindow(ItemStackItemContainer bag) { super(bag); this.bag = bag; }
        @Override public boolean onOpen0(Ref<EntityStore> ref, Store<EntityStore> store) {
            if (!samePouch(bag) || !super.onOpen0(ref, store)) return false;
            identityRegistration = bag.getParentContainer().registerChangeEvent(event -> {
                if (!samePouch(bag)) invalidate();
            });
            return true;
        }
        @Override public void onClose0(Ref<EntityStore> ref, com.hypixel.hytale.component.ComponentAccessor<EntityStore> store) {
            if (identityRegistration != null) identityRegistration.unregister();
            super.onClose0(ref, store);
        }
    }
}
