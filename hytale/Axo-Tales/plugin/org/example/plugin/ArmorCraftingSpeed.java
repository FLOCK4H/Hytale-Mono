package org.example.plugin;

import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.system.PlayerCraftingSystems;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;


final class ArmorCraftingSpeed extends TickingSystem<EntityStore> {
    static float bonus(boolean sar,boolean veil,boolean ancient){return ancient?3:sar?1:veil?.5f:0;}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,PlayerCraftingSystems.CraftingTickingSystem.class));}
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        if(!Float.isFinite(delta)||delta<=0)return;
        store.forEachChunk(Query.and(Player.getComponentType(),CraftingManager.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var ref=chunk.getReferenceTo(i);if(!ArcanePickaxe.living(buffer,ref))continue;
                float bonus=bonus(ArcaneArmorSets.sar(buffer,ref),ArcaneArmorSets.robe(buffer,ref),ArcaneArmorSets.ancient(buffer,ref));
                var manager=chunk.getComponent(i,CraftingManager.getComponentType());
                if(bonus>0&&manager.hasBenchSet())manager.tick(ref,buffer,delta*bonus);
            }
        });
    }
}
