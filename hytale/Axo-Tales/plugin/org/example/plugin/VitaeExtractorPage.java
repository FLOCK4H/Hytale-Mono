package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import java.util.*;
import javax.annotation.Nonnull;


public final class VitaeExtractorPage extends InteractiveCustomUIPage<VitaeExtractorPage.Input> {
    public static final class Input {
        static final BuilderCodec<Input> CODEC=BuilderCodec.builder(Input.class,Input::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(v,x)->v.action=x,v->v.action).add()
            .append(new KeyedCodec<>("@Token",Codec.STRING),(v,x)->v.token=x,v->v.token).add()
            .append(new KeyedCodec<>("@KeyText",Codec.STRING),(v,x)->v.keyText=x,v->v.keyText).add().build();
        String action,token,keyText;
    }
    private record Target(ItemContainer source,short slot,ItemStack expected,boolean machine){}
    final Ref<ChunkStore> machineRef;
    private final VitaeExtractorSystem service;
    private final ItemContainer items;
    private Map<String,Target> targets=Map.of();
    private String section="Storage",message="LMB: transfer stack     RMB: transfer one";
    private int inventoryPage;
    private boolean closing;
    private List<ItemStack> previous=List.of();
    private VitaeExtractorSystem.Progress lastProgress;
    VitaeExtractorPage(PlayerRef owner,VitaeExtractorSystem service,Ref<ChunkStore> ref,ItemContainer items){super(owner,CustomPageLifetime.CanDismissOrCloseThroughInteraction,Input.CODEC);this.service=service;this.machineRef=ref;this.items=items;}
    UUID owner(){return playerRef.getUuid();}
    void abandon(){closing=true;targets=Map.of();service.release(this);}
    void dismiss(){if(closing)return;abandon();close();}
    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store){abandon();}
    boolean attached(Ref<EntityStore> ref,Store<EntityStore> store){
        if(closing||!ref.isValid())return false;var actor=store.getComponent(ref,PlayerRef.getComponentType());if(actor==null||!owner().equals(actor.getUuid()))return false;
        var machine=VitaeExtractorSystem.resolve(machineRef,store.getExternalData().getWorld());return machine!=null&&machine.items()==items&&VitaeExtractorSystem.attached(ref,store,machine);
    }
    @Override public void build(@Nonnull Ref<EntityStore> ref,@Nonnull UICommandBuilder ui,@Nonnull UIEventBuilder events,@Nonnull Store<EntityStore> store){
        ui.append("Pages/AxoVitaeExtractor.ui");
        PageKeyboard.bind(events,"#PageKeyboard");
        for(String action:List.of("Close","Storage","Backpack","Hotbar","Previous","Next"))events.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,new EventData().append("Action",action));
        for(String prefix:List.of("Machine","Inventory"))for(int i=0;i<(prefix.equals("Machine")?9:36);i++){
            String selector="#"+prefix+i,token="#"+prefix+"Token"+i+".Value";
            events.addEventBinding(CustomUIEventBindingType.Activating,selector,new EventData().append("Action","Stack").append("@Token",token));
            events.addEventBinding(CustomUIEventBindingType.RightClicking,selector,new EventData().append("Action","One").append("@Token",token));
        }
        render(ref,store,ui);renderProgress(ui);
    }
    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store,Input input){
        if(closing)return;if(!attached(ref,store)){traceTransfer(ref,store,null,0,"invalidAttachment");dismiss();return;}
        if("Keyboard".equals(input.action)){if(PageKeyboard.closes(input.keyText))dismiss();else{var ui=new UICommandBuilder();PageKeyboard.reset(ui,"#PageKeyboard");sendUpdate(ui,false);}return;}
        try{
            String action=input.action==null?"":input.action;
            if(action.equals("Close")){dismiss();return;}
            if(List.of("Storage","Backpack","Hotbar").contains(action)){section=action;inventoryPage=0;}
            else if(action.equals("Previous"))inventoryPage=Math.max(0,inventoryPage-1);
            else if(action.equals("Next"))inventoryPage++;
            else if(action.equals("Stack")||action.equals("One")){
                var target=input.token==null?null:targets.get(input.token);int amount=action.equals("One")?1:Integer.MAX_VALUE,moved=0;
                if(target==null||!Objects.equals(target.expected,target.source.getItemStack(target.slot)))message="That slot changed. Select the item again.";
                else if(target.machine){
                    for(var destination:destinations(store,ref)){moved+=move(target.source,target.slot,target.source.getItemStack(target.slot),amount-moved,destination);if(moved>=amount||ItemStack.isEmpty(target.source.getItemStack(target.slot)))break;}
                    message=moved>0?"Retrieved "+moved+" item"+(moved==1?".":"s."):"Your inventory is full. Items kept in the extractor.";
                }else if(destinations(store,ref).contains(target.source)){
                    moved=deposit(target.source,target.slot,target.expected,amount,items);message=moved>0?"Added "+moved+" item"+(moved==1?".":"s."):"No free specimen or reserve slot. Items retained.";
                }
                if(moved>0)service.dirty(machineRef);
                traceTransfer(ref,store,target==null?null:target.expected,moved,"nativeTransaction");
            }
        }catch(RuntimeException failure){message="Transfer failed. Items retained; see the server log.";if(service.errors!=null)service.errors.report(playerRef,"Vitae Extractor transfer failed",failure);}
        if(!closing){var ui=new UICommandBuilder();render(ref,store,ui);renderProgress(ui);PageKeyboard.focus(ui,"#PageKeyboard");sendUpdate(ui,false);}
    }
    private void traceTransfer(Ref<EntityStore> ref,Store<EntityStore> store,ItemStack expected,int moved,String reason){
        if(service.debug==null)return;
        var stats=ref.isValid()?store.getComponent(ref,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType()):null;
        int healthIndex=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth();var hp=stats==null?null:stats.get(healthIndex);
        service.debug.traceFileOnly(playerRef,"VitaeExtractor event=pageTransfer cancelled=false itemSource=serverViewToken item="+(ItemStack.isEmpty(expected)?"missing":expected.getItemId())+" healthIndex="+healthIndex+" health="+(hp==null?"missing":hp.get()+"/"+hp.getMax()+" min="+hp.getMin())+" gate=livingNonSpectatingOwnerWithin8Blocks reason="+reason+" quantity="+moved+" allow="+(moved>0));
    }
    static boolean accepted(ItemStack stack){return !ItemStack.isEmpty(stack)&&(LeechBlood.blood(stack.getItemId())||Set.of(VitaeExtractorSystem.ESSENCE,BloodSample.DNA).contains(stack.getItemId()));}
    static List<ItemContainer> destinations(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref){var result=new ArrayList<ItemContainer>();for(var value:Arrays.asList(InventoryComponentAccess.storage(store,ref),InventoryComponentAccess.backpack(store,ref),InventoryComponentAccess.hotbar(store,ref)))if(value!=null&&!result.contains(value))result.add(value);return result;}
    static int move(ItemContainer source,short slot,ItemStack expected,int amount,ItemContainer destination){
        if(source==destination||amount<=0||ItemStack.isEmpty(expected)||!Objects.equals(expected,source.getItemStack(slot)))return 0;
        source.moveItemStackFromSlot(slot,Math.min(amount,expected.getQuantity()),destination,false,true);var after=source.getItemStack(slot);return expected.getQuantity()-(ItemStack.isEmpty(after)?0:after.getQuantity());
    }
    static int deposit(ItemContainer source,short slot,ItemStack expected,int amount,ItemContainer destination){
        if(!accepted(expected)||source==destination||amount<=0||!Objects.equals(expected,source.getItemStack(slot)))return 0;
        int before=expected.getQuantity(),remaining=Math.min(before,amount);short[] layout=layout(destination);var order=new ArrayList<Short>();
        if(VitaeExtractorSystem.ESSENCE.equals(expected.getItemId()))order.add(layout[0]);else for(int i=1;i<=3;i++)order.add(layout[i]);
        for(short i=0;i<destination.getCapacity();i++)if(!order.contains(i))order.add(i);
        for(short target:order){
            var item=destination.getItemStack(target);if(!ItemStack.isEmpty(item)&&!item.withQuantity(1).equals(expected.withQuantity(1)))continue;
            var current=source.getItemStack(slot);if(ItemStack.isEmpty(current)||remaining<=0)break;int count=current.getQuantity();
            source.moveItemStackFromSlotToSlot(slot,remaining,destination,target);var after=source.getItemStack(slot);remaining-=count-(ItemStack.isEmpty(after)?0:after.getQuantity());
        }
        var after=source.getItemStack(slot);return before-(ItemStack.isEmpty(after)?0:after.getQuantity());
    }

    static short[] layout(ItemContainer items){
        var remaining=new ArrayList<Short>();for(short i=0;i<items.getCapacity();i++)remaining.add(i);var result=new short[items.getCapacity()];int out=0;
        short fuel=choose(remaining,items,true);result[out++]=fuel;remaining.remove(Short.valueOf(fuel));
        for(int i=0;i<3&&out<result.length;i++){short vial=choose(remaining,items,false);result[out++]=vial;remaining.remove(Short.valueOf(vial));}
        for(short index:remaining)result[out++]=index;return result;
    }
    private static short choose(List<Short> remaining,ItemContainer items,boolean fuel){
        for(short index:remaining){var item=items.getItemStack(index);if(ItemStack.isEmpty(item))continue;String id=item.getItemId();if(fuel?VitaeExtractorSystem.ESSENCE.equals(id):LeechBlood.blood(id)||BloodSample.DNA.equals(id))return index;}
        for(short index:remaining)if(ItemStack.isEmpty(items.getItemStack(index)))return index;return remaining.getFirst();
    }
    private ItemContainer inventory(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref){return switch(section){case "Backpack"->InventoryComponentAccess.backpack(store,ref);case "Hotbar"->InventoryComponentAccess.hotbar(store,ref);default->InventoryComponentAccess.storage(store,ref);};}
    private List<ItemStack> contents(Ref<EntityStore> ref,Store<EntityStore> store){var result=new ArrayList<ItemStack>();for(short i=0;i<items.getCapacity();i++)result.add(items.getItemStack(i));var inventory=inventory(store,ref);if(inventory!=null)for(short i=0;i<inventory.getCapacity();i++)result.add(inventory.getItemStack(i));return result;}
    void refresh(Ref<EntityStore> ref,Store<EntityStore> store){var current=contents(ref,store);var progress=service.progress(machineRef,items);if(current.equals(previous)&&progress.equals(lastProgress))return;var ui=new UICommandBuilder();if(!current.equals(previous))render(ref,store,ui);renderProgress(ui);sendUpdate(ui,false);}
    private void render(Ref<EntityStore> ref,Store<EntityStore> store,UICommandBuilder ui){
        var mapping=new HashMap<String,Target>();var slots=layout(items);
        for(int i=0;i<9;i++){short slot=slots[i];draw(ui,"Machine",i,items.getItemStack(slot),new Target(items,slot,items.getItemStack(slot),true),mapping);}
        var inventory=inventory(store,ref);int capacity=inventory==null?0:inventory.getCapacity(),pages=Math.max(1,(capacity+35)/36);inventoryPage=Math.clamp(inventoryPage,0,pages-1);
        for(int i=0;i<36;i++){int slot=inventoryPage*36+i;var item=slot<capacity?inventory.getItemStack((short)slot):null;draw(ui,"Inventory",i,item,accepted(item)?new Target(inventory,(short)slot,item,false):null,mapping);}
        for(String tab:List.of("Storage","Backpack","Hotbar"))ui.set("#Active"+tab+".Visible",tab.equals(section));
        ui.set("#InventoryLabel.Text",section+"  /  "+(inventoryPage+1)+" of "+pages);ui.set("#Previous.Disabled",inventoryPage==0);ui.set("#Next.Disabled",inventoryPage==pages-1);ui.set("#Status.Text",message);
        previous=contents(ref,store);targets=Map.copyOf(mapping);
    }
    private void draw(UICommandBuilder ui,String prefix,int i,ItemStack stack,Target target,Map<String,Target> mapping){
        String id="#"+prefix+i,slot="#"+prefix+"Item"+i;
        if(ItemStack.isEmpty(stack))ui.setNull(slot+".ItemId");else ui.set(slot+".ItemId",stack.getItemId());ui.set(slot+".Quantity",ItemStack.isEmpty(stack)?0:stack.getQuantity());
        boolean enabled=target!=null&&!ItemStack.isEmpty(stack);ui.set(id+".Disabled",!enabled);
        String description=ItemStack.isEmpty(stack)?(prefix.equals("Machine")?"Select ingredients below to add them.":""):AstralBagPage.detail(stack,playerRef.getLanguage())+"\n"+(enabled?"LMB: stack  /  RMB: one":"Only blood, DNA and Essence of Life go into the extractor.");ui.set(id+".TooltipText",description);
        String token=enabled?UUID.randomUUID().toString():"";if(enabled)mapping.put(token,target);ui.set("#"+prefix+"Token"+i+".Value",token);
        if(prefix.equals("Machine")&&i>=1&&i<=3)ui.set("#BottleLabel"+i+".Text",ItemStack.isEmpty(stack)?"Blood sample":BloodSample.DNA.equals(stack.getItemId())?"DNA ready":LeechBlood.blood(stack.getItemId())?"Blood sample":"Stored item");
    }
    private void renderProgress(UICommandBuilder ui){
        var progress=service.progress(machineRef,items);lastProgress=progress;ui.set("#WorkState.Text",progress.status());
        drawProgress(ui,progress.seconds());
        short sample=VitaeExtractorSystem.bloodSlot(items);BloodSample data=sample<0?null:BloodSample.read(items.getItemStack(sample),BloodSample.BLOOD);
        if(data==null)for(short i=0;i<items.getCapacity();i++){data=BloodSample.read(items.getItemStack(i),BloodSample.DNA);if(data!=null)break;}
        ui.set("#Species.Text",data==null?"Species-bound DNA":data.name==null?data.species.replace('_',' '):data.name);
        var layout=layout(items);for(int i=1;i<=3;i++)ui.set("#ActiveBottle"+i+".Visible",layout[i]==progress.slot());
    }
    static void drawProgress(UICommandBuilder ui,double seconds){
        int width=(int)Math.round(132*Math.clamp(seconds/VitaeExtractorSystem.DURATION,0,1));

        var anchor=new Anchor();anchor.setLeft(Value.of(0));anchor.setTop(Value.of(0));anchor.setWidth(Value.of(width));anchor.setHeight(Value.of(8));
        ui.setObject("#ProgressFill.Anchor",anchor);
        ui.set("#ProgressText.Text",seconds>0?String.format(Locale.ROOT,"%.1f / 5.0 seconds",seconds):"1 blood + 1 essence");
    }
}
