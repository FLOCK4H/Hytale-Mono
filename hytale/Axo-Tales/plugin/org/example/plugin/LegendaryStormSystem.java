package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;


public final class LegendaryStormSystem extends TickingSystem<EntityStore> {
    static final int MANA_COST = 80, DAMAGE = 50, RADIUS = 40, PULSES = 8;
    static final double RELEASE_SECONDS = .45, COOLDOWN_SECONDS = 24;
    private final SpellbookPouchService pouches;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final Map<Ref<EntityStore>, Storm> active = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, Integer> lastChain = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, Long> nextCast = new ConcurrentHashMap<>();

    static final class Storm {
        final String book, physical;
        final short slot;
        final Vector3d center;
        double elapsed, visualAt;
        int pulses;
        boolean released;
        Storm(String book, String physical, short slot, Vector3d center) {
            this.book=book;this.physical=physical;this.slot=slot;this.center=new Vector3d(center);
        }

        int due() { return Math.min(PULSES, Math.max(0, (int)Math.floor(elapsed-RELEASE_SECONDS+1e-6))); }
    }
    LegendaryStormSystem(SpellbookPouchService pouches, PluginDebugReporter debug, PluginErrorReporter errors) {
        this.pouches=pouches;this.debug=debug;this.errors=errors;
    }
    static boolean book(String id) { return "Axo_Lightning_Book".equals(id)||"Axo_Meteorain_Book".equals(id); }
    static boolean inRange(Vector3d center, Vector3d target) { return target.isFinite() && center.distanceSquared(target)<=RADIUS*RADIUS; }
    void cast(Store<EntityStore> store, Ref<EntityStore> caster, String book, String physical, int chain) {
        if (!book(book) || Objects.equals(lastChain.put(caster,chain),chain)) return;
        if (active.containsKey(caster) || System.nanoTime()<nextCast.getOrDefault(caster,0L)) {
            trace(store,caster,book,false,"stormActiveOrCooldown");feedback(store,caster,"Your storm is still gathering strength.");return;
        }
        if (!held(store,caster,book,physical) || dead(store,caster)) { trace(store,caster,book,false,"heldItemChangedOrDead");return; }
        if (!hasMana(store,caster)) { trace(store,caster,book,false,"insufficientMana");feedback(store,caster,"This legendary book requires 80 mana.");return; }

        var target=TargetUtil.getTargetLocation(caster,40,store);
        var transform=store.getComponent(caster,TransformComponent.getComponentType());
        if (transform==null) return;
        Vector3d center=target!=null && target.isFinite()?target:new Vector3d(transform.getPosition());
        active.put(caster,new Storm(book,physical,InventoryComponentAccess.activeHotbarSlot(store,caster),center));
        trace(store,caster,book,true,"windupStarted");
    }
    @Override public void tick(float delta, int tick, @Nonnull Store<EntityStore> store) {
        active.keySet().removeIf(ref->!ref.isValid());lastChain.keySet().removeIf(ref->!ref.isValid());nextCast.keySet().removeIf(ref->!ref.isValid());
        if (!Float.isFinite(delta)||delta<=0) return;
        for (var entry:active.entrySet()) {
            var caster=entry.getKey();if (caster.getStore()!=store) continue;var storm=entry.getValue();
            try {
                if (dead(store,caster)) {active.remove(caster);continue;}
                storm.elapsed+=delta;
                if (!storm.released) {
                    if (!held(store,caster,storm.book,storm.physical)||storm.slot!=InventoryComponentAccess.activeHotbarSlot(store,caster)) {
                        trace(store,caster,storm.book,false,"windupItemChanged");active.remove(caster);continue;
                    }
                    if (storm.elapsed<RELEASE_SECONDS) continue;
                    if (!hasMana(store,caster)) {trace(store,caster,storm.book,false,"releaseManaTooLow");active.remove(caster);continue;}
                    var stats=store.getComponent(caster,EntityStatMap.getComponentType());
                    stats.subtractStatValue(DefaultEntityStatTypes.getMana(),MANA_COST);
                    storm.released=true;nextCast.put(caster,System.nanoTime()+(long)(COOLDOWN_SECONDS*1e9));
                    trace(store,caster,storm.book,true,"stormReleased");
                }
                if (storm.elapsed>=storm.visualAt) {visuals(store,storm);storm.visualAt=storm.elapsed+.25;}
                int due=storm.due();
                while (storm.pulses<due) {storm.pulses++;pulse(store,caster,storm);}
                if (storm.pulses==PULSES) {active.remove(caster);trace(store,caster,storm.book,true,"eightPulsesCompleted");}
            } catch (RuntimeException failure) {active.remove(caster);errors.report((PlayerRef)null,"Legendary storm tick failed",failure);}
        }
    }
    private void pulse(Store<EntityStore> store, Ref<EntityStore> caster, Storm storm) {
        var cause=DamageCause.getAssetMap().getAsset(storm.book.contains("Lightning")?"Lightning":"Fire");
        if (cause==null) cause=DamageCause.getAssetMap().getAsset("Physical");
        if (cause==null) throw new IllegalStateException("Native storm damage cause missing");
        int attempts=0;

        for (var victim:List.copyOf(TargetUtil.getAllEntitiesInSphere(storm.center,RADIUS,store))) {
            if (!victim.isValid()||victim.equals(caster)||dead(store,victim)) continue;
            var transform=store.getComponent(victim,TransformComponent.getComponentType());
            var stats=store.getComponent(victim,EntityStatMap.getComponentType());
            var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
            if (transform==null||hp==null||hp.get()<=hp.getMin()||!inRange(storm.center,transform.getPosition())) continue;

            DamageSystems.executeDamage(victim,store,new Damage(new Damage.EntitySource(caster),cause,DAMAGE));attempts++;
        }
        debug.traceFileOnly(store.getComponent(caster,PlayerRef.getComponentType()),"LegendaryStorm event=damagePulse cancelled=false book="+storm.book
            +" pulse="+storm.pulses+" radius="+RADIUS+" damage="+DAMAGE+" attempts="+attempts+" center="+storm.center);
    }
    private void visuals(Store<EntityStore> store, Storm storm) {
        boolean lightning=storm.book.contains("Lightning");
        String strike=lightning?"AxoTales_Storm_Lightning":"AxoTales_Storm_Meteor";
        double phase=storm.elapsed*2.399963;

        for (int i=0;i<5;i++) {
            double angle=phase+i*Math.PI*2/5, radius=RADIUS*(.2+.775*((storm.elapsed*.31+i*.37)%1));
            var at=new Vector3d(storm.center).add(Math.cos(angle)*radius,.15,Math.sin(angle)*radius);
            ParticleUtil.spawnParticleEffect(strike,at,store);
        }
        if (storm.visualAt==0 || (int)(storm.elapsed*4)%4==0) for (int i=0;i<40;i++) {
            double angle=i*Math.PI/20;
            ParticleUtil.spawnParticleEffect(lightning?"AxoTales_Storm_CyanRune":"AxoTales_Storm_EmberRune",
                new Vector3d(storm.center).add(Math.cos(angle)*RADIUS,.15,Math.sin(angle)*RADIUS),store);
        }
    }
    private boolean held(Store<EntityStore> store, Ref<EntityStore> caster, String book, String physical) {
        var item=InventoryComponentAccess.itemInHand(store,caster);
        if (ItemStack.isEmpty(item)||!physical.equals(item.getItemId())) return false;
        var spell=SpellbookCatalog.fromWand(physical);
        return spell==null?book.equals(physical):book.equals(spell.itemId())&&pouches.owns(store,caster,spell);
    }
    private static boolean dead(Store<EntityStore> store,Ref<EntityStore> ref) {return store.getComponent(ref,DeathComponent.getComponentType())!=null;}
    private static boolean hasMana(Store<EntityStore> store,Ref<EntityStore> ref) {
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());
        var mana=stats==null?null:stats.get(DefaultEntityStatTypes.getMana());
        return mana!=null&&mana.get()-mana.getMin()>=MANA_COST;
    }
    private static void feedback(Store<EntityStore> store,Ref<EntityStore> ref,String message) {
        var player=store.getComponent(ref,PlayerRef.getComponentType());if(player!=null)player.sendMessage(Message.raw(message));
    }
    private void trace(Store<EntityStore> store,Ref<EntityStore> ref,String book,boolean allow,String reason) {
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getMana();var mana=stats==null?null:stats.get(index);
        debug.traceFileOnly(store.getComponent(ref,PlayerRef.getComponentType()),"LegendaryStorm event=NativeSpellInteraction cancelled=false itemId="+book
            +" itemSource=server.itemInHand+carriedBook mana.index="+index+" mana.current="+(mana==null?"missing":mana.get())
            +" mana.min="+(mana==null?"missing":mana.getMin())+" mana.max="+(mana==null?"missing":mana.getMax())+" allow="+allow+" reason="+reason);
    }
}
