package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


public final class TomTomState implements Component<EntityStore> {
    public static final BuilderCodec<TomTomState> CODEC=BuilderCodec.builder(TomTomState.class,TomTomState::new)
        .append(new KeyedCodec<>("BornAt",Codec.LONG),(v,x)->v.bornAt=x,v->v.bornAt).add()
        .append(new KeyedCodec<>("Natural",Codec.BOOLEAN),(v,x)->v.natural=x,v->v.natural).add()
        .append(new KeyedCodec<>("FuseDeadline",Codec.LONG),(v,x)->v.fuseDeadline=x,v->v.fuseDeadline).add()
        .append(new KeyedCodec<>("Finished",Codec.BOOLEAN),(v,x)->v.finished=x,v->v.finished).add().build();
    long bornAt,fuseDeadline;
    boolean natural,finished;
    boolean prime(long now){if(fuseDeadline!=0 || finished)return false;fuseDeadline=now+2667;return true;}
    boolean finish(){if(fuseDeadline==0 || finished)return false;finished=true;return true;}
    boolean expired(long now){return fuseDeadline>0 && !finished && now>=fuseDeadline;}
    @Override public TomTomState clone(){var copy=new TomTomState();copy.bornAt=bornAt;copy.natural=natural;copy.fuseDeadline=fuseDeadline;copy.finished=finished;return copy;}
}
