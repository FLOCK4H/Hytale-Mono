package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import java.util.*;


public final class BloodFurnacePage extends InteractiveCustomUIPage<BloodFurnacePage.Input> {
    static final class Input {
        static final BuilderCodec<Input> CODEC=BuilderCodec.builder(Input.class,Input::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(v,x)->v.action=x,v->v.action).add()
            .append(new KeyedCodec<>("@Token",Codec.STRING),(v,x)->v.token=x,v->v.token).add()
            .append(new KeyedCodec<>("@KeyText",Codec.STRING),(v,x)->v.keyText=x,v->v.keyText).add().build();
        String action,token,keyText;
    }
    private record Target(ItemContainer source,short slot,ItemStack expected,boolean machine){}
    private record Frame(double fuel,double progress,String recipe,String status,boolean enabled){}
    final Ref<ChunkStore> machineRef;
    private final BloodFurnaceSystem service;
    private final ItemContainer items;
    private String section="Storage",message="LMB: transfer stack     RMB: transfer one";
    private int page;
    private boolean closing;
    private Map<String,Target> targets=Map.of();
    private List<ItemStack> previous=List.of();
    private Frame previousFrame;
    BloodFurnacePage(PlayerRef owner,BloodFurnaceSystem service,Ref<ChunkStore> ref,ItemContainer items){super(owner,CustomPageLifetime.CanDismissOrCloseThroughInteraction,Input.CODEC);this.service=service;machineRef=ref;this.items=items;}
    UUID owner(){return playerRef.getUuid();}
    void abandon(){closing=true;targets=Map.of();service.release(this);}
    void dismiss(){if(closing)return;abandon();close();}
    @Override public void onDismiss(Ref<EntityStore> ref,Store<EntityStore> store){abandon();}
    boolean attached(Ref<EntityStore> ref,Store<EntityStore> store){
        if(closing||!ref.isValid())return false;
        var owner=store.getComponent(ref,PlayerRef.getComponentType());var machine=service.resolve(machineRef,store.getExternalData().getWorld());
        return !closing&&owner!=null&&owner().equals(owner.getUuid())&&machine!=null&&machine.items()==items&&BloodFurnaceSystem.attached(ref,store,machine);
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder ui,UIEventBuilder events,Store<EntityStore> store){
        ui.append("Pages/AxoBloodFurnace.ui");
        PageKeyboard.bind(events,"#PageKeyboard");
        for(String action:List.of("Close","Toggle","Storage","Backpack","Hotbar","Previous","Next"))events.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action));
        for(String prefix:List.of("Machine","Inventory"))for(int i=0;i<(prefix.equals("Machine")?9:36);i++){
            var data=new EventData().append("Action","Stack").append("@Token","#"+prefix+"Token"+i+".Value");events.addEventBinding(CustomUIEventBindingType.Activating,"#"+prefix+i,data);
            events.addEventBinding(CustomUIEventBindingType.RightClicking,"#"+prefix+i,new EventData().append("Action","One").append("@Token","#"+prefix+"Token"+i+".Value"));
        }
        render(ref,store,ui);progress(store,ui);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Input input){
        if(closing)return;if(!attached(ref,store)){dismiss();return;}
        if("Keyboard".equals(input.action)){if(PageKeyboard.closes(input.keyText))dismiss();else{var ui=new UICommandBuilder();PageKeyboard.reset(ui,"#PageKeyboard");sendUpdate(ui,false);}return;}
        try{
            String action=input.action==null?"":input.action;
            if(action.equals("Close")){dismiss();return;}
            if(List.of("Storage","Backpack","Hotbar").contains(action)){section=action;page=0;}
            else if(action.equals("Previous"))page=Math.max(0,page-1);
            else if(action.equals("Next"))page++;
            else if(action.equals("Toggle")){var machine=service.resolve(machineRef,store.getExternalData().getWorld());machine.state().enabled=!machine.state().enabled;service.dirty(machineRef);service.trace(store,ref,"toggle",true,null,0);}
            else if(action.equals("Stack")||action.equals("One")){
                var target=input.token==null?null:targets.get(input.token);int amount=action.equals("One")?1:Integer.MAX_VALUE,moved=0;
                if(target==null||!Objects.equals(target.expected,target.source.getItemStack(target.slot)))message="That slot changed. Select it again.";
                else if(target.machine){
                    for(var destination:AstralBagService.destinations(store,ref)){moved+=VitaeExtractorPage.move(target.source,target.slot,target.source.getItemStack(target.slot),amount-moved,destination);if(moved>=amount||ItemStack.isEmpty(target.source.getItemStack(target.slot)))break;}
                    message=moved>0?"Collected "+moved+" item"+(moved==1?".":"s."):"Inventory full · output kept in furnace";
                }else if(AstralBagService.destinations(store,ref).contains(target.source)){
                    moved=deposit(target.source,target.slot,target.expected,amount);message=moved>0?"Added "+moved+" item"+(moved==1?".":"s."):"No space for this ingredient";
                }
                if(moved>0)service.dirty(machineRef);service.trace(store,ref,"nativeTransfer",moved>0,target==null?null:target.expected,moved);
            }
        }catch(Throwable failure){message="Transfer failed · items retained";service.errors.report(playerRef,"Blood Furnace transfer failed",failure);}
        if(!closing){var ui=new UICommandBuilder();render(ref,store,ui);progress(store,ui);PageKeyboard.focus(ui,"#PageKeyboard");sendUpdate(ui,false);}
    }
    private int deposit(ItemContainer source,short slot,ItemStack expected,int amount){
        boolean fuel=LeechBlood.blood(expected.getItemId());if(!fuel&&!service.ingredient(expected))return 0;
        int moved=0;for(short dest=(short)(fuel?0:1);dest<(fuel?1:5);dest++){
            var existing=items.getItemStack(dest);var current=source.getItemStack(slot);if(ItemStack.isEmpty(current)||moved>=amount)break;
            if(!ItemStack.isEmpty(existing)&&!existing.withQuantity(1).equals(current.withQuantity(1)))continue;
            int before=current.getQuantity();source.moveItemStackFromSlotToSlot(slot,Math.min(amount-moved,before),items,dest);var after=source.getItemStack(slot);moved+=before-(ItemStack.isEmpty(after)?0:after.getQuantity());
        }
        return moved;
    }
    private ItemContainer inventory(Store<EntityStore> store,Ref<EntityStore> ref){return switch(section){case "Backpack"->InventoryComponentAccess.backpack(store,ref);case "Hotbar"->InventoryComponentAccess.hotbar(store,ref);default->InventoryComponentAccess.storage(store,ref);};}
    private List<ItemStack> contents(Store<EntityStore> store,Ref<EntityStore> ref){var result=new ArrayList<ItemStack>();for(short i=0;i<9;i++)result.add(items.getItemStack(i));var inventory=inventory(store,ref);if(inventory!=null)for(short i=0;i<inventory.getCapacity();i++)result.add(inventory.getItemStack(i));return result;}
    void refresh(Ref<EntityStore> ref,Store<EntityStore> store){var machine=service.resolve(machineRef,store.getExternalData().getWorld());if(machine==null)return;
        var state=machine.state();var frame=new Frame(state.fuel,state.progress,state.recipe,state.status,state.enabled);var current=contents(store,ref);
        if(current.equals(previous)&&frame.equals(previousFrame))return;var ui=new UICommandBuilder();if(!current.equals(previous))render(ref,store,ui);progress(store,ui);sendUpdate(ui,false);
    }
    private void render(Ref<EntityStore> ref,Store<EntityStore> store,UICommandBuilder ui){
        var mapping=new HashMap<String,Target>();for(short i=0;i<9;i++)draw(ui,"Machine",i,items.getItemStack(i),new Target(items,i,items.getItemStack(i),true),mapping);
        var inventory=inventory(store,ref);int capacity=inventory==null?0:inventory.getCapacity(),pages=Math.max(1,(capacity+35)/36);page=Math.clamp(page,0,pages-1);
        for(int i=0;i<36;i++){int slot=page*36+i;var item=slot<capacity?inventory.getItemStack((short)slot):null;boolean accepted=!ItemStack.isEmpty(item)&&(LeechBlood.blood(item.getItemId())||service.ingredient(item));draw(ui,"Inventory",i,item,accepted?new Target(inventory,(short)slot,item,false):null,mapping);}
        ui.set("#InventoryLabel.Text",section+"  /  "+(page+1)+" of "+pages);ui.set("#Previous.Disabled",page==0);ui.set("#Next.Disabled",page==pages-1);ui.set("#Status.Text",message);
        for(String tab:List.of("Storage","Backpack","Hotbar"))ui.set("#Active"+tab+".Visible",section.equals(tab));targets=Map.copyOf(mapping);previous=contents(store,ref);
    }
    private void draw(UICommandBuilder ui,String prefix,int i,ItemStack item,Target target,Map<String,Target> mapping){
        String id="#"+prefix+i,icon="#"+prefix+"Item"+i;boolean empty=ItemStack.isEmpty(item);
        if(empty)ui.setNull(icon+".ItemId");else ui.set(icon+".ItemId",item.getItemId());ui.set(icon+".Quantity",empty?0:item.getQuantity());
        boolean enabled=target!=null&&!empty;ui.set(id+".Disabled",!enabled);ui.set(id+".TooltipText",empty?"":AstralBagPage.detail(item,playerRef.getLanguage())+"\nLMB: stack  /  RMB: one");
        String token=enabled?UUID.randomUUID().toString():"";if(enabled)mapping.put(token,target);ui.set("#"+prefix+"Token"+i+".Value",token);
    }
    private void progress(Store<EntityStore> store,UICommandBuilder ui){
        var machine=service.resolve(machineRef,store.getExternalData().getWorld());if(machine==null)return;var state=machine.state();var recipe=com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe.getAssetMap().getAsset(state.recipe);
        double duration=recipe==null?0:recipe.getTimeSeconds();int seconds=(int)Math.ceil(state.fuel);
        ui.set("#Status.Text",state.status.isBlank()?message:state.status+"  |  "+message);ui.set("#FuelTime.Text",String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60));ui.set("#Toggle.Text",state.enabled?"Pause":"Resume");
        width(ui,"#SmeltFill",(int)Math.round(360*(duration>0?Math.clamp(state.progress/duration,0,1):0)));width(ui,"#FuelFill",(int)Math.round(108*Math.clamp(state.fuel/BloodFurnaceState.BLOOD_SECONDS,0,1)));
        previousFrame=new Frame(state.fuel,state.progress,state.recipe,state.status,state.enabled);
    }
    private static void width(UICommandBuilder ui,String selector,int width){var anchor=new Anchor();anchor.setLeft(Value.of(0));anchor.setTop(Value.of(0));anchor.setHeight(Value.of(7));anchor.setWidth(Value.of(width));ui.setObject(selector+".Anchor",anchor);}
}
