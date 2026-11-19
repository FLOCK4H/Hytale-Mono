package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.blockanimation.BlockAnimationModule;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.*;
import org.joml.*;


final class OraculumSystem extends TickingSystem<EntityStore> {
    static final double RADIUS=250;
    static final float DAMAGE=4_000;
    private final ComponentType<ChunkStore,OraculumState> type;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    record Device(Ref<ChunkStore> ref,OraculumState state,BlockModule.BlockStateInfo info,Vector3i at,BlockType block){}
    OraculumSystem(ComponentType<ChunkStore,OraculumState> type,PluginDebugReporter debug,PluginErrorReporter errors){this.type=type;this.debug=debug;this.errors=errors;}
    static boolean device(BlockType block){return AxoItems.block(block,OraculumState.ITEM);}
    Device resolve(World world,Ref<ChunkStore> ref){
        if(ref==null||!ref.isValid())return null;var chunks=world.getChunkStore().getStore();if(ref.getStore()!=chunks)return null;
        var state=chunks.getComponent(ref,type);var info=chunks.getComponent(ref,BlockModule.BlockStateInfo.getComponentType());var at=new Vector3i();
        if(state==null||info==null||!info.fillWorldPos(chunks,at))return null;
        var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(at.x,at.z));var block=column==null?null:column.getBlockType(at.x,at.y,at.z);
        return device(block)?new Device(ref,state,info,at,block):null;
    }
    boolean use(InteractionContext context){
        var ref=context.getEntity();var buffer=context.getCommandBuffer();var target=context.getTargetBlock();if(ref==null||buffer==null||target==null)return false;
        var requested=new Vector3i(target.x,target.y,target.z);
        buffer.run(store->{if(!ref.isValid())return;var player=store.getComponent(ref,PlayerRef.getComponentType());
            try{
                var world=store.getExternalData().getWorld();var base=BlockOperations.resolveBaseBlockPosition(world.getChunkStore(),new com.hypixel.hytale.protocol.BlockPosition(requested.x,requested.y,requested.z));
                var machine=resolve(world,BlockModule.getBlockEntity(world,base.x,base.y,base.z));
                var at=store.getComponent(ref,TransformComponent.getComponentType());var held=InventoryComponentAccess.itemInHand(store,ref);
                var authoritative=TargetUtil.getTargetBlock(ref,6,store);
                var rayBase=authoritative==null?null:BlockOperations.resolveBaseBlockPosition(world.getChunkStore(),new com.hypixel.hytale.protocol.BlockPosition(authoritative.x,authoritative.y,authoritative.z));
                boolean allow=player!=null&&store.getComponent(ref,Player.getComponentType())!=null&&machine!=null&&at!=null&&at.getPosition().isFinite()
                    &&at.getPosition().distanceSquared(base.x+.5,base.y+.5,base.z+.5)<=49&&ScoutGearSystem.living(ref,store)&&store.getComponent(ref,Spectating.getComponentType())==null
                    &&rayBase!=null&&base.x==rayBase.x&&base.y==rayBase.y&&base.z==rayBase.z&&InteractionValidation.canPlayerInteractWithBlock(ref,store,held,requested);
                if(!allow){trace(store,ref,"invalidTargetOrProtection",false);return;}
                var state=machine.state.clone();
                if(!state.arm(player.getUuid(),store.getComponent(ref,EntityStatMap.getComponentType()),System.currentTimeMillis())){
                    trace(store,ref,"alreadyArmedOrNoMana",false);player.sendMessage(Message.raw(machine.state.armed()?"The Oraculum is already armed.":"The Oraculum needs mana to activate."));return;
                }
                world.getChunkStore().getStore().putComponent(machine.ref,type,state);machine.info.markNeedsSaving(world.getChunkStore().getStore());

                var section=world.getChunkStore().getChunkSectionReferenceAtBlock(base.x,base.y,base.z);
                if(section!=null&&section.isValid())BlockOperations.setBlockInteractionState(world.getChunkStore(),section,base.x,base.y,base.z,machine.block,"Armed",true);
                var animation=BlockAnimationModule.get();if(animation!=null)animation.setBlockAnimationSpeed(world,base.x,base.y,base.z,1,0);
                player.sendMessage(Message.raw("Oraculum armed: 5 seconds. 250-block blast radius."));
                trace(store,ref,"armedSpent="+state.spentMana,true);
            }catch(RuntimeException failure){errors.report(player,"Oraculum activation failed",failure);}
        });return true;
    }
    @Override public void tick(float delta,int tick,Store<EntityStore> entities){
        var world=entities.getExternalData().getWorld();var chunks=world.getChunkStore().getStore();long now=System.currentTimeMillis();
        chunks.forEachChunk(Query.and(type,BlockModule.BlockStateInfo.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);buffer.run(access->{
                try{
                    var d=resolve(world,ref);if(d==null||!d.state.armed())return;
                    var center=new Vector3d(d.at.x+.5,d.at.y+1,d.at.z+.5);
                    if(d.state.fired){removeOwnDevice(world,d);return;}
                    if(now<d.state.deadline){if(now>=d.state.nextSpark){d.state.nextSpark=now+500;ParticleUtil.spawnParticleEffect("Bow_Signature_Charge",center,entities);}return;}
                    var state=d.state.clone();if(!state.spend(now))return;
                    access.putComponent(ref,type,state);d.info.markNeedsSaving(access);

                    var owner=state.owner==null?null:entities.getExternalData().getRefFromUUID(state.owner);
                    applyBlast(entities,owner,center);
                    int boom=com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getIndex("SFX_Gobliterator_Self_Destruct_Explosion");
                    if(boom>=0)SoundUtil.playSoundEvent3d(boom,com.hypixel.hytale.protocol.SoundCategory.SFX,center.x,center.y,center.z,2,.75f,entities);
                    var viewers=world.getPlayerRefs().stream().map(PlayerRef::getReference).filter(r->r!=null&&r.isValid()&&r.getStore()==entities)
                        .filter(r->{var t=entities.getComponent(r,TransformComponent.getComponentType());return t!=null&&t.getPosition().distanceSquared(center)<=300*300;}).toList();
                    ParticleUtil.spawnParticleEffect("AxoTales_Oraculum_Blast",center,0,0,0,8,null,viewers,entities);
                    removeOwnDevice(world,d);
                    debug.traceFileOnly(owner==null?null:entities.getComponent(owner,PlayerRef.getComponentType()),"Oraculum event=detonated damage=4000 radius="+RADIUS+" includesActivator=true terrainDamage=false singleUse=true allow=true");
                }catch(RuntimeException failure){errors.report((PlayerRef)null,"Oraculum fuse failed",failure);}
            });}
        });
    }
    static float damage(Vector3d center,Vector3d target){return center.isFinite()&&target.isFinite()&&center.distanceSquared(target)<=RADIUS*RADIUS?DAMAGE:0;}
    static void applyBlast(Store<EntityStore> entities,Ref<EntityStore> owner,Vector3d center){
        var cause=DamageCause.getAssetMap().getAsset("Environment");if(cause==null)throw new IllegalStateException("Native Environment damage missing");
        Damage.Source source=owner!=null&&owner.isValid()?new Damage.EntitySource(owner):new Damage.EnvironmentSource("server.general.deathCause.explosion");
        entities.forEachChunk(Query.and(TransformComponent.getComponentType(),EntityStatMap.getComponentType()),(chunk,commands)->{
            for(int i=0;i<chunk.size();i++){
                var victim=chunk.getReferenceTo(i);if(!ScoutGearSystem.living(victim,commands))continue;
                float amount=damage(center,chunk.getComponent(i,TransformComponent.getComponentType()).getPosition());
                if(amount>0)DamageSystems.executeDamage(victim,commands,new Damage(source,cause,amount));
            }
        });
    }
    private static void removeOwnDevice(World world,Device d){
        var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(d.at.x,d.at.z));
        if(column!=null&&device(column.getBlockType(d.at.x,d.at.y,d.at.z)))column.setBlock(d.at.x,d.at.y,d.at.z,BlockType.EMPTY_ID);
    }
    private void trace(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref,String event,boolean allow){
        var held=InventoryComponentAccess.itemInHand(store,ref);var stats=store.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getMana();var mana=stats==null||index<0?null:stats.get(index);
        debug.traceFileOnly(store.getComponent(ref,PlayerRef.getComponentType()),"Oraculum event="+event+" cancelled=false source=serverUseAndLoadedBlock item="+(ItemStack.isEmpty(held)?"empty":held.getItemId())+" section=Hotbar slot="+InventoryComponentAccess.activeHotbarSlot(store,ref)+" mana.index="+index+" mana.current="+(mana==null?"missing":mana.get())+" mana.min="+(mana==null?"missing":mana.getMin())+" mana.max="+(mana==null?"missing":mana.getMax())+" allow="+allow);
    }
    boolean armed(Store<EntityStore> store,Vector3i at){
        var world=store.getExternalData().getWorld();var base=BlockOperations.resolveBaseBlockPosition(world.getChunkStore(),new com.hypixel.hytale.protocol.BlockPosition(at.x,at.y,at.z));
        var d=resolve(world,BlockModule.getBlockEntity(world,base.x,base.y,base.z));return d!=null&&d.state.armed();
    }
    static final class ProtectBreak extends EntityEventSystem<EntityStore,BreakBlockEvent>{
        private final OraculumSystem system;ProtectBreak(OraculumSystem system){super(BreakBlockEvent.class);this.system=system;}
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,BreakBlockEvent event){if(device(event.getBlockType())&&system.armed(store,event.getTargetBlock()))event.setCancelled(true);}
    }
    static final class ProtectDamage extends EntityEventSystem<EntityStore,DamageBlockEvent>{
        private final OraculumSystem system;ProtectDamage(OraculumSystem system){super(DamageBlockEvent.class);this.system=system;}
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,DamageBlockEvent event){if(device(event.getBlockType())&&system.armed(store,event.getTargetBlock()))event.setCancelled(true);}
    }
}
