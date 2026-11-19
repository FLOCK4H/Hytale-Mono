package org.example.plugin;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;


public final class WhisperionRide implements Component<EntityStore> {
    public static final BuilderCodec<WhisperionRide> CODEC=BuilderCodec.builder(WhisperionRide.class,WhisperionRide::new)
        .append(new KeyedCodec<>("HorseUuid",Codec.UUID_STRING),(v,x)->v.horseUuid=x,v->v.horseUuid).add()
        .append(new KeyedCodec<>("OriginalModel",Model.ModelReference.CODEC),(v,x)->v.originalModelRef=x,v->v.originalModelRef).add()
        .append(new KeyedCodec<>("OriginalUse",Codec.STRING),(v,x)->v.originalUse=x,v->v.originalUse).add()
        .append(new KeyedCodec<>("OriginalStatus",Codec.STRING),(v,x)->v.originalStatus=x,v->v.originalStatus).add()
        .append(new KeyedCodec<>("WasFlying",Codec.BOOLEAN),(v,x)->v.wasFlying=x,v->v.wasFlying).add().build();
    Ref<EntityStore> horse;
    UUID horseUuid;
    Model originalModel;
    Model.ModelReference originalModelRef;
    String originalUse;
    String originalStatus;
    Interactions originalInteractions;
    boolean wasFlying;
    boolean initialized;
    String animation;
    long startedAtNanos;
    Integer mountChainId;
    org.joml.Vector3d lastPosition;

    Ref<EntityStore> resolveHorse(Store<EntityStore> store) {
        if(horseUuid!=null && (horse==null || !horse.isValid() || horse.getStore()!=store)) {
            var current=store.getExternalData().getRefFromUUID(horseUuid);
            if(current!=null && current.isValid() && current.getStore()==store)horse=current;
        }
        return horse;
    }

    boolean claimHorse(Ref<EntityStore> candidate,UUID uuid) {
        if(originalModel==null || candidate==null || !candidate.isValid() || horseUuid==null || !horseUuid.equals(uuid))return false;
        horse=candidate;return true;
    }

    String dismountDecision(long expectedStart,Integer chainId,long now) {
        if(expectedStart!=startedAtNanos)return "staleRide";
        if(mountChainId!=null && mountChainId.equals(chainId))return "mountChainReplay";
        if(now-startedAtNanos<500_000_000L)return "mountTransition";
        if(!initialized)return "flightStarting";
        return "dismount";
    }
    @Override public Component<EntityStore> clone() {
        var copy=new WhisperionRide();copy.horse=horse;copy.horseUuid=horseUuid;copy.originalModel=originalModel;
        copy.originalModelRef=originalModelRef;copy.originalUse=originalUse;copy.originalStatus=originalStatus;
        copy.originalInteractions=originalInteractions==null?null:(Interactions)originalInteractions.clone();
        copy.wasFlying=wasFlying;copy.initialized=initialized;copy.animation=animation;
        copy.startedAtNanos=startedAtNanos;copy.mountChainId=mountChainId;
        copy.lastPosition=lastPosition==null?null:new org.joml.Vector3d(lastPosition);return copy;
    }
}
