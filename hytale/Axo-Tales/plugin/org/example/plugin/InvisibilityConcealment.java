package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.*;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import java.util.*;
import javax.annotation.Nonnull;


final class InvisibilityConcealment extends TickingSystem<EntityStore> {
    private final PluginDebugReporter debug;
    private final Map<Ref<EntityStore>,Boolean> announced=new HashMap<>();
    InvisibilityConcealment(PluginDebugReporter debug){this.debug=debug;}
    static boolean hidden(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref) {
        if(ref==null || !ref.isValid() || store.getComponent(ref,Player.getComponentType())==null)return false;
        if(store.getComponent(ref,DeathComponent.getComponentType())!=null)return false;
        if(SkillInvisibility.eligible(store,ref))return true;
        if(InvisibilityCloakSystem.CLOAK_ITEM_ID.equals(InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Chest)))return true;
        var type=com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType();
        if(type==null)return false;
        var effects=store.getComponent(ref,type);
        if(effects==null)return false;
        int index=com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getIndexOrDefault(InvisibilityCloakSystem.INVISIBILITY_EFFECT_ID,-1);
        return effects!=null&&index>=0&&effects.getActiveEffects().containsKey(index);
    }

    static Attitude attitude(Ref<EntityStore> observer,int role,Ref<EntityStore> target,ComponentAccessor<EntityStore> store) {
        return hidden(store,target)?Attitude.IGNORE:null;
    }
    StoreSystem<EntityStore> provider(){return new StoreSystem<>() {
        @Override public void onSystemAddedToStore(@Nonnull Store<EntityStore> store) {
            var view=store.getResource(Blackboard.getResourceType()).getView(AttitudeView.class,0L);
            view.registerProvider(-100,(IAttitudeProvider)InvisibilityConcealment::attitude);
        }
        @Override public void onSystemRemovedFromStore(@Nonnull Store<EntityStore> store){}
    };}
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store) {
        var hidden=new HashSet<Ref<EntityStore>>();
        store.forEachChunk(Query.and(Player.getComponentType(),PlayerRef.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var ref=chunk.getReferenceTo(i);boolean active=hidden(store,ref);if(active)hidden.add(ref);
                var before=announced.put(ref,active);
                if(before!=null&&before!=active)debug.traceFileOnly(chunk.getComponent(i,PlayerRef.getComponentType()),
                    "Invisibility event=creatureConcealment active="+active+" invulnerable=false modelSwap=false source=cloakOrNativeEffect");
            }
        });

        if(!hidden.isEmpty())store.forEachChunk(NPCEntity.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var ref=chunk.getReferenceTo(i);var marked=MarkedEntitySupport.get(ref,buffer);if(marked==null)continue;
                for(int slot=0;slot<marked.getMarkedEntitySlotCount();slot++)if(hidden.contains(marked.getMarkedEntityRef(slot)))marked.clearMarkedEntity(slot);
            }
        });
        announced.keySet().removeIf(ref->!ref.isValid());
    }
}
