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


final class WhisperionPets {
    record Entry(UUID horse,UUID owner,UUID world,String worldName,double x,double y,double z) {}
    private final Path file;private Map<UUID,Entry> records;
    private final Set<UUID> live=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Long> recent=new ConcurrentHashMap<>(),recalling=new ConcurrentHashMap<>();
    private final PluginErrorReporter errors;
    WhisperionPets(Path data,PluginErrorReporter errors){file=data.resolve("whisperion-companions.json");this.errors=errors;}
    private Map<UUID,Entry> read() throws IOException {
        if(records!=null)return records;var next=new LinkedHashMap<UUID,Entry>();
        if(Files.exists(file))try{
            for(var value:JsonParser.parseString(Files.readString(file)).getAsJsonArray()){
                var o=value.getAsJsonObject();var e=new Entry(UUID.fromString(o.get("horse").getAsString()),UUID.fromString(o.get("owner").getAsString()),UUID.fromString(o.get("world").getAsString()),o.has("worldName")?o.get("worldName").getAsString():null,o.get("x").getAsDouble(),o.get("y").getAsDouble(),o.get("z").getAsDouble());
                if(!new Vector3d(e.x,e.y,e.z).isFinite())throw new IOException("Invalid saved horse position");if(next.put(e.horse,e)!=null)throw new IOException("Duplicate horse identity");
            }
        }catch(RuntimeException invalid){throw new IOException("Invalid saved horse roster",invalid);}
        records=next;return records;
    }
    private void write(Map<UUID,Entry> next) throws IOException {
        var array=new JsonArray();for(var e:next.values()){
            var o=new JsonObject();o.addProperty("horse",e.horse.toString());o.addProperty("owner",e.owner.toString());o.addProperty("world",e.world.toString());if(e.worldName!=null)o.addProperty("worldName",e.worldName);o.addProperty("x",e.x);o.addProperty("y",e.y);o.addProperty("z",e.z);array.add(o);
        }
        Files.createDirectories(file.getParent());var temporary=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temporary,array.toString(),java.nio.charset.StandardCharsets.UTF_8);
        try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}records=next;
    }
    synchronized void remember(Entry e) throws IOException {
        var before=read();var old=before.get(e.horse);if(old!=null && !old.owner.equals(e.owner))throw new IOException("Cat already belongs to another owner");
        if(e.equals(old))return;var next=new LinkedHashMap<>(before);next.put(e.horse,e);write(next);
    }
    synchronized void forget(UUID horse) throws IOException {var next=new LinkedHashMap<>(read());if(next.remove(horse)!=null)write(next);live.remove(horse);recent.remove(horse);recalling.remove(horse);}
    synchronized List<Entry> missing(Set<UUID> owners,long now) throws IOException {
        return read().values().stream().filter(e->owners.contains(e.owner) && !live.contains(e.horse) && now-recent.getOrDefault(e.horse,0L)>5000 && now>=recalling.getOrDefault(e.horse,0L)).toList();
    }
    synchronized boolean recallLease(UUID horse,long now){if(now<recalling.getOrDefault(horse,0L))return false;recalling.put(horse,now+30_000);return true;}
    void active(UUID horse){live.add(horse);recent.put(horse,System.currentTimeMillis());}
    void unloaded(UUID horse){live.remove(horse);recent.put(horse,System.currentTimeMillis());}
    private final Map<String,java.util.concurrent.CompletableFuture<com.hypixel.hytale.server.core.universe.world.World>> loadingWorlds=new ConcurrentHashMap<>();
    void recall(Set<UUID> owners){
        long now=System.currentTimeMillis();try{for(var e:missing(owners,now)){
            if(!recallLease(e.horse,now))continue;var universe=Universe.get();if(universe==null)continue;
            var world=universe.getWorld(e.world);
            if(world!=null && world.isAlive()){loadOriginal(e,world);continue;}

            if(e.worldName==null || !universe.isWorldLoadable(e.worldName))continue;
            loadingWorlds.computeIfAbsent(e.worldName,universe::loadWorld).whenComplete((loaded,failure)->{
                loadingWorlds.remove(e.worldName);
                if(failure!=null){if(errors!=null)errors.report((PlayerRef)null,"Whisperion saved-world recall failed; original retained",failure);return;}
                if(loaded!=null && loaded.isAlive() && e.world.equals(loaded.getWorldConfig().getUuid()))loadOriginal(e,loaded);
            });
        }}catch(IOException failure){if(errors!=null)errors.report((PlayerRef)null,"Whisperion saved roster unavailable; recall denied",failure);}
    }
    private void loadOriginal(Entry e,com.hypixel.hytale.server.core.universe.world.World world) {
        world.execute(()->{
            int flags=GetChunkFlags.NO_GENERATE|GetChunkFlags.SET_TICKING;var chunks=world.getChunkStore();
            chunks.getChunkReferenceAsync(ChunkUtil.indexChunkFromBlock((int)Math.floor(e.x),(int)Math.floor(e.z)),flags)
                .thenCompose(column->column==null?java.util.concurrent.CompletableFuture.completedFuture(null):chunks.getChunkSectionReferenceAtBlockAsync((int)Math.floor(e.x),(int)Math.floor(e.y),(int)Math.floor(e.z),flags))
                .whenComplete((section,failure)->{if(failure!=null && errors!=null)errors.report((PlayerRef)null,"Whisperion original recall failed; no replacement spawned",failure);});
        });
    }
    RefSystem<EntityStore> lifecycle(ComponentType<EntityStore,WhisperionState> stateType){return lifecycle(stateType,s->s.getExternalData().getWorld().getWorldConfig().getUuid());}
    RefSystem<EntityStore> lifecycle(ComponentType<EntityStore,WhisperionState> stateType,java.util.function.Function<Store<EntityStore>,UUID> worldId){return new RefSystem<>(){
        @Override public Query<EntityStore> getQuery(){return Query.and(stateType,UUIDComponent.getComponentType(),TransformComponent.getComponentType());}
        private Entry entry(Ref<EntityStore> ref,Store<EntityStore> store,ComponentAccessor<EntityStore> access){
            var state=access.getComponent(ref,stateType);if(state==null || state.owner==null)return null;var at=access.getComponent(ref,TransformComponent.getComponentType()).getPosition();
            return new Entry(access.getComponent(ref,UUIDComponent.getComponentType()).getUuid(),state.owner,worldId.apply(store),store.getExternalData().getWorld()==null?null:store.getExternalData().getWorld().getName(),at.x,at.y,at.z);
        }
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){var e=entry(ref,store,buffer);if(e==null)return;active(e.horse);buffer.run(s->{try{remember(e);}catch(IOException failure){if(errors!=null)errors.report((PlayerRef)null,"Whisperion load location could not be saved",failure);}});}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){var e=entry(ref,store,buffer);if(e==null)return;
            if(reason==RemoveReason.UNLOAD)unloaded(e.horse);else live.remove(e.horse);
            buffer.run(s->{try{if(reason==RemoveReason.UNLOAD)remember(e);else forget(e.horse);}catch(IOException failure){if(errors!=null)errors.report((PlayerRef)null,"Whisperion lifecycle location could not be saved",failure);}});
        }
    };}
}
