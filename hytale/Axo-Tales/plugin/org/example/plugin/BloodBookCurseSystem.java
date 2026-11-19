package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.*;


final class BloodBookCurseSystem extends TickingSystem<EntityStore> {
    static final String CAUSE="AxoTales_Blood_Curse",EFFECT="AxoTales_Blood_Burden";
    static final class CurseSource implements Damage.Source {}
    static final Damage.Source SOURCE=new CurseSource();
    static final float HEALTH_COST=8;
    static Damage pulse(DamageCause cause){return new Damage(SOURCE,cause,HEALTH_COST);}
    private final ComponentType<EntityStore,BloodBookCurseState> type;
    private final SpellbookPouchService pouches;
    private final PluginDebugReporter debug;
    private final Map<Store<EntityStore>,Double> accrued=Collections.synchronizedMap(new WeakHashMap<>());
    BloodBookCurseSystem(ComponentType<EntityStore,BloodBookCurseState> type,SpellbookPouchService pouches,PluginDebugReporter debug){this.type=type;this.pouches=pouches;this.debug=debug;}
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store) {
        double total=accrued.getOrDefault(store,0d)+delta;if(total<.25){accrued.put(store,total);return;}accrued.put(store,0d);
        final double step=Math.min(1,total);
        store.forEachChunk(Query.and(PlayerRef.getComponentType(),EntityStatMap.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var ref=chunk.getReferenceTo(i);var stats=chunk.getComponent(i,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getHealth();
                var hp=index>=0 && index<stats.size()?stats.get(index):null;
                boolean alive=hp!=null && hp.get()>hp.getMin() && buffer.getComponent(ref,DeathComponent.getComponentType())==null;
                buffer.run(s->{if(!ref.isValid())return;
                    boolean carried=alive && pouches.owns(s,ref,SpellbookCatalog.BLOODY);
                    var state=s.getComponent(ref,type);if(state==null && !carried)return;
                    if(state==null){state=new BloodBookCurseState();s.putComponent(ref,type,state);}
                    double previous=state.seconds;boolean pulse=state.advance(carried,alive,step);
                    if(state.seconds!=previous)SoulSystem.dirty(s,ref);
                    effect(s,ref,carried);
                    if(pulse){var cause=DamageCause.getAssetMap().getAsset(CAUSE);if(cause==null)throw new IllegalStateException("Blood curse damage cause missing");
                        s.forEachChunk(PlayerRef.getComponentType(),(players,commands)->{
                            for(int j=0;j<players.size();j++)if(players.getReferenceTo(j).equals(ref))DamageSystems.executeDamage(ref,commands,pulse(cause));
                        });
                        debug.traceFileOnly(s.getComponent(ref,PlayerRef.getComponentType()),"BloodBookCurse event=pulse itemSource=serverInventoryAndCarriedPouches item=Book_Bloody_Texture health.index="+index+" health.current="+hp.get()+" health.min="+hp.getMin()+" health.max="+hp.getMax()+" damage="+HEALTH_COST+" intervalSeconds=5 nativeDamage=true");
                    }
                });
            }
        });
    }
    private static void effect(Store<EntityStore> store,Ref<EntityStore> ref,boolean active) {
        var effect=EntityEffect.getAssetMap().getAsset(EFFECT);int index=EntityEffect.getAssetMap().getIndexOrDefault(EFFECT,-1);
        var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());if(effect==null || index<0 || effects==null)return;
        var before=effects.getActiveEffects().get(index);
        if(!active){if(before!=null)effects.removeEffect(ref,index,store);return;}
        if(before==null || before.getRemainingDuration()<.5f)effects.addEffect(ref,index,effect,1.1f,OverlapBehavior.OVERWRITE,store);
    }
}
