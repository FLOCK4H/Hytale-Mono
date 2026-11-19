package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import javax.annotation.Nonnull;


public final class ArcaneBackpackSystem extends TickingSystem<EntityStore> {
    private final ComponentType<EntityStore,ArcaneRobeInventoryState> stateType;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final Map<Store<EntityStore>,Long> sweeps=Collections.synchronizedMap(new WeakHashMap<>());
    public ArcaneBackpackSystem(ComponentType<EntityStore,ArcaneRobeInventoryState> stateType,PluginDebugReporter debug,PluginErrorReporter errors) {
        this.stateType=stateType;this.debug=debug;this.errors=errors;
    }
    @Override public void tick(float delta,int count,@Nonnull Store<EntityStore> store) {
        long now=System.nanoTime();if(sweeps.getOrDefault(store,0L)>now)return;sweeps.put(store,now+250_000_000L);
        store.forEachChunk(Query.and(PlayerRef.getComponentType(),InventoryComponent.Backpack.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var ref=chunk.getReferenceTo(i);
                buffer.run(accessor->{if(ref.isValid())refresh(accessor,ref);});
            }
        });
    }
    private void refresh(Store<EntityStore> store,Ref<EntityStore> ref) {
        var player=store.getComponent(ref,PlayerRef.getComponentType());
        try {
            var state=store.getComponent(ref,stateType);


            boolean legacyRobe=state!=null && state.backpackBase<0;
            if(state==null)state=store.addComponent(ref,stateType);
            var backpack=store.getComponent(ref,InventoryComponent.Backpack.getComponentType());
            boolean worn=ArcaneArmorSets.robe(store,ref);
            boolean relic=ArcaneArmorSets.relic(store,ref);
            int earned=ArcaneArmorSets.ancient(store,ref)?27:relic?18:(worn || legacyRobe || state.grantedCapacity>0)?9:0;
            int before=state.armorSlots>0?state.armorSlots:state.robeUnlocked?9:0;
            boolean changed=reconcileArmor(backpack,state,earned);
            var storage=store.getComponent(ref,InventoryComponent.Storage.getComponentType());
            String migration=storage==null?"none":migrate(storage,backpack,state);
            changed|=!migration.equals("none") && !migration.equals("deferred");
            if(changed) {
                store.putComponent(ref,stateType,(ArcaneRobeInventoryState)state.clone());
                debug.traceFileOnly(player,"ArcaneBackpack event=equipmentRefresh cancelled=false itemSource=serverArmorSlots fullSet="+worn
                    +" relicFullSet="+relic+" armorBonusSlots="+state.armorSlots+" decision=preserveNativeBackpack base="+state.backpackBase+" bonus="+state.backpackBonus+" capacity="+capacity(backpack)+" migration="+migration);
                if(state.armorSlots>before)player.sendMessage(Message.raw("Armor attunement: permanently unlocked "+state.armorSlots+" bonus backpack slots. Open Backpack from Inventory."));
            }
        } catch(Throwable failure){errors.report(player,"Arcane backpack refresh failed; inventory retained.",failure);}
    }
    static int capacity(InventoryComponent.Backpack backpack){return backpack.getInventory()==null?0:backpack.getInventory().getCapacity();}
    static boolean initializeExisting(InventoryComponent.Backpack backpack,ArcaneRobeInventoryState state,int ignoredFrom,int ignoredTarget) {
        if(state.backpackBase>=0)return false;
        state.backpackBase=capacity(backpack);return true;
    }
    static boolean reconcile(InventoryComponent.Backpack backpack,ArcaneRobeInventoryState state,boolean worn) {
        return reconcileArmor(backpack,state,worn?9:0);
    }

    static boolean reconcileArmor(InventoryComponent.Backpack backpack,ArcaneRobeInventoryState state,int earned) {
        int current=capacity(backpack);boolean changed=initializeExisting(backpack,state,0,0);
        if(state.armorSlots==0 && state.robeUnlocked){state.armorSlots=9;changed=true;}
        if(current!=state.backpackBase+state.backpackBonus) {
            state.backpackBase=Math.max(0,current-state.backpackBonus);changed=true;
            if(current<state.backpackBonus)state.backpackBonus=current;
        }


        if(state.upgradeTwoPadding>0 && removePadding(backpack,state))changed=true;
        int desired=Math.max(state.armorSlots,Math.max(earned,state.grantedCapacity>0?9:0));
        if(desired>state.armorSlots && capacity(backpack)<=Short.MAX_VALUE-(desired-state.armorSlots)) {
            int extra=desired-state.armorSlots;resize(backpack,capacity(backpack)+extra);
            state.armorSlots=desired;state.robeUnlocked=true;state.backpackBonus+=extra;changed=true;
        }
        return changed;
    }
    static boolean removePadding(InventoryComponent.Backpack backpack,ArcaneRobeInventoryState state) {
        int padding=state.upgradeTwoPadding,current=capacity(backpack),target=current-padding;
        if(padding<=0 || target<0)return false;
        if(padding>state.backpackBonus){state.upgradeTwoPadding=0;return true;}
        var items=backpack.getInventory();var overflow=new ArrayList<Short>();var empty=new ArrayList<Short>();
        for(short i=0;i<target;i++)if(ItemStack.isEmpty(items.getItemStack(i)))empty.add(i);
        for(short i=(short)target;i<current;i++)if(!ItemStack.isEmpty(items.getItemStack(i)))overflow.add(i);
        if(empty.size()<overflow.size())return false;
        int moved=0;var originals=new ArrayList<ItemStack>();
        try {
            for(int i=0;i<overflow.size();i++) {
                var item=items.getItemStack(overflow.get(i));originals.add(item);
                if(!items.setItemStackForSlot(empty.get(i),item,false).succeeded())throw new IllegalStateException("Backpack compaction rejected");
                moved++;
                if(!items.setItemStackForSlot(overflow.get(i),null,false).succeeded())throw new IllegalStateException("Backpack compaction removal rejected");
            }
        }catch(Throwable failure){for(int i=0;i<moved;i++){items.setItemStackForSlot(empty.get(i),null,false);items.setItemStackForSlot(overflow.get(i),originals.get(i),false);}throw failure;}
        var excess=new ArrayList<ItemStack>();backpack.resize((short)target,excess);
        if(!excess.isEmpty())throw new IllegalStateException("Empty padding returned overflow");
        state.backpackBonus-=padding;state.upgradeTwoPadding=0;backpack.markDirty();return true;
    }

    static String upgrade(InventoryComponent.Backpack backpack,ArcaneRobeInventoryState state,int from,int target,boolean second) {
        reconcile(backpack,state,false);
        if(state.backpackBase!=from)return state.backpackBase>=target?"alreadyApplied":"previousUpgradeRequired";
        int desired=capacity(backpack)+(target-from);
        if(target<=from || desired>Short.MAX_VALUE)return "invalidCapacity";
        resize(backpack,desired);state.backpackBase=target;return "upgraded";
    }
    static void resize(InventoryComponent.Backpack backpack,int target) {
        var overflow=new ArrayList<ItemStack>();backpack.resize((short)target,overflow);
        if(!overflow.isEmpty())throw new IllegalStateException("Backpack growth returned overflow");
        backpack.markDirty();
    }

    static String migrate(InventoryComponent.Storage storage,InventoryComponent.Backpack backpack,ArcaneRobeInventoryState state) {
        if(state.grantedCapacity==0)return "none";
        if(storage.getInventory().getCapacity()!=state.grantedCapacity || state.baseCapacity<0 || state.grantedCapacity-state.baseCapacity!=9) {
            state.baseCapacity=0;state.grantedCapacity=0;state.foreignResize=true;return "foreignStoragePreserved";
        }
        var source=storage.getInventory();var dest=backpack.getInventory();
        var occupied=new ArrayList<Short>();var empty=new ArrayList<Short>();
        for(short i=(short)state.baseCapacity;i<state.grantedCapacity;i++)if(!ItemStack.isEmpty(source.getItemStack(i)))occupied.add(i);
        for(short i=0;i<dest.getCapacity();i++)if(ItemStack.isEmpty(dest.getItemStack(i)))empty.add(i);
        if(empty.size()<occupied.size())return "deferred";
        var originals=new ArrayList<ItemStack>();int moved=0;
        try {
            for(int i=0;i<occupied.size();i++) {
                ItemStack item=source.getItemStack(occupied.get(i));originals.add(item);
                if(!dest.setItemStackForSlot(empty.get(i),item,false).succeeded())throw new IllegalStateException("Backpack transfer rejected");
                moved++;
                if(!source.setItemStackForSlot(occupied.get(i),null,false).succeeded())throw new IllegalStateException("Legacy row removal rejected");
            }
        } catch(Throwable failure) {
            for(int i=0;i<moved;i++){dest.setItemStackForSlot(empty.get(i),null,false);source.setItemStackForSlot(occupied.get(i),originals.get(i),false);}
            throw failure;
        }
        var overflow=new ArrayList<ItemStack>();storage.ensureCapacity((short)state.baseCapacity,overflow);
        if(!overflow.isEmpty())throw new IllegalStateException("Empty legacy row returned overflow");
        if(source.getCapacity()>0)storage.getInventory().setItemStackForSlot((short)0,storage.getInventory().getItemStack((short)0),false);
        storage.markDirty();backpack.markDirty();state.baseCapacity=0;state.grantedCapacity=0;return "legacyRowMovedToBackpack";
    }

    public void onUpgrade(InteractionContext context,int from,int target,boolean second) {
        var buffer=context.getCommandBuffer();var ref=context.getEntity();
        var player=buffer.getComponent(ref,PlayerRef.getComponentType());
        var backpack=buffer.getComponent(ref,InventoryComponent.Backpack.getComponentType());
        var state=buffer.getComponent(ref,stateType);boolean legacyRobe=state!=null && state.backpackBase<0;
        if(state==null){state=new ArcaneRobeInventoryState();buffer.putComponent(ref,stateType,state);}
        var held=context.getHeldItem();String decision="missingBackpack";
        if(backpack!=null && !ItemStack.isEmpty(held)) {
            initializeExisting(backpack,state,0,0);
            reconcile(backpack,state,legacyRobe);
            if(state.backpackBase!=from)decision=state.backpackBase>=target?"alreadyApplied":"previousUpgradeRequired";
            else if(target<=from || capacity(backpack)+target-from>Short.MAX_VALUE)decision="invalidCapacity";
            else {
                var items=context.getHeldItemContainer();short slot=context.getHeldItemSlot();
                var taken=items.removeItemStackFromSlot(slot,held,1);
                if(!taken.succeeded())decision="itemRemovalRejected";
                else try {decision=upgrade(backpack,state,from,target,second);}
                catch(Throwable failure){items.setItemStackForSlot(slot,held,false);throw failure;}
            }
            buffer.putComponent(ref,stateType,(ArcaneRobeInventoryState)state.clone());
        }
        boolean allow=decision.equals("upgraded");if(!allow)context.getState().state=InteractionState.Failed;
        if(player!=null)player.sendMessage(Message.raw(allow?"Backpack upgraded: "+capacity(backpack)+" slots.":decision.equals("previousUpgradeRequired")?"Apply the previous backpack upgrade first.":"This backpack upgrade has already been applied or cannot be used."));
        debug.traceFileOnly(player,"ArcaneBackpack event=nativeUpgrade cancelled=false itemSource=interactionHeldItem item="+(held==null?"none":held.getItemId())
            +" from="+from+" target="+target+" allow="+allow+" decision="+decision+" base="+state.backpackBase+" bonus="+state.backpackBonus+" mana=notUsed health=notUsed");
    }
}
