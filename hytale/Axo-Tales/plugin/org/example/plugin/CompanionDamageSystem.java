package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;


final class CompanionDamageSystem extends DamageEventSystem {
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
    @Override public Query<EntityStore> getQuery(){return Query.or(NPCEntity.getComponentType(),PlayerRef.getComponentType());}
    @Override public void handle(int index,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> buffer,@Nonnull Damage damage) {
        if(damage.isCancelled() || !(damage.getSource() instanceof Damage.EntitySource source))return;
        var attacker=source.getRef();if(attacker==null || !attacker.isValid())return;
        var npc=buffer.getComponent(attacker,NPCEntity.getComponentType());
        if(npc==null || !CompanionLoyalty.mage(npc.getRoleName()) && CompanionLoyalty.owner(buffer,attacker)==null)return;
        var target=chunk.getReferenceTo(index);
        if(CompanionLoyalty.player(buffer,target) || CompanionLoyalty.pet(buffer,target)) {damage.setAmount(0);damage.setCancelled(true);}
    }
}
