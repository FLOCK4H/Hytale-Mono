package org.example.plugin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;


final class AncientArrowAmmo {
    static final String ID="Axo_Ancient_Arrow";
    record Slot(ItemContainer container,short index,ItemStack original){
        boolean consume(){return container.removeItemStackFromSlot(index,original,1).succeeded();}
    }
    static Slot find(ComponentAccessor<EntityStore> store,Ref<EntityStore> owner){
        return find(Arrays.asList(InventoryComponentAccess.hotbar(store,owner),InventoryComponentAccess.storage(store,owner),InventoryComponentAccess.backpack(store,owner)));
    }
    static Slot find(List<ItemContainer> containers){
        for(var items:containers){if(items==null)continue;for(short slot=0;slot<items.getCapacity();slot++){
            var item=items.getItemStack(slot);if(!ItemStack.isEmpty(item)&&ID.equals(item.getItemId())&&item.getQuantity()>0)return new Slot(items,slot,item);
        }}return null;
    }
}
