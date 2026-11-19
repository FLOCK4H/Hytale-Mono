package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;


final class SkillTreePage extends InteractiveCustomUIPage<SkillTreePage.Action> {
    static final class Action {
        static final BuilderCodec<Action> CODEC=BuilderCodec.builder(Action.class,Action::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(s,v)->s.action=v,s->s.action).add()
            .append(new KeyedCodec<>("@KeyText",Codec.STRING),(s,v)->s.keyText=v,s->s.keyText).add().build();
        String action,keyText;
    }
    private final Vector3i at;
    private int selected;
    private boolean superskills;
    private String message="Select a skill to inspect its gifts.";
    SkillTreePage(PlayerRef player,Vector3i at){super(player,CustomPageLifetime.CanDismissOrCloseThroughInteraction,Action.CODEC);this.at=new Vector3i(at);}
    @Override public void build(@Nonnull Ref<EntityStore> ref,@Nonnull UICommandBuilder ui,@Nonnull UIEventBuilder events,@Nonnull Store<EntityStore> store){
        ui.append("Pages/AxoSkillTree.ui");
        PageKeyboard.bind(events,"#SkillTreeKeyboard");
        for(var node:SkillCatalog.ALL)events.addEventBinding(CustomUIEventBindingType.Activating,"#Node"+node.id(),EventData.of("Action","Select:"+node.id()));
        events.addEventBinding(CustomUIEventBindingType.Activating,"#ShowSkills",EventData.of("Action","Skills"));
        events.addEventBinding(CustomUIEventBindingType.Activating,"#ShowSuperskills",EventData.of("Action","Superskills"));
        events.addEventBinding(CustomUIEventBindingType.Activating,"#Learn",EventData.of("Action","Learn"));
        events.addEventBinding(CustomUIEventBindingType.Activating,"#Close",EventData.of("Action","Close"));
        render(ref,store,ui);
    }
    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store,@Nonnull Action data){
        if("Close".equals(data.action)||"Keyboard".equals(data.action)&&closes(data.keyText)){close();return;}
        if(!SkillTreeSystem.attached(ref,store,at)){close();return;}
        if("Skills".equals(data.action)){superskills=false;selected=0;message="";}
        else if("Superskills".equals(data.action)){superskills=true;selected=SkillCatalog.FEROX;message="";}
        else if(data.action!=null&&data.action.startsWith("Select:")){
            try{int id=Integer.parseInt(data.action.substring(7));if(SkillCatalog.node(id)!=null){selected=id;message="";}}catch(NumberFormatException ignored){}
        }else if("Learn".equals(data.action)){
            var state=SkillProgression.read(store,ref);if(state==null)return;
            var next=state.clone();String failure=next.unlock(selected);
            if(failure==null){store.putComponent(ref,SkillProgression.TYPE,next);message=SkillCatalog.node(selected).name()+" learned.";}else message=failure;
        }
        var ui=new UICommandBuilder();render(ref,store,ui);PageKeyboard.reset(ui,"#SkillTreeKeyboard");PageKeyboard.focus(ui,"#SkillTreeKeyboard");sendUpdate(ui,new UIEventBuilder(),false);
    }
    static boolean closes(String key){return "f".equalsIgnoreCase(key)||"\t".equals(key);}
    private void render(Ref<EntityStore> ref,Store<EntityStore> store,UICommandBuilder ui){
        var state=SkillProgression.read(store,ref);if(state==null)state=new SkillProgression();
        present(state,selected,message,ui,superskills);
    }
    static void present(SkillProgression state,int selected,String message,UICommandBuilder ui){
        present(state,selected,message,ui,selected>=SkillCatalog.FEROX);
    }
    static void present(SkillProgression state,int selected,String message,UICommandBuilder ui,boolean superskills){
        ui.set("#RuneCount.Text",state.runes+" runes");
        ui.set("#LearnedCount.Text",superskills?Integer.bitCount(state.unlocked&((1<<SkillCatalog.FEROX)|(1<<SkillCatalog.EVANESCO)))+" / 2 superskills learned":Integer.bitCount(state.unlocked&0x1ffff)+" / "+SkillCatalog.NODES.size()+" learned");
        ui.set("#CoreView.Visible",!superskills);ui.set("#SupersView.Visible",superskills);ui.set("#CoreTabOn.Visible",!superskills);ui.set("#SuperTabOn.Visible",superskills);
        for(var node:SkillCatalog.ALL){
            boolean owned=state.has(node.id()),available=SkillCatalog.implemented(node.id())&&!owned&&state.has(node.parent())&&state.runes>=node.cost();
            ui.set("#State"+node.id()+".Text",!SkillCatalog.implemented(node.id())?"COMING LATER":owned?"LEARNED":available?node.cost()+" RUNE"+(node.cost()==1?"":"S"):node.id()>=SkillCatalog.FEROX?"100 RUNES":"LOCKED");
            ui.set("#Owned"+node.id()+".Visible",owned);ui.set("#Available"+node.id()+".Visible",available);
            ui.set("#Selected"+node.id()+".Visible",selected==node.id());ui.set("#DetailRune"+node.id()+".Visible",selected==node.id());
            if(node.id()<SkillCatalog.FEROX&&node.parent()>=0)ui.set("#Path"+node.id()+".Visible",owned);
        }
        var node=SkillCatalog.node(selected);boolean owned=state.has(selected);
        ui.set("#SkillName.Text",node.name());ui.set("#Description.Text",node.description());
        ui.set("#Prerequisite.Text",!SkillCatalog.implemented(selected)?"Not available yet.":selected==0?"Your journey begins here.":selected>=SkillCatalog.FEROX?"Permanent superskill":"Requires "+SkillCatalog.node(node.parent()).name());
        ui.set("#Cost.Text",owned?"Already learned":node.cost()+" rune"+(node.cost()==1?"":"s"));
        ui.set("#Learn.Text",!SkillCatalog.implemented(selected)?"Unavailable":owned?"Learned":selected>=SkillCatalog.FEROX?"Learn superskill":"Learn skill");
        ui.set("#Learn.Disabled",!SkillCatalog.implemented(selected)||owned||!state.has(node.parent())||state.runes<node.cost());
        ui.set("#Feedback.Text",message);
        bonuses(state.unlocked).forEach((key,value)->ui.set("#Bonus"+key+".Text",value));
    }
    static Map<String,String> bonuses(int mask){
        return Map.ofEntries(
            Map.entry("Attack",percent((1+SkillCatalog.attack(mask))*SkillCatalog.superDamage(mask)-1)),Map.entry("Defense",percent(SkillCatalog.defense(mask))),
            Map.entry("Speed",percent(SkillCatalog.speed(mask))),Map.entry("Health",percent((1+SkillCatalog.health(mask))*SkillCatalog.superHealth(mask)-1)+(SkillCatalog.has(mask,13)?" +"+number(25*SkillCatalog.superHealth(mask)):"")),
            Map.entry("Stamina",SkillCatalog.has(mask,5)?"+5%":"+0%"),Map.entry("Reach","+"+SkillCatalog.reach(mask)+" blocks"),
            Map.entry("HealthRegen","+"+((SkillCatalog.has(mask,9)?1:0)+(SkillCatalog.has(mask,16)?3:0))+" / 2s"),
            Map.entry("ManaRegen",SkillCatalog.has(mask,0)?"+2 / 5s":"+0 / 5s"),Map.entry("StaminaRegen",SkillCatalog.has(mask,5)?"+2 / 5s":"+0 / 5s"),
            Map.entry("Projectile",percent(SkillCatalog.projectiles(mask))),Map.entry("Fall",SkillCatalog.has(mask,8)?"+25%":"+0%"),
            Map.entry("Poison",SkillCatalog.has(mask,11)?"+25%":"+0%"),Map.entry("JumpHeight",SkillCatalog.has(mask,8)?"+1 block":"+0 blocks"),
            Map.entry("ExtraJump",SkillCatalog.has(mask,12)?"+1":"+0"),Map.entry("Knockback",SkillCatalog.has(mask,10)?"Slight":"Locked"),
            Map.entry("Airwave",SkillCatalog.has(mask,14)?number(31*SkillCatalog.superDamage(mask))+" damage":"Locked"),Map.entry("Stealth",SkillCatalog.has(mask,SkillCatalog.EVANESCO)?"Invisible":SkillCatalog.has(mask,15)?"2 blocks":"Locked"),
            Map.entry("Lazarus",SkillCatalog.has(mask,16)?"3s / 5min":"Locked"));
    }
    private static String percent(float bonus){return String.format(Locale.ROOT,"+%.1f%%",bonus*100);}
    private static String number(float value){return value==(int)value?Integer.toString((int)value):String.format(Locale.ROOT,"%.2f",value).replaceAll("0+$","");}
}
