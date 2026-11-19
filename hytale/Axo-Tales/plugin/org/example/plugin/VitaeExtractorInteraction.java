package org.example.plugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import java.util.function.Predicate;


public final class VitaeExtractorInteraction extends SimpleInstantInteraction {
    static final String TYPE="AxoTales_Vitae";
    private final Predicate<InteractionContext> open;
    private VitaeExtractorInteraction(Predicate<InteractionContext> open){this.open=open;}
    static BuilderCodec<VitaeExtractorInteraction> codec(Predicate<InteractionContext> open){return BuilderCodec.builder(VitaeExtractorInteraction.class,()->new VitaeExtractorInteraction(open),SimpleInstantInteraction.CODEC).build();}
    @Override public WaitForDataFrom getWaitForDataFrom(){return WaitForDataFrom.Server;}
    @Override public boolean needsRemoteSync(){return true;}
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){if(!open.test(context))context.getState().state=InteractionState.Failed;}
    @Override protected void simulateFirstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){}
}
