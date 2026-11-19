package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.item.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3d;
import org.joml.Vector3i;


final class SoulLogistics {
    record Chest(Ref<ChunkStore> ref,Vector3d at,ItemContainer items,BlockModule.BlockStateInfo info) {}
    private final ComponentType<EntityStore,FossorWork> workType;
    private final PluginDebugReporter debug;
    SoulLogistics(ComponentType<EntityStore,FossorWork> workType,PluginDebugReporter debug){this.workType=workType;this.debug=debug;}
    static int deposit(ItemContainer cargo,short slot,ItemContainer chest) {
        var before=cargo.getItemStack(slot);if(ItemStack.isEmpty(before))return 0;
        cargo.moveItemStackFromSlot(slot,chest);
        var after=cargo.getItemStack(slot);
        return before.getQuantity()-(ItemStack.isEmpty(after)?0:after.getQuantity());
    }
    void villa(Store<EntityStore> store,Ref<EntityStore> ref,SoulState state,boolean scan) {
        var body=store.getComponent(ref,TransformComponent.getComponentType());
        if(!scan)return;
        var carried=first(state.cargo);
        if(carried!=null) {
            var chest=chest(store,state,carried,body.getPosition());
            if(chest==null){state.destination=state.anchor();return;}
            state.destination=new Vector3d(chest.at).add(.5,.8,.5);
            if(body.getPosition().distanceSquared(state.destination)>4)return;
            int moved=0;
            for(short slot=0;slot<state.cargo.getCapacity();slot++)moved+=deposit(state.cargo,slot,chest.items);
            if(moved>0){chest.info.markNeedsSaving(chest.ref.getStore());debug.traceFileOnly(null,"Soul event=deposit kind=Villa owner="+state.owner+" quantity="+moved+" chest="+chest.at+" allow=true");}
            return;
        }
        var nearby=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(Query.and(ItemComponent.getComponentType(),TransformComponent.getComponentType(),
            Query.not(PreventPickup.getComponentType()),Query.not(PickupItemComponent.getComponentType()),
            Query.not(Interactable.getComponentType()),Query.not(PropComponent.getComponentType())),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var item=chunk.getComponent(i,ItemComponent.getComponentType());
                var at=chunk.getComponent(i,TransformComponent.getComponentType()).getPosition();
                if(item.canPickUp() && !item.isRemovedByPlayerPickup() && at.distanceSquared(state.anchor())<=2500 && !ItemStack.isEmpty(item.getItemStack()))nearby.add(chunk.getReferenceTo(i));
            }
        });
        nearby.sort(Comparator.comparingDouble(r->store.getComponent(r,TransformComponent.getComponentType()).getPosition().distanceSquared(body.getPosition())));
        for(var drop:nearby) {
            if(!drop.isValid())continue;
            var item=store.getComponent(drop,ItemComponent.getComponentType());var stack=item.getItemStack();
            var roots=stack.getItem().getInteractions();
            if(roots!=null && roots.containsKey(com.hypixel.hytale.protocol.InteractionType.Pickup))continue;
            if(chest(store,state,stack,body.getPosition())==null)continue;
            state.destination=new Vector3d(store.getComponent(drop,TransformComponent.getComponentType()).getPosition());
            if(body.getPosition().distanceSquared(state.destination)<=4) {
                var accepted=ItemComponent.addToItemContainer(store,drop,state.cargo);
                if(!ItemStack.isEmpty(accepted))debug.traceFileOnly(null,"Soul event=collect kind=Villa owner="+state.owner+" item="+accepted.getItemId()+" quantity="+accepted.getQuantity()+" allow=true");
            }
            return;
        }
        state.destination=state.anchor();
    }
    private static ItemStack first(ItemContainer items){for(short s=0;s<items.getCapacity();s++){var i=items.getItemStack(s);if(!ItemStack.isEmpty(i))return i;}return null;}
    private static Chest chest(Store<EntityStore> store,SoulState state,ItemStack item,Vector3d soulAt) {
        var world=store.getExternalData().getWorld();var chunks=world.getChunkStore().getStore();
        var spatial=chunks.getResource(BlockModule.get().getItemContainerSpatialResourceType());if(spatial==null)return null;
        var candidates=new ArrayList<Ref<ChunkStore>>();spatial.getSpatialStructure().ordered(state.anchor(),50,candidates);
        Chest best=null;double distance=Double.POSITIVE_INFINITY;
        for(var ref:candidates) {
            if(!ref.isValid())continue;
            var component=chunks.getComponent(ref,ItemContainerBlock.getComponentType());
            var info=chunks.getComponent(ref,BlockModule.BlockStateInfo.getComponentType());
            var at=new Vector3i();if(component==null || info==null || !info.fillWorldPos(chunks,at))continue;
            var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(at.x,at.z));
            var block=column==null?null:column.getBlockType(at.x,at.y,at.z);
            if(block==null || !block.getId().toLowerCase(Locale.ROOT).contains("chest"))continue;
            var inventory=component.getItemContainer();if(inventory==null || !inventory.canAddItemStack(item.withQuantity(1)))continue;
            var pos=new Vector3d(at.x,at.y,at.z);double d=pos.distanceSquared(soulAt);
            if(d<distance){distance=d;best=new Chest(ref,pos,inventory,info);}
        }
        return best;
    }
    void fossor(Store<EntityStore> store,Ref<EntityStore> ref,SoulState state,float delta) {
        state.workBlocked=!clearWorkSite(store,state);
        if(state.workBlocked)return;
        if(!FossorLoot.valid(state.workItem)){state.workItem=FossorLoot.roll(state.feeds,ThreadLocalRandom.current().nextDouble());state.workSeconds=0;state.workEntity=null;}
        if(state.workEntity==null && state.workSeconds<.3f) {
            state.workSeconds=Math.min(.3f,state.workSeconds+delta);return;
        }
        if(!ensureWork(store,ref,state))return;
        if(!state.advanceMining(delta))return;
        var at=state.anchor().add(0,.65,1.8);
        var drop=ItemComponent.generateItemDrop(store,new ItemStack(state.workItem,1),at,new Rotation3f(),0,0,0);
        if(drop==null){state.workSeconds=SoulState.MINING_SECONDS;return;}
        store.addEntity(drop,AddReason.SPAWN);
        String harvested=state.workItem;state.workItem=null;state.workSeconds=0;
        SoulSystem.dirty(store,ref);
        debug.traceFileOnly(null,"Soul event=mined kind=Fossor owner="+state.owner+" item="+harvested+" elapsedSeconds="+SoulState.MINING_SECONDS+" crystalFeeds="+state.feeds+" allow=true");
        clearWork(store,state);state.nextWorkCast=0;
    }
    private static boolean clearWorkSite(Store<EntityStore> store,SoulState state) {
        var at=state.anchor().add(0,.65,1.8);var world=store.getExternalData().getWorld();
        for(double dx:new double[]{-.25,.25})for(double dy:new double[]{-.25,.25})for(double dz:new double[]{-.25,.25}) {
            int x=(int)Math.floor(at.x+dx),y=(int)Math.floor(at.y+dy),z=(int)Math.floor(at.z+dz);
            var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(x,z));
            if(column==null)return false;
        }
        return true;
    }
    private boolean ensureWork(Store<EntityStore> store,Ref<EntityStore> soul,SoulState state) {
        var existing=state.workEntity==null?null:store.getExternalData().getRefFromUUID(state.workEntity);
        var id=store.getComponent(soul,UUIDComponent.getComponentType()).getUuid();
        if(existing!=null && existing.isValid()) {
            var work=store.getComponent(existing,workType);
            if(work!=null && id.equals(work.soul)){state.missingWorkSince=0;return true;}
        }
        if(state.workEntity!=null) {
            long now=System.nanoTime();if(state.missingWorkSince==0)state.missingWorkSince=now;
            if(now-state.missingWorkSince<5_000_000_000L)return false;
        }
        var asset=ModelAsset.getAssetMap().getAsset(FossorLoot.model(state.workItem));
        if(asset==null)throw new IllegalStateException("Missing Fossor block model: "+state.workItem);
        var model=Model.createUnitScaleModel(asset);var holder=EntityStore.REGISTRY.newHolder();var uuid=UUIDComponent.randomUUID();
        holder.putComponent(UUIDComponent.getComponentType(),uuid);
        holder.putComponent(TransformComponent.getComponentType(),new TransformComponent(state.anchor().add(0,.65,1.8),new Rotation3f()));
        holder.putComponent(ModelComponent.getComponentType(),new ModelComponent(model));
        holder.putComponent(PersistentModel.getComponentType(),new PersistentModel(model.toReference()));
        holder.putComponent(PropComponent.getComponentType(),PropComponent.get());
        holder.putComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.putComponent(Intangible.getComponentType(),Intangible.INSTANCE);
        holder.putComponent(Invulnerable.getComponentType(),Invulnerable.INSTANCE);
        holder.putComponent(workType,new FossorWork(id));
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        store.addEntity(holder,AddReason.SPAWN);state.workEntity=uuid.getUuid();state.missingWorkSince=0;return true;
    }
    void clearWork(Store<EntityStore> store,SoulState state) {
        var ref=state.workEntity==null?null:store.getExternalData().getRefFromUUID(state.workEntity);
        if(ref!=null && ref.isValid() && store.getComponent(ref,workType)!=null)store.removeEntity(ref,RemoveReason.REMOVE);
        state.workEntity=null;state.missingWorkSince=0;
    }
    void reconcileWork(Store<EntityStore> store,ComponentType<EntityStore,SoulState> stateType) {
        var stale=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(Query.and(workType,UUIDComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var id=chunk.getComponent(i,workType).soul;
                if(id==null){stale.add(chunk.getReferenceTo(i));continue;}
                var parent=store.getExternalData().getRefFromUUID(id);
                if(parent==null || !parent.isValid())continue;
                var owner=buffer.getComponent(parent,stateType);
                if(owner!=null && !chunk.getComponent(i,UUIDComponent.getComponentType()).getUuid().equals(owner.workEntity))stale.add(chunk.getReferenceTo(i));
            }
        });
        for(var ref:stale)if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);
    }
}
