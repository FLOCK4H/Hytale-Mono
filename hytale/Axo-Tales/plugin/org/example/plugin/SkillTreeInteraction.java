package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import java.util.function.BiPredicate;


public final class SkillTreeInteraction extends SimpleInstantInteraction {
    static final String TYPE="AxoTales_SkillTree";
    private String mode="open";
    private final BiPredicate<String,InteractionContext> handler;
    private SkillTreeInteraction(BiPredicate<String,InteractionContext> handler){this.handler=handler;}
    static BuilderCodec<SkillTreeInteraction> codec(BiPredicate<String,InteractionContext> handler){return BuilderCodec.builder(SkillTreeInteraction.class,()->new SkillTreeInteraction(handler),SimpleInstantInteraction.CODEC)
        .append(new KeyedCodec<>("Mode",Codec.STRING),(s,v)->s.mode=v,s->s.mode).add().build();}
    @Override public WaitForDataFrom getWaitForDataFrom(){return WaitForDataFrom.Server;}
    @Override public boolean needsRemoteSync(){return true;}
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){if(!handler.test(mode,context))context.getState().state=InteractionState.Failed;}
    @Override protected void simulateFirstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown){}
}
