package org.example.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Minimal persistent debug logger for player-reproducible issues.
 */
public final class PluginDebugReporter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PluginBase plugin;
    private final Path logFile;

    public PluginDebugReporter(@Nonnull PluginBase plugin) {
        this.plugin = plugin;
        this.logFile = plugin.getDataDirectory().resolve("logs").resolve("spellbooks-debug.log");
        ensureLogDirectoryExists();
        appendToFile("[" + Instant.now() + "] Debug log initialized.");
    }

    public Path getLogFile() {
        return logFile;
    }

    public void trace(@Nullable PlayerRef player, @Nonnull String message) {
        String prefix = "[" + Instant.now() + "] ";
        String withPlayer = player != null && player.getUuid() != null
            ? prefix + "[" + player.getUuid() + "] " + message
            : prefix + message;

        try {
            plugin.getLogger().atInfo().log(withPlayer);
        } catch (Throwable ignored) {
            LOGGER.atInfo().log(withPlayer);
        }

        appendToFile(withPlayer);
    }

    /**
     * Writes a debug line to the persistent debug log without spamming the server console.
     */
    public void traceFileOnly(@Nullable PlayerRef player, @Nonnull String message) {
        String prefix = "[" + Instant.now() + "] ";
        String withPlayer = player != null && player.getUuid() != null
            ? prefix + "[" + player.getUuid() + "] " + message
            : prefix + message;

        appendToFile(withPlayer);
    }

    private void ensureLogDirectoryExists() {
        try {
            Files.createDirectories(logFile.getParent());
        } catch (Throwable t) {
            try {
                plugin.getLogger()
                    .atWarning()
                    .withCause(t)
                    .log("Failed to create debug log directory: %s", logFile.getParent());
            } catch (Throwable ignored) {
                LOGGER.atWarning().withCause(t).log("Failed to create debug log directory: %s", logFile.getParent());
            }
        }
    }

    private void appendToFile(@Nonnull String line) {
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(
                logFile,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (Throwable ignored) {
            // Best-effort: console logging already happened.
        }
    }
}
