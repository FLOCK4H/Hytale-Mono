package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Vector3d;


final class AncientCombatSystem {
    static final class BlastSource extends Damage.EntitySource {BlastSource(Ref<EntityStore> owner){super(owner);}}
    static final String FIREBALL="AxoTales_Ancient_Blue_Fireball",SWORD="Axo_Ancient_Sword",SLASH="AxoTales_Endgame_Slash",RETURN="AxoTales_Ancient_Reflection",BLOODFANG="Axo_Bloodfang_Daggers";
    private final ComponentType<EntityStore,AncientProjectileState> type;
    private final PluginDebugReporter debug;private final PluginErrorReporter errors;
    private record Input(Ref<EntityStore> ref,String mode){}
    private record Cast(int chain,long at){}
    private final Map<Input,Cast> casts=new ConcurrentHashMap<>();
    private final Map<Input,Cast> pending=new ConcurrentHashMap<>();
    private final Set<Ref<EntityStore>> shots=ConcurrentHashMap.newKeySet();
    private record DaggerDraw(int chain,byte slot,com.hypixel.hytale.server.core.inventory.ItemStack item,long started){}
    private final Map<Ref<EntityStore>,DaggerDraw> daggerDraws=Collections.synchronizedMap(new WeakHashMap<>());
    AncientCombatSystem(ComponentType<EntityStore,AncientProjectileState> type,PluginDebugReporter debug,PluginErrorReporter errors){this.type=type;this.debug=debug;this.errors=errors;}
    boolean handle(String mode,InteractionContext context){
        try{
            var buffer=context.getCommandBuffer();var owner=context.getEntity();if(buffer==null||owner==null||!owner.isValid())return false;
            if("impact".equals(mode))return impact(context,buffer,owner);
            var held=InventoryComponentAccess.itemInHand(buffer,owner);String item=ItemStack.isEmpty(held)?null:held.getItemId();
            if(!ScoutGearSystem.living(owner,buffer)||context.getChain()==null)return false;
            int chain=context.getChain().getChainId();long now=System.nanoTime();boolean allowed;
            if(mode.startsWith("dagger"))return daggers(mode,context,owner,buffer,held,chain,now);
            if("bloodfang".equals(mode)){
                var movement=buffer.getComponent(owner,MovementStatesComponent.getComponentType());
                allowed=BLOODFANG.equals(item)&&groundDash(movement==null?null:movement.getMovementStates())&&reserve(owner,mode,chain,now,400_000_000L);trace(owner,buffer,mode,allowed,"dash=boundedNativeSetForce jumpPounce=false cooldown=.4");return allowed;
            }
            boolean gloves=AncientArmor.worn(buffer,owner,ItemArmorSlot.Hands,AncientArmor.HANDS)&&item==null;
            if("gauntletStart".equals(mode)){
                boolean clear=gloves&&airCast(owner,buffer);
                allowed=clear&&reserve(owner,mode,chain,now,900_000_000L);
                if(allowed){pending.put(new Input(owner,"gauntlets"),new Cast(chain,now));AnimationUtils.playAnimation(owner,AnimationSlot.Action,"AxoTales_AncientGauntlets","AncientFireball",true,buffer);var at=TargetUtil.getLook(owner,buffer);if(at!=null)ParticleUtil.spawnParticleEffect("AxoTales_Ancient_Fireball_Cast",new Vector3d(at.getPosition()).fma(.65,at.getDirection()).add(0,-.25,0),buffer);}


                trace(owner,buffer,mode,allowed,"hands="+AncientArmor.HANDS+" emptyHand="+(item==null)+" clearAim="+clear);return clear;
            }
            if("gauntlets".equals(mode)){
                var start=pending.remove(new Input(owner,"gauntlets"));if(!gloves||start==null||start.chain!=chain||now-start.at<280_000_000L||!airCast(owner,buffer)){trace(owner,buffer,mode,false,"reason=targetOrInterruptedCast");return false;}
                allowed=shoot(owner,buffer,FIREBALL,roll(ThreadLocalRandom.current()),cause("Fire"),true,false,null)!=null;
                trace(owner,buffer,mode,allowed,"baseDamage=71..110 blastRadius=4 terrainDamage=false");return allowed;
            }
            if("swordStart".equals(mode)||"sword".equals(mode)){
                var stats=buffer.getComponent(owner,EntityStatMap.getComponentType());int manaIndex=DefaultEntityStatTypes.getMana();var mana=stats==null||manaIndex<0?null:stats.get(manaIndex);
                if(!SWORD.equals(item)||held.getDurability()<=0||mana==null||mana.get()-mana.getMin()<30){trace(owner,buffer,mode,false,"reason=itemDurabilityOrMana");return false;}
                if("swordStart".equals(mode)){
                    if(!reserve(owner,mode,chain,now,1_250_000_000L))return false;
                    pending.put(new Input(owner,"sword"),new Cast(chain,now));AnimationUtils.playAnimation(owner,AnimationSlot.Action,"AxoTales_AncientEndgameSword","SpellCast",true,buffer);trace(owner,buffer,mode,true,"releaseDelay=.34 manaCost=30");return true;
                }
                var start=pending.remove(new Input(owner,"sword"));if(start==null||start.chain!=chain||now-start.at<300_000_000L){trace(owner,buffer,mode,false,"reason=staleOrInterruptedCast");return false;}
                allowed=shoot(owner,buffer,SLASH,100,cause("Projectile"),false,false,null)!=null;
                if(allowed){stats.subtractStatValue(manaIndex,30);stats.update();}
                trace(owner,buffer,mode,allowed,"manaCost=30 baseDamage=100");return allowed;
            }
            return false;
        }catch(RuntimeException failure){errors.report((PlayerRef)null,"Ancient native combat action failed",failure);return false;}
    }
    static boolean groundDash(MovementStates states){return states!=null&&states.onGround&&!states.jumping&&!states.flying;}
    static boolean airCast(Ref<EntityStore> owner,ComponentAccessor<EntityStore> access){
        return TargetUtil.getTargetBlock(owner,6,access)==null&&ScoutTargets.visible(owner,6,false,access)==null;
    }
    private boolean reserve(Ref<EntityStore> ref,String mode,int chain,long now,long interval){
        var key=new Input(ref,mode);var previous=casts.get(key);
        if(previous!=null&&(previous.chain==chain||now-previous.at<interval))return false;
        casts.put(key,new Cast(chain,now));casts.keySet().removeIf(k->!k.ref.isValid());return true;
    }
    static boolean daggerCharged(double seconds){return Double.isFinite(seconds)&&seconds+.025>=.8;}
    private boolean daggers(String mode,InteractionContext context,Ref<EntityStore> owner,CommandBuffer<EntityStore> buffer,com.hypixel.hytale.server.core.inventory.ItemStack held,int chain,long now){
        if(ItemStack.isEmpty(held)||!BLOODFANG.equals(held.getItemId())||held.isBroken())return false;
        byte slot=InventoryComponentAccess.activeHotbarSlot(buffer,owner);
        if("daggerDraw".equals(mode)){daggerDraws.put(owner,new DaggerDraw(chain,slot,held,now));allowedTrace(owner,buffer,true,"daggerDraw");return true;}
        var draw=daggerDraws.get(owner);
        boolean valid=draw!=null&&draw.chain==chain&&draw.slot==slot&&draw.item.equals(held)&&daggerCharged((now-draw.started)/1e9);
        if("daggerStart".equals(mode)){
            boolean allowed=valid&&reserve(owner,"daggerThrow",chain,now,5_000_000_000L);allowedTrace(owner,buffer,allowed,"daggerCharge");return allowed;
        }
        daggerDraws.remove(owner);
        var reserved=casts.get(new Input(owner,"daggerThrow"));
        if(!"daggerRelease".equals(mode)||!valid||reserved==null||reserved.chain!=chain||now-reserved.at>2_000_000_000L)return false;
        var look=TargetUtil.getLook(owner,buffer);var config=ProjectileConfig.getAssetMap().getAsset("AxoTales_Bloodfang_Thrown");if(look==null||config==null)return false;
        var direction=new Vector3d(look.getDirection()).normalize();var side=new Vector3d(direction.z,0,-direction.x);if(side.lengthSquared()<.001)side.set(1,0,0);else side.normalize();
        var victim=ScoutTargets.visible(owner,48,false,buffer);Vector3d head=null;
        if(victim!=null&&victim.isValid()){
            var body=buffer.getComponent(victim,ModelComponent.getComponentType());var at=buffer.getComponent(victim,TransformComponent.getComponentType());
            if(at!=null)head=new Vector3d(at.getPosition()).add(0,body==null?1.25:body.getModel().getEyeHeight(),0);
        }
        var pair=new ArrayList<Ref<EntityStore>>();
        for(int sign:new int[]{-1,1}){
            var start=new Vector3d(look.getPosition()).fma(sign*.22,side);var aim=head==null?new Vector3d(direction):new Vector3d(head).sub(start).normalize();
            var blade=ProjectileModule.get().spawnProjectile(owner,buffer,config,start,aim);
            if(blade==null){for(var previous:pair)buffer.removeEntity(previous,RemoveReason.REMOVE);return false;}
            pair.add(blade);buffer.putComponent(blade,type,new AncientProjectileState(owner,125,cause("Projectile"),false,false));shots.add(blade);
        }
        trace(owner,buffer,"daggerRelease",true,"pair=2 totalBaseDamage=250 cooldown=5 headAim="+(head!=null));return true;
    }
    private void allowedTrace(Ref<EntityStore> owner,CommandBuffer<EntityStore> buffer,boolean allow,String mode){trace(owner,buffer,mode,allow,"itemSource=serverHotbar charge=.8 cooldown=5 prediction=false");}
    static int roll(java.util.random.RandomGenerator rng){return rng.nextInt(71,111);}
    private static int cause(String id){int index=DamageCause.getAssetMap().getIndex(id);if(index<0)throw new IllegalStateException("Missing damage cause "+id);return index;}
    private Ref<EntityStore> shoot(Ref<EntityStore> owner,CommandBuffer<EntityStore> buffer,String id,float damage,int cause,boolean explosion,boolean reflection,Vector3d toward){
        var look=TargetUtil.getLook(owner,buffer);var config=ProjectileConfig.getAssetMap().getAsset(id);if(look==null||config==null)return null;
        var start=new Vector3d(look.getPosition());var direction=toward==null?look.getDirection():new Vector3d(toward).sub(start);
        if(!start.isFinite()||!direction.isFinite()||direction.lengthSquared()<.0001)return null;
        var projectile=ProjectileModule.get().spawnProjectile(owner,buffer,config,start,direction.normalize());if(projectile==null)return null;
        buffer.putComponent(projectile,type,new AncientProjectileState(owner,damage,cause,explosion,reflection));shots.removeIf(ref->!ref.isValid());shots.add(projectile);return projectile;
    }
    private boolean impact(InteractionContext context,CommandBuffer<EntityStore> buffer,Ref<EntityStore> projectile){
        var state=buffer.getComponent(projectile,type);var position=buffer.getComponent(projectile,TransformComponent.getComponentType());
        if(state==null||position==null||state.spent)return false;
        if(state.owner==null||!state.owner.isValid()||state.owner.getStore()!=buffer.getStore()){buffer.removeEntity(projectile,RemoveReason.REMOVE);return false;}
        var at=position.getPosition();var target=context.getTargetEntity();
        if(state.explosion)ParticleUtil.spawnParticleEffect("AxoTales_Ancient_Fireball_Impact",at,buffer);
        if(!applyImpact(state,projectile,at,target,state.explosion?List.copyOf(TargetUtil.getAllEntitiesInSphere(at,4,buffer)):List.of(),buffer))return false;
        trace(state.owner,buffer,"projectileImpact",true,"damage="+state.damage+" explosion="+state.explosion+" reflection="+state.reflection+" terrainDamage=false");
        buffer.removeEntity(projectile,RemoveReason.REMOVE);shots.remove(projectile);return true;
    }
    static boolean applyImpact(AncientProjectileState state,Ref<EntityStore> projectile,Vector3d at,Ref<EntityStore> target,List<Ref<EntityStore>> victims,CommandBuffer<EntityStore> buffer){
        if(!state.spend())return false;
        if(!state.explosion){if(target!=null&&!target.equals(state.owner)&&ScoutGearSystem.living(target,buffer))damage(state,projectile,target,true,buffer);return true;}
        for(var victim:victims){
            if(victim.equals(state.owner)||victim.equals(projectile)||!ScoutGearSystem.living(victim,buffer))continue;
            var t=buffer.getComponent(victim,TransformComponent.getComponentType());if(t!=null&&t.getPosition().distanceSquared(at)<=16)damage(state,projectile,victim,victim.equals(target),buffer);
        }
        return true;
    }
    private static void damage(AncientProjectileState state,Ref<EntityStore> projectile,Ref<EntityStore> victim,boolean direct,CommandBuffer<EntityStore> buffer){
        Damage.Source source=direct?new Damage.ProjectileSource(state.owner,projectile):new BlastSource(state.owner);
        DamageSystems.executeDamage(victim,buffer,new Damage(source,state.cause,state.damage));
    }
    boolean reflect(Ref<EntityStore> defender,Damage damage,CommandBuffer<EntityStore> buffer){
        if(!(damage.getSource() instanceof Damage.ProjectileSource source)||source.getRef()==null||!source.getRef().isValid()||source.getRef().equals(defender))return false;
        var old=source.getProjectile();var marker=old!=null&&old.isValid()?buffer.getComponent(old,type):null;if(marker!=null&&marker.reflection)return false;
        var attacker=buffer.getComponent(source.getRef(),TransformComponent.getComponentType());if(attacker==null||source.getRef().getStore()!=buffer.getStore())return false;
        var visual=old!=null&&old.isValid()?buffer.getComponent(old,ModelComponent.getComponentType()):null;
        var returned=shoot(defender,buffer,RETURN,damage.getInitialAmount()*1.3f,damage.getDamageCauseIndex(),false,true,new Vector3d(attacker.getPosition()).add(0,.7,0));
        if(returned==null)return false;
        if(visual!=null)buffer.putComponent(returned,ModelComponent.getComponentType(),new ModelComponent(visual.getModel()));
        damage.setCancelled(true);damage.setAmount(0);trace(defender,buffer,"projectileReflected",true,"damage="+damage.getInitialAmount()*1.3f+" nativePhysics=true");return true;
    }
    boolean reflected(Damage damage,ComponentAccessor<EntityStore> access){
        if(!(damage.getSource() instanceof Damage.ProjectileSource p)||p.getProjectile()==null||!p.getProjectile().isValid())return false;
        var state=access.getComponent(p.getProjectile(),type);return state!=null&&state.reflection;
    }
    void close(){for(var ref:shots)if(ref.isValid()){var store=ref.getStore();store.getExternalData().getWorld().execute(()->{if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);});}shots.clear();casts.clear();pending.clear();daggerDraws.clear();}
    private void trace(Ref<EntityStore> ref,ComponentAccessor<EntityStore> access,String event,boolean allow,String details){
        var stats=access.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getMana();var v=stats==null||index<0?null:stats.get(index);var held=InventoryComponentAccess.itemInHand(access,ref);
        debug.traceFileOnly(access.getComponent(ref,PlayerRef.getComponentType()),"AncientCombat event="+event+" cancelled=false source=nativeServerInteraction item="+(ItemStack.isEmpty(held)?"empty":held.getItemId())+" allow="+allow+" mana.index="+index+" mana="+(v==null?"missing":v.get()+"/"+v.getMax()+" min="+v.getMin())+" "+details);
    }
}
