package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import javax.annotation.Nonnull;


final class TomTomCombat extends TickingSystem<EntityStore> {
    static final String ROLE="Tom_Tom",BOMB="AxoTales_TomTom_Bomb";
    private final ComponentType<EntityStore,TomTomState> stateType;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final Map<Store<EntityStore>,Long> next=Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<Ref<EntityStore>> spentBombs=Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    TomTomCombat(ComponentType<EntityStore,TomTomState> type,PluginDebugReporter debug,PluginErrorReporter errors){stateType=type;this.debug=debug;this.errors=errors;}
    boolean handle(InteractionContext context,boolean detonate){
        var buffer=context.getCommandBuffer();var ref=context.getEntity();
        if(buffer==null || ref==null || !ref.isValid())return false;
        if(detonate){
            var physics=buffer.getComponent(ref,StandardPhysicsProvider.getComponentType());var model=buffer.getComponent(ref,ModelComponent.getComponentType());
            if(physics==null || physics.getCreatorUuid()==null || model==null || model.getModel()==null || !BOMB.equals(model.getModel().getModelAssetId())){trace(ref,buffer,"detonateRejectedProjectile",false);return false;}
            var creator=buffer.getStore().getExternalData().getRefFromUUID(physics.getCreatorUuid());


            var transform=buffer.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return false;
            var at=new org.joml.Vector3d(transform.getPosition());



            if(!spentBombs.add(ref))return false;



            if(creator!=null&&creator.isValid()&&!finish(creator,buffer,"bombDetonated"))return false;
            buffer.run(store->TomTomBlast.apply(store,creator,ref,at,point->TomTomBlast.visible(store,at,point)));
            return true;
        }
        var stats=buffer.getComponent(ref,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        if(!isTom(ref,buffer) || dead(ref,buffer) || hp==null || hp.get()<=hp.getMin()){trace(ref,buffer,"primeRejected",false);return false;}
        var state=buffer.getComponent(ref,stateType);if(state==null){state=new TomTomState();state.bornAt=System.currentTimeMillis();buffer.putComponent(ref,stateType,state);}
        boolean allowed=state.prime(System.currentTimeMillis());dirty(ref,buffer);
        trace(ref,buffer,"prime",allowed);return allowed;
    }
    private boolean isTom(Ref<EntityStore> ref,ComponentAccessor<EntityStore> access){var npc=access.getComponent(ref,NPCEntity.getComponentType());return npc!=null && ROLE.equals(npc.getRoleName());}
    private static boolean dead(Ref<EntityStore> ref,ComponentAccessor<EntityStore> access){return access.getComponent(ref,DeathComponent.getComponentType())!=null;}
    private boolean finish(Ref<EntityStore> owner,CommandBuffer<EntityStore> buffer,String reason){
        if(!owner.isValid())return false;
        if(!isTom(owner,buffer)){trace(owner,buffer,reason+"RejectedRole",false);return false;}
        var state=buffer.getComponent(owner,stateType);var stats=buffer.getComponent(owner,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        if(state==null){trace(owner,buffer,reason+"RejectedState",false);return false;}
        var cause=DamageCause.getAssetMap().getAsset("Physical");if(cause==null)throw new IllegalStateException("Native Physical damage cause missing");
        if(!state.finish()){trace(owner,buffer,reason+"RejectedSpentFuse",false);return false;}
        dirty(owner,buffer);trace(owner,buffer,reason,true);
        if(!dead(owner,buffer)&&hp!=null&&hp.get()>hp.getMin())DamageSystems.executeDamage(owner,buffer,new Damage(Damage.NULL_SOURCE,cause,Math.max(1000,hp.getMax()*100)));
        return true;
    }
    private static void dirty(Ref<EntityStore> ref,ComponentAccessor<EntityStore> access){var value=access.getComponent(ref,Dirty.getComponentType());if(value!=null)value.markDirty();}
    private void trace(Ref<EntityStore> ref,ComponentAccessor<EntityStore> access,String event,boolean allowed){
        if(debug==null)return;var stats=access.getComponent(ref,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        var uuid=access.getComponent(ref,UUIDComponent.getComponentType());
        debug.traceFileOnly(null,"TomTom event="+event+" cancelled=false source=nativeNpcOrProjectile item=none role="+ROLE+" uuid="+(uuid==null?null:uuid.getUuid())
            +" healthIndex="+DefaultEntityStatTypes.getHealth()+" health="+(hp==null?"missing":hp.get()+"/"+hp.getMax()+" min="+hp.getMin())+" allow="+allowed);
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        long now=System.currentTimeMillis();if(now<next.getOrDefault(store,0L))return;next.put(store,now+250);
        store.forEachChunk(Query.and(stateType,NPCEntity.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++)if(chunk.getComponent(i,stateType).expired(now)){
                var ref=chunk.getReferenceTo(i);


                try{finish(ref,buffer,"expiredFuseFallback");}catch(Throwable failure){if(errors!=null)errors.report((com.hypixel.hytale.server.core.universe.PlayerRef)null,"Tom Tom fuse failed",failure);}
            }
        });
    }
}
