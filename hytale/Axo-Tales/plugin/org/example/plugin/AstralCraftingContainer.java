package org.example.plugin;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import com.hypixel.hytale.server.core.inventory.container.filter.*;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;
import java.util.*;
import java.util.function.*;


final class AstralCraftingContainer extends ItemContainer {
    private record Part(AstralVault.Vault vault,UUID entry){}
    private final Supplier<List<AstralVault.Vault>> carried;
    private final Supplier<List<ItemContainer>> nativeSources;
    private final Runnable changed;
    private final Consumer<Throwable> reportFailure;
    private final List<List<Part>> parts=new ArrayList<>();
    private final List<ItemStack> slots=new ArrayList<>();
    private final Map<AstralVault.Vault,AstralVault.Snapshot> originals=new LinkedHashMap<>();
    private final Map<ItemContainer,List<ItemStack>> inventoryOriginals=new LinkedHashMap<>();
    private int depth;
    private boolean writing,failed,capacityReported;

    AstralCraftingContainer(Supplier<List<AstralVault.Vault>> carried,Supplier<List<ItemContainer>> nativeSources,Runnable changed){
        this(carried,nativeSources,changed,failure->{});
    }
    AstralCraftingContainer(Supplier<List<AstralVault.Vault>> carried,Supplier<List<ItemContainer>> nativeSources,Runnable changed,Consumer<Throwable> reportFailure){
        this.carried=carried;this.nativeSources=nativeSources;this.changed=changed;this.reportFailure=reportFailure;
    }
    private void prepare(){
        slots.clear();parts.clear();originals.clear();inventoryOriginals.clear();writing=false;failed=false;
        int reserved=0;for(var source:nativeSources.get())reserved=Math.addExact(reserved,source.getCapacity());
        int capacity=Math.max(0,Short.MAX_VALUE-reserved);
        var indices=new LinkedHashMap<org.bson.BsonDocument,Integer>();
        for(var vault:carried.get()){
            if(originals.containsKey(vault))continue;var snapshot=vault.snapshot();originals.put(vault,snapshot);
            for(var entry:snapshot.entries()){
                var item=entry.item();if(com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(item.getItemId())==null)continue;
                var prototype=ItemStack.CODEC.encode(item.withQuantity(1),new com.hypixel.hytale.codec.ExtraInfo());Integer index=indices.get(prototype);
                if(index==null){
                    if(slots.size()>=capacity){
                        slots.clear();parts.clear();originals.clear();failed=true;
                        if(!capacityReported){capacityReported=true;reportFailure.accept(new IllegalStateException("Native workbench ingredient view exceeds its short slot range; Satchel ingredients unavailable, contents retained"));}
                        return;
                    }
                    index=slots.size();indices.put(prototype,index);slots.add(item.withQuantity(1));parts.add(new ArrayList<>());
                }
                var list=parts.get(index);long old=list.isEmpty()?0:slots.get(index).getQuantity();
                slots.set(index,item.withQuantity((int)Math.min(Integer.MAX_VALUE,old+Math.min(Integer.MAX_VALUE,entry.count()))));list.add(new Part(vault,entry.id()));
            }
        }
    }
    @Override protected void lockForRead(){
        AstralVault.TRANSACTIONS.lock();try{if(depth++==0)prepare();}catch(Throwable failure){depth--;AstralVault.TRANSACTIONS.unlock();throw failure;}
    }
    @Override protected void unlockForRead(){if(--depth==0){originals.clear();inventoryOriginals.clear();writing=false;}AstralVault.TRANSACTIONS.unlock();}
    @Override protected void lockForWrite(){
        lockForRead();try{if(!writing){
            writing=true;for(var source:nativeSources.get())if(!inventoryOriginals.containsKey(source)){var before=new ArrayList<ItemStack>();for(short i=0;i<source.getCapacity();i++)before.add(source.getItemStack(i));inventoryOriginals.put(source,before);}
        }}catch(Throwable failure){unlockForRead();throw failure;}
    }


