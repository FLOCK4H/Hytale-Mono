package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;


public final class AncientGearSystem extends TickingSystem<EntityStore> {
    static final String PRIMARY="AxoAncient_Unarmed_Primary";
    private final ComponentType<EntityStore,AncientFlightState> fuelType;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final Map<Ref<EntityStore>,Runtime> players=new ConcurrentHashMap<>();
    private static final class Runtime {
        PlayerRef player;HudManager manager;AncientFlightHud hud;
        boolean initialized,jumpPressed,ownsPrimary,blockedPrimary,ownsFlight;
        String originalPrimary;int skillJumps=-1;Boolean jumpsAttuned;Item jumpDefinition;
        float helmTime,setTime;
        EntityStatType staminaType;List<RegeneratingValue> regen=List.of();
    }
    AncientGearSystem(ComponentType<EntityStore,AncientFlightState> type,PluginDebugReporter debug,PluginErrorReporter errors){fuelType=type;this.debug=debug;this.errors=errors;}
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        if(!Float.isFinite(delta)||delta<=0)return;
        store.forEachChunk(Query.and(Player.getComponentType(),PlayerRef.getComponentType(),MovementStatesComponent.getComponentType(),MovementManager.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);buffer.run(access->{if(ref.isValid())try{refresh(access,ref,delta);}catch(RuntimeException failure){errors.report((PlayerRef)null,"Ancient gear refresh failed",failure);}});}
        });
        for(var it=players.entrySet().iterator();it.hasNext();){var entry=it.next();if(!entry.getKey().isValid()){removeHud(entry.getValue());it.remove();}}
    }
    private void refresh(Store<EntityStore> store,Ref<EntityStore> ref,float delta){
        boolean chest=AncientArmor.worn(store,ref,ItemArmorSlot.Chest,AncientArmor.CHEST);
        boolean helm=AncientArmor.worn(store,ref,ItemArmorSlot.Head,AncientArmor.HEAD);
        boolean hands=AncientArmor.worn(store,ref,ItemArmorSlot.Hands,AncientArmor.HANDS);
        boolean full=ArcaneArmorSets.ancient(store,ref);
        var fuel=store.getComponent(ref,fuelType);var runtime=players.get(ref);
        if(!chest&&!helm&&!hands&&!full&&fuel==null&&runtime==null)return;
        if(runtime==null){runtime=new Runtime();runtime.player=store.getComponent(ref,PlayerRef.getComponentType());runtime.manager=store.getComponent(ref,Player.getComponentType()).getHudManager();players.put(ref,runtime);}
        var player=store.getComponent(ref,Player.getComponentType());var manager=store.getComponent(ref,MovementManager.getComponentType());
        var states=store.getComponent(ref,MovementStatesComponent.getComponentType()).getMovementStates();
        boolean living=store.getComponent(ref,DeathComponent.getComponentType())==null;
        if(fuel==null&&(chest||hands))fuel=store.addComponent(ref,fuelType);
        if(!runtime.initialized){
            runtime.initialized=true;
            if(fuel!=null&&fuel.primaryBound){var current=store.getComponent(ref,Interactions.getComponentType());runtime.originalPrimary=fuel.originalPrimary;runtime.ownsPrimary=current!=null&&PRIMARY.equals(current.getInteractionId(InteractionType.Primary));runtime.blockedPrimary=!runtime.ownsPrimary;}
            if(fuel!=null&&fuel.wasActive){fuel.active=fuel.wasActive=false;if(player.getGameMode()!=GameMode.Creative){if(manager.getSettings().fly==FlyMode.Forced)manager.getSettings().fly=manager.getDefaultSettings().fly;player.setFlying(ref,false,store);manager.update(runtime.player.getPacketHandler());}}
        }
        bindPrimary(store,ref,runtime,hands&&living&&ItemStack.isEmpty(InventoryComponentAccess.itemInHand(store,ref))&&AncientCombatSystem.airCast(ref,store));
        if(fuel!=null&&(fuel.primaryBound!=runtime.ownsPrimary||!Objects.equals(fuel.originalPrimary,runtime.originalPrimary))){fuel.primaryBound=runtime.ownsPrimary;fuel.originalPrimary=runtime.originalPrimary;store.putComponent(ref,fuelType,fuel.clone());}
        updateJumps(runtime,full&&living,living&&SkillCatalog.has(SkillProgression.mask(store,ref),12)?1:0);
        if(living)regenerate(store,ref,runtime,helm,full,delta);else{runtime.helmTime=runtime.setTime=0;runtime.regen=List.of();}
        if(fuel==null)return;
        boolean chord=states.crouching&&states.jumping;
        boolean toggled=chord&&!runtime.jumpPressed;runtime.jumpPressed=chord;
        boolean before=fuel.active;float previousFuel=fuel.fuel;
        boolean controllerAvailable=manager.getSettings().fly==FlyMode.Disabled&&player.getMountEntityId()==0;
        if(!chest||!living){fuel.active=fuel.wasActive=false;}
        else if(toggled){
            fuel.toggle(controllerAvailable,states.onGround);
            if(fuel.active&&!before){runtime.ownsFlight=AncientFlightControl.begin(player,manager,runtime.player,ref,store);if(!runtime.ownsFlight)fuel.active=fuel.wasActive=false;}
            trace(runtime.player,"flightToggle",ref,store,fuel.active,"fuel="+fuel.fuel+" item="+AncientArmor.CHEST);
        }
        if(fuel.active&&(manager.getSettings().fly!=FlyMode.Forced||player.getMountEntityId()!=0)){fuel.active=fuel.wasActive=false;}
        fuel.advance(delta,states.onGround&&living);
        if(runtime.ownsFlight&&!fuel.active)stopFlight(store,ref,runtime,player,manager);
        if(before!=fuel.active||previousFuel!=fuel.fuel)store.putComponent(ref,fuelType,fuel.clone());
        if(chest&&living){if(runtime.hud==null){runtime.hud=new AncientFlightHud(runtime.player);runtime.manager.addCustomHud(runtime.player,runtime.hud);}runtime.hud.refresh(fuel.fuel,fuel.active);}else removeHud(runtime);
    }
    private void stopFlight(Store<EntityStore> store,Ref<EntityStore> ref,Runtime runtime,Player player,MovementManager manager){

        AncientFlightControl.end(player,manager,runtime.player,ref,store);
        runtime.ownsFlight=false;trace(runtime.player,"flightEnded",ref,store,true,"controller=nativePlayer fuelRefill=landing");
    }
    private static void bindPrimary(Store<EntityStore> store,Ref<EntityStore> ref,Runtime runtime,boolean enabled){
        var current=store.getComponent(ref,Interactions.getComponentType());String id=current==null?null:current.getInteractionId(InteractionType.Primary);
        if(runtime.ownsPrimary&&!PRIMARY.equals(id)){runtime.ownsPrimary=false;runtime.blockedPrimary=true;}
        if(!enabled){
            if(runtime.ownsPrimary){var next=(Interactions)current.clone();if(runtime.originalPrimary==null)next.removeInteractionId(InteractionType.Primary);else next.setInteractionId(InteractionType.Primary,runtime.originalPrimary);store.putComponent(ref,Interactions.getComponentType(),next);runtime.ownsPrimary=false;}
            runtime.blockedPrimary=false;return;
        }
        if(!runtime.ownsPrimary&&!runtime.blockedPrimary){runtime.originalPrimary=id;var next=current==null?new Interactions():(Interactions)current.clone();next.setInteractionId(InteractionType.Primary,PRIMARY);store.putComponent(ref,Interactions.getComponentType(),next);runtime.ownsPrimary=true;}
    }
    private static void updateJumps(Runtime runtime,boolean full,int skillJumps){
        Item definition=Item.getAssetMap().getAsset(AncientArmor.BOOTS);if(definition==null)return;
        if(runtime.jumpsAttuned!=null&&runtime.jumpsAttuned==full&&runtime.jumpDefinition==definition&&runtime.skillJumps==skillJumps)return;
        var original=definition.toPacket();if(original.armor==null||original.armor.movementSettings==null)return;
        runtime.player.getPacketHandler().writeNoCache(AncientJumpSettings.packet(original,full,skillJumps));runtime.skillJumps=skillJumps;runtime.jumpsAttuned=full;runtime.jumpDefinition=definition;
    }
    private static void regenerate(Store<EntityStore> store,Ref<EntityStore> ref,Runtime runtime,boolean helm,boolean full,float delta){
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());if(stats==null)return;
        double helmElapsed=helm?(double)runtime.helmTime+delta:0,setElapsed=full?(double)runtime.setTime+delta:0;
        runtime.helmTime=(float)(helmElapsed%4);runtime.setTime=(float)(setElapsed%2);
        double pulses=Math.floor(helmElapsed/4)+Math.floor(setElapsed/2);
        int manaIndex=DefaultEntityStatTypes.getMana();var mana=manaIndex<0?null:stats.get(manaIndex);
        if(pulses>0&&mana!=null)stats.addStatValue(manaIndex,(float)Math.min(mana.getMax()-mana.get(),pulses*10));
        int index=DefaultEntityStatTypes.getStamina();var type=index<0?null:EntityStatType.getAssetMap().getAsset(index);var value=index<0?null:stats.get(index);
        if(!full){runtime.regen=List.of();runtime.staminaType=null;}
        else if(type!=null&&value!=null){
            if(runtime.staminaType!=type){runtime.staminaType=type;runtime.regen=type.getRegenerating()==null?List.of():Arrays.stream(type.getRegenerating()).filter(r->r.getAmount()>0).map(RegeneratingValue::new).toList();}
            float bonus=0;for(var regen:runtime.regen)bonus+=Math.max(0,regen.regenerate(store,ref,Instant.now(),delta,value,value.get()))*.5f;
            if(bonus>0)stats.addStatValue(index,bonus);
        }
        stats.update();
    }
    private static void removeHud(Runtime runtime){if(runtime.hud!=null){if(runtime.manager.getCustomHud(AncientFlightHud.KEY)==runtime.hud)runtime.manager.removeCustomHud(runtime.player,AncientFlightHud.KEY);runtime.hud=null;}}
    void disconnect(PlayerRef player){for(var entry:new ArrayList<>(players.entrySet()))if(entry.getValue().player.getUuid().equals(player.getUuid()))release(entry.getKey(),entry.getValue());}
    private void release(Ref<EntityStore> ref,Runtime runtime){
        removeHud(runtime);
        if(ref.isValid()){var store=ref.getStore();store.getExternalData().getWorld().execute(()->{
            if(!ref.isValid())return;bindPrimary(store,ref,runtime,false);updateJumps(runtime,false,0);
            var fuel=store.getComponent(ref,fuelType);if(fuel!=null){fuel.active=fuel.wasActive=false;fuel.primaryBound=runtime.ownsPrimary;fuel.originalPrimary=runtime.originalPrimary;store.putComponent(ref,fuelType,fuel.clone());}
            if(runtime.ownsFlight)stopFlight(store,ref,runtime,store.getComponent(ref,Player.getComponentType()),store.getComponent(ref,MovementManager.getComponentType()));
        });}
        players.remove(ref);
    }
    void close(){for(var entry:new ArrayList<>(players.entrySet()))release(entry.getKey(),entry.getValue());}
    private void trace(PlayerRef player,String event,Ref<EntityStore> ref,ComponentAccessor<EntityStore> store,boolean allow,String details){
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getStamina();var v=stats==null||index<0?null:stats.get(index);
        debug.traceFileOnly(player,"AncientGear event="+event+" cancelled=false source=nativeMovementStates crouchJump=true allow="+allow+" stamina.index="+index+" stamina="+(v==null?"missing":v.get()+"/"+v.getMax()+" min="+v.getMin())+" "+details);
    }
}
