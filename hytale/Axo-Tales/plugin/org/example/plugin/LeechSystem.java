package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;


final class LeechSystem extends TickingSystem<EntityStore> {
    static final String BOOK="Axo_Leech_Book";
    static final float RANGE=16,DAMAGE=30,HEAL=20,MANA=10;
    static final class Channel {
        String physical;short slot;UUID target;int chainId;WeakReference<InteractionChain> chain;
        double elapsed,visual;float seconds;
        boolean feedbackDisabled;RuntimeException feedbackFailure;
        boolean advance(float delta) {
            if(!Float.isFinite(delta)||delta<=0)return false;
            elapsed+=delta;seconds+=delta;
            if(seconds+.0001f<1)return false;
            seconds=seconds<2?Math.max(0,seconds-1):seconds%1;return true;
        }
    }
    record Pulse(boolean accepted,float damage,float healed,float mana){}
    private final Map<Ref<EntityStore>,Channel> channels=new ConcurrentHashMap<>();
    private final SweepClock<Store<EntityStore>> bloodClock=new SweepClock<>();
    private final SpellbookPouchService pouches;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    LeechSystem(SpellbookPouchService pouches,PluginDebugReporter debug,PluginErrorReporter errors){this.pouches=pouches;this.debug=debug;this.errors=errors;}
    com.hypixel.hytale.component.system.RefSystem<EntityStore> lifecycle(){return new com.hypixel.hytale.component.system.RefSystem<>(){
        @Override public Query<EntityStore> getQuery(){return Player.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){channels.remove(ref);}
    };}


