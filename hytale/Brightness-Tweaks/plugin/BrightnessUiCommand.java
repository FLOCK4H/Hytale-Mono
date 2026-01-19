package org.example.plugin;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;

/**
 * Opens the Brightness Tweaks UI for the executing player.
 */
public final class BrightnessUiCommand extends CommandBase {

    private final BrightnessService brightnessService;
    private final BrightnessTweaksConfigStore configStore;

    public BrightnessUiCommand(@Nonnull BrightnessService brightnessService, @Nonnull BrightnessTweaksConfigStore configStore) {
        super("br", "Opens the Brightness Tweaks settings UI.");
        this.setPermissionGroup(GameMode.Adventure);
        this.brightnessService = brightnessService;
        this.configStore = configStore;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("This command can only be used by a player."));
            return;
        }

        PlayerRef player = Universe.get().getPlayer(ctx.sender().getUuid());
        if (player == null) {
            ctx.sendMessage(Message.raw("Unable to find your player session."));
            return;
        }

        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            ctx.sendMessage(Message.raw("Unable to find your current world."));
            return;
        }

        world.execute(() -> openPageOnWorldThread(ctx, player, world));
    }

    private void openPageOnWorldThread(@Nonnull CommandContext ctx, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        EntityStore entityStore = world.getEntityStore();
        if (entityStore == null) {
            ctx.sendMessage(Message.raw("Unable to open UI (no EntityStore)."));
            return;
        }

        Store<EntityStore> store = entityStore.getStore();
        if (store == null) {
            ctx.sendMessage(Message.raw("Unable to open UI (no Store)."));
            return;
        }

        Ref<EntityStore> ref = world.getEntityRef(playerRef.getUuid());
        if (ref == null) {
            ref = entityStore.getRefFromUUID(playerRef.getUuid());
        }
        if (ref == null) {
            ctx.sendMessage(Message.raw("Unable to open UI (no player entity ref)."));
            return;
        }

        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            ctx.sendMessage(Message.raw("Unable to open UI (player entity missing)."));
            return;
        }

        playerEntity
            .getPageManager()
            .openCustomPage(ref, store, new BrightnessTweaksPage(playerRef, brightnessService, configStore));
    }
}

