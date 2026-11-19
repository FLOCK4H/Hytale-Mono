package org.example.plugin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


final class ArcaneArmorSets {
    static final int ROBE_DETECTION_BLOCKS=5;
    private ArcaneArmorSets() {}
    static boolean robe(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref) {
        return matches(store,ref,"Axo_Arcane_Veil_Hood","Axo_Arcane_Veil_Robe","Axo_Arcane_Veil_Leggings");
    }
    static boolean sar(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref) {
        return matches(store,ref,"Axo_Sar_Diadem","Axo_Sar_Armor","Axo_Sar_Boots")
            && "Axo_Sar_Warfists".equals(InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Hands));
    }
    static boolean ancientPieces(String head,String chest,String hands,String legs) {
        return "Axo_Ancient_Helm".equals(head)
            && "Axo_Ancient_Chestplate".equals(chest)
            && "Axo_Ancient_Gauntlets".equals(hands)
            && "Axo_Ancient_Boots".equals(legs);
    }
    static boolean ancient(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref) {
        return ancientPieces(InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Head),
            InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Chest),
            InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Hands),
            InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Legs));
    }

    static boolean relic(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref) {
        return sar(store,ref) || ancient(store,ref);
    }
    private static boolean matches(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref,String head,String chest,String legs) {
        return head.equals(InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Head))
            && chest.equals(InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Chest))
            && legs.equals(InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Legs));
    }
    static boolean concealed(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref,double distanceSquared) {
        int blocks=detectionBlocks(store,ref);return blocks>0&&distanceSquared>blocks*blocks;
    }
    static int detectionBlocks(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref){
        if(SkillCatalog.has(SkillProgression.mask(store,ref),15)&&com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(InventoryComponentAccess.itemInHand(store,ref)))return 2;
        return AncientArmor.worn(store,ref,ItemArmorSlot.Chest,AncientArmor.CHEST)?4:robe(store,ref)?ROBE_DETECTION_BLOCKS:0;
    }
}
