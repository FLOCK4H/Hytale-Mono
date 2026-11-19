package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;


final class WhisperionVisuals {
    static final String EMPTY="Invisible_Projectile",EMPTY_MESH="NPC/MISC/Empty.blockymodel";
    private static final Set<String> OWNED=Set.of("Ride_Idle","Ride_Walk","Ride_Run","Ride_Swim","Ride_Flight","Ride_Glide","Ride_TakeOff","Ride_Land");
    static void attachHorse(Store<EntityStore> store,Ref<EntityStore> horse,Ref<EntityStore> rider) {
        var type=com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType();
        var current=store.getComponent(horse,type);
        if(current!=null && current.getMountedToEntity()==rider)return;
        if(current!=null)throw new IllegalStateException("Whisperion already has a foreign render parent");
        store.putComponent(horse,type,new com.hypixel.hytale.builtin.mounts.MountedComponent(rider,new org.joml.Vector3f(),com.hypixel.hytale.protocol.MountController.Minecart));
    }
    static void detachHorse(Store<EntityStore> store,Ref<EntityStore> horse,Ref<EntityStore> rider) {
        var type=com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType();
        var current=store.getComponent(horse,type);if(current==null)return;
        var parent=current.getMountedToEntity();
        if(parent==rider || parent==null || !parent.isValid())store.tryRemoveComponent(horse,type);
    }
    static void animateHorse(Store<EntityStore> store,Ref<EntityStore> horse,String id) {
        var active=store.getComponent(horse,ActiveAnimationComponent.getComponentType());
        if(active!=null && id.equals(active.getActiveAnimations()[AnimationSlot.Status.ordinal()]))return;
        if(active==null){active=new ActiveAnimationComponent();store.putComponent(horse,ActiveAnimationComponent.getComponentType(),active);}
        active.setPlayingAnimation(AnimationSlot.Status,id);
        AnimationUtils.playAnimation(horse,AnimationSlot.Status,id,store);
    }
    static Model park(Model current,Model empty,WhisperionState state) {
        if(EMPTY_MESH.equals(current.getModel()))return current;
        if(state.parkedModel==null)state.parkedModel=current.toReference();
        return empty;
    }
    static Model restore(Model current,WhisperionState state) {
        if(!EMPTY_MESH.equals(current.getModel()))return current;
        Model original=state.parkedModel==null?null:state.parkedModel.toModel();
        if(original==null) {
            var asset=ModelAsset.getAssetMap().getAsset(state.saddled?"Whisperion_Saddled":"Whisperion");
            if(asset==null)throw new IllegalStateException("Missing original Whisperion model during restoration");
            original=Model.createUnitScaleModel(asset);
        }
        return original;
    }
    static String animation(MovementStates states) {
        return animation(states,!states.horizontalIdle,states.onGround);
    }
    static String animation(MovementStates states,boolean moving,boolean grounded) {
        return states.inFluid?"Swim":!grounded?"Flight":!moving?"Idle":states.sprinting&&!states.crouching?"Run":"Walk";
    }
    static String sample(MovementStates states,org.joml.Vector3dc at,WhisperionRide ride,boolean supported) {
        boolean moving=ride.lastPosition==null?!states.horizontalIdle:
            Math.pow(at.x()-ride.lastPosition.x,2)+Math.pow(at.z()-ride.lastPosition.z,2)>.0004;
        ride.lastPosition=new org.joml.Vector3d(at);
        return animation(states,moving,states.onGround||supported);
    }
    static void animate(Store<EntityStore> store,Ref<EntityStore> player,String id) {
        var active=store.getComponent(player,ActiveAnimationComponent.getComponentType());
        if(active==null){active=new ActiveAnimationComponent();store.putComponent(player,ActiveAnimationComponent.getComponentType(),active);}
        active.setPlayingAnimation(AnimationSlot.Status,id);

        AnimationUtils.playAnimation(player,AnimationSlot.Status,id,true,store);
    }
    static void restoreAnimation(Store<EntityStore> store,Ref<EntityStore> player,String previous) {
        var active=store.getComponent(player,ActiveAnimationComponent.getComponentType());
        if(active==null)return;
        String current=active.getActiveAnimations()[AnimationSlot.Status.ordinal()];
        if(current==null || !OWNED.contains(current))return;
        active.setPlayingAnimation(AnimationSlot.Status,previous);
        if(store.getComponent(player,NetworkId.getComponentType())==null)return;
        if(previous==null)AnimationUtils.stopAnimation(player,AnimationSlot.Status,true,store);
        else AnimationUtils.playAnimation(player,AnimationSlot.Status,previous,true,store);
    }
}
