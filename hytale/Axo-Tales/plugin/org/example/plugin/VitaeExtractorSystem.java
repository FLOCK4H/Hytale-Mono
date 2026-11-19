package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3i;
import javax.annotation.Nonnull;


final class VitaeExtractorSystem extends TickingSystem<EntityStore> {
    static final String MACHINE="Axo_Vitae_Extractor",ESSENCE="Ingredient_Life_Essence";
    static final double DURATION=5;
    record Machine(ItemContainer items,BlockModule.BlockStateInfo info,Vector3i at){}
    record Progress(short slot,double seconds,String status){}
    private final Map<Ref<ChunkStore>,Engine> work=new ConcurrentHashMap<>();
    private final Map<UUID,VitaeExtractorPage> pages=new ConcurrentHashMap<>();
    private final Map<Store<EntityStore>,Long> refreshAt=Collections.synchronizedMap(new WeakHashMap<>());
    final PluginDebugReporter debug;
    final PluginErrorReporter errors;
    VitaeExtractorSystem(PluginDebugReporter debug,PluginErrorReporter errors){this.debug=debug;this.errors=errors;}
    static boolean isMachine(BlockType block){return AxoItems.block(block,MACHINE);}
    static Machine resolve(Ref<ChunkStore> ref,World world){
        if(ref==null||!ref.isValid()||ref.getStore()!=world.getChunkStore().getStore())return null;
        var chunks=ref.getStore();var info=chunks.getComponent(ref,BlockModule.BlockStateInfo.getComponentType());var component=chunks.getComponent(ref,ItemContainerBlock.getComponentType());
        if(info==null||component==null)return null;var at=new Vector3i();if(!info.fillWorldPos(chunks,at))return null;
        var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(at.x,at.z));
        return column!=null&&isMachine(column.getBlockType(at.x,at.y,at.z))?new Machine(component.getItemContainer(),info,at):null;
    }
    boolean open(InteractionContext context){
        var ref=context.getEntity();var buffer=context.getCommandBuffer();var target=context.getTargetBlock();if(ref==null||buffer==null||target==null){if(debug!=null)debug.traceFileOnly(null,"VitaeExtractor event=nativeUse cancelled=false source=serverTargetBlock reason=missingContextOrTarget allow=false");return false;}
        var owner=buffer.getComponent(ref,PlayerRef.getComponentType());var actor=buffer.getComponent(ref,Player.getComponentType());var world=buffer.getExternalData().getWorld();
        var machineRef=BlockModule.getBlockEntity(world,target.x,target.y,target.z);var machine=resolve(machineRef,world);
        boolean allowed=owner!=null&&actor!=null&&machine!=null&&attached(ref,buffer,machine);
        if(debug!=null){
            var held=InventoryComponentAccess.itemInHand(buffer,ref);var stats=buffer.getComponent(ref,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());int healthIndex=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth();var hp=stats==null?null:stats.get(healthIndex);
            debug.traceFileOnly(owner,"VitaeExtractor event=nativeUse cancelled=false target="+target.x+","+target.y+","+target.z+" block="+(machine==null?"missing":MACHINE)+" source=serverTargetBlock heldSource=serverInventory item="+(ItemStack.isEmpty(held)?"empty":held.getItemId())+" healthIndex="+healthIndex+" health="+(hp==null?"missing":hp.get()+"/"+hp.getMax()+" min="+hp.getMin())+" allow="+allowed);
        }
        if(!allowed)return false;
        var previous=pages.get(owner.getUuid());if(previous!=null&&actor.getPageManager().getCustomPage()==previous&&previous.machineRef.equals(machineRef))return true;
        if(previous!=null)previous.abandon();
        var page=new VitaeExtractorPage(owner,this,machineRef,machine.items);pages.put(owner.getUuid(),page);actor.getPageManager().openCustomPage(ref,ref.getStore(),page);return true;
    }
    static boolean attached(Ref<EntityStore> player,ComponentAccessor<EntityStore> access,Machine machine){
        if(!player.isValid()||access.getComponent(player,DeathComponent.getComponentType())!=null||access.getComponent(player,Spectating.getComponentType())!=null)return false;
        var transform=access.getComponent(player,TransformComponent.getComponentType());if(transform==null)return false;var p=transform.getPosition();var at=machine.at;
        return p.isFinite()&&p.distanceSquared(at.x+.5,at.y+.5,at.z+.5)<=64;
    }
    Progress progress(Ref<ChunkStore> ref,ItemContainer items){var engine=work.get(ref);return engine==null?new Progress((short)-1,0,Engine.idle(items)):engine.progress();}
    void release(VitaeExtractorPage page){pages.remove(page.owner(),page);}
    void dirty(Ref<ChunkStore> ref){if(ref.isValid()){var info=ref.getStore().getComponent(ref,BlockModule.BlockStateInfo.getComponentType());if(info!=null)info.markNeedsSaving(ref.getStore());}}
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> entities){
        if(!Float.isFinite(delta)||delta<=0)return;var world=entities.getExternalData().getWorld();if(world==null)return;var chunks=world.getChunkStore().getStore();
        var machines=new LinkedHashMap<Ref<ChunkStore>,Machine>();
        chunks.forEachChunk(Query.and(ItemContainerBlock.getComponentType(),BlockModule.BlockStateInfo.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);var machine=resolve(ref,world);if(machine!=null)machines.put(ref,machine);}
        });
        for(var entry:machines.entrySet())try{
            var ref=entry.getKey();var machine=entry.getValue();var engine=work.computeIfAbsent(ref,key->new Engine());String previous=engine.status;
            boolean converted=engine.advance(machine.items,delta);if(converted)machine.info.markNeedsSaving(chunks);
            if(debug!=null&&(converted||!Objects.equals(previous,engine.status)))debug.traceFileOnly(null,"VitaeExtractor event="+(converted?"convert":"workState")+" cancelled=false blood="+bloodSlot(machine.items)+" essence="+essenceSlot(machine.items)+" seconds="+engine.seconds+" state="+engine.status+" allow="+(converted||"Extracting DNA".equals(engine.status)));
        }catch(RuntimeException failure){if(errors!=null)errors.report((PlayerRef)null,"Vitae Extractor conversion failed",failure);work.remove(entry.getKey());}
        work.keySet().removeIf(ref->!ref.isValid()||(ref.getStore()==chunks&&!machines.containsKey(ref)));
        long now=System.nanoTime();if(now<refreshAt.getOrDefault(entities,0L))return;refreshAt.put(entities,now+200_000_000L);
        if(!pages.isEmpty())entities.forEachChunk(Query.and(PlayerRef.getComponentType(),Player.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var owner=chunk.getComponent(i,PlayerRef.getComponentType());var page=pages.get(owner.getUuid());if(page==null)continue;var ref=chunk.getReferenceTo(i);
                buffer.run(store->{if(!ref.isValid()){page.abandon();return;}var player=store.getComponent(ref,Player.getComponentType());if(player==null||player.getPageManager().getCustomPage()!=page)page.abandon();else if(!page.attached(ref,store))page.dismiss();else page.refresh(ref,store);});}
        });
    }
    void close(){for(var page:pages.values())page.dismiss();pages.clear();work.clear();refreshAt.clear();}
    static short bloodSlot(ItemContainer items){for(short s=0;s<items.getCapacity();s++)if(BloodSample.read(items.getItemStack(s),BloodSample.BLOOD)!=null&&items.getItemStack(s).getQuantity()==1)return s;return -1;}
    static short essenceSlot(ItemContainer items){for(short s=0;s<items.getCapacity();s++){var v=items.getItemStack(s);if(!ItemStack.isEmpty(v)&&ESSENCE.equals(v.getItemId()))return s;}return -1;}
    static final class Engine {
        short slot=-1;ItemStack sample;double seconds;String status="Add a blood sample";
        static String idle(ItemContainer items){
            if(bloodSlot(items)>=0)return essenceSlot(items)<0?"Add Essence of Life":"Ready to extract";
            for(short i=0;i<items.getCapacity();i++){var stack=items.getItemStack(i);if(!ItemStack.isEmpty(stack)&&LeechBlood.blood(stack.getItemId()))return "Blood has no valid species data";}
            for(short i=0;i<items.getCapacity();i++)if(BloodSample.read(items.getItemStack(i),BloodSample.DNA)!=null)return "DNA ready to collect";
            return "Add a blood sample";
        }
        Progress progress(){return new Progress(slot,seconds,status);}
        boolean advance(ItemContainer items,double delta){
            if(!Double.isFinite(delta)||delta<=0)return false;short current=bloodSlot(items);var stack=current<0?null:items.getItemStack(current);
            if(current<0||essenceSlot(items)<0){slot=-1;sample=null;seconds=0;status=idle(items);return false;}
            if(slot!=current||!Objects.equals(sample,stack)){slot=current;sample=stack;seconds=0;}
            seconds=Math.min(DURATION,seconds+Math.min(delta,.25));status="Extracting DNA";if(seconds+1e-6<DURATION)return false;
            if(!convert(items,slot,sample)){status="Output blocked; items retained";return false;}
            slot=-1;sample=null;seconds=0;status="DNA ready to collect";return true;
        }
    }
    static boolean convert(ItemContainer items,short bloodSlot,ItemStack expected){
        if(bloodSlot<0||bloodSlot>=items.getCapacity()||!Objects.equals(expected,items.getItemStack(bloodSlot)))return false;
        var sample=BloodSample.read(expected,BloodSample.BLOOD);if(sample==null||expected.getQuantity()!=1)return false;
        short fuel=essenceSlot(items);if(fuel<0||fuel==bloodSlot)return false;var essence=items.getItemStack(fuel);
        if(!items.removeItemStackFromSlot(fuel,essence,1).succeeded())return false;


        if(FrozenStorage.produced(items,()->items.setItemStackForSlot(bloodSlot,sample.item(BloodSample.DNA))).succeeded())return true;
        if(!items.setItemStackForSlot(fuel,essence,false).succeeded())throw new IllegalStateException("Cannot roll back Essence of Life");return false;
    }
}
