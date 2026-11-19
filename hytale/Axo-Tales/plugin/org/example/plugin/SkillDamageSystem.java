package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;


@SuppressWarnings("deprecation")
final class SkillDamageSystem extends DamageEventSystem {
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,DamageSystems.FilterPlayerWorldConfig.class),new SystemDependency<>(Order.AFTER,DamageSystems.FilterNPCWorldConfig.class),new SystemDependency<>(Order.BEFORE,DamageSystems.ArmorDamageReduction.class));}
    @Override public Query<EntityStore> getQuery(){return Query.any();}
    @Override public void handle(int i,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> buffer,@Nonnull Damage damage){
        if(damage.isCancelled()||damage.getAmount()<=0||damage.getSource() instanceof BloodBookCurseSystem.CurseSource)return;
        var cause=DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        if(AncientDamageSystem.inherits(cause,"Command")||AncientDamageSystem.inherits(cause,"OutOfWorld"))return;
        var victim=chunk.getReferenceTo(i);
        if(damage.getSource() instanceof Damage.EntitySource source&&source.getRef()!=null&&source.getRef().isValid()&&!source.getRef().equals(victim)){
            int mask=SkillProgression.mask(buffer,source.getRef());damage.setAmount(damage.getAmount()*SkillCatalog.superDamage(mask)*(source instanceof SkillTreeSystem.WaveSource?1:1+SkillCatalog.attack(mask)));
            if(SkillCatalog.has(mask,10)&&meleeNudgeEligible(damage,cause)){
                var from=buffer.getComponent(source.getRef(),TransformComponent.getComponentType());var to=buffer.getComponent(victim,TransformComponent.getComponentType());
                if(from!=null&&to!=null)addMeleeNudge(victim,from.getPosition(),to.getPosition(),buffer);
            }
        }
        int mask=SkillProgression.mask(buffer,victim);
        float remaining=1-SkillCatalog.defense(mask);
        if(damage.getSource() instanceof Damage.ProjectileSource||AncientDamageSystem.inherits(cause,"Projectile"))remaining*=1-SkillCatalog.projectiles(mask);
        if(SkillCatalog.has(mask,8)&&AncientDamageSystem.inherits(cause,"Fall"))remaining*=.75f;
        if(SkillCatalog.has(mask,11)&&AncientDamageSystem.inherits(cause,"Poison"))remaining*=.75f;
        damage.setAmount(damage.getAmount()*Math.max(0,remaining));
    }
    static boolean meleeNudgeEligible(Damage damage,DamageCause cause){
        return damage.getSource().getClass()==Damage.EntitySource.class&&AncientDamageSystem.inherits(cause,"Physical");
    }
    static boolean addMeleeNudge(Ref<EntityStore> victim,org.joml.Vector3d from,org.joml.Vector3d to,CommandBuffer<EntityStore> buffer){

        if(buffer.getComponent(victim,KnockbackComponent.getComponentType())!=null)return false;
        var direction=new org.joml.Vector3d(to).sub(from);direction.y=0;
        if(!direction.isFinite()||direction.lengthSquared()<=.001)return false;
        var knock=new KnockbackComponent();knock.setVelocity(direction.normalize().mul(.75));
        knock.setVelocityType(ChangeVelocityType.Set);


        knock.setDuration(0);
        var drag=new com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig();
        drag.setAirResistance(.8f);drag.setAirResistanceMax(.8f);drag.setGroundResistance(.8f);drag.setGroundResistanceMax(.8f);drag.setThreshold(0);
        knock.setVelocityConfig(drag);buffer.putComponent(victim,KnockbackComponent.getComponentType(),knock);return true;
    }

    static final class Lazarus extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.and(SkillProgression.TYPE,EntityStatMap.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int i,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> buffer,@Nonnull Damage damage){
            if(damage.isCancelled()||damage.getAmount()<=0)return;
            var cause=DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
            if(AncientDamageSystem.inherits(cause,"Command")||AncientDamageSystem.inherits(cause,"OutOfWorld"))return;
            var ref=chunk.getReferenceTo(i);var state=buffer.getComponent(ref,SkillProgression.TYPE);long now=System.currentTimeMillis();
            if(!state.has(16))return;
            if(now<state.immuneUntil){damage.setCancelled(true);return;}
            var stats=chunk.getComponent(i,EntityStatMap.getComponentType());var hp=stats.get(DefaultEntityStatTypes.getHealth());
            if(hp==null||hp.get()<=hp.getMin()||damage.getAmount()<hp.get()-hp.getMin())return;
            if(!state.saveFromDeath(now))return;

            buffer.putComponent(ref,SkillProgression.TYPE,state.clone());
            damage.setCancelled(true);stats.setStatValue(DefaultEntityStatTypes.getHealth(),Math.max(hp.getMin()+1,hp.get()));stats.update();
        }
    }
}
