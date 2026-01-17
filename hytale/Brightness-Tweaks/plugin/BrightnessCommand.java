package org.example.plugin;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Adjusts a player-centered dynamic light to mimic a torch-like brightness boost.
 */
public class BrightnessCommand extends CommandBase {
    private final BrightnessService brightnessService;

    /**
     * Creates the brightness command and registers the value variant.
     */
    public BrightnessCommand(@Nonnull BrightnessService brightnessService) {
        super("brightness", "Adjusts a torch-like light around your player.");
        this.setPermissionGroup(GameMode.Adventure);
        this.brightnessService = brightnessService;
        this.addUsageVariant(new BrightnessValueCommand());
        this.addSubCommand(new BrightnessColorCommand());
        this.addSubCommand(new BrightnessWarmthCommand());
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        applyBrightness(ctx, null);
    }

    private void applyBrightness(@Nonnull CommandContext ctx, Float value) {
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
        brightnessService.setDesiredBrightness(playerUuid, value);
        brightnessService.syncPlayer(world, playerUuid, true);
    }

    private void applyTintRgb(@Nonnull CommandContext ctx, Integer rgb) {
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
        int rgbMasked = rgb & 0xFFFFFF;
        brightnessService.setDesiredTintRgb(playerUuid, rgbMasked);
        brightnessService.syncPlayer(world, playerUuid, false);

        int red = (rgbMasked >> 16) & 0xFF;
        int green = (rgbMasked >> 8) & 0xFF;
        int blue = rgbMasked & 0xFF;
        ctx.sendMessage(Message.raw("Brightness tint set to rgb " + red + "/" + green + "/" + blue + "."));
    }

    private void clearTintRgb(@Nonnull CommandContext ctx) {
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
        brightnessService.setDesiredTintRgb(playerUuid, null);
        brightnessService.syncPlayer(world, playerUuid, false);
        ctx.sendMessage(Message.raw("Brightness tint cleared (reverts to the torch's default tint)."));
    }

    private void applyWarmth(@Nonnull CommandContext ctx, Float warmth01) {
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
        float clamped = Math.max(0.0f, Math.min(1.0f, warmth01));
        brightnessService.setDesiredWarmth(playerUuid, clamped);
        brightnessService.syncPlayer(world, playerUuid, false);
        ctx.sendMessage(Message.raw("Brightness warmth set to " + clamped + " (0 = torch tint, 1 = warmer torch tint)."));
    }

    private void clearWarmth(@Nonnull CommandContext ctx) {
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
        brightnessService.setDesiredWarmth(playerUuid, null);
        brightnessService.syncPlayer(world, playerUuid, false);
        ctx.sendMessage(Message.raw("Brightness warmth cleared (reverts to the torch's default tint)."));
    }

    private final class BrightnessValueCommand extends CommandBase {

        private final RequiredArg<Float> valueArg;

        private BrightnessValueCommand() {
            super("Adjusts a torch-like light around your player.");
            this.setPermissionGroup(GameMode.Adventure);
            this.valueArg = this.withRequiredArg(
                "value",
                "Brightness (" + BrightnessService.MIN_BRIGHTNESS + " - " + BrightnessService.MAX_BRIGHTNESS + ").",
                ArgTypes.FLOAT
            );
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            BrightnessCommand.this.applyBrightness(ctx, valueArg.get(ctx));
        }
    }

    private final class BrightnessColorCommand extends CommandBase {

        private BrightnessColorCommand() {
            super("color", "Sets the tint of the boosted light (hex color like #FFAA00).");
            this.setPermissionGroup(GameMode.Adventure);
            this.addUsageVariant(new BrightnessColorValueCommand());
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            BrightnessCommand.this.clearTintRgb(ctx);
        }
    }

    private final class BrightnessWarmthCommand extends CommandBase {

        private BrightnessWarmthCommand() {
            super("warmth", "Sets warmth for the boosted light (0.0 - 1.0).");
            this.setPermissionGroup(GameMode.Adventure);
            this.addUsageVariant(new BrightnessWarmthValueCommand());
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            BrightnessCommand.this.clearWarmth(ctx);
        }
    }

    private final class BrightnessColorValueCommand extends CommandBase {

        private final RequiredArg<Integer> colorArg;

        private BrightnessColorValueCommand() {
            super("Sets the tint of the boosted light (hex color like #FFAA00).");
            this.setPermissionGroup(GameMode.Adventure);
            this.colorArg = this.withRequiredArg("color", "Hex color like #FFAA00.", ArgTypes.COLOR);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            BrightnessCommand.this.applyTintRgb(ctx, colorArg.get(ctx));
        }
    }

    private final class BrightnessWarmthValueCommand extends CommandBase {

        private final RequiredArg<Float> warmthArg;

        private BrightnessWarmthValueCommand() {
            super("Sets warmth for the boosted light (0.0 - 1.0).");
            this.setPermissionGroup(GameMode.Adventure);
            this.warmthArg = this.withRequiredArg("value", "Warmth from 0.0 (torch tint) to 1.0 (warmer).", ArgTypes.FLOAT);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            BrightnessCommand.this.applyWarmth(ctx, warmthArg.get(ctx));
        }
    }
}