    boolean input(InteractionType type,InteractionContext context,boolean release,boolean alternate) {
        var held=context.getHeldItem();String physical=ItemStack.isEmpty(held)?"":held.getItemId();
        if(type!=InteractionType.Secondary||!physical(physical))return false;
        var ref=context.getOwningEntity();var store=ref.getStore();var chain=context.getChain();
        short slot=InventoryComponentAccess.activeHotbarSlot(context.getCommandBuffer(),ref);
        store.getExternalData().getWorld().execute(()->{
            if(!ref.isValid())return;
            if(release) {
                var active=channels.get(ref);
                if(active!=null&&active.chainId==chain.getChainId()){channels.remove(ref);trace(store,ref,"release",false,"nativeRelease");}
                return;
            }
            if(alternate){cancel(store,ref,chain);trace(store,ref,"start",false,"noAlternateAction");return;}
            if(channels.containsKey(ref)){if(channels.get(ref).chainId!=chain.getChainId())cancel(store,ref,chain);trace(store,ref,"start",false,"alreadyChanneling");return;}
            String nativeState=" requestedChain="+chain.getChainId()+" native.server="+chain.getServerState()+" native.client="+chain.getClientState()+" native.outcome="+chain.getFinalState();
            if(ended(chain)){cancel(store,ref,chain);trace(store,ref,"start",false,"nativeChainEnded"+nativeState);return;}
            if(!held(store,ref,physical,slot)){cancel(store,ref,chain);trace(store,ref,"start",false,"handSlotOrBookChanged"+nativeState);return;}
            if(dead(store,ref)){cancel(store,ref,chain);trace(store,ref,"start",false,"casterDead"+nativeState);return;}
            var target=TargetUtil.getTargetEntity(ref,RANGE,store);String denied=eligible(store,ref,target);
            if(denied!=null||!enoughMana(store,ref)){cancel(store,ref,chain);trace(store,ref,"start",false,denied==null?"insufficientMana":denied);return;}
            var channel=new Channel();channel.physical=physical;channel.slot=slot;channel.chainId=chain.getChainId();channel.chain=new WeakReference<>(chain);
            channel.target=store.getComponent(target,UUIDComponent.getComponentType()).getUuid();channels.put(ref,channel);
            trace(store,ref,"start",true,"lockedTarget="+channel.target+nativeState);
        });
        return true;
    }
    static boolean physical(String id){return BOOK.equals(id)||SpellbookCatalog.LEECH.wandId().equals(id);}
    private boolean held(Store<EntityStore> store,Ref<EntityStore> ref,String physical,short slot) {
        var item=InventoryComponentAccess.itemInHand(store,ref);
        return !ItemStack.isEmpty(item)&&physical.equals(item.getItemId())&&slot==InventoryComponentAccess.activeHotbarSlot(store,ref)
            &&(BOOK.equals(physical)||pouches.owns(store,ref,SpellbookCatalog.LEECH));
    }
    static boolean ended(InteractionChain chain){



        return chain==null||chain.getServerState()!=InteractionState.NotFinished
            ||(chain.requiresClient()&&chain.getClientState()!=InteractionState.NotFinished);
    }
    private static void cancel(Store<EntityStore> store,Ref<EntityStore> ref,InteractionChain chain){
        var manager=store.getComponent(ref,com.hypixel.hytale.server.core.modules.interaction.InteractionModule.get().getInteractionManagerComponent());
        if(manager!=null&&chain!=null&&!ended(chain))manager.cancelChains(chain);
    }
    private static boolean dead(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref){return store.getComponent(ref,DeathComponent.getComponentType())!=null;}
    private static boolean enoughMana(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref) {
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());var mana=stats==null?null:stats.get(DefaultEntityStatTypes.getMana());
        return mana!=null&&Float.isFinite(mana.get())&&mana.get()-mana.getMin()>=MANA;
    }
    private static String eligible(Store<EntityStore> store,Ref<EntityStore> caster,Ref<EntityStore> target) {
        if(target==null||!target.isValid()||target.equals(caster)||target.getStore()!=store||store.getComponent(target,UUIDComponent.getComponentType())==null||dead(store,target))return "invalidTarget";
        boolean player=store.getComponent(target,Player.getComponentType())!=null;
        if(!player&&store.getComponent(target,NPCEntity.getComponentType())==null)return "notLivingActor";
        if(player&&!store.getExternalData().getWorld().getWorldConfig().isPvpEnabled())return "pvpDisabled";
        var stats=store.getComponent(target,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        if(hp==null||!Float.isFinite(hp.get())||hp.get()<=hp.getMin()||store.getComponent(target,Invulnerable.getComponentType())!=null)return "protectedOrDead";
        var a=store.getComponent(caster,TransformComponent.getComponentType());var b=store.getComponent(target,TransformComponent.getComponentType());
        if(a==null||b==null||!a.getPosition().isFinite()||!b.getPosition().isFinite()||a.getPosition().distanceSquared(b.getPosition())>RANGE*RANGE)return "outOfRange";
        var look=TargetUtil.getLook(caster,store);
        return look!=null&&BloodMagicSystem.sight(store,look.getPosition(),LeechBeam.target(store,target))?null:"blockedSight";
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store) {
        if(bloodClock.due(store,System.nanoTime(),2_000_000_000L))store.forEachChunk(Player.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++)LeechBlood.upgrade(SpellbookPouchService.inventorySections(store,chunk.getReferenceTo(i)));
        });
        for(var entry:channels.entrySet()) {
            var caster=entry.getKey();var channel=entry.getValue();
            if(!caster.isValid()){channels.remove(caster,channel);continue;}
            if(caster.getStore()!=store)continue;
            try {
                var chain=channel.chain.get();var target=store.getExternalData().getRefFromUUID(channel.target);
                String denied=eligible(store,caster,target);
                if(ended(chain)||dead(store,caster)||!held(store,caster,channel.physical,channel.slot)||denied!=null||!enoughMana(store,caster)) {
                    channels.remove(caster,channel);cancel(store,caster,chain);trace(store,caster,"stop",false,denied==null?"releasedChangedHandOrMana":denied);continue;
                }
                var result=advanceChannel(channel,delta,caster,target,store,
                    ()->LeechBeam.emit(store,caster,target,!BOOK.equals(channel.physical),channel.elapsed));
                if(channel.feedbackFailure!=null){
                    var failure=channel.feedbackFailure;channel.feedbackFailure=null;
                    trace(store,caster,"feedbackUnavailable",true,"drainContinues cause="+failure.getClass().getSimpleName());
                    errors.report((PlayerRef)null,"Leech visual feedback failed; drain continues",failure);
                }
                if(result!=null) {
                    trace(store,caster,"pulse",result.accepted(),"target="+channel.target+" damage="+result.damage()+" healed="+result.healed()+" manaSpent="+result.mana());
                    if(!result.accepted()||dead(store,caster)||dead(store,target)){channels.remove(caster,channel);cancel(store,caster,chain);continue;}
                }
            }catch(RuntimeException failure){channels.remove(caster,channel);cancel(store,caster,channel.chain.get());errors.report((PlayerRef)null,"Leech channel failed",failure);}
        }
    }

    static Pulse advanceChannel(Channel channel,float delta,Ref<EntityStore> caster,Ref<EntityStore> target,ComponentAccessor<EntityStore> store,Runnable feedback){
        if(channel.chain==null||ended(channel.chain.get()))return null;
        var result=channelPulse(channel,delta,caster,target,store);
        if(result!=null&&(!result.accepted()||dead(store,caster)||dead(store,target)))return result;
        if(!channel.feedbackDisabled&&channel.elapsed>=channel.visual){
            channel.visual=channel.elapsed+.2;
            try{feedback.run();}catch(RuntimeException failure){channel.feedbackDisabled=true;channel.feedbackFailure=failure;}
        }
        return result;
    }
    static Pulse channelPulse(Channel channel,float delta,Ref<EntityStore> caster,Ref<EntityStore> target,ComponentAccessor<EntityStore> store){
        if(channel.chain==null||ended(channel.chain.get())||!channel.advance(delta))return null;
        return pulse(caster,target,store);
    }
    static Pulse pulse(Ref<EntityStore> caster,Ref<EntityStore> target,ComponentAccessor<EntityStore> store) {
        int health=DefaultEntityStatTypes.getHealth(),manaIndex=DefaultEntityStatTypes.getMana();
        var cs=store.getComponent(caster,EntityStatMap.getComponentType());var ts=store.getComponent(target,EntityStatMap.getComponentType());
        var mana=cs==null?null:cs.get(manaIndex);var ch=cs==null?null:cs.get(health);var th=ts==null?null:ts.get(health);
        if(mana==null||ch==null||th==null||!Float.isFinite(ch.get())||!Float.isFinite(th.get())||!enoughMana(store,caster)||ch.get()<=ch.getMin()||th.get()<=th.getMin()||dead(store,caster)||dead(store,target))return new Pulse(false,0,0,0);
        var cause=DamageCause.getAssetMap().getAsset("Magic");if(cause==null)cause=DamageCause.getAssetMap().getAsset("Physical");
        if(cause==null)throw new IllegalStateException("Native leech damage cause missing");
        float before=th.get(),oldMana=mana.get();cs.addStatValue(manaIndex,-MANA);float paid=oldMana-mana.get();
        if(paid+.0001f<MANA){cs.addStatValue(manaIndex,paid);return new Pulse(false,0,0,0);}
        Damage damage=new Damage(new Damage.EntitySource(caster),cause,DAMAGE);
        DamageSystems.executeDamage(target,store,damage);
        float taken=Math.max(0,before-th.get());
        if(damage.isCancelled()||taken<=0){cs.addStatValue(manaIndex,paid);return new Pulse(false,0,0,0);}
        float oldHealth=ch.get();if(!dead(store,caster)&&ch.get()>ch.getMin())cs.addStatValue(health,HEAL);
        return new Pulse(true,taken,ch.get()-oldHealth,paid);
    }
    private void trace(Store<EntityStore> store,Ref<EntityStore> ref,String event,boolean allow,String reason) {
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getMana();var mana=stats==null?null:stats.get(index);
        var hand=InventoryComponentAccess.itemInHand(store,ref);var active=channels.get(ref);
        debug.traceFileOnly(store.getComponent(ref,PlayerRef.getComponentType()),"Leech event="+event+" cancelled="+!allow+" itemSource=nativeServerHeldChain itemId="+(ItemStack.isEmpty(hand)?"none":hand.getItemId())+" slot="+InventoryComponentAccess.activeHotbarSlot(store,ref)+" chain="+(active==null?"none":active.chainId)
            +" mana.index="+index+" mana.current="+(mana==null?"missing":mana.get())+" mana.min="+(mana==null?"missing":mana.getMin())+" mana.max="+(mana==null?"missing":mana.getMax())+" allow="+allow+" reason="+reason);
    }
}
