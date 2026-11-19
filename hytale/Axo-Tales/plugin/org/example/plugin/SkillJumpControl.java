package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;


final class SkillJumpControl {
    private Item sent;
    private PlayerRef player;
    private boolean active,available,pressed;
    private float airborne;
    static UpdateItems packet(ItemBase original,boolean enabled){
        var copy=original.clone();copy.armor=original.armor.clone();
        copy.armor.movementSettings=original.armor.movementSettings==null?new ItemMovementSettings():original.armor.movementSettings.clone();
        if(enabled)copy.armor.movementSettings.extraJumpCount++;
        return new UpdateItems(UpdateType.AddOrUpdate,Map.of(original.id,copy),null,false,false);
    }
    void refresh(Store<EntityStore> store,Ref<EntityStore> ref,boolean enabled,float delta){
        player=store.getComponent(ref,PlayerRef.getComponentType());
        String id=InventoryComponentAccess.armorItemId(store,ref,ItemArmorSlot.Legs);var definition=id==null?null:Item.getAssetMap().getAsset(id);
        boolean nativeBoots=definition!=null&&definition.toPacket().armor!=null;
        if(sent!=definition||active!=enabled){
            restore();active=enabled;

            if(enabled&&nativeBoots&&!AncientArmor.BOOTS.equals(id)){sent=definition;player.getPacketHandler().writeNoCache(packet(definition.toPacket(),true));}
        }
        var component=store.getComponent(ref,MovementStatesComponent.getComponentType());if(component==null)return;var state=component.getMovementStates();
        if(state.onGround){available=enabled;airborne=0;}else airborne+=delta;
        if(!enabled||state.flying||state.inFluid||state.climbing){available=false;pressed=state.jumping;return;}


        if(!nativeBoots&&available&&airborne>.18f&&state.jumping&&!pressed){
            var velocity=store.getComponent(ref,Velocity.getComponentType());var movement=store.getComponent(ref,MovementManager.getComponentType());
            if(velocity!=null&&movement!=null){var next=new org.joml.Vector3d(velocity.getVelocity());next.y=movement.getSettings().jumpForce;velocity.addInstruction(next,null,ChangeVelocityType.Set);store.getComponent(ref,Player.getComponentType()).setCurrentFallDistance(0);available=false;}
        }
        pressed=state.jumping;
    }
    void restore(){if(sent!=null&&player!=null){player.getPacketHandler().writeNoCache(packet(sent.toPacket(),false));sent=null;}}
}
