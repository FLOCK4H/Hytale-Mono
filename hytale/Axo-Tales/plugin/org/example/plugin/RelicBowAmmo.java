package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;


final class RelicBowAmmo {
    static boolean arrow(String id){
        if(AncientArrowAmmo.ID.equals(id)||id.startsWith("Weapon_Arrow_"))return true;
        var item=Item.getAssetMap().getAsset(id);var data=item==null?null:item.getData();
        var tags=data==null?null:data.getRawTags();var family=tags==null?null:tags.get("Family");
        return family!=null&&Arrays.asList(family).contains("Arrow");
    }
    static AncientArrowAmmo.Slot find(ComponentAccessor<EntityStore> access,Ref<EntityStore> owner){
        return find(Arrays.asList(InventoryComponentAccess.hotbar(access,owner),InventoryComponentAccess.storage(access,owner),InventoryComponentAccess.backpack(access,owner)));
    }
    static AncientArrowAmmo.Slot find(List<ItemContainer> containers){

        var ancient=AncientArrowAmmo.find(containers);if(ancient!=null)return ancient;
        for(var items:containers){if(items==null)continue;for(short slot=0;slot<items.getCapacity();slot++){
            var item=items.getItemStack(slot);if(!ItemStack.isEmpty(item)&&item.getQuantity()>0&&arrow(item.getItemId()))return new AncientArrowAmmo.Slot(items,slot,item);
        }}return null;
    }
    static boolean current(AncientArrowAmmo.Slot slot){return slot!=null&&Objects.equals(slot.original(),slot.container().getItemStack(slot.index()));}
    static boolean spend(AncientArrowAmmo.Slot slot,boolean infinity){return infinity?(slot==null||current(slot)):current(slot)&&slot.consume();}
}