    @Override protected void unlockForWrite(){unlockForRead();}
    @Override protected <V> V readAction(Supplier<V> action){lockForRead();try{return action.get();}finally{unlockForRead();}}
    @Override protected <X,V> V readAction(Function<X,V> action,X value){return readAction(()->action.apply(value));}
    @Override protected <V> V writeAction(Supplier<V> action){lockForWrite();try{return action.get();}catch(Throwable failure){rollback(failure);throw failure;}finally{unlockForWrite();}}
    @Override protected <X,V> V writeAction(Function<X,V> action,X value){return writeAction(()->action.apply(value));}
    @Override public short getCapacity(){return readAction(()->(short)slots.size());}
    @Override protected ItemStack internal_getSlot(short slot){return slots.get(slot);}
    @Override protected ItemStack internal_setSlot(short slot,ItemStack after){
        var before=slots.get(slot);int remaining=ItemStack.isEmpty(after)?0:after.getQuantity();
        if(!writing||failed||remaining>(ItemStack.isEmpty(before)?0:before.getQuantity())||!ItemStack.isEmpty(after)&&!after.withQuantity(1).equals(before.withQuantity(1)))throw new IllegalStateException("Crafting view accepts ingredient removals only");
        int needed=before.getQuantity()-remaining;if(needed==0)return before;
        try{
            if(!new HashSet<>(carried.get()).containsAll(originals.keySet()))throw new IllegalStateException("A crafting Satchel is no longer carried");
            var removals=new LinkedHashMap<AstralVault.Vault,Map<UUID,Long>>();
            for(var part:parts.get(slot)){
                var entry=part.vault.snapshot().entries().stream().filter(e->e.id().equals(part.entry)).findFirst().orElse(null);if(entry==null)continue;
                long take=Math.min(needed,entry.count());removals.computeIfAbsent(part.vault,k->new LinkedHashMap<>()).put(part.entry,take);needed-=take;if(needed==0)break;
            }
            if(needed!=0)throw new IllegalStateException("Crafting Satchel contents changed");
            for(var removal:removals.entrySet())removal.getKey().consume(removal.getValue());
            slots.set(slot,ItemStack.isEmpty(after)?null:after);changed.run();return before;
        }catch(Throwable failure){rollback(failure);try{reportFailure.accept(failure);}catch(Throwable reporting){failure.addSuppressed(reporting);}throw new IllegalStateException("Satchel crafting stopped; ingredients restored",failure);}
    }
    private void rollback(Throwable failure){
        if(!writing||failed)return;failed=true;
        for(var original:originals.entrySet())if(original.getKey().snapshot().revision()!=original.getValue().revision())try{original.getKey().restoreSnapshot(original.getValue());}catch(Throwable restore){failure.addSuppressed(restore);}
        for(var inventory:inventoryOriginals.entrySet())for(short i=0;i<inventory.getValue().size();i++)if(!inventory.getKey().setItemStackForSlot(i,inventory.getValue().get(i),false).succeeded())failure.addSuppressed(new IllegalStateException("Native crafting inventory rollback rejected"));
    }
    @Override protected ItemStack internal_removeSlot(short slot){return internal_setSlot(slot,null);}
    @Override protected ClearTransaction internal_clear(){throw new UnsupportedOperationException("Crafting view cannot be cleared");}
    @Override protected boolean cantAddToSlot(short slot,ItemStack item,ItemStack existing){return true;}
    @Override protected boolean cantRemoveFromSlot(short slot){return failed;}
    @Override protected boolean cantDropFromSlot(short slot){return true;}
    @Override protected boolean cantMoveToSlot(ItemContainer source,short slot){return true;}
    @Override public void setGlobalFilter(FilterType filter){throw new UnsupportedOperationException("Fixed crafting policy");}
    @Override public void setSlotFilter(FilterActionType action,short slot,SlotFilter filter){throw new UnsupportedOperationException("Fixed crafting policy");}
    @Override public ItemContainer clone(){return readAction(()->{var copy=new SimpleItemContainer((short)slots.size());for(short i=0;i<slots.size();i++)copy.setItemStackForSlot(i,slots.get(i),false);return copy;});}
}
