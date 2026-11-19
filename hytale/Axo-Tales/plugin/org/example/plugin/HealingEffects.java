package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.*;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


final class HealingEffects {
    private HealingEffects(){}
    static boolean harmful(EntityEffect effect){
        if(effect.isDebuff()||"Freeze".equals(effect.getId()))return true;

        var application=effect.getApplicationEffects();
        if(application!=null&&application.getHorizontalSpeedMultiplier()<1)return true;
        var damage=effect.getDamageCalculator();
        if(damage!=null){float[] range=new float[2];damage.computeDamageRange(1,range);return range[1]>0;}
        return false;
    }
    static int cleanse(Ref<EntityStore> target,ComponentAccessor<EntityStore> store){
        var effects=store.getComponent(target,EffectControllerComponent.getComponentType());
        return effects==null?0:cleanse(effects,target,store);
    }
    static int cleanse(EffectControllerComponent effects,Ref<EntityStore> target,ComponentAccessor<EntityStore> store){
        int removed=0;
        for(int index:effects.getActiveEffects().keySet().toIntArray()){
            var effect=EntityEffect.getAssetMap().getAsset(index);
            if(effect!=null&&harmful(effect)){

                effects.removeEffect(target,index,RemovalBehavior.COMPLETE,store);removed++;
            }
        }
        return removed;
    }
}
