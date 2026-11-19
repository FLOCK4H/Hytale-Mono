package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


final class AncientArmor {
    static final String HEAD="Axo_Ancient_Helm",CHEST="Axo_Ancient_Chestplate",HANDS="Axo_Ancient_Gauntlets",BOOTS="Axo_Ancient_Boots";
    private AncientArmor(){}
    static boolean worn(ComponentAccessor<EntityStore> access,Ref<EntityStore> ref,ItemArmorSlot slot,String id){return id.equals(InventoryComponentAccess.armorItemId(access,ref,slot));}

    static boolean capacity(EntityStatMap stats,int index,String percentKey,float multiplier,String flatKey,float bonus){
        if(stats==null||index<0||stats.get(index)==null)return false;
        float targetFactor=factor(stats,index,percentKey)*multiplier;
        float amount=bonus>0&&targetFactor>0&&Float.isFinite(targetFactor)?bonus/targetFactor:0;
        var old=stats.getModifier(index,flatKey);float previous=old instanceof StaticModifier m?m.getAmount():0;
        boolean changed=false;
        if(amount>previous)changed|=flat(stats,index,flatKey,amount);
        changed|=ArcaneRobeSystem.percentage(stats,index,percentKey,multiplier);
        if(amount<=previous)changed|=flat(stats,index,flatKey,amount);
        return changed;
    }
    private static float factor(EntityStatMap stats,int index,String except){
        float factor=0;boolean scaled=false;
        var modifiers=stats.get(index).getModifiers();
        if(modifiers!=null)for(var entry:modifiers.entrySet())if(!entry.getKey().equals(except)&&entry.getValue() instanceof StaticModifier m
                &&m.getTarget()==Modifier.ModifierTarget.MAX&&m.getCalculationType()==StaticModifier.CalculationType.MULTIPLICATIVE){factor+=m.getAmount();scaled=true;}
        return scaled?factor:1;
    }
    private static boolean flat(EntityStatMap stats,int index,String key,float amount){
        var old=stats.getModifier(index,key);
        if(amount==0){if(old==null)return false;stats.removeModifier(index,key);return true;}
        if(old instanceof StaticModifier m&&m.getAmount()==amount&&m.getTarget()==Modifier.ModifierTarget.MAX&&m.getCalculationType()==StaticModifier.CalculationType.ADDITIVE)return false;
        stats.putModifier(index,key,new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,amount));return true;
    }
}
