package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonString;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public final class AstralBagService extends TickingSystem<EntityStore> {
    private final AstralVault repository;
    private final AstralCrafting crafting;
    final PluginDebugReporter debug;
    final PluginErrorReporter errors;
    private final Map<UUID,AstralBagPage> byBag=new ConcurrentHashMap<>(),byPlayer=new ConcurrentHashMap<>();
    private final Map<UUID,Long> debounce=new ConcurrentHashMap<>();
    private final Map<Store<EntityStore>,Long> sweeps=Collections.synchronizedMap(new WeakHashMap<>());
    public AstralBagService(Path directory,PluginDebugReporter debug,PluginErrorReporter errors){repository=new AstralVault(directory);this.debug=debug;this.errors=errors;crafting=new AstralCrafting(this,repository);}
    @Override public void tick(float delta,int count,Store<EntityStore> store) {
        long now=System.nanoTime();if(sweeps.getOrDefault(store,0L)>now)return;sweeps.put(store,now+250_000_000L);
        store.forEachChunk(Query.and(PlayerRef.getComponentType(),Player.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var ref=chunk.getReferenceTo(i);var actor=chunk.getComponent(i,PlayerRef.getComponentType());var page=byPlayer.get(actor.getUuid());
                buffer.run(accessor->{if(!ref.isValid()){if(page!=null)page.abandon();}else {
                    var entity=accessor.getComponent(ref,Player.getComponentType());
                    if(entity!=null)crafting.refresh(accessor,ref,entity);
                    if(page!=null){if(entity==null || entity.getPageManager().getCustomPage()!=page)page.abandon();
                    else if(!page.attached(accessor,ref))page.dismiss();}
                }});
            }
        });
    }
    static UUID identity(ItemStack item) {
        if(ItemStack.isEmpty(item) || !AstralVault.ITEM.equals(item.getItemId()))return null;
        var value=AstralVault.metadata(item).get(AstralVault.ID_KEY);
        try{return value==null || !value.isString()?null:UUID.fromString(value.asString().getValue());}catch(IllegalArgumentException malformed){return null;}
    }
    static boolean same(ItemContainer parent,short slot,UUID id) {
        return slot>=0 && slot<parent.getCapacity() && id.equals(identity(parent.getItemStack(slot)));
    }
    public void onOpen(InteractionContext context) {
        var buffer=context.getCommandBuffer();var ref=context.getEntity();var player=buffer.getComponent(ref,PlayerRef.getComponentType());
        var parent=context.getHeldItemContainer();short slot=context.getHeldItemSlot();var item=context.getHeldItem();
        if(player==null || ItemStack.isEmpty(item) || !AstralVault.ITEM.equals(item.getItemId()) || parent==null || slot<0 || !item.equals(parent.getItemStack(slot)))return;
        if(buffer.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType())!=null
                || buffer.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.Spectating.getComponentType())!=null)return;
        long now=System.nanoTime();Long before=debounce.put(player.getUuid(),now);if(before!=null && now-before<400_000_000L)return;

        buffer.run(store->{
            if(!ref.isValid() || !Objects.equals(item,parent.getItemStack(slot)))return;
            try {
                var entity=store.getComponent(ref,Player.getComponentType());if(entity==null)return;
                UUID id=identity(item);AstralVault.Vault vault;
                if(id==null) {
                    if(AstralVault.metadata(item).containsKey(AstralVault.ID_KEY))throw new IllegalStateException("Malformed satchel identity; original item retained");
                    id=UUID.randomUUID();vault=repository.create(id);
                    if(!parent.setItemStackForSlot(slot,item.withMetadata(AstralVault.ID_KEY,new BsonString(id.toString())),false).succeeded())throw new IllegalStateException("Satchel identity assignment rejected");
                }else vault=repository.open(id);
                var previous=byPlayer.get(player.getUuid());if(previous!=null)previous.dismiss();
                var page=new AstralBagPage(player,this,vault,parent,slot);
                if(byBag.putIfAbsent(id,page)!=null){player.sendMessage(Message.raw("This satchel is already open. Close its other window first."));return;}
                byPlayer.put(player.getUuid(),page);
                try {entity.getPageManager().openCustomPage(ref,store,page);}
                catch(Throwable failure){release(page);throw failure;}
                trace(player,"open",true,"itemSource=interactionHeldItem bag="+id);
            } catch(Throwable failure){errors.report(player,"Astral Satchel could not open; its saved contents were retained.",failure);}
        });
    }
    void release(AstralBagPage page){byBag.remove(page.vault.id,page);byPlayer.remove(page.owner(),page);}
    public void disconnect(PlayerRef player){var page=byPlayer.remove(player.getUuid());if(page!=null)page.abandon();debounce.remove(player.getUuid());}
    static List<ItemContainer> destinations(Store<EntityStore> store,Ref<EntityStore> ref) {
        var result=new ArrayList<ItemContainer>();
        for(var section:new ItemContainer[]{InventoryComponentAccess.storage(store,ref),InventoryComponentAccess.backpack(store,ref),InventoryComponentAccess.hotbar(store,ref)})
            if(section!=null && !result.contains(section))result.add(section);return result;
    }
    void trace(PlayerRef player,String event,boolean allow,String detail) {
        debug.traceFileOnly(player,"AstralSatchel event="+event+" cancelled=false item="+AstralVault.ITEM+" allow="+allow
            +" health=notUsed mana=notUsed "+detail);
    }
    void traceAction(Store<EntityStore> store,Ref<EntityStore> ref,String event,boolean allow,String detail){
        var stats=store.getComponent(ref,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
        int index=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth();var hp=stats==null||index<0?null:stats.get(index);
        debug.traceFileOnly(store.getComponent(ref,PlayerRef.getComponentType()),"AstralSatchel event="+event+" cancelled=false item="+AstralVault.ITEM+" allow="+allow
            +" health.index="+index+" health.current="+(hp==null?"missing":hp.get())+" health.min="+(hp==null?"missing":hp.getMin())+" health.max="+(hp==null?"missing":hp.getMax())+" mana=notUsed "+detail);
    }
}
