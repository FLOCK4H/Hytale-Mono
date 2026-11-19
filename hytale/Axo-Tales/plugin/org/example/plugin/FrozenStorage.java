package org.example.plugin;

import com.hypixel.fastutil.ints.Int2ObjectConcurrentHashMap;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import com.hypixel.hytale.server.core.inventory.container.filter.*;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import org.joml.Vector3i;


final class FrozenStorage extends TickingSystem<ChunkStore> {
    static final String CHEST="Axo_Frozen_Chest",DOUBLE="Axo_Frozen_Chest_Double";
    enum Policy { STORAGE,FROZEN,EXTRACTOR,FURNACE }
    private static final Field FILTERS=filtersField(SimpleItemContainer.class),PORTABLE_FILTERS=filtersField(ItemStackItemContainer.class);
    private static final ThreadLocal<ItemContainer> PRODUCTION=new ThreadLocal<>();
    private final Map<Store<ChunkStore>,Float> clocks=Collections.synchronizedMap(new WeakHashMap<>());
    private final PluginErrorReporter errors;
    private final ComponentType<ChunkStore,ItemContainerBlock> containers;
    private final ComponentType<ChunkStore,BlockModule.BlockStateInfo> positions;
    private final BiFunction<Store<ChunkStore>,Ref<ChunkStore>,BlockType> blocks;
    FrozenStorage(PluginErrorReporter errors){this(errors,ItemContainerBlock.getComponentType(),BlockModule.BlockStateInfo.getComponentType(),FrozenStorage::loadedBlock);}
    FrozenStorage(PluginErrorReporter errors,ComponentType<ChunkStore,ItemContainerBlock> containers,ComponentType<ChunkStore,BlockModule.BlockStateInfo> positions,BiFunction<Store<ChunkStore>,Ref<ChunkStore>,BlockType> blocks){this.errors=errors;this.containers=containers;this.positions=positions;this.blocks=blocks;}
    private static BlockType loadedBlock(Store<ChunkStore> store,Ref<ChunkStore> ref){
        var info=store.getComponent(ref,BlockModule.BlockStateInfo.getComponentType());var at=new Vector3i();
        if(info==null||!info.fillWorldPos(store,at))return null;
        var column=WorldBlocks.loaded(store.getExternalData().getWorld(),ChunkUtil.indexChunkFromBlock(at.x,at.z));
        return column==null?null:column.getBlockType(at.x,at.y,at.z);
    }
    private static Field filtersField(Class<?> type){
        try{var f=type.getDeclaredField("slotFilters");f.setAccessible(true);return f;}
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    static boolean specimen(ItemStack item){return !ItemStack.isEmpty(item)&&(LeechBlood.blood(item.getItemId())||BloodSample.DNA.equals(AxoItems.canonical(item.getItemId())));}
    static Policy policy(BlockType block){
        if(AxoItems.block(block,CHEST)||AxoItems.block(block,DOUBLE))return Policy.FROZEN;
        if(AxoItems.block(block,AxoItems.VITAE_EXTRACTOR))return Policy.EXTRACTOR;
        if(AxoItems.block(block,AxoItems.BLOOD_FURNACE))return Policy.FURNACE;
        return Policy.STORAGE;
    }
    static boolean allowed(Policy policy,ItemContainer container,short slot,ItemStack item){
        boolean blood=!ItemStack.isEmpty(item)&&LeechBlood.blood(item.getItemId());
        boolean dna=!ItemStack.isEmpty(item)&&BloodSample.DNA.equals(AxoItems.canonical(item.getItemId()));
        return switch(policy){
            case FROZEN -> blood||dna;
            case STORAGE -> !blood&&!dna;
            case FURNACE -> !dna&&(!blood||slot==0);
            case EXTRACTOR -> !dna||PRODUCTION.get()==container;
        };
    }
    static <T> T produced(ItemContainer container,Supplier<T> action){
        var previous=PRODUCTION.get();PRODUCTION.set(container);
        try{return action.get();}finally{if(previous==null)PRODUCTION.remove();else PRODUCTION.set(previous);}
    }
    private record Gate(Policy policy,SlotFilter previous) implements SlotFilter {
        @Override public boolean test(FilterActionType action,ItemContainer container,short slot,ItemStack item){
            return allowed(policy,container,slot,item)&&(previous==null||previous.test(action,container,slot,item));
        }
    }
    @SuppressWarnings("unchecked")
    static void apply(ItemContainer container,Policy policy){
        if(container==null)return;
        try{
            var field=container instanceof SimpleItemContainer?FILTERS:container instanceof ItemStackItemContainer?PORTABLE_FILTERS:null;
            if(field==null)return;
            var filters=(Map<FilterActionType,Int2ObjectConcurrentHashMap<SlotFilter>>)field.get(container);
            var add=filters.get(FilterActionType.ADD);
            for(short slot=0;slot<container.getCapacity();slot++){
                SlotFilter old=add==null?null:add.get(slot);
                if(old instanceof Gate gate){if(gate.policy==policy)continue;old=gate.previous;}
                container.setSlotFilter(FilterActionType.ADD,slot,new Gate(policy,old));
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("Pinned native container filters unavailable",failure);}
    }
    private void attach(Store<ChunkStore> store,Ref<ChunkStore> ref){
        if(!ref.isValid())return;
        try{
            var component=store.getComponent(ref,containers);var block=blocks.apply(store,ref);
            if(component!=null&&block!=null)apply(component.getItemContainer(),policy(block));
        }catch(RuntimeException failure){if(errors!=null)errors.report((PlayerRef)null,"Specimen storage filter attachment deferred",failure);}
    }
    RefSystem<ChunkStore> lifecycle(){return new RefSystem<>(){
        @Override public Query<ChunkStore> getQuery(){return Query.and(containers,positions);}
        @Override public void onEntityAdded(Ref<ChunkStore> ref,AddReason reason,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){attach(store,ref);}
        @Override public void onEntityRemove(Ref<ChunkStore> ref,RemoveReason reason,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){}
    };}
    RefChangeSystem<ChunkStore,ItemContainerBlock> changes(){return new RefChangeSystem<>(){
        @Override public ComponentType<ChunkStore,ItemContainerBlock> componentType(){return containers;}
        @Override public Query<ChunkStore> getQuery(){return Query.and(containers,positions);}
        @Override public void onComponentAdded(Ref<ChunkStore> ref,ItemContainerBlock component,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){attach(store,ref);}
        @Override public void onComponentSet(Ref<ChunkStore> ref,ItemContainerBlock old,ItemContainerBlock component,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){attach(store,ref);}
        @Override public void onComponentRemoved(Ref<ChunkStore> ref,ItemContainerBlock component,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){}
    };}
    @Override public void tick(float delta,int tick,@Nonnull Store<ChunkStore> store){
        if(!Float.isFinite(delta)||delta<=0)return;float elapsed=clocks.getOrDefault(store,0f)+delta;
        if(elapsed<.25f){clocks.put(store,elapsed);return;}clocks.put(store,0f);
        store.forEachChunk(Query.and(containers,positions),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++)attach(store,chunk.getReferenceTo(i));
        });
    }
}
