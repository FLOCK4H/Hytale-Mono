package org.example.plugin;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import org.bson.*;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.locks.ReentrantLock;


public class AstralVault {

    static final ReentrantLock TRANSACTIONS=new ReentrantLock();
    public static final String ITEM="Axo_Astral_Satchel",ID_KEY="AxoTalesAstralId";
    private static final JsonWriterSettings JSON=JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();
    public record Entry(UUID id,ItemStack item,long count) {
        public Entry {if(id==null || ItemStack.isEmpty(item) || count<=0)throw new IllegalArgumentException("Invalid vault entry");}
    }
    public record Snapshot(long revision,List<Entry> entries) {}
    private final Path directory;


    private final Map<UUID,WeakReference<Vault>> loaded=Collections.synchronizedMap(new WeakHashMap<>());
    public AstralVault(Path directory){this.directory=directory;}
    public Vault create(UUID id) throws IOException {
        Files.createDirectories(directory);Path path=path(id);
        if(Files.exists(path))throw new IOException("Satchel identity already exists");
        var vault=new Vault(id,0,List.of());write(vault,0,List.of());loaded.put(id,new WeakReference<>(vault));return vault;
    }
    public Vault open(UUID id) throws IOException {
        var current=cached(id);if(current!=null)return current;
        synchronized(loaded) {
            current=cached(id);if(current!=null)return current;
            var doc=read(path(id));
            if(doc.getInt32("Version").getValue()!=1 || !id.toString().equals(doc.getString("Id").getValue()))throw new IOException("Invalid satchel storage identity/version");
            var entries=new ArrayList<Entry>();var seen=new HashSet<UUID>();
            for(var value:doc.getArray("Entries")) {
                var entry=value.asDocument();UUID key=UUID.fromString(entry.getString("Id").getValue());
                var item=AxoItemMigration.stack(ItemStack.CODEC.decode(entry.getDocument("Item"),new ExtraInfo()));long count=entry.getNumber("Count").longValue();
                if(!seen.add(key) || !admissible(item) || item.getQuantity()!=1)throw new IOException("Invalid or recursive satchel entry");
                entries.add(new Entry(key,item.withQuantity(1),count));
            }
            long revision=doc.getNumber("Revision").longValue();if(revision<0)throw new IOException("Invalid satchel revision");
            current=new Vault(id,revision,entries);loaded.put(id,new WeakReference<>(current));return current;
        }
    }
    private Vault cached(UUID id){var reference=loaded.get(id);return reference==null?null:reference.get();}
    private Path path(UUID id){return directory.resolve(id+".json.gz");}
    private static BsonDocument read(Path path) throws IOException {

        try(var stream=new GZIPInputStream(Files.newInputStream(path))){return BsonDocument.parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}
        catch(RuntimeException failure){throw new IOException("Cannot read satchel storage "+path.getFileName(),failure);}
    }
    protected void write(Vault vault,long revision,List<Entry> entries) throws IOException {
        Files.createDirectories(directory);var values=new BsonArray();
        for(var entry:entries)values.add(new BsonDocument("Id",new BsonString(entry.id.toString()))
            .append("Item",ItemStack.CODEC.encode(entry.item,new ExtraInfo())).append("Count",new BsonInt64(entry.count)));
        var doc=new BsonDocument("Version",new BsonInt32(1)).append("Id",new BsonString(vault.id.toString()))
            .append("Revision",new BsonInt64(revision)).append("Entries",values);
        Path target=path(vault.id),temporary=Files.createTempFile(directory,vault.id+"-", ".pending");
        try {


            try(var output=new GZIPOutputStream(Files.newOutputStream(temporary))){output.write(doc.toJson(JSON).getBytes(StandardCharsets.UTF_8));}
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){channel.force(true);}

            if(Files.exists(target))Files.copy(target,directory.resolve(vault.id+".previous.json.gz"),StandardCopyOption.REPLACE_EXISTING);
            try {Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }
    public final class Vault {
        public final UUID id;
        private long revision;
        private List<Entry> entries;
        private Vault(UUID id,long revision,List<Entry> entries){this.id=id;this.revision=revision;this.entries=List.copyOf(entries);}
        public Snapshot snapshot(){TRANSACTIONS.lock();try{return new Snapshot(revision,entries);}finally{TRANSACTIONS.unlock();}}
        private void commit(List<Entry> next) throws IOException {long following=Math.addExact(revision,1);write(this,following,next);entries=List.copyOf(next);revision=following;}

        public int deposit(long expected,ItemContainer source,short slot,ItemStack before,int maximum) throws IOException {
            TRANSACTIONS.lock();try {
            if(expected!=revision || !Objects.equals(before,source.getItemStack(slot)) || !admissible(before))return 0;
            int quantity=Math.min(maximum,before.getQuantity());if(quantity<=0)return 0;
            var next=new ArrayList<>(entries);ItemStack template=before.withQuantity(1);var encoded=ItemStack.CODEC.encode(template,new ExtraInfo());int existing=-1;
            for(int i=0;i<next.size();i++)if(encoded.equals(ItemStack.CODEC.encode(next.get(i).item,new ExtraInfo()))){existing=i;break;}
            if(existing>=0){var old=next.get(existing);next.set(existing,new Entry(old.id,old.item,Math.addExact(old.count,quantity)));}
            else next.add(new Entry(UUID.randomUUID(),ItemStack.CODEC.decode(encoded.clone(),new ExtraInfo()),quantity));
            if(!source.removeItemStackFromSlot(slot,before,quantity).succeeded())return 0;
            try {commit(next);return quantity;}
            catch(Throwable failure){restore(source,slot,before,failure);if(failure instanceof IOException io)throw io;throw failure;}
            }finally{TRANSACTIONS.unlock();}
        }

        public int withdraw(long expected,UUID entryId,int maximum,List<ItemContainer> destinations) throws IOException {
            return withdraw(expected,entryId,maximum,destinations,true);
        }
        int withdrawAll(long expected,UUID entryId,List<ItemContainer> destinations) throws IOException {
            return withdraw(expected,entryId,Integer.MAX_VALUE,destinations,false);
        }
        private int withdraw(long expected,UUID entryId,int maximum,List<ItemContainer> destinations,boolean stackOnly) throws IOException {
            TRANSACTIONS.lock();try {
            if(expected!=revision || maximum<=0)return 0;int index=-1;
            for(int i=0;i<entries.size();i++)if(entries.get(i).id.equals(entryId)){index=i;break;}
            if(index<0)return 0;var entry=entries.get(index);
            if(com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(entry.item.getItemId())==null)return 0;
            int requested=(int)Math.min(entry.count,stackOnly?Math.min(maximum,Math.max(1,entry.item.getItem().getMaxStack())):maximum);
            var snapshots=new LinkedHashMap<ItemContainer,List<ItemStack>>();
            for(var destination:destinations) {
                if(snapshots.containsKey(destination))continue;var slots=new ArrayList<ItemStack>();
                for(short i=0;i<destination.getCapacity();i++)slots.add(destination.getItemStack(i));snapshots.put(destination,slots);
            }
            ItemStack remainder=entry.item.withQuantity(requested);
            try {
                for(var destination:snapshots.keySet()){remainder=destination.addItemStack(remainder,false,false,true).getRemainder();if(ItemStack.isEmpty(remainder))break;}
                int moved=requested-(ItemStack.isEmpty(remainder)?0:remainder.getQuantity());if(moved<=0)return 0;
                var next=new ArrayList<>(entries);if(entry.count==moved)next.remove(index);else next.set(index,new Entry(entry.id,entry.item,entry.count-moved));
                commit(next);return moved;
            }catch(Throwable failure) {
                for(var snapshot:snapshots.entrySet())for(short i=0;i<snapshot.getValue().size();i++)restore(snapshot.getKey(),i,snapshot.getValue().get(i),failure);
                if(failure instanceof IOException io)throw io;throw failure;
            }
            }finally{TRANSACTIONS.unlock();}
        }

        void consume(Map<UUID,Long> amounts) throws IOException {
            if(!TRANSACTIONS.isHeldByCurrentThread())throw new IllegalStateException("Missing vault transaction lock");
            var next=new ArrayList<Entry>();var missing=new HashSet<>(amounts.keySet());
            for(var entry:entries){long take=amounts.getOrDefault(entry.id,0L);if(take<0||take>entry.count)throw new IllegalStateException("Stale crafting ingredient");missing.remove(entry.id);if(entry.count>take)next.add(new Entry(entry.id,entry.item,entry.count-take));}
            if(!missing.isEmpty())throw new IllegalStateException("Missing crafting ingredient");
            commit(next);
        }
        void restoreSnapshot(Snapshot before) throws IOException {
            if(!TRANSACTIONS.isHeldByCurrentThread())throw new IllegalStateException("Missing vault transaction lock");
            write(this,before.revision,before.entries);entries=before.entries;revision=before.revision;
        }
    }
    private static void restore(ItemContainer target,short slot,ItemStack original,Throwable failure) {
        if(!target.setItemStackForSlot(slot,original,false).succeeded())failure.addSuppressed(new IllegalStateException("Native slot rollback rejected"));
    }
    public static boolean admissible(ItemStack item) {
        if(ItemStack.isEmpty(item) || ITEM.equals(item.getItemId()))return false;
        return !containsSatchel(metadata(item),0);
    }
    static BsonDocument metadata(ItemStack item) {
        return ItemStack.CODEC.encode(item,new ExtraInfo()).getDocument("Metadata",new BsonDocument());
    }
    private static boolean containsSatchel(BsonValue value,int depth) {
        if(value==null)return false;
        if(depth>128)return true;
        if(value.isString())return ITEM.equals(value.asString().getValue());
        if(value.isDocument())return value.asDocument().values().stream().anyMatch(v->containsSatchel(v,depth+1));
        if(value.isArray())return value.asArray().stream().anyMatch(v->containsSatchel(v,depth+1));return false;
    }
}
