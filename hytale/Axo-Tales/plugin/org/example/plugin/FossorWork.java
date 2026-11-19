package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;


public final class FossorWork implements Component<EntityStore> {
    public static final BuilderCodec<FossorWork> CODEC=BuilderCodec.builder(FossorWork.class,FossorWork::new)
        .append(new KeyedCodec<>("Soul",Codec.UUID_STRING),(v,x)->v.soul=x,v->v.soul).add().build();
    UUID soul;
    public FossorWork(){}
    FossorWork(UUID soul){this.soul=soul;}
    @Override public FossorWork clone(){return new FossorWork(soul);}
}
