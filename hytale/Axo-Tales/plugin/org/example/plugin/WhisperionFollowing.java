package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Rotation3f;
import java.io.IOException;


final class WhisperionFollowing {
    private final ComponentType<EntityStore,WhisperionState> type;
    private final WhisperionPets pets;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    WhisperionFollowing(ComponentType<EntityStore,WhisperionState> type,WhisperionPets pets,PluginDebugReporter debug,PluginErrorReporter errors) {
        this.type=type;this.pets=pets;this.debug=debug;this.errors=errors;
    }
    static boolean distant(double distanceSquared){return distanceSquared>50*50;}
    private static float viewYaw(Store<EntityStore> store,Ref<EntityStore> player,TransformComponent at) {
        var head=store.getComponent(player,HeadRotation.getComponentType());
        return head==null?at.getRotation().yaw():head.getRotation().yaw();
    }
    static boolean queue(Store<EntityStore> store,Ref<EntityStore> ref,ComponentType<EntityStore,WhisperionState> stateType,
                         ComponentType<EntityStore,Teleport> teleportType,java.util.UUID owner,Teleport request) {
        if(ref==null || !ref.isValid() || ref.getStore()!=store)return false;
        var state=store.getComponent(ref,stateType);
        if(state==null || !owner.equals(state.owner) || state.ridingPlayer!=null || store.getComponent(ref,teleportType)!=null)return false;
        store.addComponent(ref,teleportType,request);return true;
    }
    static void name(Store<EntityStore> store,Ref<EntityStore> ref) {
        var type=Nameplate.getComponentType();var current=store.getComponent(ref,type);
        if(current==null)store.putComponent(ref,type,new Nameplate("Whisperion"));
        else if(!"Whisperion".equals(current.getText()))current.setText("Whisperion");
    }
    void record(Store<EntityStore> store,Ref<EntityStore> ref,WhisperionState state,long now) throws IOException {
        if(state.owner==null)return;
        name(store,ref);
        var id=store.getComponent(ref,UUIDComponent.getComponentType()).getUuid();pets.active(id);
        if(now<state.nextRecord)return;
        var at=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        pets.remember(new WhisperionPets.Entry(id,state.owner,store.getExternalData().getWorld().getWorldConfig().getUuid(),store.getExternalData().getWorld().getName(),at.x,at.y,at.z));
        state.nextRecord=now+2000;
    }
    void update(Store<EntityStore> store,Ref<EntityStore> ref,WhisperionState state,long now) {
        if(state.owner==null || state.ridingPlayer!=null || now<state.nextCatchup)return;
        var universe=Universe.get();var owner=universe==null?null:universe.getPlayer(state.owner);
        if(owner==null || owner.getWorldUuid()==null)return;
        var world=store.getExternalData().getWorld();
        if(!world.getWorldConfig().getUuid().equals(owner.getWorldUuid())){transfer(store,ref,state,owner,now);return;}
        var player=store.getExternalData().getRefFromUUID(state.owner);
        if(player==null || !player.isValid() || store.getComponent(player,DeathComponent.getComponentType())!=null)return;
        var at=store.getComponent(player,TransformComponent.getComponentType());var self=store.getComponent(ref,TransformComponent.getComponentType());
        if(at==null || self==null || !distant(at.getPosition().distanceSquared(self.getPosition())))return;
        state.nextCatchup=now+500;
        var safe=WhisperionTerrain.near(world,at.getPosition(),viewYaw(store,player,at));
        if(safe!=null && queue(store,ref,type,Teleport.getComponentType(),state.owner,Teleport.createExact(safe,self.getRotation()))) {
            debug.traceFileOnly(owner,"Whisperion event=ownerCatchup allow=true behindOwner=true originalUuid="+store.getComponent(ref,UUIDComponent.getComponentType()).getUuid());
        }
    }
    private void transfer(Store<EntityStore> store,Ref<EntityStore> ref,WhisperionState state,PlayerRef owner,long now) {
        if(state.transferPending)return;
        var destination=Universe.get().getWorld(owner.getWorldUuid());if(destination==null || !destination.isAlive())return;
        state.nextCatchup=now+500;state.transferPending=true;
        var source=store.getExternalData().getWorld();var id=store.getComponent(ref,UUIDComponent.getComponentType()).getUuid();
        var expectedOwner=state.owner;var expectedWorld=owner.getWorldUuid();
        destination.execute(()->{
            try {
                var targetStore=destination.getEntityStore().getStore();var player=destination.getEntityStore().getRefFromUUID(expectedOwner);
                var at=player!=null && player.isValid()?targetStore.getComponent(player,TransformComponent.getComponentType()):null;
                var safe=at==null || targetStore.getComponent(player,DeathComponent.getComponentType())!=null?null:WhisperionTerrain.near(destination,at.getPosition(),viewYaw(targetStore,player,at));
                var rotation=at==null?new Rotation3f():new Rotation3f(at.getRotation());
                source.execute(()->{
                    state.transferPending=false;
                    var current=store.getExternalData().getRefFromUUID(id);var liveOwner=Universe.get().getPlayer(expectedOwner);
                    if(safe==null || current==null || !current.isValid() || liveOwner==null || !expectedWorld.equals(liveOwner.getWorldUuid()) || store.getComponent(current,DeathComponent.getComponentType())!=null)return;
                    var live=store.getComponent(current,type);
                    if(live==null || !expectedOwner.equals(live.owner) || live.ridingPlayer!=null || store.getComponent(current,Teleport.getComponentType())!=null)return;
                    if(queue(store,current,type,Teleport.getComponentType(),expectedOwner,new Teleport(destination,safe,rotation)))
                        debug.traceFileOnly(liveOwner,"Whisperion event=worldFollow allow=true nativeOriginalTransfer=true behindOwner=true sourceNpc="+id);
                });
            }catch(RuntimeException failure){source.execute(()->state.transferPending=false);errors.report(owner,"Whisperion world follow failed; original retained",failure);}
        });
    }
}
