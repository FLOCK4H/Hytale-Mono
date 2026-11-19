package org.example.plugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;


public final class NativeLeechHoldInteraction extends ChargingInteraction {
    public static final String TYPE="AxoTales_LeechHold";
    private final NativeSpellInputInteraction.Handler handler;

    private NativeLeechHoldInteraction(NativeSpellInputInteraction.Handler handler){this.handler=handler;}

    public static BuilderCodec<NativeLeechHoldInteraction> codec(NativeSpellInputInteraction.Handler handler){
        return BuilderCodec.builder(NativeLeechHoldInteraction.class,
            ()->new NativeLeechHoldInteraction(handler),ChargingInteraction.CODEC).build();
    }

    @Override protected void tick0(boolean firstRun,float time,InteractionType type,InteractionContext context,CooldownHandler cooldown){
        super.tick0(firstRun,time,type,context,cooldown);


        if(firstRun&&context.getClientState().chargeValue==-1f
                &&context.getState().state==InteractionState.NotFinished)
            handler.handle(type,context,false,false);
    }


}
