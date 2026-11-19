package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.*;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.component.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.*;
import org.bson.*;
import org.joml.Vector3d;


final class RelicBowSystem {
    static final String EVERFLAME="Axo_Everflame_Shortbow",MIRETHORN="Axo_Mirethorn_Shortbow";
    static final double[] CHARGE={.1,.3,.6,.9,1.2};
    private record Draw(int chain,ItemStack bow,byte slot,AncientArrowAmmo.Slot ammo,long started){}
    private final Map<Ref<EntityStore>,Draw> draws=Collections.synchronizedMap(new WeakHashMap<>());
    private final ScoutGearSystem scout;
    private final PluginDebugReporter debug;
    RelicBowSystem(ScoutGearSystem scout,PluginDebugReporter debug){this.scout=scout;this.debug=debug;}
    static boolean bow(String id){return EVERFLAME.equals(id)||MIRETHORN.equals(id);}
    static boolean charged(int strength,double seconds){return strength>=0&&strength<CHARGE.length&&Double.isFinite(seconds)&&seconds+.025>=CHARGE[strength];}
    boolean handle(InteractionContext context,String mode){
        var access=context.getCommandBuffer();var owner=context.getEntity();
        if(access==null||owner==null||!owner.isValid()||context.getChain()==null)return false;
        var held=InventoryComponentAccess.itemInHand(access,owner);var player=access.getComponent(owner,PlayerRef.getComponentType());
        if(player==null||ItemStack.isEmpty(held)||!bow(held.getItemId())||held.isBroken()||!ScoutGearSystem.living(owner,access)||access.getComponent(owner,Spectating.getComponentType())!=null)return false;
        int chain=context.getChain().getChainId();byte slot=InventoryComponentAccess.activeHotbarSlot(access,owner);
        if("draw".equals(mode)){
            var ammo=RelicBowAmmo.find(access,owner);boolean allow=EVERFLAME.equals(held.getItemId())||ammo!=null;
            if(allow)draws.put(owner,new Draw(chain,held,slot,ammo,System.nanoTime()));
            else {draws.remove(owner);player.sendMessage(Message.raw("Mirethorn needs an arrow."));}
            trace(context,mode,allow);return allow;
        }
        if(!mode.startsWith("fire"))return false;
        var draw=draws.remove(owner);int strength;
        try{strength=Integer.parseInt(mode.substring(4));}catch(NumberFormatException invalid){return false;}
        boolean allow=draw!=null&&draw.chain==chain&&draw.slot==slot&&Objects.equals(draw.bow,held)&&charged(strength,(System.nanoTime()-draw.started)/1e9)
            &&(draw.ammo==null||RelicBowAmmo.current(draw.ammo));
        if(!allow){trace(context,"releaseTokenOrAmmoMismatch",false);return false;}
        boolean infinity=EVERFLAME.equals(held.getItemId());Ref<EntityStore> arrow;
        var modifiers=ImpactModifiers.collect(context,owner,access);
        if(draw.ammo!=null&&AncientArrowAmmo.ID.equals(draw.ammo.original().getItemId())){
            arrow=scout.fireAncient(context,draw.ammo,!infinity);
        }else{
            var config=ProjectileConfig.getAssetMap().getAsset("AxoRelicBow_"+(infinity?"Everflame":"Mirethorn")+"_"+strength);
            var look=TargetUtil.getLook(owner,access);if(config==null||look==null||!look.getPosition().isFinite()||!look.getDirection().isFinite()||look.getDirection().lengthSquared()<.001)return false;
            arrow=ProjectileModule.get().spawnProjectile(owner,access,config,new Vector3d(look.getPosition()),new Vector3d(look.getDirection()).normalize());
            if(arrow!=null){
                try{if(draw.ammo!=null)appearance(arrow,draw.ammo.original().getItemId(),access);}
                catch(RuntimeException invalidAppearance){access.removeEntity(arrow,RemoveReason.REMOVE);throw invalidAppearance;}
                if(!RelicBowAmmo.spend(draw.ammo,infinity)){access.removeEntity(arrow,RemoveReason.REMOVE);trace(context,"staleReceipt",false);return false;}
            }
        }
        if(arrow==null){trace(context,"projectileNotCreated",false);return false;}
        access.putComponent(arrow,RelicBowShot.TYPE,new RelicBowShot(held.getItemId()));
        access.putComponent(arrow,ImpactModifiers.getComponentType(),modifiers);
        access.putComponent(arrow,ProjectileLaunchContext.getComponentType(),new ProjectileLaunchContext(InteractionType.Primary,true));
        access.invoke(owner,new com.hypixel.hytale.server.core.modules.projectile.event.ProjectileLaunchEvent(arrow,InteractionType.Primary,true));
        var items=InventoryComponentAccess.hotbar(access,owner);
        if(items!=null&&slot>=0&&slot<items.getCapacity()&&Objects.equals(items.getItemStack(slot),held))items.setItemStackForSlot(slot,held.withDurability(Math.max(0,held.getDurability()-.35)),false);
        trace(context,"released:"+(draw.ammo==null?"conjured":draw.ammo.original().getItemId()),true);return true;
    }
    private static void appearance(Ref<EntityStore> arrow,String id,CommandBuffer<EntityStore> access){
        var item=Item.getAssetMap().getAsset(id);var existing=access.getComponent(arrow,ModelComponent.getComponentType());
        if(item==null||item.getModel()==null||item.getTexture()==null||existing==null)return;
        var doc=new BsonDocument("Model",new BsonString(item.getModel())).append("Texture",new BsonString(item.getTexture()))
            .append("HitBox",com.hypixel.hytale.math.shape.Box.CODEC.encode(existing.getModel().getBoundingBox(),new com.hypixel.hytale.codec.ExtraInfo()));
        var asset=ModelAsset.CODEC.decode(doc,new com.hypixel.hytale.codec.ExtraInfo());
        access.putComponent(arrow,ModelComponent.getComponentType(),new ModelComponent(Model.createUnitScaleModel(asset,existing.getModel().getBoundingBox())));
    }
    private void trace(InteractionContext context,String event,boolean allow){
        var access=context.getCommandBuffer();var owner=context.getEntity();var held=InventoryComponentAccess.itemInHand(access,owner);var stats=access.getComponent(owner,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getMana();var mana=stats==null||index<0?null:stats.get(index);
        debug.traceFileOnly(access.getComponent(owner,PlayerRef.getComponentType()),"RelicBow event="+event+" cancelled=false source=serverInteractionAndExactAmmoReceipt item="+held.getItemId()+" section=Hotbar slot="+InventoryComponentAccess.activeHotbarSlot(access,owner)+" mana.index="+index+" mana.current="+(mana==null?"missing":mana.get())+" mana.min="+(mana==null?"missing":mana.getMin())+" mana.max="+(mana==null?"missing":mana.getMax())+" mana.cost=0 allow="+allow);
    }
}
