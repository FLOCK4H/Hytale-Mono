package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Example command that toggles a player-centered dynamic light (torch-like local light).
 */
public class ExampleCommand extends CommandBase {

    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 1.0f;

    private static final int MIN_LIGHT_RADIUS = 6;
    private static final int MAX_LIGHT_RADIUS = 24;

    private static final int MAX_LIGHT_INTENSITY = 220;
    private final PluginErrorReporter errors;

    /**
     * Creates the example command and registers the value variant.
     */
    public ExampleCommand(@Nonnull PluginErrorReporter errors) {
        super("example", "Toggles a torch-like light around your player (template command).");
        this.errors = errors;
        this.setPermissionGroup(GameMode.Adventure);
        this.addUsageVariant(new ExampleValueCommand(errors));
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        applyLocalLight(ctx, null, errors);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float normalize01(float value, float min, float max) {
        float clamped = clamp(value, min, max);
        if (max == min) {
            return 0.0f;
        }
        return (clamped - min) / (max - min);
    }

    private static int lerpInt(int a, int b, float t) {
        float clamped = clamp(t, 0.0f, 1.0f);
        return Math.round(a + (b - a) * clamped);
    }

    private static ColorLight toLight(float scale) {
        float t = normalize01(scale, MIN_SCALE, MAX_SCALE);
        int radius = lerpInt(MIN_LIGHT_RADIUS, MAX_LIGHT_RADIUS, t);
        int intensity = lerpInt(
            Math.max(1, Math.round(MAX_LIGHT_INTENSITY * MIN_SCALE)),
            MAX_LIGHT_INTENSITY,
            t
        );

        byte channel = (byte) Math.max(0, Math.min(255, intensity));
        return new ColorLight((byte) radius, channel, channel, channel);
    }

    private static void applyLocalLight(@Nonnull CommandContext ctx, Float value, @Nonnull PluginErrorReporter errors) {
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

            UUID playerUuid = player.getUuid();
            Float requestedValue = value;
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

                    Ref<EntityStore> playerRef = world.getEntityRef(playerUuid);
                    if (playerRef == null) {
                        playerRef = entityStore.getRefFromUUID(playerUuid);
                    }
                    if (playerRef == null) {
                        player.sendMessage(Message.raw("Unable to locate your player entity."));
                        return;
                    }

                    if (requestedValue == null) {
                        store.tryRemoveComponent(playerRef, DynamicLight.getComponentType());
                        player.sendMessage(Message.raw("Local light disabled."));
                        return;
                    }

                    float clamped = clamp(requestedValue, MIN_SCALE, MAX_SCALE);
                    ColorLight light = toLight(clamped);
                    DynamicLight dynamicLight = store.getComponent(playerRef, DynamicLight.getComponentType());
                    if (dynamicLight == null) {
                        store.putComponent(playerRef, DynamicLight.getComponentType(), new DynamicLight(light));
                    } else {
                        dynamicLight.setColorLight(light);
                    }

                    int unsignedRadius = Byte.toUnsignedInt(light.radius);
                    int unsignedIntensity = Byte.toUnsignedInt(light.red);
                    player.sendMessage(
                        Message.raw(
                            "Local light scale set to "
                                + clamped
                                + " (radius "
                                + unsignedRadius
                                + ", intensity "
                                + unsignedIntensity
                                + ")."
                        )
                    );
                } catch (Throwable t) {
                    errors.report(player, "Failed to apply local light.", t);
                }
            });
        } catch (Throwable t) {
            errors.report(ctx, "Failed to run /example.", t);
        }
    }

    private static final class ExampleValueCommand extends CommandBase {

        private final RequiredArg<Float> valueArg;
        private final PluginErrorReporter errors;

        private ExampleValueCommand(@Nonnull PluginErrorReporter errors) {
            super("Sets a torch-like light around your player (template command).");
            this.setPermissionGroup(GameMode.Adventure);
            this.errors = errors;
            this.valueArg = this.withRequiredArg(
                "value",
                "Scale (" + MIN_SCALE + " - " + MAX_SCALE + ").",
                ArgTypes.FLOAT
            );
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            applyLocalLight(ctx, valueArg.get(ctx), errors);
        }
    }
}
