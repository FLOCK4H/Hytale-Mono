package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import javax.annotation.Nonnull;


public final class AstralBagPage extends InteractiveCustomUIPage<AstralBagPage.Input> {
    public static final class Input {
        public static final BuilderCodec<Input> CODEC=BuilderCodec.builder(Input.class,Input::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(v,x)->v.action=x,v->v.action).add()
            .append(new KeyedCodec<>("@Token",Codec.STRING),(v,x)->v.token=x,v->v.token).add()
            .append(new KeyedCodec<>("@Query",Codec.STRING),(v,x)->v.query=x,v->v.query).add()
            .append(new KeyedCodec<>("@KeyText",Codec.STRING),(v,x)->v.keyText=x,v->v.keyText).add()
            .append(new KeyedCodec<>("@Page",Codec.INTEGER),(v,x)->v.page=x,v->v.page).add().build();
        String action,token,query,keyText;Integer page;
    }
    enum Filter {All,Materials,Equipment,Building,Consumables,Other}
    private record Target(UUID entry,ItemContainer source,short slot,ItemStack expected) {}
    final AstralVault.Vault vault;
    private final AstralBagService service;
    private final ItemContainer parent;
    private final short parentSlot;
    private Map<String,Target> targets=Map.of();
    private List<UUID> visibleBag=List.of();
    private List<AstralBulkTransfer.Source> visibleInventory=List.of();
    private String bulkToken="";
    private long revision;
    private String query="",section="Storage",message="LMB: transfer a stack    RMB: transfer one";
    private Filter filter=Filter.All;
    private int page,inventoryPage,pages=1,inventoryPages=1;
    private boolean closing;
    AstralBagPage(PlayerRef owner,AstralBagService service,AstralVault.Vault vault,ItemContainer parent,short slot) {
        super(owner,CustomPageLifetime.CanDismissOrCloseThroughInteraction,Input.CODEC);this.service=service;this.vault=vault;this.parent=parent;parentSlot=slot;
    }
    UUID owner(){return playerRef.getUuid();}
    void abandon(){closing=true;targets=Map.of();service.release(this);}
    void dismiss(){if(closing)return;abandon();close();}
    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store){abandon();}
    boolean attached(Store<EntityStore> store,Ref<EntityStore> ref) {
        var actor=ref.isValid()?store.getComponent(ref,PlayerRef.getComponentType()):null;
        return !closing && actor!=null && owner().equals(actor.getUuid()) && store.getComponent(ref,DeathComponent.getComponentType())==null
            && store.getComponent(ref,Spectating.getComponentType())==null
            && SpellbookPouchService.inventorySections(store,ref).contains(parent) && AstralBagService.same(parent,parentSlot,vault.id);
    }
    @Override public void build(@Nonnull Ref<EntityStore> ref,@Nonnull UICommandBuilder commands,@Nonnull UIEventBuilder events,@Nonnull Store<EntityStore> store) {
        commands.append("Pages/AxoAstralSatchel.ui");
        events.addEventBinding(CustomUIEventBindingType.Activating,"#Close",EventData.of("Action","Close"));
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#SatchelKeyboard",new EventData().append("Action","Keyboard").append("@KeyText","#SatchelKeyboard.Value"),false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#Search",new EventData().append("Action","Search").append("@Query","#Search.Value"),false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#Pager",new EventData().append("Action","Page").append("@Page","#Pager.Value"),false);
        for(String action:List.of("Previous","Next","InventoryPrevious","InventoryNext","Refresh"))
            events.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action));
        for(String action:List.of("TakeVisible","StoreVisible","Quickstack"))
            events.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,new EventData().append("Action",action).append("@Token","#BulkToken.Value"));
        for(var kind:Filter.values())events.addEventBinding(CustomUIEventBindingType.Activating,"#Filter"+kind,EventData.of("Action","Filter"+kind));
        for(String name:List.of("Storage","Backpack","Hotbar"))events.addEventBinding(CustomUIEventBindingType.Activating,"#Tab"+name,EventData.of("Action","Tab"+name));
        for(String prefix:List.of("Bag","Inventory"))for(int i=0;i<(prefix.equals("Bag")?27:36);i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating,"#"+prefix+i,new EventData().append("Action","Stack").append("@Token","#"+prefix+"Token"+i+".Value"));
            events.addEventBinding(CustomUIEventBindingType.RightClicking,"#"+prefix+i,new EventData().append("Action","One").append("@Token","#"+prefix+"Token"+i+".Value"));
        }
        render(ref,store,commands);
    }
    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store,@Nonnull Input data) {
        if(closing)return;
        if("Keyboard".equals(data.action)){
            if(PageKeyboard.closes(data.keyText)){service.traceAction(store,ref,"keyboardClose",true,"itemSource=nativeFocusedKeyboard");dismiss();}
            else if(data.keyText!=null&&!data.keyText.isEmpty()){var reset=new UICommandBuilder();reset.set("#SatchelKeyboard.Value","");sendUpdate(reset,false);}
            return;
        }
        if("Close".equals(data.action)){dismiss();return;}
        if(!attached(store,ref)){service.trace(playerRef,"staleWindow",false,"itemSource=boundInventorySlot");dismiss();return;}
        try {
            String action=data.action==null?"":data.action;
            if(action.equals("Search")){String next=data.query==null?"":data.query.substring(0,Math.min(80,data.query.length()));if(next.equals(query))return;query=next;page=0;}
            else if(action.equals("Page") && data.page!=null){int next=Math.clamp(data.page,0,pages-1);if(next==page)return;page=next;}
            else if(action.equals("Previous"))page=Math.max(0,page-1);
            else if(action.equals("Next"))page=Math.min(pages-1,page+1);
            else if(action.equals("InventoryPrevious"))inventoryPage=Math.max(0,inventoryPage-1);
            else if(action.equals("InventoryNext"))inventoryPage=Math.min(inventoryPages-1,inventoryPage+1);
            else if(action.startsWith("Filter")){filter=Arrays.stream(Filter.values()).filter(f->action.equals("Filter"+f)).findFirst().orElse(filter);page=0;}
            else if(action.startsWith("Tab")){section=action.substring(3);if(!List.of("Storage","Backpack","Hotbar").contains(section))section="Storage";inventoryPage=0;}
            else if(List.of("TakeVisible","StoreVisible","Quickstack").contains(action)) {
                long moved=0;
                if(!bulkToken.equals(data.token)||revision!=vault.snapshot().revision())message="The view changed. Please try again.";
                else {
                    if(action.equals("TakeVisible"))moved=AstralBulkTransfer.withdraw(vault,revision,visibleBag,AstralBagService.destinations(store,ref));
                    else if(visibleInventory.stream().allMatch(s->SpellbookPouchService.inventorySections(store,ref).contains(s.container())))
                        moved=AstralBulkTransfer.deposit(vault,revision,visibleInventory,action.equals("Quickstack"));
                    message=moved>0?"Transferred "+moved+" item"+(moved==1?".":"s."):action.equals("TakeVisible")?"No inventory space. Items kept in the Satchel.":action.equals("Quickstack")?"No visible items match stored types.":"No visible items to store.";
                }
                service.traceAction(store,ref,action,moved>0,"itemSource=serverVisiblePageToken bag="+vault.id+" revision="+revision+" quantity="+moved);
            }
            else if(action.equals("Stack") || action.equals("One")) {
                Target target=data.token==null?null:targets.get(data.token);int moved=0;
                if(target==null || revision!=vault.snapshot().revision()){message="The view changed. Please select the item again.";}
                else {
                    int amount=action.equals("One")?1:Integer.MAX_VALUE;
                    if(target.entry!=null)moved=vault.withdraw(revision,target.entry,amount,AstralBagService.destinations(store,ref));
                    else if(SpellbookPouchService.inventorySections(store,ref).contains(target.source))moved=vault.deposit(revision,target.source,target.slot,target.expected,amount);
                    message=moved>0?(target.entry!=null?"Retrieved ":"Stored ")+moved+" item"+(moved==1?".":"s.")
                        :target.entry!=null?"No room, or this item's source mod is unavailable. Contents kept.":"That inventory slot changed. Please select it again.";
                    service.trace(playerRef,target.entry!=null?"withdraw":"deposit",moved>0,"itemSource=serverViewToken bag="+vault.id+" revision="+revision+" quantity="+moved);
                }
            }
        }catch(Throwable failure){message="Transfer stopped. Completed transfers were saved; remaining items retained.";service.errors.report(playerRef,"Astral Satchel transfer failed.",failure);}
        if(closing)return;
        var update=new UICommandBuilder();render(ref,store,update);if(!"Search".equals(data.action))PageKeyboard.focus(update,"#SatchelKeyboard");sendUpdate(update,false);
    }
    private void render(Ref<EntityStore> ref,Store<EntityStore> store,UICommandBuilder ui) {
        var snapshot=vault.snapshot();revision=snapshot.revision();var mapping=new HashMap<String,Target>();
        var shownBag=new ArrayList<UUID>();var shownInventory=new ArrayList<AstralBulkTransfer.Source>();
        var visible=snapshot.entries().stream().filter(e->matches(e.item(),query,filter,playerRef.getLanguage())).sorted(Comparator.comparing(e->name(e.item(),playerRef.getLanguage()))).toList();
        pages=Math.max(1,(int)(((long)visible.size()+26)/27));page=Math.clamp(page,0,pages-1);
        ui.set("#Summary.Text",snapshot.entries().size()+" stored types · no slot limit");
        ui.set("#Result.Text",visible.size()+" matching types · page "+(page+1)+" / "+pages);
        ui.set("#Empty.Visible",visible.isEmpty());ui.set("#Empty.Text",snapshot.entries().isEmpty()?"No stored items.\nSelect an item below to store it.":"No items match. Try another name or category.");
        for(int i=0;i<27;i++) {
            int index=page*27+i;var entry=index<visible.size()?visible.get(index):null;
            if(entry!=null)shownBag.add(entry.id());
            drawSlot(ui,"Bag",i,entry==null?null:entry.item(),entry==null?0:entry.count(),entry==null?null:new Target(entry.id(),null,(short)-1,null),mapping);
        }
        ui.set("#Pager.Max",Math.max(1,pages-1));ui.set("#Pager.Value",page);ui.set("#Pager.IsReadOnly",pages<=1);
        ui.set("#Previous.Disabled",page==0);ui.set("#Next.Disabled",page==pages-1);
        for(var kind:Filter.values())ui.set("#Active"+kind+".Visible",filter==kind);
        ItemContainer inventory=switch(section){case "Backpack"->InventoryComponentAccess.backpack(store,ref);case "Hotbar"->InventoryComponentAccess.hotbar(store,ref);default->InventoryComponentAccess.storage(store,ref);};
        int capacity=inventory==null?0:inventory.getCapacity();inventoryPages=Math.max(1,(capacity+35)/36);inventoryPage=Math.clamp(inventoryPage,0,inventoryPages-1);
        for(int i=0;i<36;i++) {
            int index=inventoryPage*36+i;var item=index<capacity?inventory.getItemStack((short)index):null;
            var target=ItemStack.isEmpty(item) || !AstralVault.admissible(item)?null:new Target(null,inventory,(short)index,item);
            if(target!=null)shownInventory.add(new AstralBulkTransfer.Source(inventory,(short)index,item));
            drawSlot(ui,"Inventory",i,item,ItemStack.isEmpty(item)?0:item.getQuantity(),target,mapping);
        }
        for(String tab:List.of("Storage","Backpack","Hotbar"))ui.set("#TabActive"+tab+".Visible",section.equals(tab));
        ui.set("#InventoryCount.Text",section+" · "+capacity+" slots · "+(inventoryPage+1)+" / "+inventoryPages);
        ui.set("#InventoryPrevious.Disabled",inventoryPage==0);ui.set("#InventoryNext.Disabled",inventoryPage==inventoryPages-1);
        ui.set("#Status.Text",message);targets=Map.copyOf(mapping);
        visibleBag=List.copyOf(shownBag);visibleInventory=List.copyOf(shownInventory);bulkToken=UUID.randomUUID().toString();ui.set("#BulkToken.Value",bulkToken);
        ui.set("#TakeVisible.Disabled",visibleBag.isEmpty());ui.set("#StoreVisible.Disabled",visibleInventory.isEmpty());
        var storedTypes=new HashSet<String>();for(var entry:snapshot.entries())storedTypes.add(entry.item().getItemId());
        ui.set("#Quickstack.Disabled",visibleInventory.stream().noneMatch(s->storedTypes.contains(s.expected().getItemId())));
    }
    private void drawSlot(UICommandBuilder ui,String prefix,int index,ItemStack item,long count,Target target,Map<String,Target> mapping) {
        String selector="#"+prefix+"Item"+index;
        if(ItemStack.isEmpty(item))ui.setNull(selector+".ItemId");else ui.set(selector+".ItemId",item.getItemId());
        ui.set(selector+".Quantity",(int)Math.min(Integer.MAX_VALUE,count));
        ui.set(selector+".ShowQuantity",count<=9999);
        ui.set("#"+prefix+"Count"+index+".Text",count>9999?compact(count):"");
        ui.set("#"+prefix+index+".Disabled",target==null);
        ui.set("#"+prefix+index+".TooltipText",ItemStack.isEmpty(item)?"":detail(item,playerRef.getLanguage())+"\n"+String.format(Locale.ROOT,"%,d",count)+(prefix.equals("Bag")?" stored":" carried")+(target==null?"\nSatchels cannot be nested.":"\nLMB: stack · RMB: one"));
        String token=target==null?"":UUID.randomUUID().toString();if(target!=null)mapping.put(token,target);
        ui.set("#"+prefix+"Token"+index+".Value",token);
    }
    static String compact(long count){return count>=1_000_000_000?String.format(Locale.ROOT,"%.1fB",count/1e9):count>=1_000_000?String.format(Locale.ROOT,"%.1fM",count/1e6):count>=10_000?String.format(Locale.ROOT,"%.1fk",count/1e3):Long.toString(count);}
    static String name(ItemStack item,String language) {
        var definition=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(item.getItemId());
        String key=definition==null?null:definition.getTranslationKey();var i18n=I18nModule.get();
        String value=key==null || i18n==null?null:i18n.getMessage(language,key);
        String title=value==null || value.isBlank()?item.getItemId().replace('_',' '):value;
        var sample=BloodSample.read(item,BloodSample.BLOOD);if(sample==null)sample=BloodSample.read(item,BloodSample.DNA);
        return sample==null?title:title+" — "+(sample.name==null || sample.name.isBlank()?sample.species.replace('_',' '):sample.name);
    }
    static String detail(ItemStack item,String language) {
        return name(item,language)+(item.getMaxDurability()>0?String.format(Locale.ROOT,"\nDurability: %.1f / %.1f",item.getDurability(),item.getMaxDurability()):"");
    }
    static boolean matches(ItemStack item,String query,Filter filter,String language) {
        String term=query.strip().toLowerCase(Locale.ROOT);
        if(!term.isEmpty() && !name(item,language).toLowerCase(Locale.ROOT).contains(term) && !item.getItemId().toLowerCase(Locale.ROOT).contains(term))return false;
        if(filter==Filter.All)return true;
        var definition=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(item.getItemId());
        if(definition==null)return filter==Filter.Other;
        String categories=String.join(" ",definition.getCategories()==null?new String[0]:definition.getCategories()).toLowerCase(Locale.ROOT);
        Filter category=definition.getArmor()!=null || definition.getWeapon()!=null || categories.contains("weapons") || categories.contains("tools")?Filter.Equipment:
            categories.contains("ingredient") || categories.contains("resource") || categories.contains("ore")?Filter.Materials:
            categories.contains("consumable") || categories.contains("food") || categories.contains("potion")?Filter.Consumables:
            definition.getBlockId()!=null || categories.contains("block") || categories.contains("furniture")?Filter.Building:Filter.Other;
        return filter==category;
    }
}
