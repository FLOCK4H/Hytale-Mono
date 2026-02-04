package org.example.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Reports exceptions to the player chat (when available) and writes a persistent log file under the plugin data
 * directory.
 */
public final class PluginErrorReporter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PluginBase plugin;
    private final Path logFile;

    public PluginErrorReporter(@Nonnull PluginBase plugin) {
        this.plugin = plugin;
        this.logFile = plugin.getDataDirectory().resolve("logs").resolve("plugin.log");
        ensureLogDirectoryExists();
    }

    public void report(@Nonnull CommandContext ctx, @Nonnull String context, @Nonnull Throwable t) {
        PlayerRef player = resolvePlayer(ctx);
        report(player, context, t);
        if (player == null) {
            try {
                ctx.sendMessage(
                    Message.raw("Error: " + context + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ").")
                );
                ctx.sendMessage(Message.raw("Details saved to: " + logFile));
            } catch (Throwable ignored) {
                // Ignore: we already logged to file + server log.
            }
        }
    }

    public void report(PlayerRef player, @Nonnull String context, @Nonnull Throwable t) {
        try {
            plugin.getLogger().atSevere().withCause(t).log(context);
        } catch (Throwable ignored) {
            LOGGER.atSevere().withCause(t).log(context);
        }

        appendToFile(context, t);
        if (player != null) {
            player.sendMessage(Message.raw("Error: " + context + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")."));
            player.sendMessage(Message.raw("Details saved to: " + logFile));
        }
    }

    private PlayerRef resolvePlayer(@Nonnull CommandContext ctx) {
        try {
            if (!ctx.isPlayer()) {
                return null;
            }
            return Universe.get().getPlayer(ctx.sender().getUuid());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void ensureLogDirectoryExists() {
        try {
            Files.createDirectories(logFile.getParent());
        } catch (Throwable t) {
            try {
                plugin.getLogger()
                    .atSevere()
                    .withCause(t)
                    .log("Failed to create plugin log directory: %s", logFile.getParent());
            } catch (Throwable ignored) {
                LOGGER.atSevere().withCause(t).log("Failed to create plugin log directory: %s", logFile.getParent());
            }
        }
    }

    private void appendToFile(@Nonnull String context, @Nonnull Throwable t) {
        try {
            Files.createDirectories(logFile.getParent());

            StringWriter stringWriter = new StringWriter();
            t.printStackTrace(new PrintWriter(stringWriter));

            String prefix = "[" + Instant.now() + "] ";
            String entry = prefix + context + System.lineSeparator() + stringWriter + System.lineSeparator();
            Files.writeString(
                logFile,
                entry,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (Throwable writeFailure) {
            try {
                plugin.getLogger()
                    .atSevere()
                    .withCause(writeFailure)
                    .log("Failed to write exception log to %s", logFile);
            } catch (Throwable ignored) {
                LOGGER.atSevere().withCause(writeFailure).log("Failed to write exception log to %s", logFile);
            }
        }
    }
}
