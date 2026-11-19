package org.example.plugin;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;


final class SpyglassSettings {
    static final List<String> DEFAULT_FIELDS=List.of("health","mana","distance");
    final List<String> fields;
    SpyglassSettings(List<String> fields){this.fields=fields.stream().filter(Objects::nonNull).map(String::trim).filter(x->!x.isEmpty()).distinct().limit(7).toList();}
    static SpyglassSettings load(Path directory,PluginErrorReporter errors){
        var file=directory.resolve("spyglass-display.json");
        try{
            if(!Files.exists(file)){
                Files.createDirectories(directory);Files.writeString(file,"{\n  \"fields\": [\"health\", \"mana\", \"distance\"]\n}\n");return new SpyglassSettings(DEFAULT_FIELDS);
            }
            var doc=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var fields=new ArrayList<String>();
            for(var value:doc.getAsJsonArray("fields"))fields.add(value.getAsString());return new SpyglassSettings(fields);
        }catch(Exception failure){errors.report((com.hypixel.hytale.server.core.universe.PlayerRef)null,"Could not read spyglass-display.json; using safe display defaults",failure);return new SpyglassSettings(DEFAULT_FIELDS);}
    }
}
