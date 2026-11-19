package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.*;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import java.util.*;


final class CompanionLoyalty extends TickingSystem<EntityStore> {
    static final String MAGE="Elder_Mage", LEGACY_MAGE="Kudu_Adept_Magician";
    private static ComponentType<EntityStore,WhisperionState> horseType;
    private static ComponentType<EntityStore,GrayCatState> catType;
    private static ComponentType<EntityStore,KuduAdeptBondPersistedComponent> mageType;
    private static ComponentType<EntityStore,SoulState> soulType;
    private static ComponentType<EntityStore,BloodboundState> bloodType;
    private static String customMage;
    private final Map<Store<EntityStore>,Long> next=Collections.synchronizedMap(new WeakHashMap<>());

    CompanionLoyalty(ComponentType<EntityStore,WhisperionState> horse,ComponentType<EntityStore,GrayCatState> cat,
                     ComponentType<EntityStore,KuduAdeptBondPersistedComponent> mage,ComponentType<EntityStore,SoulState> soul,
                     ComponentType<EntityStore,BloodboundState> blood,String configuredMage) {
        horseType=horse;catType=cat;mageType=mage;soulType=soul;bloodType=blood;customMage=configuredMage;
    }
    static boolean mage(String role){return MAGE.equals(role)||LEGACY_MAGE.equals(role)||role!=null && role.equals(customMage);}
    static boolean mage(String actual,String configured){return mage(actual)||actual!=null && actual.equals(configured);}
    static String spawnRole(String configured){return configured==null || configured.isBlank() || LEGACY_MAGE.equals(configured)?MAGE:configured;}
    static UUID owner(ComponentAccessor<EntityStore> access,Ref<EntityStore> ref) {
        if(ref==null || !ref.isValid())return null;
        var horse=horseType==null?null:access.getComponent(ref,horseType);if(horse!=null && horse.owner!=null)return horse.owner;
        var cat=catType==null?null:access.getComponent(ref,catType);if(cat!=null && cat.owner!=null)return cat.owner;
        var mage=mageType==null?null:access.getComponent(ref,mageType);if(mage!=null && mage.hasOwnerUuid())return mage.getOwnerUuid();
        var soul=soulType==null?null:access.getComponent(ref,soulType);if(soul!=null && soul.owner!=null)return soul.owner;
        var blood=bloodType==null?null:access.getComponent(ref,bloodType);return blood==null?null:blood.owner;
    }
    static boolean player(ComponentAccessor<EntityStore> access,Ref<EntityStore> ref) {
        return ref!=null && ref.isValid() && (access.getComponent(ref,PlayerRef.getComponentType())!=null || access.getComponent(ref,Player.getComponentType())!=null);
    }
    static boolean pet(ComponentAccessor<EntityStore> access,Ref<EntityStore> ref) {
        if(owner(access,ref)!=null)return true;
        var npc=ref==null || !ref.isValid()?null:access.getComponent(ref,NPCEntity.getComponentType());
        String role=npc==null?null:npc.getRoleName();

        return role!=null && (role.startsWith("Tamed_") || role.startsWith("Pet_") || SoulKind.role(role)!=null || role.startsWith("AxoTales_Bloodbound_"));
    }
    static boolean wildTarget(ComponentAccessor<EntityStore> access,Ref<EntityStore> target) {
        if(target==null || !target.isValid() || player(access,target) || pet(access,target) || access.getComponent(target,DeathComponent.getComponentType())!=null)return false;
        var npc=access.getComponent(target,NPCEntity.getComponentType());return npc!=null && !mage(npc.getRoleName());
    }
    static Attitude attitude(Ref<EntityStore> observer,int role,Ref<EntityStore> target,ComponentAccessor<EntityStore> access) {
        if(observer==null || !observer.isValid() || target==null || !target.isValid())return null;
        var npc=access.getComponent(observer,NPCEntity.getComponentType());if(npc==null)return null;
        if(mage(npc.getRoleName())) {
            if(player(access,target) || pet(access,target))return Attitude.FRIENDLY;
            var other=access.getComponent(target,NPCEntity.getComponentType());
            if(other!=null)return mage(other.getRoleName())?Attitude.FRIENDLY:Attitude.HOSTILE;
        }
        if(owner(access,observer)!=null && (player(access,target)||pet(access,target)))return Attitude.FRIENDLY;
        return null;
    }
    StoreSystem<EntityStore> provider(){return new StoreSystem<>() {
        @Override public void onSystemAddedToStore(@Nonnull Store<EntityStore> store) {
            store.getResource(Blackboard.getResourceType()).getView(AttitudeView.class,0L).registerProvider(-90,(IAttitudeProvider)CompanionLoyalty::attitude);
        }
        @Override public void onSystemRemovedFromStore(@Nonnull Store<EntityStore> store){}
    };}

    static void retain(Store<EntityStore> store,Ref<EntityStore> ref) {
        if(owner(store,ref)==null)return;
        var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc==null)return;
        boolean changed=npc.isDespawning() || npc.getDespawnTime()!=0 || store.getComponent(ref,DespawnComponent.getComponentType())!=null
            || store.getComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType())!=null;
        npc.setDespawnRemainingSeconds(0);npc.setDespawning(false);
        store.tryRemoveComponent(ref,DespawnComponent.getComponentType());store.tryRemoveComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType());
        if(changed){var dirty=store.getComponent(ref,Dirty.getComponentType());if(dirty!=null)dirty.markDirty();}
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store) {
        long now=System.nanoTime();if(now<next.getOrDefault(store,0L))return;next.put(store,now+250_000_000L);
        store.forEachChunk(NPCEntity.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);if(owner(buffer,ref)!=null)buffer.run(s->{if(ref.isValid())retain(s,ref);});}
        });
    }
}
