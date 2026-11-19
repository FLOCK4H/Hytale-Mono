package org.example.plugin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.fastutil.ints.Int2ObjectConcurrentHashMap;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import javax.annotation.Nonnull;


final class FrozenStorageWindows extends TickingSystem<EntityStore> {
    private static final java.lang.reflect.Field WINDOWS=windowsField();
    private final PluginErrorReporter errors;
    FrozenStorageWindows(PluginErrorReporter errors){this.errors=errors;}
    private static java.lang.reflect.Field windowsField(){try{var f=WindowManager.class.getDeclaredField("windows");f.setAccessible(true);return f;}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
    private static final class GuardedWindows extends Int2ObjectConcurrentHashMap<Window> {
        final java.util.function.Supplier<Set<ItemContainer>> carried;
        GuardedWindows(Int2ObjectConcurrentHashMap<Window> previous,java.util.function.Supplier<Set<ItemContainer>> carried){super(previous);this.carried=carried;}
        private Window prepare(Window window){
            if(window instanceof ItemContainerWindow items&&!(window instanceof ContainerBlockWindow))filter(items.getItemContainer(),carried.get());
            return window;
        }
        @Override public Window put(int id,Window window){return super.put(id,prepare(window));}
        @Override public Window putIfAbsent(int id,Window window){return super.putIfAbsent(id,prepare(window));}
        @Override public Window get(int id){return prepare(super.get(id));}
        @Override public com.hypixel.fastutil.FastCollection<Window> values(){var result=super.values();result.forEach(this::prepare);return result;}
    }
    private static void filter(ItemContainer items,Set<ItemContainer> carried){
        if(carried.contains(items))return;
        if(items instanceof com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer combined){for(int i=0;i<combined.getContainersSize();i++)filter(combined.getContainer(i),carried);}
        else FrozenStorage.apply(items,FrozenStorage.Policy.STORAGE);
    }
    @SuppressWarnings("unchecked")
    static void guard(WindowManager manager,java.util.function.Supplier<Set<ItemContainer>> carried){
        try{
            var current=(Int2ObjectConcurrentHashMap<Window>)WINDOWS.get(manager);
            if(current instanceof GuardedWindows)return;
            if(current.getClass()!=Int2ObjectConcurrentHashMap.class)return;
            WINDOWS.set(manager,new GuardedWindows(current,carried));
        }catch(IllegalAccessException failure){throw new IllegalStateException("Pinned native window table unavailable",failure);}
    }
    RefSystem<EntityStore> lifecycle(){return new RefSystem<>(){
        @Override public Query<EntityStore> getQuery(){return Player.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){attach(store,ref,store.getComponent(ref,Player.getComponentType()));}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
    };}
    private void attach(Store<EntityStore> store,Ref<EntityStore> ref,Player player){
        guard(player.getWindowManager(),()->{Set<ItemContainer> carried=Collections.newSetFromMap(new IdentityHashMap<>());if(ref.isValid())carried.addAll(SpellbookPouchService.inventorySections(store,ref));return carried;});
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        store.forEachChunk(Player.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var player=chunk.getComponent(i,Player.getComponentType());var ref=chunk.getReferenceTo(i);
                Set<ItemContainer> carried=Collections.newSetFromMap(new IdentityHashMap<>());carried.addAll(SpellbookPouchService.inventorySections(store,ref));
                try{
                    attach(store,ref,player);
                    for(var window:player.getWindowManager().getWindows()){
                        if(window instanceof ContainerBlockWindow)continue;
                        if(window instanceof ItemContainerWindow items&&!carried.contains(items.getItemContainer()))FrozenStorage.apply(items.getItemContainer(),FrozenStorage.Policy.STORAGE);
                    }
                }catch(RuntimeException failure){if(errors!=null)errors.report((com.hypixel.hytale.server.core.universe.PlayerRef)null,"Portable specimen storage filter refresh deferred",failure);}
            }
        });
    }
}
