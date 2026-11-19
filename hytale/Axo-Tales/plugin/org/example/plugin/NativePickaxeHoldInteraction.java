package org.example.plugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import java.util.function.Predicate;


public final class NativePickaxeHoldInteraction extends ChargingInteraction {
    public static final String TYPE="AxoTales_PickaxeHold";
    private final Predicate<InteractionContext> handler;
    private NativePickaxeHoldInteraction(Predicate<InteractionContext> handler){this.handler=handler;}
    static BuilderCodec<NativePickaxeHoldInteraction> codec(Predicate<InteractionContext> handler){
        return BuilderCodec.builder(NativePickaxeHoldInteraction.class,()->new NativePickaxeHoldInteraction(handler),ChargingInteraction.CODEC).build();
    }
    @Override protected void tick0(boolean firstRun,float time,InteractionType type,InteractionContext context,CooldownHandler cooldown){
        super.tick0(firstRun,time,type,context,cooldown);
        if(firstRun&&context.getClientState().chargeValue==-1f&&context.getState().state==InteractionState.NotFinished&&!handler.test(context))context.getState().state=InteractionState.Failed;
    }
}
