package org.example.plugin;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.*;


final class LeechBlood {
    static final Map<String,String> SPECIES=Map.of("Rex_Cave","Rex","Dreadmaw","Dreadmaw","Yeti","Yeti","Emberwulf","Emberwulf");
    static String itemFor(String species) {
        String name=SPECIES.get(species);return name==null?BloodSample.BLOOD:AxoItems.LEGACY.get("AxoTales_Blood_"+name);
    }
    static boolean blood(String id){return BloodSample.BLOOD.equals(AxoItems.canonical(id))||SPECIES.keySet().stream().anyMatch(species->itemFor(species).equals(AxoItems.canonical(id)));}
    static boolean matches(String id,String species){return BloodSample.BLOOD.equals(id)||itemFor(species).equals(id);}
    @SuppressWarnings("deprecation")
    static ItemStack upgrade(ItemStack old) {
        if(ItemStack.isEmpty(old)||!BloodSample.BLOOD.equals(old.getItemId()))return old;
        var sample=BloodSample.read(old,BloodSample.BLOOD);if(sample==null)return old;
        String id=itemFor(sample.species);if(id.equals(old.getItemId()))return old;
        return new ItemStack(id,old.getQuantity(),old.getDurability(),old.getMaxDurability(),old.getQualityIndex(),old.getMetadata());
    }
    static int upgrade(List<ItemContainer> inventories) {
        int changed=0;
        for(var items:inventories)for(short slot=0;slot<items.getCapacity();slot++) {
            var old=items.getItemStack(slot);var replacement=upgrade(old);
            if(replacement!=old&&items.replaceItemStackInSlot(slot,old,replacement).succeeded())changed++;
        }
        return changed;
    }
}
