package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;


public final class ScoutGearSystem extends TickingSystem<EntityStore> {
    static final String SPYGLASS="Axo_Spyglass",PROJECTILE="Axo_Ancient_Arrow";
    static final float LOCK_RANGE=96,VIEW_RANGE=120;
    private final ComponentType<EntityStore,AncientArrowState> arrowType;
    private final SpyglassSettings settings;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final Map<Ref<EntityStore>,Long> cooldowns=new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>,Integer> lastFiredChain=new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>,View> views=new ConcurrentHashMap<>();
    private final Set<Ref<EntityStore>> shots=ConcurrentHashMap.newKeySet();
    private final Map<Store<EntityStore>,Long> hudAt=Collections.synchronizedMap(new WeakHashMap<>());
    private record View(PlayerRef player,com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager manager,SpyglassHud hud){}
    ScoutGearSystem(ComponentType<EntityStore,AncientArrowState> arrowType,SpyglassSettings settings,PluginDebugReporter debug,PluginErrorReporter errors){this.arrowType=arrowType;this.settings=settings;this.debug=debug;this.errors=errors;}
    static boolean living(Ref<EntityStore> ref,ComponentAccessor<EntityStore> access){
        if(ref==null||!ref.isValid()||access.getComponent(ref,DeathComponent.getComponentType())!=null)return false;
        var stats=access.getComponent(ref,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());return hp!=null&&hp.get()>hp.getMin();
    }
    boolean handle(InteractionContext context,String mode){
        var buffer=context.getCommandBuffer();var owner=context.getEntity();if(buffer==null||owner==null||!owner.isValid())return false;
        var player=buffer.getComponent(owner,PlayerRef.getComponentType());if(player==null)return false;
        var held=InventoryComponentAccess.itemInHand(buffer,owner);
        if("spyglass".equals(mode)){boolean allowed=!ItemStack.isEmpty(held)&&SPYGLASS.equals(held.getItemId())&&living(owner,buffer);trace(owner,buffer,"nativeSpyglassRaise",allowed);return allowed;}
        if(ItemStack.isEmpty(held)||!held.getItemId().startsWith("Weapon_Shortbow_"))return false;
        var definition=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(held.getItemId());
        if(definition!=null&&definition.getMaxDurability()>0&&held.getDurability()<=0)return false;
        var ammo=AncientArrowAmmo.find(buffer,owner);
        if("select".equals(mode)){
            var stats=buffer.getComponent(owner,EntityStatMap.getComponentType());var signature=SpyglassReadout.value(stats,"SignatureCharges");
            boolean selected=ammo!=null&&living(owner,buffer)&&(signature==null||signature.get()<1);trace(owner,buffer,"nativeBowAmmoChoice",selected);return selected;
        }
        if(!"fire".equals(mode)||ammo==null||!living(owner,buffer))return false;
        return fireAncient(context,ammo,true)!=null;
    }
    Ref<EntityStore> fireAncient(InteractionContext context,AncientArrowAmmo.Slot ammo,boolean consume){
        var buffer=context.getCommandBuffer();var owner=context.getEntity();var player=buffer.getComponent(owner,PlayerRef.getComponentType());
        if(ammo==null||player==null||!living(owner,buffer))return null;
        if(context.getChain()==null||Objects.equals(lastFiredChain.get(owner),context.getChain().getChainId())){trace(owner,buffer,"releaseReplay",false);return null;}
        if(System.nanoTime()<cooldowns.getOrDefault(owner,0L)){trace(owner,buffer,"releaseCooldown",false);return null;}
        var target=ScoutTargets.visible(owner,LOCK_RANGE,true,buffer);
        if(target==null||target.equals(owner)||!living(target,buffer)){
            player.sendMessage(Message.raw("Ancient Arrows need a living target in your crosshair within 96 blocks."));trace(owner,buffer,"releaseNoTargetAmmoPreserved",false);return null;
        }
        var look=TargetUtil.getLook(owner,buffer);var end=buffer.getComponent(target,TransformComponent.getComponentType());var config=ProjectileConfig.getAssetMap().getAsset(PROJECTILE);
        if(look==null||end==null||config==null)return null;
        var aim=new Vector3d(end.getPosition()).add(0,.7,0);var start=new Vector3d(look.getPosition());var direction=new Vector3d(aim).sub(start);
        if(!aim.isFinite()||!start.isFinite()||direction.lengthSquared()<.001)return null;
        var arrow=ProjectileModule.get().spawnProjectile(owner,buffer,config,start,direction.normalize());
        if(arrow==null)return null;


        if(!RelicBowAmmo.spend(ammo,!consume)){buffer.removeEntity(arrow,RemoveReason.REMOVE);trace(owner,buffer,"releaseStaleAmmo",false);return null;}



        buffer.removeComponent(arrow,StandardPhysicsProvider.getComponentType());
        buffer.putComponent(arrow,arrowType,new AncientArrowState(owner,target));shots.add(arrow);lastFiredChain.put(owner,context.getChain().getChainId());cooldowns.put(owner,System.nanoTime()+800_000_000L);
        trace(owner,buffer,"lockedArrowReleased",true);return arrow;
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        if(!Float.isFinite(delta)||delta<=0)return;
        store.forEachChunk(Query.and(arrowType,TransformComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var ref=chunk.getReferenceTo(i);var state=chunk.getComponent(i,arrowType);
                try{
                    if(state.finished||state.owner==null||state.target==null||state.owner.getStore()!=store||state.target.getStore()!=store||!living(state.owner,buffer)||!living(state.target,buffer)){
                        buffer.removeEntity(ref,RemoveReason.REMOVE);continue;
                    }
                    var transform=chunk.getComponent(i,TransformComponent.getComponentType());var target=buffer.getComponent(state.target,TransformComponent.getComponentType());
                    if(target==null){buffer.removeEntity(ref,RemoveReason.REMOVE);continue;}
                    var aim=new Vector3d(target.getPosition()).add(0,.7,0);double seconds=Math.min(delta,.25);state.elapsed+=delta;
                    if(!aim.isFinite()||!transform.getPosition().isFinite()){buffer.removeEntity(ref,RemoveReason.REMOVE);continue;}
                    var position=new Vector3d(transform.getPosition());var next=AncientArrowState.step(position,aim,seconds);
                    if(next.distanceSquared(aim)<.35*.35||state.elapsed>=AncientArrowState.MAX_FLIGHT){detonate(state,ref,aim,buffer);buffer.removeEntity(ref,RemoveReason.REMOVE);continue;}
                    transform.setPosition(next);transform.setRotation(Rotation3f.lookAt(new Vector3d(aim).sub(next)));
                    var velocity=buffer.getComponent(ref,Velocity.getComponentType());if(velocity!=null)velocity.set(new Vector3d(next).sub(position).div(seconds));
                }catch(RuntimeException failure){buffer.removeEntity(ref,RemoveReason.REMOVE);errors.report((PlayerRef)null,"Ancient arrow guidance failed",failure);}
            }
        });
        long now=System.nanoTime();if(now>=hudAt.getOrDefault(store,0L)){hudAt.put(store,now+150_000_000L);updateHud(store);}
        cooldowns.keySet().removeIf(ref->!ref.isValid());lastFiredChain.keySet().removeIf(ref->!ref.isValid());shots.removeIf(ref->!ref.isValid());
    }
    void detonate(AncientArrowState state,Ref<EntityStore> arrow,Vector3d at,CommandBuffer<EntityStore> buffer){
        if(!applyBlast(state,arrow,at,List.copyOf(TargetUtil.getAllEntitiesInSphere(at,AncientArrowState.RADIUS,buffer)),buffer))return;
        ParticleUtil.spawnParticleEffect("Explosion_Medium",at,buffer);
        debug.traceFileOnly(buffer.getComponent(state.owner,PlayerRef.getComponentType()),"AncientArrow event=detonated cancelled=false direct=180 blast=60 radius=4 terrainDamage=false allow=true");
    }
    static boolean applyBlast(AncientArrowState state,Ref<EntityStore> arrow,Vector3d at,List<Ref<EntityStore>> victims,CommandBuffer<EntityStore> buffer){
        if(!state.spend())return false;
        var cause=DamageCause.getAssetMap().getAsset("Projectile");if(cause==null)cause=DamageCause.getAssetMap().getAsset("Physical");
        if(cause==null)throw new IllegalStateException("Native arrow damage cause missing");
        DamageSystems.executeDamage(state.target,buffer,new Damage(new Damage.ProjectileSource(state.owner,arrow),cause,AncientArrowState.DIRECT_DAMAGE));


        for(var victim:victims){
            if(victim.equals(state.owner)||victim.equals(state.target)||victim.equals(arrow)||!living(victim,buffer))continue;
            var t=buffer.getComponent(victim,TransformComponent.getComponentType());if(t==null||t.getPosition().distanceSquared(at)>AncientArrowState.RADIUS*AncientArrowState.RADIUS)continue;
            DamageSystems.executeDamage(victim,buffer,new Damage(new Damage.ProjectileSource(state.owner,arrow),cause,AncientArrowState.BLAST_DAMAGE));
        }
        return true;
    }
    private void updateHud(Store<EntityStore> store){
        var active=new HashSet<Ref<EntityStore>>();
        store.forEachChunk(Query.and(PlayerRef.getComponentType(),Player.getComponentType(),DamageDataComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var ref=chunk.getReferenceTo(i);var held=InventoryComponentAccess.itemInHand(buffer,ref);var wield=chunk.getComponent(i,DamageDataComponent.getComponentType()).getCurrentWielding();
                if(ItemStack.isEmpty(held)||!SPYGLASS.equals(held.getItemId())||wield==null||!"AxoSpyglass_Wield".equals(wield.getId())||!living(ref,buffer))continue;
                active.add(ref);var view=views.get(ref);
                if(view==null){var player=chunk.getComponent(i,PlayerRef.getComponentType());var manager=chunk.getComponent(i,Player.getComponentType()).getHudManager();var hud=new SpyglassHud(player);manager.addCustomHud(player,hud);view=new View(player,manager,hud);views.put(ref,view);}
                var target=ScoutTargets.visible(ref,VIEW_RANGE,false,buffer);view.hud.refresh(SpyglassReadout.capture(buffer,ref,target,settings.fields));
            }
        });
        for(var it=views.entrySet().iterator();it.hasNext();){var entry=it.next();if((!entry.getKey().isValid()||entry.getKey().getStore()==store)&&!active.contains(entry.getKey())){remove(entry.getValue());it.remove();}}
    }
    private static void remove(View view){if(view.manager.getCustomHud(SpyglassHud.KEY)==view.hud)view.manager.removeCustomHud(view.player,SpyglassHud.KEY);}
    void close(){
        for(var view:views.values())remove(view);views.clear();cooldowns.clear();lastFiredChain.clear();hudAt.clear();
        for(var ref:shots)if(ref.isValid()){var store=ref.getStore();store.getExternalData().getWorld().execute(()->{if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);});}
        shots.clear();AncientBowRoutes.close();
    }
    private void trace(Ref<EntityStore> owner,ComponentAccessor<EntityStore> store,String event,boolean allow){
        var item=InventoryComponentAccess.itemInHand(store,owner);var stats=store.getComponent(owner,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        var signature=SpyglassReadout.value(stats,"SignatureCharges");
        debug.traceFileOnly(store.getComponent(owner,PlayerRef.getComponentType()),"Scout event="+event+" cancelled=false item="+(ItemStack.isEmpty(item)?"empty":item.getItemId())+" source=serverHeldItem healthIndex="+DefaultEntityStatTypes.getHealth()+" health="+(hp==null?"missing":hp.get()+"/"+hp.getMax()+" min="+hp.getMin())+" signatureIndex="+com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType.getAssetMap().getIndex("SignatureCharges")+" signature="+(signature==null?"missing":signature.get()+"/"+signature.getMax()+" min="+signature.getMin())+" allow="+allow);
    }
}
