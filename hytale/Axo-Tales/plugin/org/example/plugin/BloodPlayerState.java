package org.example.plugin;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BloodPlayerState implements Component<EntityStore> {
    public static final BuilderCodec<BloodPlayerState> CODEC=BuilderCodec.builder(BloodPlayerState.class,BloodPlayerState::new)
        .append(new KeyedCodec<>("LastDamageMillis",Codec.LONG),(v,x)->v.lastDamage=x,v->v.lastDamage).add()
        .append(new KeyedCodec<>("DamageRevision",Codec.LONG),(v,x)->v.revision=x,v->v.revision).add().build();
    long lastDamage,revision;
    @Override public BloodPlayerState clone(){var v=new BloodPlayerState();v.lastDamage=lastDamage;v.revision=revision;return v;}
}
