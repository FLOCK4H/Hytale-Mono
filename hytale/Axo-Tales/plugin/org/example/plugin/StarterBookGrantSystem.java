package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


public final class StarterBookGrantSystem extends TickingSystem<EntityStore> {
    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Set<UUID> claimedPlayers = new HashSet<>();
    private final Set<UUID> waitingPlayers = new HashSet<>();
    private StarterBookClaims claims;
    private final OpeningTale tale;

    public StarterBookGrantSystem(PluginErrorReporter errors, PluginDebugReporter debug) {
        this.errors = errors;
        this.debug = debug;
        this.tale = new OpeningTale(errors, debug);
    }

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getWorld() == null) {
            return;
        }
        Ref<EntityStore> ref = event.getPlayerRef();
        player.getWorld().execute(() -> {
            if (ref != null && ref.isValid()) {
                grant(ref.getStore(), ref, "PlayerReadyEvent");
            }
        });
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {

        if (tickCount % 20 != 0) {
            return;
        }
        store.forEachChunk(Query.and(Player.getComponentType(), PlayerRef.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    grant(store, chunk.getReferenceTo(i), "StarterBookRetry");
                }
            });
    }

    private synchronized void grant(Store<EntityStore> store, Ref<EntityStore> ref, String event) {
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        if (claimedPlayers.contains(player.getUuid())) { tale.show(store, ref, player); return; }
        UUID uuid = player.getUuid();
        try {
            if (claims == null) {


                claims = new StarterBookClaims(Universe.get().getPath());
            }
            if (claims.isClaimed(uuid)) {
                claimedPlayers.add(uuid);
                waitingPlayers.remove(uuid);
                tale.show(store, ref, player);
                return;
            }

            ItemStack book = new ItemStack(SpellbookInputInterceptor.SILVER_BOOK_ITEM_ID, 1);
            ItemContainer destination = null;
            for (ItemContainer inventory : new ItemContainer[] {
                    InventoryComponentAccess.hotbar(store, ref),
                    InventoryComponentAccess.storage(store, ref),
                    InventoryComponentAccess.backpack(store, ref)}) {
                if (inventory != null && inventory.canAddItemStack(book)) {
                    destination = inventory;
                    break;
                }
            }
            if (destination == null) {
                if (waitingPlayers.add(uuid)) {
                    trace(player, event, false, "inventoryFullRetryPending");
                    player.sendMessage(Message.raw("Make one inventory slot free to receive Axo's Silver Book."));
                }
                return;
            }
            if (!claims.reserve(uuid)) {
                claimedPlayers.add(uuid);
                tale.show(store, ref, player);
                return;
            }
            var transaction = destination.addItemStack(book);
            if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
                claims.release(uuid);
                trace(player, event, false, "inventoryTransactionFailedRetryPending");
                return;
            }
            claimedPlayers.add(uuid);
            waitingPlayers.remove(uuid);
            trace(player, event, true, "firstSpawnGrantPersisted");
            tale.show(store, ref, player);
        } catch (Exception failure) {

            errors.report(player, "StarterBook: could not grant/persist starter item.", failure);
        }
    }

    private void trace(PlayerRef player, String event, boolean allow, String reason) {
        debug.traceFileOnly(player, "StarterBook event=" + event
            + " cancelled=false itemId=" + SpellbookInputInterceptor.SILVER_BOOK_ITEM_ID
            + " itemSource=serverStarterGrant allow=" + allow + " reason=" + reason);
    }
}
