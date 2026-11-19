package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;


public final class TomTomInteraction extends SimpleInstantInteraction {
    public static final String TYPE="AxoTales_TomTom";
    @FunctionalInterface public interface Handler {boolean handle(InteractionContext context,boolean detonate);}
    private final Handler handler;
    private boolean detonate;
    private TomTomInteraction(Handler handler){this.handler=handler;}
    public static BuilderCodec<TomTomInteraction> codec(Handler handler){return BuilderCodec.builder(TomTomInteraction.class,()->new TomTomInteraction(handler),SimpleInstantInteraction.CODEC)
        .append(new KeyedCodec<>("Detonate",Codec.BOOLEAN),(v,x)->v.detonate=x,v->v.detonate).add().build();}
    @Override public WaitForDataFrom getWaitForDataFrom(){return WaitForDataFrom.Server;}
    @Override public boolean needsRemoteSync(){return true;}
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){if(!handler.handle(context,detonate) && !detonate)context.getState().state=InteractionState.Failed;}
    @Override protected void simulateFirstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){}
}
