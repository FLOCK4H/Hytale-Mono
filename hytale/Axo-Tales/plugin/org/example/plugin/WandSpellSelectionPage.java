package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;


public final class WandSpellSelectionPage extends InteractiveCustomUIPage<WandSpellSelectionPage.Selection> {
    public static final class Selection {
        public static final BuilderCodec<Selection> CODEC = BuilderCodec.builder(Selection.class, Selection::new)
            .append(new KeyedCodec<>("Spell", Codec.STRING), (data, value) -> data.spell = value, data -> data.spell).add()
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
            .append(new KeyedCodec<>("@KeyText",Codec.STRING),(data,value)->data.keyText=value,data->data.keyText).add().build();
        private String spell;
        private String action;
        private String keyText;
    }
    private final SpellbookPouchService pouches;
    private boolean closing;
    public WandSpellSelectionPage(PlayerRef player, SpellbookPouchService pouches) {
        super(player, CustomPageLifetime.CanDismiss, Selection.CODEC);
        this.pouches = pouches;
    }
    @Override public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Pages/AxoWandSpells.ui");
        events.addEventBinding(CustomUIEventBindingType.RightClicking,"#WheelPage",EventData.of("Action","Dismiss"));
        events.addEventBinding(CustomUIEventBindingType.Activating,"#WheelPage",EventData.of("Action","Dismiss"));
        var available = pouches.available(store, ref);
        var held = InventoryComponentAccess.itemInHand(store, ref);
        var selected = held == null ? null : SpellbookCatalog.fromWand(held.getItemId());
        commands.set("#Selected.Text", selected == null ? "Choose a spell" : selected.title);
        commands.set("#Summary.Text", available.isEmpty()
            ? "No spellbooks found. Carry a book in your inventory or a Spellbook Pouch."
            : available.size() + " spells carried\nHover to inspect · click to equip");
        commands.set("#Detail.Text", selected!=null && available.contains(selected) ? detail(selected) : "Carry a spellbook in your inventory or Spellbook Pouch.");
        for (var spell : SpellbookCatalog.values()) {
            commands.set("#Spell" + spell.key + ".Disabled", !available.contains(spell));
            commands.set("#Cost" + spell.key + ".Text", available.contains(spell) ? pouches.manaCost(spell) + " mana" : "Not carried");
            commands.set("#Spell" + spell.key + ".TooltipText", available.contains(spell) ? detail(spell) : spell.title+"\nBook not carried");
            commands.set("#Marker" + spell.key + ".Visible", available.contains(spell) && spell==selected);
            events.addEventBinding(CustomUIEventBindingType.Activating, "#Spell" + spell.key,
                new EventData().append("Spell", spell.key).append("Action", "Select"));
            events.addEventBinding(CustomUIEventBindingType.RightClicking,"#Spell"+spell.key,EventData.of("Action","Dismiss"));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", new EventData().append("Spell", "Close"));
        events.addEventBinding(CustomUIEventBindingType.RightClicking,"#CloseButton",EventData.of("Action","Dismiss"));
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,"#WheelKeyboard",new EventData().append("Action","Keyboard").append("@KeyText","#WheelKeyboard.Value"),false);
    }
    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Selection data) {
        if (closing) return;
        if("Dismiss".equals(data.action)){closing=true;close();return;}
        if("Keyboard".equals(data.action)){
            if(" ".equals(data.keyText)){closing=true;close();}
            else if(data.keyText!=null&&!data.keyText.isEmpty()){
                var reset=new UICommandBuilder();reset.set("#WheelKeyboard.Value","");sendUpdate(reset,false);
            }
            return;
        }
        var spell = java.util.Arrays.stream(SpellbookCatalog.values()).filter(s -> s.key.equals(data.spell)).findFirst().orElse(null);
        if ("Preview".equals(data.action)) {
            return;
        }
        closing = true;


        if (spell != null) pouches.select(store, ref, spell);
        close();
    }
    private String detail(SpellbookCatalog spell) {
        return spell.title+"\n"+pouches.manaCost(spell)+" mana\n"+spell.secondaryHint+"\n"+spell.primaryHint.replace("LMB:","Crouch+RMB:");
    }
}
