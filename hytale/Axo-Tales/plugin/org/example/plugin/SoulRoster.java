package org.example.plugin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;


final class SoulRoster {
    static final int LIMIT=3;
    private final Path directory;
    private final Map<String,UUID[]> cache=new HashMap<>();
    SoulRoster(Path dataDirectory){directory=dataDirectory.resolve("soul-slots");}
    private String key(UUID owner,SoulKind kind){return owner+"/"+kind.name();}
    private UUID[] slots(UUID owner,SoulKind kind) throws IOException {
        var key=key(owner,kind);var slots=cache.get(key);if(slots!=null)return slots;
        slots=new UUID[LIMIT];
        for(int i=0;i<LIMIT;i++){var path=directory.resolve(key).resolve(i+".claim");if(Files.exists(path))slots[i]=UUID.fromString(Files.readString(path).trim());}
        cache.put(key,slots);return slots;
    }
    synchronized int available(UUID owner,SoulKind kind) throws IOException {
        var slots=slots(owner,kind);for(int i=0;i<LIMIT;i++)if(slots[i]==null)return i;return -1;
    }
    synchronized int claim(UUID owner,SoulKind kind,UUID entity) throws IOException {
        var slots=slots(owner,kind);
        for(int i=0;i<LIMIT;i++)if(entity.equals(slots[i]))return i;
        int slot=available(owner,kind);if(slot<0)return -1;
        var path=directory.resolve(key(owner,kind)).resolve(slot+".claim");Files.createDirectories(path.getParent());
        try(var file=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            var bytes=ByteBuffer.wrap(entity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            while(bytes.hasRemaining())file.write(bytes);file.force(true);
        }
        slots[slot]=entity;return slot;
    }
    synchronized void release(UUID owner,SoulKind kind,UUID entity) throws IOException {
        var slots=slots(owner,kind);
        for(int i=0;i<LIMIT;i++)if(entity.equals(slots[i])){
            Files.deleteIfExists(directory.resolve(key(owner,kind)).resolve(i+".claim"));slots[i]=null;
        }
    }
}
