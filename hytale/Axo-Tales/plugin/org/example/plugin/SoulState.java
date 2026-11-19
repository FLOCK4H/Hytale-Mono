package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.joml.Vector3d;


public final class SoulState implements Component<EntityStore> {
    static final float MINING_SECONDS=120;
    static final String META="AxoTalesSoul", CRYSTAL="Axo_Arcane_Crystal_Shards", CRYSTAL_BLOCK="Axo_Arcane_Crystal";
    public static final BuilderCodec<SoulState> CODEC=BuilderCodec.builder(SoulState.class,SoulState::new)
        .append(new KeyedCodec<>("Owner",Codec.UUID_STRING),(v,x)->v.owner=x,v->v.owner).add()
        .append(new KeyedCodec<>("Kind",Codec.STRING),(v,x)->v.kind=x,v->v.kind).add()
        .append(new KeyedCodec<>("AnchorX",Codec.DOUBLE),(v,x)->v.x=x,v->v.x).add()
        .append(new KeyedCodec<>("AnchorY",Codec.DOUBLE),(v,x)->v.y=x,v->v.y).add()
        .append(new KeyedCodec<>("AnchorZ",Codec.DOUBLE),(v,x)->v.z=x,v->v.z).add()
        .append(new KeyedCodec<>("Cargo",SimpleItemContainer.CODEC),(v,x)->v.cargo=x,v->v.cargo).add()
        .append(new KeyedCodec<>("CrystalFeeds",Codec.INTEGER),(v,x)->v.feeds=Math.clamp(x,0,200),v->v.feeds).add()
        .append(new KeyedCodec<>("WorkSeconds",Codec.FLOAT),(v,x)->v.workSeconds=Float.isFinite(x)?Math.clamp(x,0,MINING_SECONDS):0,v->v.workSeconds).add()
        .append(new KeyedCodec<>("WorkItem",Codec.STRING),(v,x)->v.workItem=x,v->v.workItem).add().build();
    UUID owner;
    transient UUID workEntity;
    String kind, workItem;
    double x,y,z;
    int feeds;
    float workSeconds;
    SimpleItemContainer cargo=new SimpleItemContainer((short)9);
    transient UUID target;
    transient long targetUntil, nextShot, lastUse, nextWorkCast;
    transient long missingWorkSince;
    transient double scanSeconds;
    transient String animation;
    transient boolean workBlocked;
    transient boolean combatMoving, returning;
    transient Vector3d destination;
    transient Vector3d lookAt, previousOwner, wanderAt;
    transient double wanderSeconds;
    transient int wanderStep;
    transient int formationSlot=-1;
    SoulKind type(){return SoulKind.role(kind);}
    boolean advanceMining(float delta){
        if(Float.isFinite(delta)&&delta>0)workSeconds=Math.min(MINING_SECONDS,workSeconds+Math.min(delta,.25f));
        return workSeconds>=MINING_SECONDS;
    }
    Vector3d anchor(){return new Vector3d(x,y,z);}
    void anchor(Vector3d at){x=at.x;y=at.y;z=at.z;}
    String feed(UUID player,ItemContainer source,short slot,String expected) {
        return feed(player,source,slot,expected,false);
    }
    String feed(UUID player,ItemContainer source,short slot,String expected,boolean wholeStack) {
        if(owner==null || !owner.equals(player))return "notOwner";
        if(type()!=SoulKind.FOSSOR || !isCrystal(expected))return "wrongItem";
        if(feeds>=200)return "maxPower";
        if(source==null || slot<0 || slot>=source.getCapacity())return "missingItem";
        var stack=source.getItemStack(slot);
        if(ItemStack.isEmpty(stack) || !expected.equals(stack.getItemId()))return "itemChanged";
        int count=Math.min(200-feeds,wholeStack?stack.getQuantity():1);


        if(!source.removeItemStackFromSlot(slot,stack,count).succeeded())return "transactionFailed";
        feeds+=count;return "empowered";
    }
    static boolean isCrystal(String id){return CRYSTAL.equals(id)||CRYSTAL_BLOCK.equals(id);}
    boolean mayReturn(UUID player){return owner!=null && owner.equals(player);}
    ItemStack token(UUID player) {
        var saved=clone();saved.owner=player;saved.workEntity=null;
        return new ItemStack(type().item,1).withMetadata(META,CODEC,saved);
    }
    @Override public SoulState clone() {
        var v=new SoulState();v.owner=owner;v.kind=kind;v.x=x;v.y=y;v.z=z;v.cargo=cargo.clone();
        v.feeds=feeds;v.workSeconds=workSeconds;v.workItem=workItem;v.workEntity=workEntity;
        v.target=target;v.targetUntil=targetUntil;v.nextShot=nextShot;v.lastUse=lastUse;v.nextWorkCast=nextWorkCast;
        v.scanSeconds=scanSeconds;v.animation=animation;v.destination=destination==null?null:new Vector3d(destination);
        v.lookAt=lookAt==null?null:new Vector3d(lookAt);v.combatMoving=combatMoving;v.returning=returning;
        v.previousOwner=previousOwner==null?null:new Vector3d(previousOwner);v.wanderAt=wanderAt==null?null:new Vector3d(wanderAt);v.wanderSeconds=wanderSeconds;v.wanderStep=wanderStep;
        v.workBlocked=workBlocked;v.missingWorkSince=missingWorkSince;return v;
    }
}
