package org.example.plugin;

import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import java.util.*;


final class SoulChunkLease implements AutoCloseable {
    private final Set<WorldChunk> columns=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ChunkSection> sections=Collections.newSetFromMap(new IdentityHashMap<>());
    synchronized void refresh(Set<WorldChunk> wantedColumns,Set<ChunkSection> wantedSections) {
        for(var it=sections.iterator();it.hasNext();){var section=it.next();if(!wantedSections.contains(section)){section.removeKeepLoaded();it.remove();}}
        for(var it=columns.iterator();it.hasNext();){var column=it.next();if(!wantedColumns.contains(column)){column.removeKeepLoaded();it.remove();}}
        for(var column:wantedColumns){if(columns.add(column))column.addKeepLoaded();column.resetKeepAlive();column.resetActiveTimer();}
        for(var section:wantedSections){if(sections.add(section))section.addKeepLoaded();section.resetKeepAlive();section.resetActiveTimer();}
    }
    @Override public synchronized void close(){refresh(Set.of(),Set.of());}
}
