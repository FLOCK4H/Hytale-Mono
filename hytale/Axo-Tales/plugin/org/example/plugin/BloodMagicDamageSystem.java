package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;


@SuppressWarnings("deprecation")
class BloodMagicDamageSystem extends DamageEventSystem {
    private final ComponentType<EntityStore,BloodPlayerState> players;
    private final ComponentType<EntityStore,BloodboundState> bound;
    private final boolean inspect;
    BloodMagicDamageSystem(ComponentType<EntityStore,BloodPlayerState> players,ComponentType<EntityStore,BloodboundState> bound,boolean inspect){this.players=players;this.bound=bound;this.inspect=inspect;}


    static final class Filter extends BloodMagicDamageSystem {
        Filter(ComponentType<EntityStore,BloodPlayerState> players,ComponentType<EntityStore,BloodboundState> bound){super(players,bound,false);}
    }
    static final class Observe extends BloodMagicDamageSystem {
        Observe(ComponentType<EntityStore,BloodPlayerState> players,ComponentType<EntityStore,BloodboundState> bound){super(players,bound,true);}
    }
    @Override public SystemGroup<EntityStore> getGroup(){return inspect?DamageModule.get().getInspectDamageGroup():DamageModule.get().getFilterDamageGroup();}
    @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
    @Override public void handle(int i,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> buffer,@Nonnull Damage damage) {
        var victim=chunk.getReferenceTo(i);
        if(!inspect) {
            if(damage.getSource() instanceof Damage.ProjectileSource source && source.getProjectile()!=null && source.getProjectile().isValid()) {
                var projectile=buffer.getComponent(source.getProjectile(),ProjectileComponent.getComponentType());
                if(projectile!=null && BloodboundSystem.BOLT.equals(projectile.getProjectileAssetName())) {
                    var attacker=source.getRef();var owner=attacker!=null && attacker.isValid()?buffer.getComponent(attacker,PlayerRef.getComponentType()):null;
                    var shooter=owner!=null?owner.getUuid():projectile.getCreatorUuid();
                    var ally=buffer.getComponent(victim,bound);
                    if(victim.equals(attacker) || shooter!=null && ally!=null && shooter.equals(ally.owner)) {damage.setAmount(0);damage.setCancelled(true);}
                }
            }return;
        }
        if(damage.isCancelled() || !(damage.getAmount()>0))return;


        if(damage.getSource() instanceof BloodBookCurseSystem.CurseSource)return;
        var defending=buffer.getComponent(victim,PlayerRef.getComponentType());
        if(defending!=null)buffer.run(accessor->{if(victim.isValid()) {
            var state=accessor.getComponent(victim,players);state=state==null?new BloodPlayerState():state.clone();state.lastDamage=System.currentTimeMillis();state.revision++;
            accessor.putComponent(victim,players,state);SoulSystem.dirty(accessor,victim);
        }});
        if(!(damage.getSource() instanceof Damage.EntitySource source))return;var attacker=source.getRef();if(attacker==null || !attacker.isValid() || attacker.equals(victim))return;
        var attacking=buffer.getComponent(attacker,PlayerRef.getComponentType());var attackerId=buffer.getComponent(attacker,UUIDComponent.getComponentType());var victimId=chunk.getComponent(i,UUIDComponent.getComponentType());
        if(defending!=null && attackerId!=null)retarget(buffer,defending.getUuid(),attackerId.getUuid());
        if(attacking!=null && victimId!=null)retarget(buffer,attacking.getUuid(),victimId.getUuid());
        var defendedAlly=buffer.getComponent(victim,bound);if(defendedAlly!=null && attackerId!=null)retarget(buffer,defendedAlly.owner,attackerId.getUuid());
    }
    private void retarget(CommandBuffer<EntityStore> buffer,UUID owner,UUID target) {
        if(owner==null || owner.equals(target))return;
        buffer.run(store->store.forEachChunk(bound,(chunk,commands)->{
            for(int i=0;i<chunk.size();i++){var ally=chunk.getComponent(i,bound);if(owner.equals(ally.owner)){ally.target=target;ally.targetUntil=System.nanoTime()+30_000_000_000L;}}
        }));
    }
}
