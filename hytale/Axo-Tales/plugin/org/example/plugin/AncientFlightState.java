package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


public final class AncientFlightState implements Component<EntityStore> {
    public static final BuilderCodec<AncientFlightState> CODEC=BuilderCodec.builder(AncientFlightState.class,AncientFlightState::new)
        .append(new KeyedCodec<>("FuelSeconds",Codec.FLOAT),(v,x)->v.fuel=Float.isFinite(x)?Math.clamp(x,0,10):0,v->v.fuel).add()
        .append(new KeyedCodec<>("FlightWasActive",Codec.BOOLEAN),(v,x)->v.wasActive=x,v->v.wasActive).add()
        .append(new KeyedCodec<>("PrimaryWasBound",Codec.BOOLEAN),(v,x)->v.primaryBound=x,v->v.primaryBound).add()
        .append(new KeyedCodec<>("OriginalPrimary",Codec.STRING),(v,x)->v.originalPrimary=x,v->v.originalPrimary).add().build();
    float fuel=0;
    boolean wasActive;
    boolean primaryBound;
    String originalPrimary;
    boolean active,awaitingTakeoff;
    boolean toggle(boolean allowed,boolean onGround){
        if(active){active=false;wasActive=false;return true;}
        if(!allowed||fuel<=0)return false;
        active=wasActive=true;awaitingTakeoff=onGround;return true;
    }
    boolean advance(float delta,boolean onGround){
        if(!Float.isFinite(delta)||delta<=0)return false;
        if(onGround&&(!active||!awaitingTakeoff)){fuel=10;active=wasActive=false;return true;}
        if(!onGround)awaitingTakeoff=false;
        if(active){fuel=Math.max(0,fuel-delta);if(fuel<=0)active=wasActive=false;}
        return true;
    }
    @Override public AncientFlightState clone(){var v=new AncientFlightState();v.fuel=fuel;v.wasActive=wasActive;v.active=active;v.awaitingTakeoff=awaitingTakeoff;v.primaryBound=primaryBound;v.originalPrimary=originalPrimary;return v;}
}
