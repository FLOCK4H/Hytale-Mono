package org.example.plugin;

import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.assets.UpdateInteractions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.*;


final class SkillReachControl {
    private int reach;
    private PlayerRef player;
    private int maxId;
    private Map<Integer,Interaction> originals=Map.of();

    static UpdateInteractions packet(Map<Integer,Interaction> originals,int reach,int maxId){
        var copies=new HashMap<Integer,Interaction>();
        originals.forEach((id,original)->{
            if(!(original instanceof SelectInteraction selection))return;
            var copy=selection.clone();

            copy.waitForDataFrom=WaitForDataFrom.Client;


            if(copy.selector instanceof HorizontalSelector horizontal){var selector=horizontal.clone();selector.endDistance+=reach;copy.selector=selector;}
            else if(copy.selector instanceof StabSelector stab){var selector=stab.clone();selector.endDistance+=reach;copy.selector=selector;}
            copies.put(id,copy);
        });
        return new UpdateInteractions(UpdateType.AddOrUpdate,maxId,copies);
    }

    boolean refresh(PlayerRef player,int requested){
        this.player=player;
        if(reach==requested)return false;
        var assets=com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.getAssetMap();
        var current=new HashMap<Integer,Interaction>();
        for(var entry:assets.getAssetMap().entrySet()){
            if(entry.getValue() instanceof SkillMeleeSelector.Interaction){
                int id=assets.getIndex(entry.getKey());
                if(id>=0)current.put(id,entry.getValue().toPacket());
            }
        }
        maxId=assets.getNextIndex();originals=current;
        player.getPacketHandler().writeNoCache(packet(current,requested,maxId));
        reach=requested;return true;
    }

    void restore(){
        if(reach!=0&&player!=null&&!originals.isEmpty())player.getPacketHandler().writeNoCache(packet(originals,0,maxId));
        reach=0;originals=Map.of();
    }
}
