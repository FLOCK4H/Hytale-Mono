package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.*;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import org.joml.*;
import java.util.*;
import java.lang.Math;
import javax.annotation.Nonnull;


final class SkillTreeSystem extends TickingSystem<EntityStore> {
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final Map<Ref<EntityStore>,Runtime> players=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Store<EntityStore>,List<Wave>> waves=Collections.synchronizedMap(new WeakHashMap<>());
    private record PendingBreak(Ref<EntityStore> player,Vector3i at,com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent event){}
    private final Map<Store<EntityStore>,Map<Vector3i,PendingBreak>> breaks=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Runtime {
        PlayerRef player;com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager manager;SkillLazarusHud hud;
        final SkillJumpControl jumps=new SkillJumpControl();
        final SkillReachControl reach=new SkillReachControl();
        float fiveSeconds,twoSeconds;long lastWave;final List<RuneSoul> souls=new ArrayList<>();
    }
    static final class RuneSoul {
        Ref<EntityStore> visual;Vector3d at;double elapsed;
        RuneSoul(Vector3d at){this.at=new Vector3d(at);}
        static Vector3d step(Vector3d at,Vector3d target,double elapsed,double seconds){
            var offset=new Vector3d(target).sub(at);double distance=offset.length(),travel=(10+20*elapsed)*seconds;
            return distance<=travel?new Vector3d(target):new Vector3d(at).add(offset.mul(travel/distance));
        }
    }
    private static final class Wave {
        Ref<EntityStore> owner;Vector3d at,direction;double distance;final Set<Ref<EntityStore>> hit=new HashSet<>();
        float visualSeconds;boolean visualFailed,visualReported;
    }
    static final class WaveSource extends Damage.EntitySource {WaveSource(Ref<EntityStore> ref){super(ref);}}
    SkillTreeSystem(PluginDebugReporter debug,PluginErrorReporter errors){this.debug=debug;this.errors=errors;}
    boolean handle(String mode,InteractionContext context){
        var ref=context.getEntity();var buffer=context.getCommandBuffer();if(ref==null||buffer==null)return false;
        if("wave".equals(mode)){launchWave(ref,buffer,context);return true;}
        var at=context.getTargetBlock();if(at==null)return false;var target=new Vector3i(at.x,at.y,at.z);
        buffer.run(store->{
            if(!ref.isValid())return;
            var actor=store.getComponent(ref,Player.getComponentType());var owner=store.getComponent(ref,PlayerRef.getComponentType());
            var base=com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations.resolveBaseBlockPosition(store.getExternalData().getWorld().getChunkStore(),new com.hypixel.hytale.protocol.BlockPosition(target.x,target.y,target.z));
            var trunk=new Vector3i(base.x,base.y,base.z);
            boolean allow=actor!=null&&owner!=null&&attached(ref,store,trunk);
            debug.traceFileOnly(owner,"SkillTree event=nativeUse source=serverTargetBlock cancelled=false allow="+allow);
            if(!allow)return;
            if(SkillProgression.read(store,ref)==null)store.putComponent(ref,SkillProgression.TYPE,new SkillProgression());
            actor.getPageManager().openCustomPage(ref,store,new SkillTreePage(owner,trunk));
        });return true;
    }
    static boolean attached(Ref<EntityStore> ref,ComponentAccessor<EntityStore> store,Vector3i at){
        if(!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null||store.getComponent(ref,Spectating.getComponentType())!=null)return false;
        var pos=store.getComponent(ref,TransformComponent.getComponentType());if(pos==null||pos.getPosition().distanceSquared(at.x+.5,at.y+.5,at.z+.5)>64)return false;
        var world=ref.getStore().getExternalData().getWorld();var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(at.x,at.z));
        var block=column==null?null:column.getBlockType(at.x,at.y,at.z);
        return AxoItems.block(block,SkillTreeGrowth.ITEM)&&"Mature".equals(block.getCurrentInteractionState());
    }
    void absorb(Store<EntityStore> store,Ref<EntityStore> ref,Vector3d at){
        if(!ref.isValid())return;var state=SkillProgression.read(store,ref);var next=state==null?new SkillProgression():state.clone();
        if(next.incoming==Integer.MAX_VALUE)return;next.incoming++;store.putComponent(ref,SkillProgression.TYPE,next);
        var runtime=runtime(store,ref);var soul=new RuneSoul(at);runtime.souls.add(soul);
        withPlayerBuffer(store,ref,buffer->{
            var config=ProjectileConfig.getAssetMap().getAsset("AxoTales_Rune_Soul");
            if(config==null)throw new IllegalStateException("Missing AxoTales_Rune_Soul projectile config");
            soul.visual=ProjectileModule.get().spawnProjectile(ref,buffer,config,new Vector3d(at),new Vector3d(0,0,1));
            if(soul.visual!=null)buffer.removeComponent(soul.visual,StandardPhysicsProvider.getComponentType());
        });
        debug.traceFileOnly(runtime.player,"Runes event=absorbing source=nativeSuccessfulBreak damage=0 incoming="+next.incoming+" projectile="+(soul.visual!=null)+" recipient=nearestLivingPlayer");
    }
    static void withPlayerBuffer(Store<EntityStore> store,Ref<EntityStore> ref,java.util.function.Consumer<CommandBuffer<EntityStore>> action){


        store.forEachChunk(Player.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++)if(ref.equals(chunk.getReferenceTo(i))){action.accept(buffer);return;}
        });
    }
    static Ref<EntityStore> nearestPlayer(Store<EntityStore> store,Vector3d at){
        var nearest=new java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>>();double[] best={Double.POSITIVE_INFINITY};
        store.forEachChunk(Query.and(Player.getComponentType(),PlayerRef.getComponentType(),TransformComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var candidate=chunk.getReferenceTo(i);
                if(buffer.getComponent(candidate,DeathComponent.getComponentType())!=null||buffer.getComponent(candidate,Spectating.getComponentType())!=null)continue;
                double distance=chunk.getComponent(i,TransformComponent.getComponentType()).getPosition().distanceSquared(at);
                if(Double.isFinite(distance)&&distance<best[0]){best[0]=distance;nearest.set(candidate);}
            }
        });
        return nearest.get();
    }
    void confirmBreakLater(Store<EntityStore> store,Ref<EntityStore> ref,Vector3i at,com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent event){
        traceInput(store,ref,"runeBreak",event.isCancelled(),"nativeBreakBlockEvent target="+at);
        breaks.computeIfAbsent(store,key->new HashMap<>()).putIfAbsent(at,new PendingBreak(ref,at,event));
    }
    private void confirmBreaks(Store<EntityStore> store){
        var pending=breaks.remove(store);if(pending==null)return;
        for(var receipt:pending.values()){
            if(receipt.event.isCancelled())continue;
            var at=receipt.at;var column=WorldBlocks.loaded(store.getExternalData().getWorld(),ChunkUtil.indexChunkFromBlock(at.x,at.z));
            if(column!=null&&!RuneWorldgen.rune(column.getBlockType(at.x,at.y,at.z))){
                var start=new Vector3d(at.x+.5,at.y+.4,at.z+.5);var recipient=nearestPlayer(store,start);
                if(recipient!=null)absorb(store,recipient,start);
            }
        }
    }
    private Runtime runtime(Store<EntityStore> store,Ref<EntityStore> ref){return players.computeIfAbsent(ref,key->{var r=new Runtime();r.player=store.getComponent(ref,PlayerRef.getComponentType());r.manager=store.getComponent(ref,Player.getComponentType()).getHudManager();return r;});}
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        if(!Float.isFinite(delta)||delta<=0)return;
        confirmBreaks(store);
        store.forEachChunk(Query.and(Player.getComponentType(),PlayerRef.getComponentType(),EntityStatMap.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);buffer.run(access->{if(ref.isValid())try{refresh(access,ref,Math.min(delta,1));}catch(RuntimeException failure){errors.report((PlayerRef)null,"Skill progression refresh failed",failure);}});}
        });
        tickWaves(store,Math.min(delta,.1f));
        for(var it=players.entrySet().iterator();it.hasNext();){var entry=it.next();if(!entry.getKey().isValid()){removeHud(entry.getValue());entry.getValue().souls.forEach(SkillTreeSystem::removeSoul);it.remove();}}
    }
    private void refresh(Store<EntityStore> store,Ref<EntityStore> ref,float delta){
        var state=SkillProgression.read(store,ref);if(state==null){state=new SkillProgression();store.putComponent(ref,SkillProgression.TYPE,state);}
        var runtime=runtime(store,ref);long now=System.currentTimeMillis();
        if(runtime.reach.refresh(runtime.player,SkillCatalog.reach(state.unlocked)))traceInput(store,ref,"meleeReach",false,"privateNativePrediction reach="+SkillCatalog.reach(state.unlocked));
        if(state.incoming>0){

            int arrived=runtime.souls.isEmpty()?state.incoming:0;
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            if(transform!=null){var target=new Vector3d(transform.getPosition()).add(0,1,0);
                for(var it=runtime.souls.iterator();it.hasNext();){var soul=it.next();soul.elapsed+=delta;
                    var previous=new Vector3d(soul.at);

                    if(soul.elapsed<.2)soul.at.add(0,Math.min(delta,.1)*2,0);
                    else soul.at=RuneSoul.step(soul.at,target,soul.elapsed,Math.min(delta,.1));
                    if(soul.elapsed>=6||soul.elapsed>=.55&&soul.at.distanceSquared(target)<.12){removeSoul(soul);it.remove();arrived++;continue;}
                    if(soul.visual!=null&&soul.visual.isValid()){
                        var position=store.getComponent(soul.visual,TransformComponent.getComponentType());if(position!=null)position.setPosition(soul.at);
                        var velocity=store.getComponent(soul.visual,Velocity.getComponentType());if(velocity!=null)velocity.set(new Vector3d(soul.at).sub(previous).div(Math.min(delta,.1)));
                    }
                    ParticleUtil.spawnParticleEffect("AxoTales_Rune_Soul",soul.at,store);
                }
            }
            if(arrived>0){
                var next=state.clone();int credit=Math.min(next.incoming,arrived);next.runes=Math.addExact(next.runes,credit);next.incoming-=credit;store.putComponent(ref,SkillProgression.TYPE,next);state=next;
                var pos=store.getComponent(ref,TransformComponent.getComponentType());if(pos!=null)ParticleUtil.spawnParticleEffect("AxoTales_Rune_Arrival",new Vector3d(pos.getPosition()).add(0,1,0),store);
            }
        }
        boolean alive=store.getComponent(ref,DeathComponent.getComponentType())==null;
        runtime.jumps.refresh(store,ref,alive&&state.has(12),delta);
        if(alive){
            var stats=store.getComponent(ref,EntityStatMap.getComponentType());runtime.fiveSeconds+=delta;runtime.twoSeconds+=delta;
            if(runtime.fiveSeconds>=5){runtime.fiveSeconds%=5;regen(stats,DefaultEntityStatTypes.getMana(),2);if(state.has(5))regen(stats,DefaultEntityStatTypes.getStamina(),2);}
            if(runtime.twoSeconds>=2){runtime.twoSeconds%=2;regen(stats,DefaultEntityStatTypes.getHealth(),(state.has(9)?1:0)+(state.has(16)?3:0));}
        }else{runtime.fiveSeconds=runtime.twoSeconds=0;}
        if(state.has(16)){
            if(runtime.hud==null){runtime.hud=new SkillLazarusHud(runtime.player);runtime.manager.addCustomHud(runtime.player,runtime.hud);}runtime.hud.refresh(state,now);
        }else removeHud(runtime);
    }
    private static void regen(EntityStatMap stats,int index,float amount){var v=index<0?null:stats.get(index);if(v!=null&&amount>0&&v.get()<v.getMax()){stats.addStatValue(index,Math.min(amount,v.getMax()-v.get()));stats.update();}}
    private void traceInput(Store<EntityStore> store,Ref<EntityStore> ref,String event,boolean cancelled,String source){
        var held=InventoryComponentAccess.itemInHand(store,ref);var stats=store.getComponent(ref,EntityStatMap.getComponentType());
        int index=DefaultEntityStatTypes.getMana();var mana=stats==null||index<0?null:stats.get(index);
        debug.traceFileOnly(store.getComponent(ref,PlayerRef.getComponentType()),"Skills event="+event+" source="+source+" item="+(ItemStack.isEmpty(held)?"empty":held.getItemId())+" cancelled="+cancelled+" manaIndex="+index+" mana="+(mana==null?"missing":mana.get()+"/"+mana.getMax()+" min="+mana.getMin())+" allow="+!cancelled);
    }
    private void launchWave(Ref<EntityStore> ref,CommandBuffer<EntityStore> buffer,InteractionContext context){
        var held=InventoryComponentAccess.itemInHand(buffer,ref);
        String decision=waveDecision(SkillProgression.mask(buffer,ref),buffer.getComponent(ref,DeathComponent.getComponentType())!=null,held);
        var look="eligible".equals(decision)?TargetUtil.getLook(ref,buffer):null;
        if("eligible".equals(decision)&&look==null)decision="missingLook";
        int slot=context.getHeldItemSlot(),section=context.getHeldItemSectionId();
        if(!"eligible".equals(decision)){traceWave(ref,buffer,slot,section,held,decision);return;}
        buffer.run(store->{if(!ref.isValid())return;var r=runtime(store,ref);long now=System.nanoTime();
            if(now-r.lastWave<250_000_000L){traceWave(ref,store,slot,section,held,"cooldown");return;}r.lastWave=now;
            var wave=new Wave();wave.owner=ref;wave.at=new Vector3d(look.getPosition());wave.direction=new Vector3d(look.getDirection()).normalize();waves.computeIfAbsent(store,key->new ArrayList<>()).add(wave);
            traceWave(ref,store,slot,section,held,"launched");});
    }
    static String waveDecision(int mask,boolean dead,ItemStack held){
        if(!SkillCatalog.has(mask,14))return "unlearned";
        if(dead)return "dead";
        if(ItemStack.isEmpty(held)||!sword(held.getItemId()))return "wrongItem";


        return held.isBroken()?"broken":"eligible";
    }
    private void traceWave(Ref<EntityStore> ref,ComponentAccessor<EntityStore> access,int slot,int section,ItemStack held,String decision){
        if(debug==null)return;
        var stats=access.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getMana();var mana=stats==null||index<0?null:stats.get(index);
        debug.traceFileOnly(access.getComponent(ref,PlayerRef.getComponentType()),"Skills event=airwave source=nativePrimary item="+(ItemStack.isEmpty(held)?"empty":held.getItemId())
            +" slot="+slot+" section="+section+" cancelled=false manaIndex="+index+" mana="+(mana==null?"missing":mana.get()+"/"+mana.getMax()+" min="+mana.getMin())
            +" manaGated=false allow="+"launched".equals(decision)+" decision="+decision);
    }
    static boolean sword(String id){return Set.of("Axo_Ancient_Sword","Axo_Arcane_Sword","Sar_Sword","Sars_Sword").contains(id);}
    private void tickWaves(Store<EntityStore> store,float delta){
        var active=waves.get(store);if(active==null||active.isEmpty())return;
        for(var it=active.iterator();it.hasNext();){var wave=it.next();if(!wave.owner.isValid()||wave.distance>=18){it.remove();continue;}
            var previous=new Vector3d(wave.at);double step=16*delta;boolean blocked=false;
            for(double d=0;d<=step;d+=.2){var p=new Vector3d(previous).fma(d,wave.direction);var column=WorldBlocks.loaded(store.getExternalData().getWorld(),ChunkUtil.indexChunkFromBlock((int)Math.floor(p.x),(int)Math.floor(p.z)));var block=column==null?null:column.getBlockType((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z));if(column==null||block!=null&&block.getMaterial()==BlockMaterial.Solid){blocked=true;break;}}
            if(blocked){it.remove();continue;}wave.at.fma(step,wave.direction);wave.distance+=step;

            store.forEachChunk(Query.and(TransformComponent.getComponentType(),EntityStatMap.getComponentType()),(chunk,buffer)->{
                for(int i=0;i<chunk.size();i++){var target=chunk.getReferenceTo(i);if(target.equals(wave.owner)||wave.hit.contains(target)||!ScoutGearSystem.living(target,buffer))continue;
                    var pos=new Vector3d(chunk.getComponent(i,TransformComponent.getComponentType()).getPosition()).add(0,.8,0);
                    var offset=new Vector3d(pos).sub(previous);double along=Math.clamp(offset.dot(wave.direction),0,step);
                    if(pos.distanceSquared(new Vector3d(previous).fma(along,wave.direction))>1.5*1.5)continue;
                    wave.hit.add(target);int cause=DamageCause.getAssetMap().getIndex("Projectile");if(cause>=0)DamageSystems.executeDamage(target,buffer,new Damage(new WaveSource(wave.owner),cause,31));
                }
            });
            wave.visualSeconds+=delta;
            if(!wave.visualFailed&&wave.visualSeconds>=.05f){
                wave.visualSeconds%=.05f;
                try{
                    int viewers=SkillAirwave.emit(store,wave.at);
                    if(!wave.visualReported&&debug!=null){wave.visualReported=true;debug.traceFileOnly(store.getComponent(wave.owner,PlayerRef.getComponentType()),"Skills event=airwaveFeedback source=nativePlayerSpatial viewers="+viewers+" position="+wave.at+" allow=true");}
                }catch(RuntimeException failure){
                    wave.visualFailed=true;if(errors!=null)errors.report(store.getComponent(wave.owner,PlayerRef.getComponentType()),"Sword air wave feedback failed; damage continues",failure);
                }
            }
        }
    }
    private static void removeHud(Runtime runtime){if(runtime.hud!=null){if(runtime.manager.getCustomHud(SkillLazarusHud.KEY)==runtime.hud)runtime.manager.removeCustomHud(runtime.player,SkillLazarusHud.KEY);runtime.hud=null;}}
    private static void removeSoul(RuneSoul soul){if(soul.visual==null||!soul.visual.isValid())return;var ref=soul.visual;var world=ref.getStore().getExternalData().getWorld();Runnable remove=()->{if(ref.isValid())ref.getStore().removeEntity(ref,RemoveReason.REMOVE);};if(world.isInThread())remove.run();else world.execute(remove);}
    void disconnect(PlayerRef player){players.entrySet().removeIf(entry->{if(!entry.getValue().player.getUuid().equals(player.getUuid()))return false;removeHud(entry.getValue());entry.getValue().jumps.restore();entry.getValue().reach.restore();entry.getValue().souls.forEach(SkillTreeSystem::removeSoul);return true;});}
    void close(){for(var runtime:players.values()){removeHud(runtime);runtime.jumps.restore();runtime.reach.restore();runtime.souls.forEach(SkillTreeSystem::removeSoul);}players.clear();waves.clear();breaks.clear();}
}
