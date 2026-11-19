package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.joml.Vector3d;


public final class GrayCatState implements Component<EntityStore> {
    static final String ROLE="AxoTales_Gray_Cat";
    public static final BuilderCodec<GrayCatState> CODEC=BuilderCodec.builder(GrayCatState.class,GrayCatState::new)
        .append(new KeyedCodec<>("Owner",Codec.UUID_STRING),(v,x)->v.owner=x,v->v.owner).add()
        .append(new KeyedCodec<>("BornAt",Codec.LONG),(v,x)->v.bornAt=x,v->v.bornAt).add()
        .append(new KeyedCodec<>("Natural",Codec.BOOLEAN),(v,x)->v.natural=x,v->v.natural).add().build();
    UUID owner;long bornAt;boolean natural;
    transient boolean following,transferPending;
    transient long nextCatchup,nextRest,restUntil,stuckSince,nextRecord;
    transient Vector3d lastPosition;
    static boolean rawFish(String id){return id!=null && (id.equals("Food_Fish_Raw") || id.equals("Food_Fish_Raw_Uncommon") || id.equals("Food_Fish_Raw_Rare") || id.equals("Food_Fish_Raw_Epic") || id.equals("Food_Fish_Raw_Legendary"));}
    boolean bind(UUID dropper,Runnable consume){if(owner!=null || dropper==null)return false;consume.run();owner=dropper;natural=false;return true;}
    boolean follow(double distanceSquared){if(distanceSquared>15*15)following=true;else if(distanceSquared<=8*8)following=false;return following;}
    @Override public GrayCatState clone(){var copy=new GrayCatState();copy.owner=owner;copy.bornAt=bornAt;copy.natural=natural;return copy;}
}
