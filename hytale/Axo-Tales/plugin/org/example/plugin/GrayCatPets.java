package org.example.plugin;

import com.google.gson.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import org.joml.Vector3d;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


final class GrayCatPets {
    record Entry(UUID cat,UUID owner,UUID world,double x,double y,double z) {}
    private final Path file;private Map<UUID,Entry> records;
    private final Set<UUID> live=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Long> recent=new ConcurrentHashMap<>(),recalling=new ConcurrentHashMap<>();
    private final PluginErrorReporter errors;
    GrayCatPets(Path data,PluginErrorReporter errors){file=data.resolve("gray-cat-companions.json");this.errors=errors;}
    private Map<UUID,Entry> read() throws IOException {
        if(records!=null)return records;var next=new LinkedHashMap<UUID,Entry>();
        if(Files.exists(file))try{
            for(var value:JsonParser.parseString(Files.readString(file)).getAsJsonArray()){
                var o=value.getAsJsonObject();var e=new Entry(UUID.fromString(o.get("cat").getAsString()),UUID.fromString(o.get("owner").getAsString()),UUID.fromString(o.get("world").getAsString()),o.get("x").getAsDouble(),o.get("y").getAsDouble(),o.get("z").getAsDouble());
                if(!new Vector3d(e.x,e.y,e.z).isFinite())throw new IOException("Invalid saved cat position");if(next.put(e.cat,e)!=null)throw new IOException("Duplicate cat identity");
            }
        }catch(RuntimeException invalid){throw new IOException("Invalid saved cat roster",invalid);}
        records=next;return records;
    }
    private void write(Map<UUID,Entry> next) throws IOException {
        var array=new JsonArray();for(var e:next.values()){
            var o=new JsonObject();o.addProperty("cat",e.cat.toString());o.addProperty("owner",e.owner.toString());o.addProperty("world",e.world.toString());o.addProperty("x",e.x);o.addProperty("y",e.y);o.addProperty("z",e.z);array.add(o);
        }
        Files.createDirectories(file.getParent());var temporary=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temporary,array.toString(),java.nio.charset.StandardCharsets.UTF_8);
        try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}records=next;
    }
    synchronized void remember(Entry e) throws IOException {
        var before=read();var old=before.get(e.cat);if(old!=null && !old.owner.equals(e.owner))throw new IOException("Cat already belongs to another owner");
        if(e.equals(old))return;var next=new LinkedHashMap<>(before);next.put(e.cat,e);write(next);
    }
    synchronized void forget(UUID cat) throws IOException {var next=new LinkedHashMap<>(read());if(next.remove(cat)!=null)write(next);live.remove(cat);recent.remove(cat);recalling.remove(cat);}
    synchronized List<Entry> missing(Set<UUID> owners,long now) throws IOException {
        return read().values().stream().filter(e->owners.contains(e.owner) && !live.contains(e.cat) && now-recent.getOrDefault(e.cat,0L)>5000 && now>=recalling.getOrDefault(e.cat,0L)).toList();
    }
    synchronized boolean recallLease(UUID cat,long now){if(now<recalling.getOrDefault(cat,0L))return false;recalling.put(cat,now+30_000);return true;}
    void active(UUID cat){live.add(cat);recent.put(cat,System.currentTimeMillis());}
    void unloaded(UUID cat){live.remove(cat);recent.put(cat,System.currentTimeMillis());}
    void recall(Set<UUID> owners){
        long now=System.currentTimeMillis();try{for(var e:missing(owners,now)){
            if(!recallLease(e.cat,now))continue;var universe=Universe.get();var world=universe==null?null:universe.getWorld(e.world);if(world==null || !world.isAlive())continue;
            world.execute(()->{

                int flags=GetChunkFlags.NO_GENERATE|GetChunkFlags.SET_TICKING;var chunks=world.getChunkStore();
                chunks.getChunkReferenceAsync(ChunkUtil.indexChunkFromBlock((int)Math.floor(e.x),(int)Math.floor(e.z)),flags)
                    .thenCompose(column->column==null?java.util.concurrent.CompletableFuture.completedFuture(null):chunks.getChunkSectionReferenceAtBlockAsync((int)Math.floor(e.x),(int)Math.floor(e.y),(int)Math.floor(e.z),flags))
                    .whenComplete((section,failure)->{if(failure!=null && errors!=null)errors.report((PlayerRef)null,"Gray Cat original recall failed; no replacement spawned",failure);});


            });
        }}catch(IOException failure){if(errors!=null)errors.report((PlayerRef)null,"Gray Cat saved roster unavailable; recall denied",failure);}
    }
    RefSystem<EntityStore> lifecycle(ComponentType<EntityStore,GrayCatState> stateType){return lifecycle(stateType,s->s.getExternalData().getWorld().getWorldConfig().getUuid());}
    RefSystem<EntityStore> lifecycle(ComponentType<EntityStore,GrayCatState> stateType,java.util.function.Function<Store<EntityStore>,UUID> worldId){return new RefSystem<>(){
        @Override public Query<EntityStore> getQuery(){return Query.and(stateType,UUIDComponent.getComponentType(),TransformComponent.getComponentType());}
        private Entry entry(Ref<EntityStore> ref,Store<EntityStore> store,ComponentAccessor<EntityStore> access){
            var state=access.getComponent(ref,stateType);if(state==null || state.owner==null)return null;var at=access.getComponent(ref,TransformComponent.getComponentType()).getPosition();
            return new Entry(access.getComponent(ref,UUIDComponent.getComponentType()).getUuid(),state.owner,worldId.apply(store),at.x,at.y,at.z);
        }
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){var e=entry(ref,store,buffer);if(e==null)return;active(e.cat);buffer.run(s->{try{remember(e);}catch(IOException failure){if(errors!=null)errors.report((PlayerRef)null,"Gray Cat load location could not be saved",failure);}});}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){var e=entry(ref,store,buffer);if(e==null)return;
            if(reason==RemoveReason.UNLOAD)unloaded(e.cat);else live.remove(e.cat);
            buffer.run(s->{try{if(reason==RemoveReason.UNLOAD)remember(e);else forget(e.cat);}catch(IOException failure){if(errors!=null)errors.report((PlayerRef)null,"Gray Cat lifecycle location could not be saved",failure);}});
        }
    };}
}
