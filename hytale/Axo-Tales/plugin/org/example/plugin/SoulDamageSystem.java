package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import java.util.Set;
import java.util.function.BiConsumer;


@SuppressWarnings("deprecation")
final class SoulDamageSystem extends DamageEventSystem {
    private final ComponentType<EntityStore,SoulState> type;
    private final ComponentType<EntityStore,FossorWork> work;
    private final BiConsumer<Ref<EntityStore>,Ref<EntityStore>> pickup;
    private final PluginDebugReporter debug;
    SoulDamageSystem(ComponentType<EntityStore,SoulState> type,ComponentType<EntityStore,FossorWork> work){this(type,work,null);}
    SoulDamageSystem(ComponentType<EntityStore,SoulState> type,ComponentType<EntityStore,FossorWork> work,BiConsumer<Ref<EntityStore>,Ref<EntityStore>> pickup){this(type,work,pickup,null);}
    SoulDamageSystem(ComponentType<EntityStore,SoulState> type,ComponentType<EntityStore,FossorWork> work,BiConsumer<Ref<EntityStore>,Ref<EntityStore>> pickup,PluginDebugReporter debug){this.type=type;this.work=work;this.pickup=pickup;this.debug=debug;}
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,DamageSystems.FilterUnkillable.class));}
    @Override public Query<EntityStore> getQuery(){return Query.or(UUIDComponent.getComponentType(),PlayerRef.getComponentType(),work);}
    @Override public void handle(int i,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> buffer,@Nonnull Damage damage) {
        var ref=chunk.getReferenceTo(i);var npc=buffer.getComponent(ref,NPCEntity.getComponentType());
        boolean soul=buffer.getComponent(ref,type)!=null || npc!=null && SoulKind.role(npc.getRoleName())!=null;
        boolean immune=soul || buffer.getComponent(ref,work)!=null;
        if(soul && pickup!=null && !damage.isCancelled() && damage.getAmount()>0
            && !(damage.getSource() instanceof Damage.ProjectileSource) && damage.getSource() instanceof Damage.EntitySource source
            && damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)==InteractionType.Primary) {
            var attacker=source.getRef();
            if(attacker!=null && attacker.isValid()) {
                var player=buffer.getComponent(attacker,PlayerRef.getComponentType());var state=buffer.getComponent(ref,type);
                if(player!=null) {
                    String decision=state==null || !state.mayReturn(player.getUuid())?"notSummoner":!SoulSystem.emptyHand(buffer,attacker)?"handNotEmpty":"queued";
                    if(debug!=null) {
                        var held=InventoryComponentAccess.itemInHand(buffer,attacker);var hotbar=InventoryComponentAccess.hotbarComponent(buffer,attacker);
                        var stats=buffer.getComponent(attacker,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
                        int index=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getMana();var mana=stats==null?null:stats.get(index);
                        debug.traceFileOnly(player,"Soul event=nativeMeleeGate itemSource=serverActiveSlot item="+(com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(held)?"empty":held.getItemId())+" slot="+(hotbar==null?-1:hotbar.getActiveSlot())+" cancelled=true mana.index="+index+" mana.current="+(mana==null?"n/a":mana.get())+" mana.min="+(mana==null?"n/a":mana.getMin())+" mana.max="+(mana==null?"n/a":mana.getMax())+" mana.gated=false allow="+"queued".equals(decision)+" decision="+decision);
                    }
                    if("queued".equals(decision))buffer.run(s->pickup.accept(attacker,ref));
                }
            }
        }
        if(!immune && damage.getSource() instanceof Damage.ProjectileSource source && source.getProjectile()!=null && source.getProjectile().isValid()) {
            var projectile=buffer.getComponent(source.getProjectile(),ProjectileComponent.getComponentType());
            if(projectile!=null && SoulSystem.CONJURE.equals(projectile.getProjectileAssetName()))immune=true;
            if(projectile!=null && SoulSystem.BOLT.equals(projectile.getProjectileAssetName()) && ref.equals(source.getRef()))immune=true;
        }
        if(immune){damage.setAmount(0);damage.setCancelled(true);}
    }
}
