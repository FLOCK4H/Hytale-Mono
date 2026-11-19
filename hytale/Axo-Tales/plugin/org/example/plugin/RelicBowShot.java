package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.*;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


final class RelicBowShot implements Component<EntityStore> {
    static ComponentType<EntityStore,RelicBowShot> TYPE;
    static final BuilderCodec<RelicBowShot> CODEC=BuilderCodec.builder(RelicBowShot.class,RelicBowShot::new)
        .append(new KeyedCodec<>("Bow",Codec.STRING),(s,v)->s.bow=v,s->s.bow).add().build();
    String bow="";
    RelicBowShot(){}
    RelicBowShot(String bow){this.bow=bow;}
    @Override public RelicBowShot clone(){return new RelicBowShot(bow);}
    static String[] effects(String bow){return RelicBowSystem.EVERFLAME.equals(bow)?new String[]{"AxoTales_Everflame_Burn"}:RelicBowSystem.MIRETHORN.equals(bow)?new String[]{"AxoTales_Mirethorn_Poison","AxoTales_Mirethorn_Slow"}:new String[0];}

    static final class Arrive extends HolderSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery(){return Query.and(StandardPhysicsProvider.getComponentType(),ModelComponent.getComponentType());}
        @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store){
            if(holder.getComponent(TYPE)!=null)return;
            var model=holder.getComponent(ModelComponent.getComponentType()).getModel();
            if(model==null||model.getModel()==null||!model.getModel().startsWith("Items/Weapons/Arrow/"))return;
            var id=holder.getComponent(StandardPhysicsProvider.getComponentType()).getCreatorUuid();var owner=id==null?null:store.getExternalData().getRefFromUUID(id);
            if(owner==null||!owner.isValid())return;var held=InventoryComponentAccess.itemInHand(store,owner);
            if(held!=null&&RelicBowSystem.bow(held.getItemId()))holder.putComponent(TYPE,new RelicBowShot(held.getItemId()));
        }
        @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store){}
    }
    static final class Hits extends DamageEventSystem {
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public Query<EntityStore> getQuery(){return EffectControllerComponent.getComponentType();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            if(damage.isCancelled()||!(damage.getAmount()>0)||!(damage.getSource() instanceof Damage.ProjectileSource source))return;
            var arrow=source.getProjectile();if(arrow==null||!arrow.isValid())return;var shot=buffer.getComponent(arrow,TYPE);if(shot==null)return;
            var victim=chunk.getReferenceTo(i);if(!ScoutGearSystem.living(victim,buffer))return;
            var controller=chunk.getComponent(i,EffectControllerComponent.getComponentType());
            for(String id:effects(shot.bow)){
                var map=EntityEffect.getAssetMap();int index=map.getIndex(id);var effect=map.getAsset(id);
                if(index<0||effect==null)throw new IllegalStateException("Missing relic bow effect: "+id);
                controller.addEffect(victim,index,effect,RelicBowSystem.EVERFLAME.equals(shot.bow)?4:6,OverlapBehavior.OVERWRITE,buffer);
            }
        }
    }
}
