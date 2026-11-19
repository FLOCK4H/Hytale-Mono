package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PickupItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerItemEntityPickupSystem;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.system.PlayerSpatialSystem;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.Set;
import java.util.ArrayList;


public final class SpellbookPouchPickupSystem extends EntityTickingSystem<EntityStore> {
    private final SpellbookPouchService pouches;
    private final PluginErrorReporter errors;
    public SpellbookPouchPickupSystem(SpellbookPouchService pouches, PluginErrorReporter errors) {
        this.pouches = pouches;
        this.errors = errors;
    }
    @Override public Query<EntityStore> getQuery() {
        return Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType(),
            Query.not(PickupItemComponent.getComponentType()), Query.not(Interactable.getComponentType()),
            Query.not(PreventPickup.getComponentType()), Query.not(PropComponent.getComponentType()));
    }
    @Override public boolean isParallel(int archetypeSize, int taskCount) { return false; }
    @Override public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, PlayerSpatialSystem.class),
            new SystemDependency<>(Order.BEFORE, PlayerItemEntityPickupSystem.class));
    }
    @Override public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            ItemComponent item = chunk.getComponent(index, ItemComponent.getComponentType());
            if (item == null || !item.canPickUp() || item.isRemovedByPlayerPickup()) return;
            ItemStack books = item.getItemStack();
            if (ItemStack.isEmpty(books) || SpellbookCatalog.fromBook(books.getItemId()) == null) return;
            var interactions = books.getItem().getInteractions();
            if (interactions != null && interactions.containsKey(com.hypixel.hytale.protocol.InteractionType.Pickup)) return;
            var position = chunk.getComponent(index, TransformComponent.getComponentType()).getPosition();
            double radius = item.getPickupRadius(store);
            var spatial = store.getResource(EntityModule.get().getPlayerSpatialResourceType());
            if (spatial == null || radius <= 0) return;
            var candidates = new ArrayList<Ref<EntityStore>>();
            spatial.getSpatialStructure().ordered(position, radius, candidates);
            Ref<EntityStore> recipient = null;
            ItemStack remainder = books;
            for (var candidate : candidates) {
                if (!candidate.isValid() || store.getComponent(candidate, DeathComponent.getComponentType()) != null
                        || store.getComponent(candidate, Spectating.getComponentType()) != null) continue;
                remainder = pouches.collect(store, candidate, books);
                if (ItemStack.isEmpty(remainder) || remainder.getQuantity() < books.getQuantity()) {
                    recipient = candidate;
                    break;
                }

                var settings = store.getComponent(candidate, PlayerSettings.getComponentType());
                if (settings == null) settings = PlayerSettings.defaults();
                var ordinary = InventoryUtils.getContainerForItemPickup(candidate, books.getItem(), settings, store);
                if (ordinary != null && ordinary.canAddItemStack(books)) return;
            }
            if (recipient == null) return;
            int remaining = ItemStack.isEmpty(remainder) ? 0 : remainder.getQuantity();
            int accepted = books.getQuantity() - remaining;
            if (accepted <= 0) return;
            if (remaining == 0) {
                item.setRemovedByPlayerPickup(true);
                buffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE);
            } else {
                item.setItemStack(remainder);
            }

            Player.notifyPickupItem(recipient, books.withQuantity(accepted), position, store);
            pouches.trace(store.getComponent(recipient, PlayerRef.getComponentType()), "groundPickup", true, books.getItemId());
        } catch (Exception failure) {
            errors.report((PlayerRef) null, "SpellbookPouch: ground pickup failed.", failure);
        }
    }
}
