package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


final class SkillInvisibility extends TickingSystem<EntityStore> {
    static final String EFFECT="AxoTales_Evanesco";
    private final PluginDebugReporter debug;
    private final Map<Ref<EntityStore>,Boolean> announced=new ConcurrentHashMap<>();
    SkillInvisibility(PluginDebugReporter debug){this.debug=debug;}
    static boolean handsEmpty(InventoryComponent.Hotbar hotbar,InventoryComponent.Tool tools,InventoryComponent.Utility utility){


        return hotbar!=null&&utility!=null&&ItemStack.isEmpty(hotbar.getActiveItem())&&ItemStack.isEmpty(utility.getActiveItem())
            &&(tools==null||!tools.isUsingToolsItem()||ItemStack.isEmpty(tools.getActiveItem()));
    }
    static boolean eligible(ComponentAccessor<EntityStore> access,Ref<EntityStore> ref){
        int mask=SkillProgression.mask(access,ref);return SkillCatalog.has(mask,SkillCatalog.EVANESCO)&&ArcanePickaxe.living(access,ref)
            &&handsEmpty(InventoryComponentAccess.hotbarComponent(access,ref),InventoryComponentAccess.toolComponent(access,ref),InventoryComponentAccess.utilityComponent(access,ref));
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        int index=EntityEffect.getAssetMap().getIndexOrDefault(EFFECT,-1);var effect=EntityEffect.getAssetMap().getAsset(EFFECT);if(index<0||effect==null)return;
        store.forEachChunk(Query.and(Player.getComponentType(),EffectControllerComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var ref=chunk.getReferenceTo(i);var controller=chunk.getComponent(i,EffectControllerComponent.getComponentType());var active=controller.getActiveEffects().get(index);
                boolean allowed=eligible(buffer,ref);var previous=announced.put(ref,allowed);
                if(debug!=null&&(previous==null?allowed:previous!=allowed)){
                    var player=buffer.getComponent(ref,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                    debug.traceFileOnly(player,"Invisibility event=EvanescoEligibility active="+allowed+" selected="+id(InventoryComponentAccess.activeHotbarItem(buffer,ref))
                        +" effective="+id(InventoryComponentAccess.itemInHand(buffer,ref))+" utility="+id(InventoryComponentAccess.utilityItem(buffer,ref))+" effectIndex="+index+" source=nativeInventory modelSwap=false");
                }
                if(allowed){if(active==null||active.getRemainingDuration()<.2f)controller.addEffect(ref,index,effect,.5f,OverlapBehavior.OVERWRITE,buffer);}
                else if(active!=null)controller.removeEffect(ref,index,buffer);
            }
        });
        announced.keySet().removeIf(ref->!ref.isValid());
    }
    private static String id(ItemStack item){return ItemStack.isEmpty(item)?"empty":item.getItemId();}
}
