package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3i;


final class BloodFurnaceSystem extends TickingSystem<EntityStore> {
    record Machine(ItemContainer items,BloodFurnaceState state,BlockModule.BlockStateInfo info,Vector3i at,BlockType block){}
    private final ComponentType<ChunkStore,BloodFurnaceState> stateType;
    private final Map<UUID,BloodFurnacePage> pages=new ConcurrentHashMap<>();
    private final Map<Store<EntityStore>,Long> refreshAt=Collections.synchronizedMap(new WeakHashMap<>());
    private volatile List<CraftingRecipe> recipes=List.of();
    final PluginDebugReporter debug;
    final PluginErrorReporter errors;
    BloodFurnaceSystem(ComponentType<ChunkStore,BloodFurnaceState> type,PluginDebugReporter debug,PluginErrorReporter errors){stateType=type;this.debug=debug;this.errors=errors;}
    static boolean thermal(CraftingRecipe recipe){
        var requirements=recipe.getBenchRequirement();if(requirements==null)return false;
        for(var bench:requirements){String id=bench.id==null?"":bench.id.toLowerCase(Locale.ROOT);
            if(bench.type==BenchType.Processing&&(id.contains("furnace")||id.contains("campfire")||id.contains("grill")||id.contains("smelt")||id.contains("smoker")||id.contains("oven")))return true;
        }
        return false;
    }
    void refreshRecipes(){if(CraftingRecipe.getAssetStore()!=null)recipes=CraftingRecipe.getAssetMap().getAssetMap().values().stream().filter(BloodFurnaceSystem::thermal).sorted(Comparator.comparing(CraftingRecipe::getId)).toList();}
    List<CraftingRecipe> recipes(){if(recipes.isEmpty())refreshRecipes();return recipes;}
    boolean ingredient(ItemStack item){
        if(ItemStack.isEmpty(item))return false;var probe=new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short)1);probe.setItemStackForSlot((short)0,item,false);
        for(var recipe:recipes())for(var material:com.hypixel.hytale.builtin.crafting.component.CraftingManager.getInputMaterials(recipe))if(probe.canRemoveMaterial(material.clone(1)))return true;
        return false;
    }
    static boolean isFurnace(BlockType block){return AxoItems.block(block,BloodFurnaceState.ITEM);}
    Machine resolve(Ref<ChunkStore> ref,World world){
        if(ref==null||!ref.isValid()||ref.getStore()!=world.getChunkStore().getStore())return null;
        var chunks=ref.getStore();var info=chunks.getComponent(ref,BlockModule.BlockStateInfo.getComponentType());var state=chunks.getComponent(ref,stateType);var container=chunks.getComponent(ref,ItemContainerBlock.getComponentType());
        if(info==null||state==null||container==null)return null;var at=new Vector3i();if(!info.fillWorldPos(chunks,at))return null;
        var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(at.x,at.z));var block=column==null?null:column.getBlockType(at.x,at.y,at.z);
        return isFurnace(block)&&container.getItemContainer().getCapacity()==9?new Machine(container.getItemContainer(),state,info,at,block):null;
    }
    static boolean attached(Ref<EntityStore> ref,ComponentAccessor<EntityStore> store,Machine machine){
        if(!ref.isValid()||machine==null||store.getComponent(ref,DeathComponent.getComponentType())!=null||store.getComponent(ref,Spectating.getComponentType())!=null)return false;
        var transform=store.getComponent(ref,TransformComponent.getComponentType());return transform!=null&&transform.getPosition().isFinite()&&transform.getPosition().distanceSquared(machine.at.x+.5,machine.at.y+.5,machine.at.z+.5)<=64;
    }
    boolean open(InteractionContext context){
        var ref=context.getEntity();var buffer=context.getCommandBuffer();var target=context.getTargetBlock();if(ref==null||buffer==null||target==null)return false;
        var at=new Vector3i(target.x,target.y,target.z);buffer.run(store->{if(!ref.isValid())return;var owner=store.getComponent(ref,PlayerRef.getComponentType());
            try{var world=store.getExternalData().getWorld();var base=BlockOperations.resolveBaseBlockPosition(world.getChunkStore(),new com.hypixel.hytale.protocol.BlockPosition(at.x,at.y,at.z));var machineRef=BlockModule.getBlockEntity(world,base.x,base.y,base.z);var machine=resolve(machineRef,world);var actor=store.getComponent(ref,Player.getComponentType());
                boolean allow=owner!=null&&actor!=null&&attached(ref,store,machine);trace(store,ref,"nativeUse",allow,null,0);if(!allow)return;
                var previous=pages.get(owner.getUuid());if(previous!=null)previous.abandon();
                var page=new BloodFurnacePage(owner,this,machineRef,machine.items);pages.put(owner.getUuid(),page);actor.getPageManager().openCustomPage(ref,store,page);
            }catch(Throwable failure){errors.report(owner,"Blood Furnace could not open",failure);}
        });return true;
    }
    void release(BloodFurnacePage page){pages.remove(page.owner(),page);}
    void disconnect(PlayerRef player){var page=pages.remove(player.getUuid());if(page!=null)page.abandon();}
    void close(){for(var page:new ArrayList<>(pages.values()))page.abandon();pages.clear();refreshAt.clear();}
    void dirty(Ref<ChunkStore> ref){if(ref.isValid()){var info=ref.getStore().getComponent(ref,BlockModule.BlockStateInfo.getComponentType());if(info!=null)info.markNeedsSaving(ref.getStore());}}
    @Override public void tick(float delta,int count,Store<EntityStore> entities){
        if(!Float.isFinite(delta)||delta<=0)return;
        var world=entities.getExternalData().getWorld();var chunks=world.getChunkStore().getStore();
        chunks.forEachChunk(Query.and(stateType,ItemContainerBlock.getComponentType(),BlockModule.BlockStateInfo.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);buffer.run(accessor->{if(!ref.isValid())return;
                try{var machine=resolve(ref,world);if(machine==null)return;
                    if(machine.state.advance(machine.items,recipes(),delta))machine.info.markNeedsSaving(accessor);
                    String visual=machine.state.visualState();
                    if(!visual.equals(machine.state.visualKey)){
                        BlockOperations.setBlockInteractionState(world.getChunkStore(),ref,machine.at.x,machine.at.y,machine.at.z,machine.block,visual,false);
                        machine.state.visualKey=visual;
                    }
                }catch(Throwable failure){errors.report((PlayerRef)null,"Blood Furnace processing failed; items retained",failure);}
            });}
        });
        long now=System.nanoTime();if(refreshAt.getOrDefault(entities,0L)>now)return;refreshAt.put(entities,now+250_000_000L);
        for(var page:new ArrayList<>(pages.values())){var ref=entities.getExternalData().getRefFromUUID(page.owner());if(ref==null||!ref.isValid())continue;
            var actor=entities.getComponent(ref,Player.getComponentType());if(actor==null||actor.getPageManager().getCustomPage()!=page)page.abandon();else if(!page.attached(ref,entities))page.dismiss();else page.refresh(ref,entities);
        }
    }
    void trace(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref,String event,boolean allow,ItemStack item,long quantity){
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getHealth();var hp=stats==null||index<0?null:stats.get(index);
        debug.traceFileOnly(store.getComponent(ref,PlayerRef.getComponentType()),"BloodFurnace event="+event+" cancelled=false itemSource=serverBlockAndViewToken item="+(ItemStack.isEmpty(item)?BloodFurnaceState.ITEM:item.getItemId())+" health.index="+index+" health.current="+(hp==null?"missing":hp.get())+" health.min="+(hp==null?"missing":hp.getMin())+" health.max="+(hp==null?"missing":hp.getMax())+" mana=notUsed quantity="+quantity+" allow="+allow);
    }
}
