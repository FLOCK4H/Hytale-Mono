package org.example.plugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;


public final class AstralBagInteraction extends SimpleInstantInteraction {
    public static final String TYPE="AxoTales_AstralBag";
    @FunctionalInterface public interface Handler {void open(InteractionContext context);}
    private final Handler handler;
    private AstralBagInteraction(Handler handler){this.handler=handler;}
    public static BuilderCodec<AstralBagInteraction> codec(Handler handler) {
        return BuilderCodec.builder(AstralBagInteraction.class,()->new AstralBagInteraction(handler),SimpleInstantInteraction.CODEC).build();
    }
    @Override public WaitForDataFrom getWaitForDataFrom(){return WaitForDataFrom.Server;}
    @Override public boolean needsRemoteSync(){return true;}
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){handler.open(context);}
    @Override protected void simulateFirstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){}
}
