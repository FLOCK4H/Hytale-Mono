package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.BsonString;


final class GrayCatFood implements Component<EntityStore> {
    static final String NONCE="AxoTalesCatOfferNonce";
    UUID owner,world;long expires;float mergeRadius;
    @Override public GrayCatFood clone(){var c=new GrayCatFood();c.owner=owner;c.world=world;c.expires=expires;c.mergeRadius=mergeRadius;return c;}
    static ItemStack clean(ItemStack item){return nonce(item)==null?item:item.withMetadata(NONCE,(org.bson.BsonValue)null);}
    private static String nonce(ItemStack item){try{return item.getFromMetadataOrNull(NONCE,Codec.STRING);}catch(RuntimeException invalid){return null;}}
    static final class Offers {
        private final Map<String,GrayCatFood> pending=new ConcurrentHashMap<>();
        ItemStack stamp(ItemStack item,UUID owner,UUID world,long now){
            pending.values().removeIf(v->v.expires<now);
            if(item==null || item.isEmpty() || !GrayCatState.rawFish(item.getItemId()) || owner==null || world==null || pending.size()>=1024)return item;
            var offer=new GrayCatFood();offer.owner=owner;offer.world=world;offer.expires=now+120_000;
            String nonce=UUID.randomUUID().toString();pending.put(nonce,offer);return clean(item).withMetadata(NONCE,new BsonString(nonce));
        }
        GrayCatFood take(ItemStack item,long now){
            var key=nonce(item);if(key==null)return null;
            var offer=pending.remove(key);return offer!=null && now<=offer.expires && GrayCatState.rawFish(item.getItemId())?offer:null;
        }
    }
    static final class Drop extends EntityEventSystem<EntityStore,DropItemEvent.Drop> {
        private final Offers offers;private final PluginDebugReporter debug;
        Drop(Offers offers,PluginDebugReporter debug){super(DropItemEvent.Drop.class);this.offers=offers;this.debug=debug;}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,DropItemEvent.Drop event){
            var before=event.getItemStack();if(before==null || !GrayCatState.rawFish(before.getItemId()))return;
            var player=chunk.getComponent(i,PlayerRef.getComponentType());boolean allow=!event.isCancelled() && player!=null && player.getUuid()!=null && player.getWorldUuid()!=null;
            if(allow){var stamped=offers.stamp(before,player.getUuid(),player.getWorldUuid(),System.currentTimeMillis());allow=stamped!=before;event.setItemStack(stamped);}
            if(debug!=null){var stats=buffer.getComponent(chunk.getReferenceTo(i),EntityStatMap.getComponentType());int health=DefaultEntityStatTypes.getHealth();var hp=stats==null?null:stats.get(health);
                debug.traceFileOnly(player,"GrayCat event=DropItem cancelled="+event.isCancelled()+" item="+before.getItemId()+" source=nativeDropEvent quantity="+before.getQuantity()+" healthIndex="+health+" health="+(hp==null?"missing":hp.get()+"/"+hp.getMax()+" min="+hp.getMin())+" allow="+allow);}
        }
    }
    static final class Arrive extends HolderSystem<EntityStore> {
        private final Offers offers;private final ComponentType<EntityStore,GrayCatFood> type;
        Arrive(Offers offers,ComponentType<EntityStore,GrayCatFood> type){this.offers=offers;this.type=type;}
        @Override public Query<EntityStore> getQuery(){return ItemComponent.getComponentType();}
        @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store){
            var item=holder.getComponent(ItemComponent.getComponentType());var stack=item.getItemStack();if(stack==null)return;
            var offer=offers.take(stack,System.currentTimeMillis());if(offer!=null)offer.mergeRadius=item.getMergeRadius(store);
            var cleaned=clean(stack);if(cleaned!=stack)item.setItemStack(cleaned);
            if(offer!=null){item.setMergeRadius(0);holder.putComponent(type,offer);}
        }
        @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store){}
    }
}
