package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;


public final class WhisperionState implements Component<EntityStore> {
    public static final String KELP="Plant_Crop_Mana3";
    public static final String SADDLE="Axo_Whisperion_Saddle";
    public static final BuilderCodec<WhisperionState> CODEC=BuilderCodec.builder(WhisperionState.class,WhisperionState::new)
        .append(new KeyedCodec<>("OwnerUuid",Codec.UUID_STRING),(v,x)->v.owner=x,v->v.owner).add()
        .append(new KeyedCodec<>("Saddled",Codec.BOOLEAN),(v,x)->v.saddled=x,v->v.saddled).add()
        .append(new KeyedCodec<>("SaddleItem",ItemStack.CODEC),(v,x)->v.saddleItem=x,v->v.saddleItem).add()
        .append(new KeyedCodec<>("BornAt",Codec.LONG),(v,x)->v.bornAt=x,v->v.bornAt).add()
        .append(new KeyedCodec<>("RidingPlayer",Codec.UUID_STRING),(v,x)->v.ridingPlayer=x,v->v.ridingPlayer).add()
        .append(new KeyedCodec<>("ParkFlags",Codec.INTEGER),(v,x)->v.parkFlags=x,v->v.parkFlags).add()
        .append(new KeyedCodec<>("ParkedModel",Model.ModelReference.CODEC),(v,x)->v.parkedModel=x,v->v.parkedModel).add().build();
    UUID owner;
    boolean saddled;
    ItemStack saddleItem;
    long bornAt;
    UUID ridingPlayer;
    int parkFlags;
    Model.ModelReference parkedModel;
    transient long lastUse;
    transient long nextRecord,nextCatchup;
    transient boolean transferPending;


    String accept(UUID player,ItemContainer inventory,short slot,String expected) {
        if(owner!=null && !owner.equals(player))return "notOwner";
        if(KELP.equals(expected) && owner!=null)return "alreadyTamed";
        if(SADDLE.equals(expected) && owner==null)return "tameFirst";
        if(SADDLE.equals(expected) && saddled)return "alreadySaddled";
        if(!KELP.equals(expected) && !SADDLE.equals(expected))return "wrongItem";
        if(inventory==null || slot<0 || slot>=inventory.getCapacity())return "missingItem";
        var item=inventory.getItemStack(slot);
        if(item==null || item.isEmpty() || !expected.equals(item.getItemId()))return "itemChanged";
        var removed=inventory.removeItemStackFromSlot(slot,1);
        if(!removed.succeeded())return "transactionFailed";
        if(KELP.equals(expected)){owner=player;return "tamed";}
        saddleItem=item.withQuantity(1);saddled=true;return "saddled";
    }
    boolean canRide(UUID player){return saddled && owner!=null && owner.equals(player);}
    String removeSaddle(UUID player,ItemContainer destination) {
        if(owner==null || !owner.equals(player))return "notOwner";
        if(!saddled)return "needsSaddle";
        var saddle=saddleItem!=null?saddleItem:new ItemStack(SADDLE,1);
        if(destination==null || !destination.canAddItemStack(saddle) || !destination.addItemStack(saddle).succeeded())return "inventoryFull";
        saddled=false;saddleItem=null;return "unsaddled";
    }
    @Override public Component<EntityStore> clone() {
        var result=new WhisperionState();result.owner=owner;result.saddled=saddled;result.saddleItem=saddleItem;result.bornAt=bornAt;
        result.ridingPlayer=ridingPlayer;result.parkFlags=parkFlags;result.parkedModel=parkedModel;return result;
    }
}
