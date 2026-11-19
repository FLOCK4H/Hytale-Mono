package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


final class AncientFlightControl {
    static boolean begin(Player player,MovementManager manager,PlayerRef owner,Ref<EntityStore> ref,ComponentAccessor<EntityStore> access){
        if(manager.getSettings().fly!=FlyMode.Disabled||player.getMountEntityId()!=0)return false;
        manager.getSettings().fly=FlyMode.Forced;manager.update(owner.getPacketHandler());player.setFlying(ref,true,access);return true;
    }
    static boolean end(Player player,MovementManager manager,PlayerRef owner,Ref<EntityStore> ref,ComponentAccessor<EntityStore> access){
        if(manager.getSettings().fly!=FlyMode.Forced||manager.getDefaultSettings().fly!=FlyMode.Disabled)return false;
        manager.getSettings().fly=FlyMode.Disabled;manager.update(owner.getPacketHandler());player.setFlying(ref,false,access);return true;
    }
}
