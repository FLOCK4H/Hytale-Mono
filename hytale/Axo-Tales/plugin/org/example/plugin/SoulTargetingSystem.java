package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;


final class SoulTargetingSystem extends DamageEventSystem {
    private final ComponentType<EntityStore,SoulState> type;
    private final PluginDebugReporter debug;
    SoulTargetingSystem(ComponentType<EntityStore,SoulState> type,PluginDebugReporter debug){this.type=type;this.debug=debug;}
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
    @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
    @Override public void handle(int i,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> buffer,@Nonnull Damage damage) {
        if(damage.isCancelled() || damage.getAmount()<=0 || !(damage.getSource() instanceof Damage.EntitySource source))return;
        var attacker=source.getRef();var victim=chunk.getReferenceTo(i);if(attacker==null || !attacker.isValid() || attacker.equals(victim))return;
        var defending=buffer.getComponent(victim,PlayerRef.getComponentType());var attacking=buffer.getComponent(attacker,PlayerRef.getComponentType());
        var attackerId=buffer.getComponent(attacker,UUIDComponent.getComponentType());var victimId=chunk.getComponent(i,UUIDComponent.getComponentType());
        if(defending!=null && attackerId!=null)retarget(buffer,defending.getUuid(),attackerId.getUuid(),"ownerAttacked");
        if(attacking!=null && victimId!=null)retarget(buffer,attacking.getUuid(),victimId.getUuid(),"ownerAttack");
    }
    private void retarget(CommandBuffer<EntityStore> buffer,UUID owner,UUID target,String reason) {
        buffer.run(store->store.forEachChunk(type,(chunk,commands)->{
            for(int i=0;i<chunk.size();i++) {
                var state=chunk.getComponent(i,type);if(state.type()!=SoulKind.CRUOR || !owner.equals(state.owner) || owner.equals(target))continue;
                boolean changed=!target.equals(state.target);if(changed)state.combatMoving=false;state.target=target;state.targetUntil=System.nanoTime()+30_000_000_000L;
                if(changed)debug.traceFileOnly(null,"Soul event=target kind=Cruor owner="+owner+" target="+target+" reason="+reason+" cancelled=false allow=true");
            }
        }));
    }
}
