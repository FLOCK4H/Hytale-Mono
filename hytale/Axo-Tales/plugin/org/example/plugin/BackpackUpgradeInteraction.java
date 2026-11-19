package org.example.plugin;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.IncreaseBackpackCapacityInteraction;
import java.util.*;


public final class BackpackUpgradeInteraction extends IncreaseBackpackCapacityInteraction {
    public static final String TYPE="AxoTales_BackpackUpgrade";
    @FunctionalInterface public interface Handler {void apply(InteractionContext context,int from,int target,boolean second);}
    private final Handler handler;
    private BackpackUpgradeInteraction(Handler handler){this.handler=handler;}
    public static BuilderCodec<BackpackUpgradeInteraction> codec(Handler handler) {
        return BuilderCodec.builder(BackpackUpgradeInteraction.class,()->new BackpackUpgradeInteraction(handler),IncreaseBackpackCapacityInteraction.CODEC).build();
    }
    static BackpackUpgradeInteraction wrap(IncreaseBackpackCapacityInteraction original,BuilderCodec<BackpackUpgradeInteraction> codec) {
        var wrapped=codec.decode(IncreaseBackpackCapacityInteraction.CODEC.encode(original,new ExtraInfo()),new ExtraInfo());
        wrapped.id=original.getId();wrapped.data=Interaction.CODEC.getData(original);
        if(wrapped.data==null)wrapped.data=new AssetExtraInfo.Data(Interaction.class,wrapped.id,null);
        return wrapped;
    }
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown) {
        var values=IncreaseBackpackCapacityInteraction.CODEC.encode(this,new ExtraInfo());
        handler.apply(context,values.getNumber("From").intValue(),values.getNumber("Capacity").intValue(),second(id));
    }
    @Override protected void simulateFirstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown) {}
    static boolean second(String id){return id!=null && id.replaceFirst("^\\*+", "").startsWith("Upgrade_Backpack_2_");}
    static boolean standard(String id){return id!=null && id.replaceFirst("^\\*+", "").matches("Upgrade_Backpack_[A-Za-z0-9]+_.*");}


    public static void patch(LoadedAssetsEvent<?,?,?> event,BuilderCodec<BackpackUpgradeInteraction> codec,PluginDebugReporter debug) {
        if(event.getAssetClass()!=Interaction.class)return;
        var batches=new LinkedHashMap<String,List<Interaction>>();
        for(var value:new ArrayList<>(event.getLoadedAssets().values())) {
            if(value instanceof IncreaseBackpackCapacityInteraction original
                    && original.getClass()==IncreaseBackpackCapacityInteraction.class && standard(original.getId())
                    && Interaction.getAssetMap().getAsset(original.getId())==original) {
                String pack=Interaction.getAssetMap().getAssetPack(original.getId());
                batches.computeIfAbsent(pack,k->new ArrayList<>()).add(wrap(original,codec));
            }
        }
        for(var batch:batches.entrySet()) {
            Interaction.getAssetStore().loadAssets(batch.getKey(),batch.getValue());
            if(debug!=null)debug.traceFileOnly(null,"ArcaneBackpack event=resolvedNativeUpgradesPatched pack="+batch.getKey()+" leaves="+batch.getValue().size()+" recipesPreserved=true");
        }
    }
}
