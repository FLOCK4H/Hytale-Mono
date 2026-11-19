package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;


public final class NativeSpellInputInteraction extends SimpleInstantInteraction {
    public static final String TYPE = "AxoTales_Input";
    @FunctionalInterface
    public interface Handler {
        void handle(InteractionType type, InteractionContext context, boolean release, boolean alternate);
    }
    private final Handler handler;
    private boolean release;
    private boolean alternate;
    private boolean wandCast;
    private boolean serverAuthority;

    private NativeSpellInputInteraction(Handler handler) { this.handler = handler; }

    @Override public WaitForDataFrom getWaitForDataFrom() {
        return wandCast || serverAuthority ? WaitForDataFrom.Server : super.getWaitForDataFrom();
    }

    @Override public boolean needsRemoteSync() { return wandCast || serverAuthority || super.needsRemoteSync(); }

    public static BuilderCodec<NativeSpellInputInteraction> codec(Handler handler) {
        return BuilderCodec.builder(NativeSpellInputInteraction.class,
            () -> new NativeSpellInputInteraction(handler), SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Release", Codec.BOOLEAN),
                (value, flag) -> value.release = flag, value -> value.release).add()
            .append(new KeyedCodec<>("Alternate", Codec.BOOLEAN),
                (value, flag) -> value.alternate = flag, value -> value.alternate).add()
            .append(new KeyedCodec<>("WandCast", Codec.BOOLEAN),
                (value, flag) -> value.wandCast = flag, value -> value.wandCast).add()
            .append(new KeyedCodec<>("ServerAuthority", Codec.BOOLEAN),
                (value, flag) -> value.serverAuthority = flag, value -> value.serverAuthority).add().build();
    }

    @Override protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldown) {
        boolean alternateAction = alternate;
        if (wandCast) {
            var movement = context.getCommandBuffer().getComponent(context.getOwningEntity(), MovementStatesComponent.getComponentType());
            alternateAction = movement != null && movement.getMovementStates().crouching;
        }
        handler.handle(type, context, release, alternateAction);


        if (wandCast && alternateAction) context.getState().state = InteractionState.Failed;
    }

    @Override protected void simulateFirstRun(InteractionType type, InteractionContext context, CooldownHandler cooldown) {

    }
}
