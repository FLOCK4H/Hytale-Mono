package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.*;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.*;
import java.util.*;
import org.joml.Vector3d;
import javax.annotation.Nonnull;


@SuppressWarnings("deprecation")
final class BloodboundSystem extends TickingSystem<EntityStore> {
    static final String BOLT="AxoTales_Bloodbound_Bolt";
    private final ComponentType<EntityStore,BloodboundState> type;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    BloodboundSystem(ComponentType<EntityStore,BloodboundState> type,PluginDebugReporter debug,PluginErrorReporter errors){this.type=type;this.debug=debug;this.errors=errors;}
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store) {
        var allies=new ArrayList<Ref<EntityStore>>();store.forEachChunk(Query.and(type,NPCEntity.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++)allies.add(chunk.getReferenceTo(i));
        });
        for(var ref:allies)try{update(store,ref);}catch(RuntimeException failure){errors.report((PlayerRef)null,"Bloodbound companion update failed",failure);}
    }
    static void refresh(Store<EntityStore> store,Ref<EntityStore> ref,BloodboundState state,boolean newborn) {
        if(state.owner==null || state.sample==null || !state.sample.valid())return;
        var expected=state.sample.model;var current=store.getComponent(ref,ModelComponent.getComponentType());
        var saved=store.getComponent(ref,PersistentModel.getComponentType());
        if(saved==null || !expected.equals(saved.getModelReference()))store.putComponent(ref,PersistentModel.getComponentType(),new PersistentModel(expected));
        if(current==null || !expected.equals(current.getModel().toReference())) {
            store.putComponent(ref,ModelComponent.getComponentType(),new ModelComponent(expected.toModel()));
        }
        var npc=store.getComponent(ref,NPCEntity.getComponentType());

        npc.setDespawning(false);npc.setDespawnTime(0);store.tryRemoveComponent(ref,DespawnComponent.getComponentType());
        store.tryRemoveComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType());
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());int health=DefaultEntityStatTypes.getHealth();
        speciesHealth(stats,health,state.sample.health,newborn);
        if(newborn)SoulSystem.dirty(store,ref);
    }
    static void speciesHealth(EntityStatMap stats,int health,float desired,boolean newborn) {
        if(stats!=null && stats.get(health)!=null) {
            var modifier=stats.getModifier(health,"axotales:bloodbound_species_health");
            if(modifier==null) {



                float factor=0;boolean scaled=false;var values=stats.get(health).getModifiers();
                if(values!=null)for(var value:values.values())if(value instanceof StaticModifier scale && scale.getTarget()==Modifier.ModifierTarget.MAX
                        && scale.getCalculationType()==StaticModifier.CalculationType.MULTIPLICATIVE){factor+=scale.getAmount();scaled=true;}
                if(!scaled || !(factor>0) || !Float.isFinite(factor))factor=1;
                float amount=(desired-stats.get(health).getMax())/factor;
                stats.putModifier(health,"axotales:bloodbound_species_health",new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,amount));stats.update();
            }
            if(newborn)stats.setStatValue(health,stats.get(health).getMax());
        }
    }
    private void update(Store<EntityStore> store,Ref<EntityStore> ref) {
        if(!ref.isValid() || store.getComponent(ref,DeathComponent.getComponentType())!=null)return;var state=store.getComponent(ref,type);
        if(state==null || state.owner==null || state.sample==null)return;refresh(store,ref,state,false);
        var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc.getRole()==null)return;
        var marked=MarkedEntitySupport.get(ref,store);var machine=StateSupport.get(ref,store);if(marked==null || machine==null)return;
        var owner=store.getExternalData().getRefFromUUID(state.owner);var self=store.getComponent(ref,TransformComponent.getComponentType());
        if(owner==null || !owner.isValid() || store.getComponent(owner,PlayerRef.getComponentType())==null
                || store.getComponent(owner,DeathComponent.getComponentType())!=null) {
            for(int slot=0;slot<marked.getMarkedEntitySlotCount();slot++)marked.clearMarkedEntity(slot);
            setState(store,ref,machine,"Idle");state.target=null;return;
        }
        var home=store.getComponent(owner,TransformComponent.getComponentType());if(home==null || self==null)return;
        long now=System.nanoTime();
        if(self.getPosition().distanceSquared(home.getPosition())>48*48 && now>=state.nextCatchup) {
            state.nextCatchup=now+2_000_000_000L;
            var placement=BloodMagicSystem.placement(store,owner,state.sample,List.of());
            if(placement!=null && store.getComponent(ref,Teleport.getComponentType())==null)store.addComponent(ref,Teleport.getComponentType(),Teleport.createExact(placement.at(),self.getRotation()));
            return;
        }
        var target=state.target==null?null:store.getExternalData().getRefFromUUID(state.target);
        boolean valid=target!=null && target.isValid() && now<state.targetUntil && !target.equals(owner)
            && store.getComponent(target,DeathComponent.getComponentType())==null
            && (store.getComponent(target,NPCEntity.getComponentType())!=null || store.getComponent(target,PlayerRef.getComponentType())!=null);
        var ally=valid?store.getComponent(target,type):null;if(ally!=null && state.owner.equals(ally.owner))valid=false;
        var enemy=valid?store.getComponent(target,TransformComponent.getComponentType()):null;
        if(enemy==null || enemy.getPosition().distanceSquared(home.getPosition())>48*48)valid=false;
        if(!valid) {
            state.target=null;marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,owner);setState(store,ref,machine,"Idle");return;
        }
        marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,target);setState(store,ref,machine,"Combat");
        var model=store.getComponent(ref,ModelComponent.getComponentType()).getModel();double height=model.getBoundingBox()==null?1:model.getBoundingBox().height()*.65;
        var origin=new Vector3d(self.getPosition()).add(0,height,0);var at=new Vector3d(enemy.getPosition()).add(0,.7,0);
        if(now<state.nextShot || origin.distanceSquared(at)>30*30 || !BloodMagicSystem.sight(store,origin,at))return;
        if(projectile(store,origin,at,state.owner,model.getBoundingBox()==null?1:Math.max(model.getBoundingBox().width(),model.getBoundingBox().depth())*.6+.25)) {
            state.nextShot=now+1_500_000_000L;
            var animations=model.getAnimationSetMap();if(animations!=null && animations.containsKey("Alerted"))npc.playAnimation(ref,AnimationSlot.Action,"Alerted",store);
            debug.traceFileOnly(null,"Bloodbound event=cast owner="+state.owner+" species="+state.sample.species+" target="+state.target+" damage=10 intervalSeconds=1.5 allow=true");
        }
    }
    private static void setState(Store<EntityStore> store,Ref<EntityStore> ref,StateSupport state,String name) {
        if(!name.equals(state.getStateName()))state.setState(ref,name,null,store);
    }
    private static boolean projectile(Store<EntityStore> store,Vector3d from,Vector3d to,UUID owner,double outside) {
        var direction=new Vector3d(to).sub(from);if(direction.lengthSquared()<=outside*outside)return false;direction.normalize();
        var origin=new Vector3d(from).fma(outside,direction);if(!BloodMagicSystem.sight(store,from,origin))return false;
        var rotation=new Rotation3f((float)Math.asin(direction.y),(float)Math.atan2(-direction.x,-direction.z),0);
        var holder=ProjectileComponent.assembleDefaultProjectile(store.getResource(TimeResource.getResourceType()),BOLT,origin,rotation);
        if(holder==null)return false;var projectile=holder.getComponent(ProjectileComponent.getComponentType());if(projectile==null || !projectile.initialize())return false;
        projectile.shoot(holder,owner,origin.x,origin.y,origin.z,rotation.yaw(),rotation.pitch());store.addEntity(holder,AddReason.SPAWN);return true;
    }
}
