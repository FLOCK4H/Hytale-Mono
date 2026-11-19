package org.example.plugin;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.*;
import java.util.function.*;


final class BloodRitualBatch {
    static <T> void commit(List<BloodMagicSystem.Vial> vials,IntFunction<T> spawn,Consumer<T> remove,Runnable spendMana) {
        if(vials.isEmpty())return;
        var created=new ArrayList<T>();var consumed=new ArrayList<BloodMagicSystem.Vial>();
        try {
            for(int i=0;i<vials.size();i++){T entity=spawn.apply(i);if(entity==null)throw new IllegalStateException("Companion creation failed");created.add(entity);}
            for(var vial:vials) {
                if(!vial.container().removeItemStackFromSlot(vial.slot(),vial.item(),1).succeeded())throw new IllegalStateException("DNA transaction rejected");
                consumed.add(vial);
            }
            spendMana.run();
        }catch(RuntimeException failure) {
            for(T entity:created)try{remove.accept(entity);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            var restored=new IdentityHashMap<ItemContainer,Set<Short>>();
            for(var vial:consumed)if(restored.computeIfAbsent(vial.container(),k->new HashSet<>()).add(vial.slot())) {
                if(!vial.container().setItemStackForSlot(vial.slot(),vial.item(),false).succeeded())failure.addSuppressed(new IllegalStateException("DNA restoration rejected"));
            }
            throw failure;
        }
    }
}
