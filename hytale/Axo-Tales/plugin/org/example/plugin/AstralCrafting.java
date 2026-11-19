package org.example.plugin;

import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialExtraResourcesSection;
import com.hypixel.hytale.server.core.inventory.container.*;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.*;


final class AstralCrafting {


    private static final Field SECTION=sectionField();
    private final AstralBagService service;
    private final AstralVault repository;
    private final Set<Object> unavailable=Collections.newSetFromMap(new WeakHashMap<>());
    AstralCrafting(AstralBagService service,AstralVault repository){this.service=service;this.repository=repository;}
    private static Field sectionField(){try{var field=BenchWindow.class.getDeclaredField("extraResourcesSection");field.setAccessible(true);return field;}catch(ReflectiveOperationException failure){throw new IllegalStateException("Unsupported native workbench resource section",failure);}}
    static Section install(BenchWindow window,Supplier<List<AstralVault.Vault>> carried,Supplier<List<ItemContainer>> inventory,Runnable changed) throws IllegalAccessException {
        return install(window,carried,inventory,changed,failure->{});
    }
    static Section install(BenchWindow window,Supplier<List<AstralVault.Vault>> carried,Supplier<List<ItemContainer>> inventory,Runnable changed,Consumer<Throwable> reportFailure) throws IllegalAccessException {
        var previous=(MaterialExtraResourcesSection)SECTION.get(window);if(previous instanceof Section existing)return existing;
        var section=new Section(previous,carried,inventory,changed,reportFailure);SECTION.set(window,section);
        window.invalidateExtraResources();return section;
    }
    List<AstralVault.Vault> carried(Store<EntityStore> store,Ref<EntityStore> player){
        if(!player.isValid()||player.getStore()!=store||store.getComponent(player,DeathComponent.getComponentType())!=null||store.getComponent(player,Spectating.getComponentType())!=null)return List.of();
        var values=new LinkedHashMap<UUID,AstralVault.Vault>();
        for(var source:SpellbookPouchService.inventorySections(store,player))for(short slot=0;slot<source.getCapacity();slot++){
            var id=AstralBagService.identity(source.getItemStack(slot));if(id==null||values.containsKey(id))continue;
            try{values.put(id,repository.open(id));}catch(Exception failure){if(unavailable.add(id))service.errors.report(store.getComponent(player,PlayerRef.getComponentType()),"Astral Satchel crafting could not read saved ingredients; contents retained.",failure);}
        }
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }
    void refresh(Store<EntityStore> store,Ref<EntityStore> player,Player entity){
        for(var window:entity.getWindowManager().getWindows())if(window instanceof BenchWindow bench&&!unavailable.contains(window))try{
            var current=(MaterialExtraResourcesSection)SECTION.get(bench);var bags=carried(store,player);
            if(bags.isEmpty()&&!(current instanceof Section))continue;
            var section=install(bench,()->carried(store,player),()->AstralBagService.destinations(store,player),()->{
                bench.invalidateExtraResources();
                service.traceAction(store,player,"craftIngredients",true,"itemSource=carriedSatchelUUID nativeWindow="+bench.getId());
            },failure->service.errors.report(store.getComponent(player,PlayerRef.getComponentType()),"Astral Satchel could not save crafting ingredient use; recipe stopped and rollback attempted.",failure));
            String signature=bags.stream().map(v->v.id+":"+v.snapshot().revision()).reduce("",(a,b)->a+"/"+b);
            if(!signature.equals(section.signature)){section.signature=signature;bench.invalidateExtraResources();service.traceAction(store,player,"craftAvailability",!bags.isEmpty(),"itemSource=carriedSatchelUUID nativeWindow="+bench.getId()+" nativeType="+bench.getType()+" distinctBags="+bags.size());}
        }catch(Throwable failure){unavailable.add(window);service.errors.report(store.getComponent(player,PlayerRef.getComponentType()),"Astral Satchel workbench integration failed; native workbench retained.",failure);}
    }
    static final class Section extends MaterialExtraResourcesSection {
        final MaterialExtraResourcesSection nativeSection;
        private final Supplier<List<AstralVault.Vault>> carried;
        private final Supplier<List<ItemContainer>> inventory;
        private final Runnable changed;
        private final Consumer<Throwable> reportFailure;
        private ItemContainer previousNative,combined;
        private AstralCraftingContainer bridge;
        String signature;
        Section(MaterialExtraResourcesSection nativeSection,Supplier<List<AstralVault.Vault>> carried,Supplier<List<ItemContainer>> inventory,Runnable changed,Consumer<Throwable> reportFailure){this.nativeSection=nativeSection;this.carried=carried;this.inventory=inventory;this.changed=changed;this.reportFailure=reportFailure;}
        @Override public boolean isValid(){return nativeSection.isValid();}
        @Override public void setValid(boolean value){nativeSection.setValid(value);}
        @Override public void setItemContainer(ItemContainer value){nativeSection.setItemContainer(value);}
        @Override public void setExtraMaterials(ItemQuantity[] value){nativeSection.setExtraMaterials(value);}
        @Override public ItemContainer getItemContainer(){
            var original=nativeSection.getItemContainer();if(original==null)original=EmptyItemContainer.INSTANCE;
            if(original!=previousNative||combined==null){
                previousNative=original;var sources=new ArrayList<>(inventory.get());sources.add(original);var exactSources=List.copyOf(sources);
                bridge=new AstralCraftingContainer(carried,()->exactSources,changed,reportFailure);combined=new CombinedItemContainer(original,bridge);
            }
            return combined;
        }
        @Override public ExtraResources toPacket(){
            getItemContainer();var quantities=new LinkedHashMap<String,Long>();var packet=nativeSection.toPacket();
            if(packet!=null&&packet.resources!=null)for(var resource:packet.resources)quantities.merge(resource.itemId,(long)resource.quantity,(a,b)->Math.min(Integer.MAX_VALUE,a+b));
            bridge.forEach((slot,item)->{if(item!=null)quantities.merge(item.getItemId(),(long)item.getQuantity(),(a,b)->Math.min(Integer.MAX_VALUE,a+b));});
            return new ExtraResources(quantities.entrySet().stream().map(e->new ItemQuantity(e.getKey(),e.getValue().intValue())).toArray(ItemQuantity[]::new));
        }
    }
}
