package org.example.plugin;

import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import java.util.Map;




final class AncientJumpSettings {
    static UpdateItems packet(ItemBase original,boolean full){
        return packet(original,full,0);
    }
    static UpdateItems packet(ItemBase original,boolean full,int skillJumps){
        var copy=original.clone();copy.armor=original.armor.clone();
        if(original.armor.movementSettings!=null){copy.armor.movementSettings=original.armor.movementSettings.clone();copy.armor.movementSettings.extraJumpCount+=(full?1:0)+skillJumps;}
        return new UpdateItems(UpdateType.AddOrUpdate,Map.of(AncientArmor.BOOTS,copy),null,false,false);
    }
}
