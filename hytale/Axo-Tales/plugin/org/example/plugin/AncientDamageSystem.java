package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;


@SuppressWarnings("deprecation")
final class AncientDamageSystem extends DamageEventSystem {
    private final AncientCombatSystem combat;
    AncientDamageSystem(AncientCombatSystem combat){this.combat=combat;}
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,DamageSystems.FilterPlayerWorldConfig.class),new SystemDependency<>(Order.AFTER,DamageSystems.FilterNPCWorldConfig.class),new SystemDependency<>(Order.BEFORE,DamageSystems.ArmorDamageReduction.class));}
    @Override public Query<EntityStore> getQuery(){return Query.any();}
    @Override public void handle(int index,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> buffer,@Nonnull Damage damage){
        if(damage.isCancelled()||!(damage.getAmount()>0)||damage.getSource() instanceof BloodBookCurseSystem.CurseSource)return;
        var victim=chunk.getReferenceTo(index);var cause=DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        if(inherits(cause,"Command")||inherits(cause,"OutOfWorld"))return;
        if(AncientArmor.worn(buffer,victim,ItemArmorSlot.Head,AncientArmor.HEAD)&&combat.reflect(victim,damage,buffer))return;
        if(damage.getSource() instanceof Damage.EntitySource source&&source.getRef()!=null&&source.getRef().isValid()&&!source.getRef().equals(victim)&&!combat.reflected(damage,buffer)){
            boolean chest=AncientArmor.worn(buffer,source.getRef(),ItemArmorSlot.Chest,AncientArmor.CHEST);
            boolean physical=damage.getSource() instanceof Damage.ProjectileSource||damage.getSource() instanceof AncientCombatSystem.BlastSource||inherits(cause,"Physical")||inherits(cause,"Projectile");
            damage.setAmount(damage.getAmount()*attackMultiplier(ArcaneArmorSets.ancient(buffer,source.getRef()),chest,physical));
        }
        if(ArcaneArmorSets.ancient(buffer,victim))damage.setAmount(damage.getAmount()*defenseMultiplier(inherits(cause,"Elemental"),inherits(cause,"Physical")||inherits(cause,"Projectile")));
    }
    static float attackMultiplier(boolean full,boolean chest,boolean meleeOrProjectile){return (full?1.15f:1)*(chest&&meleeOrProjectile?1.3f:1);}
    static float defenseMultiplier(boolean element,boolean physical){return element?.6f:physical?.5f:1;}
    static boolean inherits(DamageCause cause,String id){
        for(int i=0;cause!=null&&i<16;i++){if(id.equalsIgnoreCase(cause.getId()))return true;String parent=cause.getInherits();cause=parent==null?null:DamageCause.getAssetMap().getAsset(parent);}return false;
    }
}
