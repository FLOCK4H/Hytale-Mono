package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;


public final class ArcaneRobeDamageSystem extends DamageEventSystem {
    private final PluginDebugReporter debug;
    public ArcaneRobeDamageSystem(PluginDebugReporter debug){this.debug=debug;}
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
    @Override public @Nonnull Query<EntityStore> getQuery(){return Player.getComponentType();}
    @Override public void handle(int index,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer,@Nonnull Damage damage) {
        var ref=chunk.getReferenceTo(index);
        boolean relic=ArcaneArmorSets.sar(buffer,ref),robe=ArcaneArmorSets.robe(buffer,ref);
        if(damage.isCancelled() || damage.getAmount()<=0 || (!relic && !robe))return;
        if(damage.getSource() instanceof BloodBookCurseSystem.CurseSource)return;
        var cause=DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        if(cause!=null && ("Command".equalsIgnoreCase(cause.getId()) || "OutOfWorld".equalsIgnoreCase(cause.getId())))return;
        float before=damage.getAmount();damage.setAmount(before*remainingDamage(relic));
        debug.traceFileOnly(store.getComponent(ref,PlayerRef.getComponentType()),
            "ArcaneArmor event=Damage cancelled=false itemSource=serverArmorSlots allow=true robeFullSet="+robe+" relicFullSet="+relic+" absorbPercent="+(relic?12.5:20)
                +" damage.before="+before+" damage.after="+damage.getAmount());
    }
    static float remainingDamage(boolean sar){return sar?.875f:.8f;}
}
