package org.example.plugin;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;


final class SpyglassHud extends CustomUIHud {
    static final String KEY="axotales:spyglass";
    private SpyglassReadout.Snapshot previous;
    SpyglassHud(PlayerRef player){super(player,KEY,100);}
    @Override protected void build(UICommandBuilder ui){ui.append("Pages/AxoSpyglass.ui");}
    void refresh(SpyglassReadout.Snapshot snapshot){
        if(snapshot.equals(previous))return;update(false,commands(snapshot));previous=snapshot;
    }
    static UICommandBuilder commands(SpyglassReadout.Snapshot snapshot){
        var ui=new UICommandBuilder().set("#TargetName.Text",snapshot.name());
        ui.setObject("#Readout.Anchor",readoutAnchor(snapshot.rows().size()));
        for(int i=0;i<7;i++){
            boolean show=i<snapshot.rows().size();ui.set("#Field"+i+".Visible",show);
            if(show){var row=snapshot.rows().get(i);ui.set("#Label"+i+".Text",row.label());var value=row.value();ui.set("#Value"+i+".Text",value.length()>26?value.substring(0,23)+"...":value);}
        }
        return ui;
    }
    static Anchor readoutAnchor(int rows){
        var anchor=new Anchor();anchor.setLeft(Value.of(56));anchor.setTop(Value.of(64));anchor.setWidth(Value.of(352));anchor.setHeight(Value.of(118+Math.clamp(rows,0,7)*34));return anchor;
    }
}
