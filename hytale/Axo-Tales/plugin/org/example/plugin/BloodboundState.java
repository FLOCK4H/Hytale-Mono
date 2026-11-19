package org.example.plugin;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

public final class BloodboundState implements Component<EntityStore> {
    public static final BuilderCodec<BloodboundState> CODEC=BuilderCodec.builder(BloodboundState.class,BloodboundState::new)
        .append(new KeyedCodec<>("Owner",Codec.UUID_STRING),(v,x)->v.owner=x,v->v.owner).add()
        .append(new KeyedCodec<>("Bloodline",BloodSample.CODEC),(v,x)->v.sample=x,v->v.sample).add().build();
    UUID owner;
    BloodSample sample;
    transient UUID target;
    transient long targetUntil,nextShot,nextCatchup;
    @Override public BloodboundState clone(){var v=new BloodboundState();v.owner=owner;v.sample=sample;return v;}
}
