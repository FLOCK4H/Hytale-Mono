package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.*;
import org.joml.Vector3d;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


final class GrayCatSystem extends TickingSystem<EntityStore> {
    private final ComponentType<EntityStore,GrayCatState> type;
    private final ComponentType<EntityStore,GrayCatFood> foodType;
    private final GrayCatPets pets;private final PluginDebugReporter debug;private final PluginErrorReporter errors;
    private final Map<Store<EntityStore>,Long> due=Collections.synchronizedMap(new WeakHashMap<>()),recallDue=Collections.synchronizedMap(new WeakHashMap<>());
    GrayCatSystem(ComponentType<EntityStore,GrayCatState> type,ComponentType<EntityStore,GrayCatFood> foodType,GrayCatPets pets,PluginDebugReporter debug,PluginErrorReporter errors){this.type=type;this.foodType=foodType;this.pets=pets;this.debug=debug;this.errors=errors;}
    @Override public void tick(float delta,int tick,Store<EntityStore> store){
        long now=System.currentTimeMillis();if(now<due.getOrDefault(store,0L))return;due.put(store,now+200);
        var foods=new ArrayList<Ref<EntityStore>>();store.forEachChunk(Query.and(foodType,ItemComponent.getComponentType(),TransformComponent.getComponentType()),(chunk,buffer)->{for(int i=0;i<chunk.size();i++)foods.add(chunk.getReferenceTo(i));});
        for(var food:foods)if(food.isValid() && store.getComponent(food,foodType).expires<now){var item=store.getComponent(food,ItemComponent.getComponentType());item.setMergeRadius(store.getComponent(food,foodType).mergeRadius);store.removeComponent(food,foodType);}
        var cats=new ArrayList<Ref<EntityStore>>();store.forEachChunk(Query.and(NPCEntity.getComponentType(),TransformComponent.getComponentType(),UUIDComponent.getComponentType()),(chunk,buffer)->{for(int i=0;i<chunk.size();i++)if(GrayCatState.ROLE.equals(chunk.getComponent(i,NPCEntity.getComponentType()).getRoleName()))cats.add(chunk.getReferenceTo(i));});
        for(var ref:cats)try{update(store,ref,foods,now);}catch(RuntimeException|java.io.IOException failure){errors.report((PlayerRef)null,"Gray Cat update failed",failure);}
        if(now>=recallDue.getOrDefault(store,0L)){recallDue.put(store,now+5000);var owners=new HashSet<UUID>();store.forEachChunk(PlayerRef.getComponentType(),(chunk,buffer)->{for(int i=0;i<chunk.size();i++)owners.add(chunk.getComponent(i,PlayerRef.getComponentType()).getUuid());});pets.recall(owners);}
    }
    private void update(Store<EntityStore> store,Ref<EntityStore> ref,List<Ref<EntityStore>> foods,long now) throws java.io.IOException {
        if(!ref.isValid())return;var id=store.getComponent(ref,UUIDComponent.getComponentType()).getUuid();
        if(store.getComponent(ref,DeathComponent.getComponentType())!=null){pets.forget(id);return;}
        var state=store.getComponent(ref,type);if(state==null){state=new GrayCatState();state.bornAt=now;store.putComponent(ref,type,state);dirty(store,ref);}
        var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc.getRole()==null)return;
        var self=store.getComponent(ref,TransformComponent.getComponentType());var world=store.getExternalData().getWorld();var worldId=world.getWorldConfig().getUuid();
        if(state.owner==null){
            var food=foods.stream().filter(f->f.isValid() && store.getComponent(f,foodType)!=null && now<=store.getComponent(f,foodType).expires)
                .filter(f->self.getPosition().distanceSquared(store.getComponent(f,TransformComponent.getComponentType()).getPosition())<=12*12)
                .filter(f->{var offer=store.getComponent(f,foodType);var owner=store.getExternalData().getRefFromUUID(offer.owner);return worldId.equals(offer.world) && owner!=null && owner.isValid() && store.getComponent(owner,PlayerRef.getComponentType())!=null && store.getComponent(owner,DeathComponent.getComponentType())==null;})
                .filter(f->BloodMagicSystem.sight(store,new Vector3d(self.getPosition()).add(0,.35,0),new Vector3d(store.getComponent(f,TransformComponent.getComponentType()).getPosition()).add(0,.1,0)))
                .min(Comparator.comparingDouble(f->self.getPosition().distanceSquared(store.getComponent(f,TransformComponent.getComponentType()).getPosition()))).orElse(null);
            if(food==null){target(store,ref,null,"Roam");return;}
            target(store,ref,food,"Food");var pos=store.getComponent(food,TransformComponent.getComponentType()).getPosition();
            if(self.getPosition().distanceSquared(pos)>1.8*1.8)return;
            var offer=store.getComponent(food,foodType);var item=store.getComponent(food,ItemComponent.getComponentType());var stack=item.getItemStack();if(stack==null || stack.isEmpty() || !stack.isValid() || !GrayCatState.rawFish(stack.getItemId()))return;
            var record=new GrayCatPets.Entry(id,offer.owner,worldId,self.getPosition().x,self.getPosition().y,self.getPosition().z);pets.remember(record);
            try{state.bind(offer.owner,()->{if(stack.getQuantity()==1)store.removeEntity(food,RemoveReason.REMOVE);else{item.setItemStack(stack.withQuantity(stack.getQuantity()-1));item.setMergeRadius(0);}});}
            catch(RuntimeException failed){pets.forget(id);throw failed;}
            dirty(store,ref);pets.active(id);npc.playAnimation(ref,AnimationSlot.Action,"Eat",store);
            debug.traceFileOnly(null,"GrayCat event=tame cancelled=false item="+stack.getItemId()+" source=nativeDroppedFish consumed=1 cat="+id+" owner="+state.owner+" chance=1 allow=true");
        }
        npc.setDespawning(false);npc.setDespawnTime(0);store.tryRemoveComponent(ref,DespawnComponent.getComponentType());store.tryRemoveComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType());
        pets.active(id);
        if(now>=state.nextRecord){state.nextRecord=now+10_000;pets.remember(new GrayCatPets.Entry(id,state.owner,worldId,self.getPosition().x,self.getPosition().y,self.getPosition().z));}
        var universe=Universe.get();var player=universe==null?null:universe.getPlayer(state.owner);
        if(player==null || player.getWorldUuid()==null){target(store,ref,null,"Roam");return;}
        if(!worldId.equals(player.getWorldUuid())){transfer(store,ref,state,player,now);return;}
        var owner=store.getExternalData().getRefFromUUID(state.owner);if(owner==null || !owner.isValid() || store.getComponent(owner,DeathComponent.getComponentType())!=null){target(store,ref,null,"Roam");return;}
        var ownerTransform=store.getComponent(owner,TransformComponent.getComponentType());if(ownerTransform==null)return;var home=ownerTransform.getPosition();double d=self.getPosition().distanceSquared(home);
        npc.setLeashPoint(new Vector3d(home));boolean following=state.follow(d);
        if(following){
            if(state.lastPosition!=null && self.getPosition().distanceSquared(state.lastPosition)<.0025){if(state.stuckSince==0)state.stuckSince=now;}else state.stuckSince=0;
            state.lastPosition=new Vector3d(self.getPosition());
            if((d>30*30 || now-state.stuckSince>6000 && state.stuckSince>0) && now>=state.nextCatchup){
                state.nextCatchup=now+2000;var safe=GrayCatTerrain.near(world,home);if(safe!=null && store.getComponent(ref,Teleport.getComponentType())==null){store.addComponent(ref,Teleport.getComponentType(),Teleport.createExact(safe,self.getRotation()));debug.traceFileOnly(player,"GrayCat event=catchup cat="+id+" distance="+Math.sqrt(d)+" safeFloor=true allow=true");return;}
            }
            if(state.restUntil!=0){state.restUntil=0;AnimationUtils.stopAnimation(ref,AnimationSlot.Action,store);}target(store,ref,owner,"Follow");return;
        }
        state.stuckSince=0;state.lastPosition=null;
        if(state.restUntil>now && d<=12*12){target(store,ref,null,"Rest");return;}
        if(state.restUntil!=0){state.restUntil=0;AnimationUtils.stopAnimation(ref,AnimationSlot.Action,store);}
        if(state.nextRest==0)state.nextRest=now+ThreadLocalRandom.current().nextLong(30_000,60_000);
        if(now>=state.nextRest && d<=8*8){state.nextRest=now+ThreadLocalRandom.current().nextLong(30_000,60_000);state.restUntil=now+2000;target(store,ref,null,"Rest");npc.playAnimation(ref,AnimationSlot.Action,"Groom",store);return;}
        target(store,ref,null,"Roam");
    }
    private void transfer(Store<EntityStore> store,Ref<EntityStore> ref,GrayCatState state,PlayerRef owner,long now){
        if(state.transferPending || now<state.nextCatchup)return;state.nextCatchup=now+2000;
        var destination=Universe.get().getWorld(owner.getWorldUuid());if(destination==null || !destination.isAlive())return;
        var source=store.getExternalData().getWorld();var cat=store.getComponent(ref,UUIDComponent.getComponentType()).getUuid();var expectedOwner=state.owner;var expectedWorld=owner.getWorldUuid();state.transferPending=true;
        destination.execute(()->{
            try{var player=destination.getEntityStore().getRefFromUUID(expectedOwner);var targetStore=destination.getEntityStore().getStore();var at=player!=null && player.isValid()?targetStore.getComponent(player,TransformComponent.getComponentType()):null;
                var safe=at==null || targetStore.getComponent(player,DeathComponent.getComponentType())!=null?null:GrayCatTerrain.near(destination,at.getPosition());
                source.execute(()->{state.transferPending=false;var current=store.getExternalData().getRefFromUUID(cat);var currentOwner=Universe.get().getPlayer(expectedOwner);
                    if(safe==null || current==null || !current.isValid() || currentOwner==null || !expectedWorld.equals(currentOwner.getWorldUuid()) || store.getComponent(current,DeathComponent.getComponentType())!=null)return;
                    var currentState=store.getComponent(current,type);if(currentState==null || !expectedOwner.equals(currentState.owner) || store.getComponent(current,Teleport.getComponentType())!=null)return;
                    store.addComponent(current,Teleport.getComponentType(),new Teleport(destination,safe,new com.hypixel.hytale.math.vector.Rotation3f()));debug.traceFileOnly(currentOwner,"GrayCat event=worldFollow cat="+cat+" nativeHolderTransfer=true allow=true");
                });
            }catch(RuntimeException failure){source.execute(()->state.transferPending=false);errors.report(owner,"Gray Cat cross-world follow failed; original retained",failure);}
        });
    }
    private static void target(Store<EntityStore> store,Ref<EntityStore> ref,Ref<EntityStore> target,String name){
        var marked=MarkedEntitySupport.get(ref,store);var state=StateSupport.get(ref,store);var world=WorldSupport.get(ref,store);if(marked==null || state==null || world==null)return;
        var before=marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        if(!Objects.equals(before,target)){if(target==null){for(int slot=0;slot<marked.getMarkedEntitySlotCount();slot++)if(MarkedEntitySupport.DEFAULT_TARGET_SLOT.equals(marked.getSlotName(slot)))marked.clearMarkedEntity(slot);}else marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,target);world.requestNewPath();}
        if(!name.equals(state.getStateName()))state.setState(ref,name,null,store);
    }
    private static void dirty(ComponentAccessor<EntityStore> access,Ref<EntityStore> ref){var dirty=access.getComponent(ref,Dirty.getComponentType());if(dirty!=null)dirty.markDirty();}
}
