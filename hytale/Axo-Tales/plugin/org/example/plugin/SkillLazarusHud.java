package org.example.plugin;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;


final class SkillLazarusHud extends CustomUIHud {
    static final String KEY="axotales:lazarus";
    private String previous="";
    SkillLazarusHud(PlayerRef player){super(player,KEY,80);}
    @Override protected void build(UICommandBuilder ui){ui.append("Pages/AxoLazarus.ui");}
    void refresh(SkillProgression state,long now){
        long remaining=Math.max(0,(state.readyAt-now+999)/1000);
        String value=now<state.immuneUntil?"Immune":remaining==0?"Ready":String.format(java.util.Locale.ROOT,"%d:%02d",remaining/60,remaining%60);
        if(value.equals(previous))return;previous=value;
        update(false,new UICommandBuilder().set("#LazarusState.Text",value).set("#LazarusState.Style.TextColor",remaining==0?"#e6d4a4":"#c7b4e7"));
    }
}
