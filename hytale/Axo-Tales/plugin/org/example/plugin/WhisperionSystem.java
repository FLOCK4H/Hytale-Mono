package org.example.plugin;

import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import javax.annotation.Nonnull;
import java.util.*;


public final class WhisperionSystem extends TickingSystem<EntityStore> {
    public static final String ROLE="Whisperion";
    private final ComponentType<EntityStore,WhisperionState> stateType;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final WhisperionFlight flight;
    private final WhisperionPets pets;
    private final WhisperionFollowing following;
    private final Map<Store<EntityStore>,Long> nextTick=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Store<EntityStore>,Long> nextRecall=Collections.synchronizedMap(new WeakHashMap<>());
    public WhisperionSystem(ComponentType<EntityStore,WhisperionState> stateType,ComponentType<EntityStore,WhisperionRide> rideType,WhisperionPets pets,PluginDebugReporter debug,PluginErrorReporter errors) {
        this.stateType=stateType;this.pets=pets;this.debug=debug;this.errors=errors;this.flight=new WhisperionFlight(stateType,rideType,debug,errors);
        this.following=new WhisperionFollowing(stateType,pets,debug,errors);
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store) {


        flight.tick(store);
        long now=System.nanoTime();if(now<nextTick.getOrDefault(store,0L))return;nextTick.put(store,now+200_000_000L);
        store.forEachChunk(Query.and(NPCEntity.getComponentType(),TransformComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var ref=chunk.getReferenceTo(i);var npc=chunk.getComponent(i,NPCEntity.getComponentType());
                if(!ROLE.equals(npc.getRoleName()) && buffer.getComponent(ref,stateType)==null)continue;
                buffer.run(s->{try{reconcile(s,ref);}catch(Throwable failure){errors.report((PlayerRef)null,"Whisperion reconciliation failed",failure);}});
            }
        });
        if(now>=nextRecall.getOrDefault(store,0L)) {
            nextRecall.put(store,now+2_000_000_000L);var owners=new HashSet<UUID>();
            store.forEachChunk(PlayerRef.getComponentType(),(chunk,buffer)->{for(int i=0;i<chunk.size();i++)owners.add(chunk.getComponent(i,PlayerRef.getComponentType()).getUuid());});
            pets.recall(owners);
        }
    }
    private WhisperionState state(Store<EntityStore> store,Ref<EntityStore> ref) {
        var value=store.getComponent(ref,stateType);
        if(value==null){value=new WhisperionState();value.bornAt=System.currentTimeMillis();store.putComponent(ref,stateType,value);}
        else if(value.bornAt==0){value.bornAt=System.currentTimeMillis();store.putComponent(ref,stateType,value);}
        return value;
    }
    private void reconcile(Store<EntityStore> store,Ref<EntityStore> ref) {
        if(!ref.isValid())return;
        if(store.getComponent(ref,DeathComponent.getComponentType())!=null){try{var id=store.getComponent(ref,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());if(id!=null)pets.forget(id.getUuid());}catch(java.io.IOException failure){errors.report((PlayerRef)null,"Whisperion death roster cleanup failed",failure);}return;}
        var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc==null)return;
        var value=state(store,ref);if(value.owner!=null)CompanionLoyalty.retain(store,ref);var mount=store.getComponent(ref,NPCMountComponent.getComponentType());
        try{following.record(store,ref,value,System.currentTimeMillis());}catch(java.io.IOException failure){errors.report((PlayerRef)null,"Whisperion location could not be saved; original retained",failure);}
        if(flight.reconcileHorse(store,ref,value))return;
        following.update(store,ref,value,System.currentTimeMillis());
        String assetName=value.saddled?"Whisperion_Saddled":"Whisperion";
        var appearance=store.getComponent(ref,ModelComponent.getComponentType());
        if(appearance==null || !assetName.equals(appearance.getModel().getModelAssetId())) {
            var asset=ModelAsset.getAssetMap().getAsset(assetName);
            if(asset!=null) {
                var model=Model.createUnitScaleModel(asset);
                store.putComponent(ref,ModelComponent.getComponentType(),new ModelComponent(model));
                store.putComponent(ref,PersistentModel.getComponentType(),new PersistentModel(model.toReference()));
            }
        }
        if(mount!=null)return;
        if("Empty_Role".equals(npc.getRoleName())) {
            RoleChangeSystem.requestRoleChange(ref,npc.getRole(),NPCPlugin.get().getIndex(ROLE),false,null,null,store);
        }
        store.ensureComponent(ref,Interactable.getComponentType());
        var current=store.getComponent(ref,Interactions.getComponentType());
        if(current==null || !"AxoWhisperion_Use".equals(current.getInteractionId(InteractionType.Use))) {
            current=current==null?new Interactions():(Interactions)current.clone();
            current.setInteractionId(InteractionType.Use,"AxoWhisperion_Use");
            current.setInteractionId(InteractionType.Secondary,"AxoWhisperion_Use");
            current.setInteractionHint("server.interactionHints.axoWhisperion");
            store.putComponent(ref,Interactions.getComponentType(),current);
        }
    }
    public void onInteraction(InteractionType type,InteractionContext context) {
        var buffer=context.getCommandBuffer();var ref=context.getOwningEntity();var target=context.getTargetEntity();
        Integer chainId=context.getChain()==null?null:context.getChain().getChainId();
        var riding=type==InteractionType.Use?flight.ride(buffer,ref):null;
        if(riding!=null) {
            long expectedStart=riding.startedAtNanos;
            buffer.run(s->{try{flight.interact(s,ref,expectedStart,chainId);}catch(RuntimeException failure){errors.report((PlayerRef)null,"Whisperion dismount failed",failure);}});return;
        }
        if(target==null || !target.isValid()) {context.getState().state=InteractionState.Failed;return;}
        var npc=buffer.getComponent(target,NPCEntity.getComponentType());
        if(npc==null || (!ROLE.equals(npc.getRoleName()) && buffer.getComponent(target,stateType)==null)) {
            context.getState().state=InteractionState.Failed;return;
        }
        String eventItem=context.getHeldItem()!=null?context.getHeldItem().getItemId():"empty";
        int section=context.getHeldItemSectionId();short slot=context.getHeldItemSlot();
        buffer.run(store->{try{use(store,ref,target,type,eventItem,section,slot,chainId);}catch(Throwable failure){errors.report((PlayerRef)null,"Whisperion interaction failed",failure);}});
    }
    private void use(Store<EntityStore> store,Ref<EntityStore> player,Ref<EntityStore> target,InteractionType type,String eventItem,int section,short slot,Integer chainId) {
        if(!player.isValid() || !target.isValid())return;
        var owner=store.getComponent(player,PlayerRef.getComponentType());if(owner==null)return;
        var npc=store.getComponent(target,NPCEntity.getComponentType());if(npc==null)return;
        var held=InventoryComponentAccess.itemInHand(store,player);String item=held!=null&&!held.isEmpty()?held.getItemId():"empty";
        boolean valid=store.getComponent(player,DeathComponent.getComponentType())==null && store.getComponent(target,DeathComponent.getComponentType())==null
            && InteractionValidation.canPlayerInteractWithEntity(player,store,held,target);
        var a=store.getComponent(player,TransformComponent.getComponentType());var b=store.getComponent(target,TransformComponent.getComponentType());
        valid &= a!=null && b!=null && a.getPosition().distanceSquared(b.getPosition())<=25;
        var value=state(store,target);String result="outOfRangeOrDead";
        if(valid) {
            long now=System.currentTimeMillis();
            if(now-value.lastUse<350)result="debounced";
            else if(value.ridingPlayer!=null || store.getComponent(target,NPCMountComponent.getComponentType())!=null)result="occupied";
            else {
                value.lastUse=now;
                var hotbar=InventoryComponentAccess.hotbarComponent(store,player);
                boolean actualHand=type==InteractionType.Secondary && section==InventoryComponent.HOTBAR_SECTION_ID && hotbar!=null
                    && hotbar.getActiveSlot()==slot && item.equals(eventItem);
                if(actualHand && (WhisperionState.KELP.equals(item) || WhisperionState.SADDLE.equals(item))) {
                    result=value.accept(owner.getUuid(),hotbar.getInventory(),slot,item);
                    if(result.equals("tamed") || result.equals("saddled")) {
                        store.putComponent(target,stateType,value);SoulSystem.dirty(store,target);
                        npc.playAnimation(target,AnimationSlot.Action,result.equals("tamed")?"Tame":"Alerted",store);
                        reconcile(store,target);
                    }
                } else if(value.canRide(owner.getUuid())) {
                    var movement=store.getComponent(player,MovementStatesComponent.getComponentType());
                    if(type==InteractionType.Use && "empty".equals(item) && movement!=null && movement.getMovementStates().crouching) {
                        result=value.removeSaddle(owner.getUuid(),InventoryComponentAccess.hotbar(store,player));
                        if("inventoryFull".equals(result))result=value.removeSaddle(owner.getUuid(),InventoryComponentAccess.storage(store,player));
                        if("unsaddled".equals(result)){store.putComponent(target,stateType,value);SoulSystem.dirty(store,target);reconcile(store,target);}
                    } else result=mount(store,player,target,npc,owner,value,chainId);
                }
                else result=value.owner==null?"needsKelp":!owner.getUuid().equals(value.owner)?"notOwner":"needsSaddle";
            }
        }
        if(!"debounced".equals(result))owner.sendMessage(Message.raw(switch(result) {
            case "tamed"->"Whisperion trusts you. Fit a Whisperion Saddle with RMB to ride.";
            case "saddled"->"Saddle fitted. Interact with Whisperion to ride.";
            case "unsaddled"->"Whisperion's saddle is back in your inventory.";
            case "inventoryFull"->"Make room in your inventory before removing Whisperion's saddle.";
            case "mounted"->"Whisperion: WASD to steer, Space to rise, Crouch to descend, Interact to dismount.";
            case "notOwner"->"Whisperion is bonded to another rider.";
            case "needsKelp","tameFirst"->"Offer Whisperion one Azure Kelp with RMB first.";
            case "needsSaddle"->"Fit a Whisperion Saddle with RMB before riding.";
            case "restoreForm"->"Restore your normal form before riding Whisperion.";
            case "alreadyTamed"->"Whisperion is already yours. No kelp was consumed.";
            case "alreadySaddled"->"Whisperion already has a saddle. Interact to ride.";
            case "occupied"->"Whisperion already has a rider.";
            default->"Whisperion: move closer and try again.";
        }));
        var stats=store.getComponent(player,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getMana();var mana=stats!=null?stats.get(index):null;
        debug.traceFileOnly(owner,"Whisperion event=nativeInteraction interactionType="+type+" cancelled=false itemSource=serverActiveSlot eventItem="+eventItem+" itemId="+item
            +" slot="+slot+" section="+section+" chainId="+chainId+" mana.index="+index+" mana.current="+(mana!=null?mana.get():"n/a")+" mana.max="+(mana!=null?mana.getMax():"n/a")
            +" mana.min="+(mana!=null?mana.getMin():"n/a")+" mana.gated=false allow="+Set.of("tamed","saddled","mounted","unsaddled").contains(result)+" decision="+result+" owner="+value.owner+" saddled="+value.saddled);
    }
    private String mount(Store<EntityStore> store,Ref<EntityStore> playerRef,Ref<EntityStore> target,NPCEntity npc,PlayerRef rider,WhisperionState state,Integer chainId) {
        return flight.begin(store,playerRef,target,npc,rider,state,chainId);
    }
}
