package org.example.plugin;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.*;
import java.nio.file.Path;
import java.util.*;
import org.bson.*;


final class AncientBowRoutes {
    static final String PREFIX="AxoAncientBowRoute_";
    private static boolean patching;
    private record Original(String pack,RootInteraction root){}
    private static final Map<String,Original> originals=new HashMap<>();
    static boolean eligible(RootInteraction root){return root!=null&&root.getId().startsWith("Root_Weapon_Shortbow_")&&root.getId().endsWith("Primary_Shoot")
        &&root.getInteractionIds().length>0&&!root.getInteractionIds()[0].startsWith(PREFIX);}
    static BsonDocument routed(RootInteraction original,String route){
        var doc=RootInteraction.CODEC.encode(original,new ExtraInfo());doc.put("Interactions",new BsonArray(List.of(new BsonString(route))));return doc;
    }
    static synchronized void patch(LoadedAssetsEvent<?,?,?> event,PluginDebugReporter debug,PluginErrorReporter errors){
        if(event.getAssetClass()!=RootInteraction.class&&event.getAssetClass()!=Interaction.class)return;
        if(patching||Interaction.getAssetStore()==null||RootInteraction.getAssetStore()==null||Interaction.getAssetMap().getAsset("AxoAncientBow_Charge")==null)return;
        patching=true;
        try{
            for(var root:new ArrayList<>(RootInteraction.getAssetMap().getAssetMap().values())){
                if(!eligible(root))continue;
                try{
                String pack=RootInteraction.getAssetMap().getAssetPack(root.getId()),route=PREFIX+root.getId();
                originals.put(root.getId(),new Original(pack,root));
                var fallback=new BsonDocument("Type",new BsonString("Serial"));fallback.put("Interactions",new BsonArray(Arrays.stream(root.getInteractionIds()).map(BsonString::new).map(x->(BsonValue)x).toList()));
                var doc=new BsonDocument("Type",new BsonString(ScoutInteraction.TYPE)).append("Mode",new BsonString("select"))
                    .append("Next",new BsonString("AxoAncientBow_Charge")).append("Failed",fallback);
                var gate=Interaction.CODEC.decodeJsonAsset(RawJsonReader.fromJsonString(doc.toJson()),new AssetExtraInfo<String>(Path.of(route+".json"),new AssetExtraInfo.Data(Interaction.class,route,null)));
                Interaction.getAssetStore().loadAssets(pack,List.of(gate));
                var replacement=RootInteraction.CODEC.decodeJsonAsset(RawJsonReader.fromJsonString(routed(root,route).toJson()),new AssetExtraInfo<String>(Path.of(root.getId()+".json"),new AssetExtraInfo.Data(RootInteraction.class,root.getId(),null)));
                RootInteraction.getAssetStore().loadAssets(pack,List.of(replacement));
                if(debug!=null)debug.traceFileOnly(null,"AncientArrow event=resolvedBowRoute cancelled=false root="+root.getId()+" pack="+pack+" nativeFallback=true nativeLeavesUnchanged=true allow=true");
                }catch(Exception failure){if(errors==null)throw new IllegalStateException("Ancient arrow route failed: "+root.getId(),failure);errors.report((com.hypixel.hytale.server.core.universe.PlayerRef)null,"Could not route Ancient Arrows for "+root.getId()+"; original native bow root retained",failure);}
            }
        }finally{patching=false;}
    }
    static synchronized void close(){
        patching=true;
        try{if(RootInteraction.getAssetStore()!=null)for(var entry:originals.entrySet()){
            var current=RootInteraction.getAssetMap().getAsset(entry.getKey());
            if(current!=null&&Arrays.stream(current.getInteractionIds()).anyMatch(id->id.startsWith(PREFIX)))RootInteraction.getAssetStore().loadAssets(entry.getValue().pack,List.of(entry.getValue().root));
        }}finally{originals.clear();patching=false;}
    }
}
