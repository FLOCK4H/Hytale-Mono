package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;


public final class RelicInteraction extends SimpleInstantInteraction {
    public static final String TYPE="AxoTales_Relic";
    @FunctionalInterface public interface Handler {boolean handle(InteractionContext context,String mode);}
    private final Handler handler;
    private String mode="draw";
    private RelicInteraction(Handler handler){this.handler=handler;}
    public static BuilderCodec<RelicInteraction> codec(Handler handler){return BuilderCodec.builder(RelicInteraction.class,()->new RelicInteraction(handler),SimpleInstantInteraction.CODEC)
        .append(new KeyedCodec<>("Mode",Codec.STRING),(v,x)->v.mode=x,v->v.mode).add().build();}
    @Override public WaitForDataFrom getWaitForDataFrom(){return WaitForDataFrom.Server;}
    @Override public boolean needsRemoteSync(){return true;}
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){if(!handler.handle(context,mode))context.getState().state=InteractionState.Failed;}
    @Override protected void simulateFirstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){}
}
