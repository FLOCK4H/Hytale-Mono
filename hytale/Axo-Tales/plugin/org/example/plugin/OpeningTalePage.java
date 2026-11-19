package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;


final class OpeningTalePage extends InteractiveCustomUIPage<OpeningTalePage.Action> {
    static final class Action {
        static final BuilderCodec<Action> CODEC = BuilderCodec.builder(Action.class, Action::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add().build();
        String action;
    }
    private final Runnable acknowledged;
    private boolean finished;

    OpeningTalePage(PlayerRef player, Runnable acknowledged) {
        super(player, CustomPageLifetime.CanDismiss, Action.CODEC);
        this.acknowledged = acknowledged;
    }
    @Override public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Pages/AxoOpeningTale.ui");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Continue", EventData.of("Action", "Close"));
    }
    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Action action) {
        if (!finished && "Close".equals(action.action)) { acknowledge(); close(); }
    }
    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) { acknowledge(); }
    private void acknowledge() {
        if (finished) return;
        finished = true;
        acknowledged.run();
    }
}
