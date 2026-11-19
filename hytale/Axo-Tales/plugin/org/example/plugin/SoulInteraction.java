package org.example.plugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import java.util.function.BiConsumer;


public final class SoulInteraction extends SimpleInstantInteraction {
    public static final String TYPE="AxoTales_Soul";
    private final BiConsumer<InteractionType,InteractionContext> handler;
    private SoulInteraction(BiConsumer<InteractionType,InteractionContext> handler){this.handler=handler;}
    public static BuilderCodec<SoulInteraction> codec(BiConsumer<InteractionType,InteractionContext> handler) {
        return BuilderCodec.builder(SoulInteraction.class,()->new SoulInteraction(handler),SimpleInstantInteraction.CODEC).build();
    }
    @Override public WaitForDataFrom getWaitForDataFrom(){return WaitForDataFrom.Server;}
    @Override public boolean needsRemoteSync(){return true;}
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){handler.accept(type,context);}
    @Override protected void simulateFirstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){}
}
