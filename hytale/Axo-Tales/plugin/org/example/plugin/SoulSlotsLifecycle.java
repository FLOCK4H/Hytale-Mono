package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


final class SoulSlotsLifecycle extends RefSystem<EntityStore> {
    private final ComponentType<EntityStore,SoulState> type;
    private final SoulRoster roster;
    private final PluginErrorReporter errors;
    SoulSlotsLifecycle(ComponentType<EntityStore,SoulState> type,SoulRoster roster,PluginErrorReporter errors){this.type=type;this.roster=roster;this.errors=errors;}
    @Override public Query<EntityStore> getQuery(){return Query.and(type,UUIDComponent.getComponentType());}
    @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
    @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        if(reason==RemoveReason.UNLOAD)return;
        var state=buffer.getComponent(ref,type);var uuid=buffer.getComponent(ref,UUIDComponent.getComponentType());
        if(state==null || state.owner==null || state.type()==null || uuid==null)return;
        var owner=state.owner;var kind=state.type();var id=uuid.getUuid();
        buffer.run(s->{try{roster.release(owner,kind,id);}catch(java.io.IOException failure){
            if(errors==null)throw new IllegalStateException(failure);
            errors.report((com.hypixel.hytale.server.core.universe.PlayerRef)null,"Soul slot release after entity removal failed",failure);
        }});
    }
}
