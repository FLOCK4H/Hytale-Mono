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
 * Adjusts a player-centered dynamic light to mimic a torch-like brightness boost.
 */
public class BrightnessCommand extends CommandBase {

    private static final float MIN_BRIGHTNESS = 0.1f;
    private static final float MAX_BRIGHTNESS = 1.0f;

    private static final int MIN_LIGHT_RADIUS = 6;
    private static final int MAX_LIGHT_RADIUS = 24;

    private static final int MAX_LIGHT_INTENSITY = 220;

    /**
     * Creates the brightness command and registers the value variant.
     */
    public BrightnessCommand() {
        super("brightness", "Adjusts a torch-like light around your player.");
        this.setPermissionGroup(GameMode.Adventure);
        this.addUsageVariant(new BrightnessValueCommand());
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        applyBrightness(ctx, null);
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
        float t = normalize01(scale, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
        int radius = lerpInt(MIN_LIGHT_RADIUS, MAX_LIGHT_RADIUS, t);
        int intensity = lerpInt(
            Math.max(1, Math.round(MAX_LIGHT_INTENSITY * MIN_BRIGHTNESS)),
            MAX_LIGHT_INTENSITY,
            t
        );

        byte channel = (byte) Math.max(0, Math.min(255, intensity));
        return new ColorLight((byte) radius, channel, channel, channel);
    }

    private static void applyBrightness(@Nonnull CommandContext ctx, Float value) {
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
                player.sendMessage(Message.raw("Brightness reset. Local light disabled."));
                return;
            }

            float clamped = clamp(requestedValue, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
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
                    "Local light set to " + clamped + " (radius " + unsignedRadius + ", intensity " + unsignedIntensity + ")."
                )
            );
        });
    }

    private static final class BrightnessValueCommand extends CommandBase {

        private final RequiredArg<Float> valueArg;

        private BrightnessValueCommand() {
            super("Adjusts a torch-like light around your player.");
            this.setPermissionGroup(GameMode.Adventure);
            this.valueArg = this.withRequiredArg(
                "value",
                "Brightness (" + MIN_BRIGHTNESS + " - " + MAX_BRIGHTNESS + ").",
                ArgTypes.FLOAT
            );
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            applyBrightness(ctx, valueArg.get(ctx));
        }
    }
}
