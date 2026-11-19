package org.example.plugin;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import java.util.*;
import org.bson.*;
import javax.annotation.Nonnull;


final class AxoItemMigration extends TickingSystem<EntityStore> {
    private final PluginDebugReporter debug;private final PluginErrorReporter errors;
    private final Map<Store<EntityStore>,Float> elapsed=Collections.synchronizedMap(new WeakHashMap<>());
    AxoItemMigration(PluginDebugReporter debug,PluginErrorReporter errors){this.debug=debug;this.errors=errors;}
    static String identity(String id,double maxDurability){
        if(AxoItems.ANCIENT_SWORD.equals(id))return maxDurability<=0?AxoItems.ARCANE_SWORD:AxoItems.ANCIENT_SWORD;
        return AxoItems.canonical(id);
    }
    static ItemStack stack(ItemStack old){
        if(ItemStack.isEmpty(old))return old;
        String id=identity(old.getItemId(),old.getMaxDurability());
        boolean nested=old.getMetadata()!=null&&old.getMetadata().containsKey(ItemStackItemContainer.CONTAINER_CODEC.getKey());
        if(id.equals(old.getItemId())&&!nested)return old;
        var encoded=ItemStack.CODEC.encode(old,new ExtraInfo());
        if(!encoded(encoded,0))return old;
        return ItemStack.CODEC.decode(encoded,new ExtraInfo());
    }
    private static boolean encoded(BsonDocument document,int depth){
        if(depth>8||!document.isString("Id"))return false;
        String before=document.getString("Id").getValue();double maximum=document.containsKey("MaxDurability")?document.getNumber("MaxDurability").doubleValue():0;
        String after=identity(before,maximum);boolean changed=!before.equals(after);
        if(changed)document.put("Id",new BsonString(after));
        var metadata=document.getDocument("Metadata",null);
        if(metadata!=null){
            var container=metadata.getDocument(ItemStackItemContainer.CONTAINER_CODEC.getKey(),null);
            if(container!=null){
                var items=container.getArray(ItemStackItemContainer.ITEMS_CODEC.getKey(),null);
                if(items!=null)for(var item:items)if(item.isDocument())changed|=encoded(item.asDocument(),depth+1);
            }
        }
        return changed;
    }
    static int container(ItemContainer items){
        if(items==null)return 0;int changed=0;
        for(short slot=0;slot<items.getCapacity();slot++){
            var before=items.getItemStack(slot);var after=stack(before);


            if(after!=before&&items.replaceItemStackInSlot(slot,before,after).succeeded())changed++;
        }
        return changed;
    }
    private void migrate(Store<EntityStore> store,Ref<EntityStore> ref){
        if(!ref.isValid())return;
        try{
            var owner=store.getComponent(ref,PlayerRef.getComponentType());int changed=0;
            if(owner!=null){for(var section:SpellbookPouchService.inventorySections(store,ref))changed+=container(section);changed+=container(InventoryComponentAccess.armor(store,ref));}
            var dropped=store.getComponent(ref,ItemComponent.getComponentType());
            if(dropped!=null){var before=dropped.getItemStack();var after=stack(before);if(after!=before){dropped.setItemStack(after);changed++;}}
            if(changed>0&&debug!=null)debug.traceFileOnly(owner,"Items event=canonicalMigration source=nativeInventoryAndItemContainers stacks="+changed+" schema=299 metadataPreserved=true");
        }catch(RuntimeException failure){if(errors!=null)errors.report((PlayerRef)null,"Canonical item identity migration deferred",failure);}
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        float total=elapsed.getOrDefault(store,0f)+delta;if(total<.25f){elapsed.put(store,total);return;}elapsed.put(store,0f);
        store.forEachChunk(Query.or(PlayerRef.getComponentType(),ItemComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);buffer.run(s->migrate(s,ref));}
        });
    }
    RefSystem<EntityStore> lifecycle(){return new RefSystem<>(){
        @Override public Query<EntityStore> getQuery(){return Query.or(PlayerRef.getComponentType(),ItemComponent.getComponentType());}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){buffer.run(s->migrate(s,ref));}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
    };}
    static final class Containers extends TickingSystem<ChunkStore> {
        private final PluginErrorReporter errors;private final Map<Store<ChunkStore>,Float> elapsed=Collections.synchronizedMap(new WeakHashMap<>());
        Containers(PluginErrorReporter errors){this.errors=errors;}
        @Override public void tick(float delta,int tick,@Nonnull Store<ChunkStore> store){
            float total=elapsed.getOrDefault(store,0f)+delta;if(total<1){elapsed.put(store,total);return;}elapsed.put(store,0f);
            store.forEachChunk(Query.and(ItemContainerBlock.getComponentType(),BlockModule.BlockStateInfo.getComponentType()),(chunk,buffer)->{
                for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);buffer.run(s->{if(!ref.isValid())return;try{
                    var items=s.getComponent(ref,ItemContainerBlock.getComponentType());
                    if(items!=null&&container(items.getItemContainer())>0)s.getComponent(ref,BlockModule.BlockStateInfo.getComponentType()).markNeedsSaving(s);
                }catch(RuntimeException failure){if(errors!=null)errors.report((PlayerRef)null,"Stored item identity migration deferred",failure);}});}
            });
        }
    }
}
