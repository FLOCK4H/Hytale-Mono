package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;


public final class SpellbookPouchInventorySystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {
    private final SpellbookPouchService pouches;
    private final PluginErrorReporter errors;
    public SpellbookPouchInventorySystem(SpellbookPouchService pouches, PluginErrorReporter errors) {
        super(InventoryChangeEvent.class);
        this.pouches = pouches;
        this.errors = errors;
    }
    @Override public Query<EntityStore> getQuery() { return Player.getComponentType(); }
    @Override public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull InventoryChangeEvent event) {
        if (!(event.getTransaction() instanceof ItemStackTransaction addition)
                || addition.getAction() != ActionType.ADD || !addition.succeeded()
                || addition.getQuery() == null || SpellbookCatalog.fromBook(addition.getQuery().getItemId()) == null) return;
        var ref = chunk.getReferenceTo(index);
        var source = event.getItemContainer();
        var slots = java.util.List.copyOf(addition.getSlotTransactions());

        store.getExternalData().getWorld().execute(() -> {
            try {
                if (ref.isValid()) pouches.stowAddedBooks(store, ref, source, slots);
            } catch (Exception failure) {
                errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "SpellbookPouch: auto-stow failed.", failure);
            }
        });
    }
}
