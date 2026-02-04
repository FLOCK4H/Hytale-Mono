package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Debug helper: force-place a visible crystal marker at the player's current column.
 */
public final class AxoPlaceholderCommand extends CommandBase {

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final CustomPlaceholderBlockWorldgen worldgen;

    public AxoPlaceholderCommand(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull CustomPlaceholderBlockWorldgen worldgen
    ) {
        super("axoplaceholder", "Places a visible Arcane Crystal marker at your current position (debug).");
        this.errors = errors;
        this.debug = debug;
        this.worldgen = worldgen;
        this.setPermissionGroup(GameMode.Creative);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        try {
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

            world.execute(() -> {
                try {
                    if (!player.isValid()) {
                        return;
                    }

                    EntityStore entityStore = world.getEntityStore();
                    if (entityStore == null) {
                        player.sendMessage(Message.raw("Unable to access the world entity store."));
                        return;
                    }

                    Store<EntityStore> store = entityStore.getStore();
                    if (store == null) {
                        player.sendMessage(Message.raw("Unable to access the world entity state."));
                        return;
                    }

                    Ref<EntityStore> playerEntityRef = world.getEntityRef(player.getUuid());
                    if (playerEntityRef == null) {
                        playerEntityRef = entityStore.getRefFromUUID(player.getUuid());
                    }
                    if (playerEntityRef == null) {
                        player.sendMessage(Message.raw("Unable to locate your player entity."));
                        return;
                    }

                    TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
                    if (transform == null) {
                        player.sendMessage(Message.raw("Unable to read your position (missing TransformComponent)."));
                        return;
                    }

                    var pos = transform.getPosition();
                    int blockX = (int) Math.floor(pos.getX());
                    int blockZ = (int) Math.floor(pos.getZ());

                    long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
                    WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                    if (chunk == null) {
                        chunk = world.getChunkIfInMemory(chunkIndex);
                    }
                    if (chunk == null) {
                        player.sendMessage(Message.raw("Your chunk is not loaded; move a bit and try again."));
                        return;
                    }

                    int localX = blockX - (chunk.getX() * 32);
                    int localZ = blockZ - (chunk.getZ() * 32);

                    int placed = worldgen.placeMarkerAtPlayerColumn(world, chunk, localX, localZ, player);
                    if (placed <= 0) {
                        player.sendMessage(Message.raw("Failed to place the Arcane Crystal marker at your column."));
                        debug.traceFileOnly(player, "AxoPlaceholderCommand: placement failed x=" + blockX + " z=" + blockZ + " world=" + world.getName());
                        return;
                    }

                    int surfaceY = chunk.getHeight(localX, localZ);
                    int baseY = Math.max(1, Math.min(ChunkUtil.HEIGHT_MINUS_1, surfaceY + 1));
                    player.sendMessage(Message.raw("Placed Arcane Crystal marker at x=" + blockX + " y=" + baseY + " z=" + blockZ + " (height " + placed + ")."));
                } catch (Throwable t) {
                    errors.report(player, "Failed to run /axoplaceholder.", t);
                }
            });
        } catch (Throwable t) {
            errors.report(ctx, "Failed to run /axoplaceholder.", t);
        }
    }
}
