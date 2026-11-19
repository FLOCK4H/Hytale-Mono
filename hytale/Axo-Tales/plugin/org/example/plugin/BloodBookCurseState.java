package org.example.plugin;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BloodBookCurseState implements Component<EntityStore> {
    public static final BuilderCodec<BloodBookCurseState> CODEC=BuilderCodec.builder(BloodBookCurseState.class,BloodBookCurseState::new)
        .append(new KeyedCodec<>("ExposureSeconds",Codec.DOUBLE),(v,x)->v.seconds=Double.isFinite(x)?Math.clamp(x,0,5):0,v->v.seconds).add().build();
    double seconds;
    boolean advance(boolean carried,boolean alive,double elapsed) {
        if(!carried || !alive){seconds=0;return false;}
        seconds+=Double.isFinite(elapsed)?Math.clamp(elapsed,0,1):0;
        if(seconds+1e-6<5)return false;
        seconds=Math.max(0,seconds-5);return true;
    }
    @Override public BloodBookCurseState clone(){var value=new BloodBookCurseState();value.seconds=seconds;return value;}
}
