package org.example.plugin;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

public final class BloodTaken implements Component<EntityStore> {
    public static final BuilderCodec<BloodTaken> CODEC=BuilderCodec.builder(BloodTaken.class,BloodTaken::new)
        .append(new KeyedCodec<>("Collector",Codec.UUID_STRING),(v,x)->v.collector=x,v->v.collector).add()
        .append(new KeyedCodec<>("Taken",Codec.BOOLEAN),(v,x)->v.taken=x,v->v.taken).add().build();
    UUID collector;
    boolean taken;
    @Override public BloodTaken clone(){var v=new BloodTaken();v.collector=collector;v.taken=taken;return v;}
}
