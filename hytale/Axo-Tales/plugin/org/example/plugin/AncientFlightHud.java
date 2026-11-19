package org.example.plugin;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Locale;


final class AncientFlightHud extends CustomUIHud {
    static final String KEY="axotales:ancient-flight";
    private String previous="";
    AncientFlightHud(PlayerRef player){super(player,KEY,90);}
    @Override protected void build(UICommandBuilder ui){ui.append("Pages/AxoAncientFlight.ui");}
    void refresh(float fuel,boolean flying){
        int tenth=Math.round(Math.clamp(fuel,0,10)*10);String state=flying?"Flying":fuel<=0?"Land to recharge":"Ready";
        String signature=state+tenth;if(signature.equals(previous))return;previous=signature;
        var ui=new UICommandBuilder().set("#FuelText.Text",String.format(Locale.ROOT,"%.1f s",tenth/10f)).set("#StateText.Text",state);
        var anchor=new Anchor();anchor.setLeft(Value.of(0));anchor.setTop(Value.of(0));anchor.setWidth(Value.of(Math.round(266*tenth/100f)));anchor.setHeight(Value.of(8));
        ui.setObject("#FuelFill.Anchor",anchor);update(false,ui);
    }
}
