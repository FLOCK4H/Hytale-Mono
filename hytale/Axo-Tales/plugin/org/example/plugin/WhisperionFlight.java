package org.example.plugin;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.*;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.asset.type.model.config.*;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import org.joml.Vector3d;
import java.util.*;


final class WhisperionFlight {
    static final String MODEL="Whisperion_Ridden",USE="AxoWhisperion_Use";
    private final ComponentType<EntityStore,WhisperionState> stateType;
    private final ComponentType<EntityStore,WhisperionRide> rideType;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    WhisperionFlight(ComponentType<EntityStore,WhisperionState> stateType,ComponentType<EntityStore,WhisperionRide> rideType,PluginDebugReporter debug,PluginErrorReporter errors) {
        this.stateType=stateType;this.rideType=rideType;this.debug=debug;this.errors=errors;
    }
    boolean isRiding(ComponentAccessor<EntityStore> store,Ref<EntityStore> player){return store.getComponent(player,rideType)!=null;}
    WhisperionRide ride(ComponentAccessor<EntityStore> store,Ref<EntityStore> player){return store.getComponent(player,rideType);}
    String begin(Store<EntityStore> store,Ref<EntityStore> player,Ref<EntityStore> horse,NPCEntity npc,PlayerRef owner,WhisperionState state,Integer chainId) {
        var body=store.getComponent(player,ModelComponent.getComponentType());
        var avatar=ModelAsset.getAssetMap().getAsset(MODEL);
        var entity=store.getComponent(player,Player.getComponentType());
        var uuid=store.getComponent(horse,UUIDComponent.getComponentType());
        if(body==null || avatar==null || ModelAsset.getAssetMap().getAsset(WhisperionVisuals.EMPTY)==null || entity==null || uuid==null || isRiding(store,player)
            || entity.getMountEntityId()!=0 || store.getComponent(player,MountedComponent.getComponentType())!=null
            || store.getComponent(horse,MountedComponent.getComponentType())!=null)return "mountUnavailable";
        if(!"Characters/Player.blockymodel".equals(body.getModel().getModel()))return "restoreForm";
        var ride=new WhisperionRide();ride.horse=horse;ride.horseUuid=uuid.getUuid();ride.originalModel=body.getModel();
        ride.originalModelRef=ride.originalModel.toReference();
        ride.startedAtNanos=System.nanoTime();ride.mountChainId=chainId;
        var old=store.getComponent(player,Interactions.getComponentType());ride.originalInteractions=old==null?null:(Interactions)old.clone();
        ride.originalUse=old==null?null:old.getInteractionId(InteractionType.Use);
        var active=store.getComponent(player,ActiveAnimationComponent.getComponentType());
        ride.originalStatus=active==null?null:active.getActiveAnimations()[AnimationSlot.Status.ordinal()];
        ride.wasFlying=entity.isFlying(player,store);
        state.parkFlags=flags(store,horse);state.ridingPlayer=owner.getUuid();
        store.putComponent(horse,stateType,state);store.putComponent(player,rideType,ride);
        try {
            RoleChangeSystem.requestRoleChange(horse,npc.getRole(),NPCPlugin.get().getIndex("Empty_Role"),false,null,null,store);
            park(store,horse,player);
            follow(store,player,horse);
            var input=old==null?new Interactions():(Interactions)old.clone();
            input.setInteractionId(InteractionType.Use,USE);store.putComponent(player,Interactions.getComponentType(),input);
            store.putComponent(player,ModelComponent.getComponentType(),new ModelComponent(presentation(ride.originalModel,Model.createUnitScaleModel(avatar))));

            return "mounted";
        } catch(RuntimeException failure) {
            try{end(store,player,"mountRollback");}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
    }
    static Model presentation(Model original,Model avatar) {
        var attachments=new ArrayList<ModelAttachment>();
        if(original.getAttachments()!=null)Collections.addAll(attachments,original.getAttachments());



        return new Model(MODEL,1,original.getRandomAttachmentIds(),attachments.toArray(ModelAttachment[]::new),avatar.getBoundingBox(),
            avatar.getModel(),original.getTexture(),original.getGradientSet(),original.getGradientId(),avatar.getEyeHeight(),0,0,0,
            avatar.getAnimationSetMap(),avatar.getCamera(),avatar.getLight(),avatar.getParticles(),avatar.getTrails(),original.getPhysicsValues(),
            avatar.getDetailBoxes(),original.getPhobia(),original.getPhobiaModelAssetId());
    }
    void tick(Store<EntityStore> store) {
        store.forEachChunk(Query.and(rideType,PlayerRef.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);buffer.run(s->{
                try{refresh(s,ref);}catch(RuntimeException failure){errors.report((PlayerRef)null,"Whisperion player-flight recovery failed",failure);}
            });}
        });
    }
    private void refresh(Store<EntityStore> store,Ref<EntityStore> ref) {
        if(!ref.isValid())return;var ride=store.getComponent(ref,rideType);if(ride==null)return;


        if(ride.originalModel==null){end(store,ref,"savedRideRecovery");return;}
        resolveHorse(store,ref,ride);
        var body=store.getComponent(ref,ModelComponent.getComponentType());
        String reason=ride.horse==null || !ride.horse.isValid()?"sourceMissing":ride.horse.getStore()!=store?"sourceWorldChanged":
            store.getComponent(ref,DeathComponent.getComponentType())!=null?"riderDead":
            store.getComponent(ride.horse,DeathComponent.getComponentType())!=null?"sourceDead":
            body==null?"modelMissing":!MODEL.equals(body.getModel().getModelAssetId())?"modelChanged":null;
        if(reason!=null){end(store,ref,reason);return;}
        park(store,ride.horse,ref);
        follow(store,ref,ride.horse);
        var manager=store.getComponent(ref,MovementManager.getComponentType());var player=store.getComponent(ref,Player.getComponentType());
        var owner=store.getComponent(ref,PlayerRef.getComponentType());var movement=store.getComponent(ref,MovementStatesComponent.getComponentType());
        if(manager==null || player==null || owner==null || movement==null)return;
        if(!ride.initialized || manager.getSettings().fly!=FlyMode.Forced) {
            var config=MovementConfig.getAssetMap().getAsset("AxoTales_Whisperion");
            var physics=store.getComponent(ref,PhysicsValues.getComponentType());if(config==null || physics==null)return;
            manager.setDefaultSettings(config,physics,player.getGameMode());manager.applyDefaultSettings();manager.update(owner.getPacketHandler());
            player.setFlying(ref,true,store);ride.initialized=true;
            debug.traceFileOnly(owner,"Whisperion event=playerFlightActivated cancelled=false allow=true controller=player mountEntityId="+player.getMountEntityId()
                +" permission="+manager.getSettings().fly+" flying="+movement.getMovementStates().flying+" sourceNpc="+ride.horseUuid);
        }
        var states=movement.getMovementStates();
        var location=store.getComponent(ref,TransformComponent.getComponentType());
        boolean supported=location!=null && WhisperionTerrain.supported(store.getExternalData().getWorld(),location.getPosition());
        String animation=location==null?WhisperionVisuals.animation(states):WhisperionVisuals.sample(states,location.getPosition(),ride,supported);
        if(!animation.equals(ride.animation)) {
            WhisperionVisuals.animate(store,ref,"Ride_"+animation);ride.animation=animation;
            debug.traceFileOnly(owner,"Whisperion event=rideAnimation cancelled=false allow=true animation=Ride_"+animation
                +" includeRider=true onGround="+states.onGround+" flying="+states.flying+" idle="+states.horizontalIdle+" sourceNpc="+ride.horseUuid);
        }
        WhisperionVisuals.animateHorse(store,ride.horse,animation);
    }
    void interact(Store<EntityStore> store,Ref<EntityStore> playerRef,long expectedStart,Integer chainId) {
        if(!playerRef.isValid())return;
        var ride=store.getComponent(playerRef,rideType);
        String decision=ride==null?"rideEnded":ride.dismountDecision(expectedStart,chainId,System.nanoTime());
        var held=InventoryComponentAccess.itemInHand(store,playerRef);
        var stats=store.getComponent(playerRef,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
        int index=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getMana();var mana=stats==null?null:stats.get(index);
        debug.traceFileOnly(store.getComponent(playerRef,PlayerRef.getComponentType()),"Whisperion event=rideUse interactionType=Use cancelled=false itemSource=serverActiveSlot itemId="
            +(held==null?"empty":held.getItemId())+" chainId="+chainId+" mountChainId="+(ride==null?"n/a":ride.mountChainId)
            +" mana.index="+index+" mana.current="+(mana==null?"n/a":mana.get())+" mana.max="+(mana==null?"n/a":mana.getMax())
            +" mana.min="+(mana==null?"n/a":mana.getMin())+" mana.gated=false allow="+"dismount".equals(decision)+" decision="+decision);
        if("dismount".equals(decision))end(store,playerRef,"interact");
    }
    private void end(Store<EntityStore> store,Ref<EntityStore> playerRef,String reason) {
        var ride=store.getComponent(playerRef,rideType);if(ride==null)return;
        resolveHorse(store,playerRef,ride);
        if(ride.horse!=null && ride.horse.isValid() && ride.horse.getStore()==store) {
            if(ride.originalModel!=null)follow(store,playerRef,ride.horse);
            restoreHorse(store,ride.horse,playerRef);
        }
        var body=store.getComponent(playerRef,ModelComponent.getComponentType());
        boolean ownsBody=body!=null && MODEL.equals(body.getModel().getModelAssetId());
        if(ownsBody) {
            var original=ride.originalModel!=null?ride.originalModel:ride.originalModelRef!=null?ride.originalModelRef.toModel():null;
            if(original==null)original=Model.ModelReference.DEFAULT_PLAYER_MODEL.toModel();
            store.putComponent(playerRef,ModelComponent.getComponentType(),new ModelComponent(original));
        }
        WhisperionVisuals.restoreAnimation(store,playerRef,ownsBody?ride.originalStatus:null);
        var current=store.getComponent(playerRef,Interactions.getComponentType());
        if(current!=null && USE.equals(current.getInteractionId(InteractionType.Use))) {
            store.putComponent(playerRef,Interactions.getComponentType(),restoreUse(current,ride.originalUse));
        }
        var player=store.getComponent(playerRef,Player.getComponentType());var manager=store.getComponent(playerRef,MovementManager.getComponentType());
        if(player!=null && manager!=null) {
            manager.resetDefaultsAndUpdate(playerRef,store);
            player.setFlying(playerRef,ride.wasFlying && manager.getSettings().fly!=FlyMode.Disabled,store);
        }
        store.tryRemoveComponent(playerRef,rideType);
        var held=InventoryComponentAccess.itemInHand(store,playerRef);
        var stats=store.getComponent(playerRef,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
        int index=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getMana();var mana=stats==null?null:stats.get(index);
        debug.traceFileOnly(store.getComponent(playerRef,PlayerRef.getComponentType()),"Whisperion event=playerFlightEnded reason="+reason+" cancelled=false itemSource=serverActiveSlot itemId="
            +(held==null?"empty":held.getItemId())+" mana.index="+index+" mana.current="+(mana==null?"n/a":mana.get())+" mana.max="+(mana==null?"n/a":mana.getMax())
            +" mana.min="+(mana==null?"n/a":mana.getMin())+" mana.gated=false allow=true sourceNpc="+ride.horseUuid);
    }
    static Interactions restoreUse(Interactions current,String previous) {
        var result=(Interactions)current.clone();
        if(USE.equals(result.getInteractionId(InteractionType.Use))) {
            if(previous==null)result.removeInteractionId(InteractionType.Use);else result.setInteractionId(InteractionType.Use,previous);
        }
        return result;
    }
    boolean reconcileHorse(Store<EntityStore> store,Ref<EntityStore> horse,WhisperionState state) {
        if(state.ridingPlayer==null)return false;
        var player=store.getExternalData().getRefFromUUID(state.ridingPlayer);
        var ride=player!=null && player.isValid()?store.getComponent(player,rideType):null;
        var uuid=store.getComponent(horse,UUIDComponent.getComponentType());
        var previous=ride==null?null:ride.horse;
        if(ride!=null && uuid!=null && ride.claimHorse(horse,uuid.getUuid())) {
            if(previous!=horse)debug.traceFileOnly(store.getComponent(player,PlayerRef.getComponentType()),
                "Whisperion event=sourceRebound cancelled=false allow=true reason=npcReconcile sourceNpc="+ride.horseUuid);


            park(store,horse,player);return true;
        }
        restoreHorse(store,horse,player);return false;
    }
    private void resolveHorse(Store<EntityStore> store,Ref<EntityStore> player,WhisperionRide ride) {
        var previous=ride.horse;var current=ride.resolveHorse(store);
        if(current!=previous && current!=null && current.isValid() && current.getStore()==store) {
            debug.traceFileOnly(store.getComponent(player,PlayerRef.getComponentType()),
                "Whisperion event=sourceRebound cancelled=false allow=true reason=referenceChanged sourceNpc="+ride.horseUuid);
        }
    }
    private void park(Store<EntityStore> store,Ref<EntityStore> horse,Ref<EntityStore> player) {
        if(store.getComponent(horse,Frozen.getComponentType())==null)store.putComponent(horse,Frozen.getComponentType(),Frozen.get());
        if(store.getComponent(horse,Intangible.getComponentType())==null)store.putComponent(horse,Intangible.getComponentType(),Intangible.INSTANCE);
        if(store.getComponent(horse,Invulnerable.getComponentType())==null)store.putComponent(horse,Invulnerable.getComponentType(),Invulnerable.INSTANCE);
        store.tryRemoveComponent(horse,Interactable.getComponentType());



        var body=store.getComponent(horse,ModelComponent.getComponentType());
        var state=store.getComponent(horse,stateType);
        if(body!=null && state!=null) {
            var original=WhisperionVisuals.restore(body.getModel(),state);
            if(state.parkedModel==null){state.parkedModel=original.toReference();store.putComponent(horse,stateType,state);}
            if(original!=body.getModel())store.putComponent(horse,ModelComponent.getComponentType(),new ModelComponent(original));
        }
        WhisperionVisuals.attachHorse(store,horse,player);
    }
    private static void follow(Store<EntityStore> store,Ref<EntityStore> player,Ref<EntityStore> horse) {
        var rider=store.getComponent(player,TransformComponent.getComponentType());
        var source=store.getComponent(horse,TransformComponent.getComponentType());
        if(rider!=null && source!=null){
            copyParkedPose(rider,source);
            var head=store.getComponent(horse,HeadRotation.getComponentType());
            if(head!=null)head.setRotation(new com.hypixel.hytale.math.vector.Rotation3f(0,rider.getRotation().yaw(),0));
        }
    }

    static void copyParkedPose(TransformComponent rider,TransformComponent source) {
        source.setPosition(rider.getPosition());source.setRotation(rider.getRotation());
    }
    private void restoreHorse(Store<EntityStore> store,Ref<EntityStore> horse,Ref<EntityStore> player) {
        var state=store.getComponent(horse,stateType);if(state==null || state.ridingPlayer==null)return;
        WhisperionVisuals.detachHorse(store,horse,player);
        int flags=state.parkFlags;
        var body=store.getComponent(horse,ModelComponent.getComponentType());
        if(body!=null) {
            var original=WhisperionVisuals.restore(body.getModel(),state);
            if(original!=body.getModel())store.putComponent(horse,ModelComponent.getComponentType(),new ModelComponent(original));
        }
        if((flags&2)==0)store.tryRemoveComponent(horse,Frozen.getComponentType());
        if((flags&4)==0)store.tryRemoveComponent(horse,Intangible.getComponentType());
        if((flags&8)==0)store.tryRemoveComponent(horse,Invulnerable.getComponentType());
        var npc=store.getComponent(horse,NPCEntity.getComponentType());
        if(npc!=null && npc.getRole()!=null && "Empty_Role".equals(npc.getRoleName()))
            RoleChangeSystem.requestRoleChange(horse,npc.getRole(),NPCPlugin.get().getIndex(WhisperionSystem.ROLE),false,null,null,store);
        var location=store.getComponent(horse,TransformComponent.getComponentType());
        if(npc!=null && location!=null)npc.saveLeashInformation(new Vector3d(location.getPosition()),location.getRotation());
        if((flags&1)!=0)store.ensureComponent(horse,EntityTrackerSystems.Visible.getComponentType());
        if((flags&16)!=0)store.ensureComponent(horse,Interactable.getComponentType());
        state.ridingPlayer=null;state.parkFlags=0;state.parkedModel=null;store.putComponent(horse,stateType,state);
    }
    private static int flags(Store<EntityStore> store,Ref<EntityStore> ref) {
        return (store.getComponent(ref,EntityTrackerSystems.Visible.getComponentType())!=null?1:0)
            |(store.getComponent(ref,Frozen.getComponentType())!=null?2:0)|(store.getComponent(ref,Intangible.getComponentType())!=null?4:0)
            |(store.getComponent(ref,Invulnerable.getComponentType())!=null?8:0)|(store.getComponent(ref,Interactable.getComponentType())!=null?16:0);
    }
}
