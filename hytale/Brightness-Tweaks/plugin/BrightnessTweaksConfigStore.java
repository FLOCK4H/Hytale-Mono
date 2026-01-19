package org.example.plugin;

import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loads and persists Brightness Tweaks settings in a global location next to the plugin jar.
 */
public final class BrightnessTweaksConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Object mutationLock = new Object();
    private final Object saveLock = new Object();

    private final Path configDir;
    private final String configName;
    private final Path configPath;
    private final Config<BrightnessTweaksConfig> config;

    private CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);

    /**
     * Creates a config store rooted at the provided directory.
     */
    public BrightnessTweaksConfigStore(@Nonnull Path configDir, @Nonnull String configName) {
        this.configDir = configDir;
        this.configName = configName;
        this.configPath = configDir.resolve(configName + ".json");
        this.config = new Config<>(configDir, configName, BrightnessTweaksConfig.CODEC);
    }

    /**
     * Creates a config store intended to be global across worlds by storing it next to the plugin jar.
     */
    @Nonnull
    public static BrightnessTweaksConfigStore forPlugin(@Nonnull BrightnessTweaksPlugin plugin) {
        PluginManifest manifest = plugin.getManifest();
        String folderName = manifest.getGroup() + "_" + manifest.getName();

        Path pluginFile = plugin.getFile();
        Path pluginDir = pluginFile == null ? null : pluginFile.getParent();
        if (pluginDir == null) {
            pluginDir = Paths.get(".");
        }

        Path configDir = pluginDir.resolve("plugin-config").resolve(folderName);
        return new BrightnessTweaksConfigStore(configDir, "settings");
    }

    /**
     * Loads the config and creates the file on disk if it does not exist yet.
     */
    public void loadBlocking() {
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.atWarning().log("Unable to create Brightness Tweaks config directory at " + configDir + ": " + e.getMessage());
        }

        boolean existed = Files.exists(configPath);
        config.load().join();

        if (!existed) {
            scheduleSave();
        }

        LOGGER.atInfo().log("Brightness Tweaks config loaded from " + configPath);
    }

    /**
     * Applies any persisted settings for this player to the runtime {@link BrightnessService}.
     */
    public void applyToService(@Nonnull UUID playerUuid, @Nonnull BrightnessService brightnessService) {
        BrightnessTweaksConfig.PlayerSettings settings = getPlayerSettings(playerUuid);
        if (settings == null) {
            return;
        }
        brightnessService.setDesiredBrightness(playerUuid, clamp(settings.getBrightness(), BrightnessService.MIN_BRIGHTNESS, BrightnessService.MAX_BRIGHTNESS));
        brightnessService.setDesiredTintRgb(playerUuid, settings.getTintRgb());
        brightnessService.setDesiredWarmth(playerUuid, clamp(settings.getWarmth01(), 0.0f, 1.0f));
    }

    /**
     * Returns the persisted brightness scale for a player (null means disabled).
     */
    @Nullable
    public Float getBrightness(@Nonnull UUID playerUuid) {
        BrightnessTweaksConfig.PlayerSettings settings = getPlayerSettings(playerUuid);
        return settings == null ? null : settings.getBrightness();
    }

    /**
     * Returns the persisted tint RGB for a player (0xRRGGBB) or null to use the torch's default tint.
     */
    @Nullable
    public Integer getTintRgb(@Nonnull UUID playerUuid) {
        BrightnessTweaksConfig.PlayerSettings settings = getPlayerSettings(playerUuid);
        return settings == null ? null : settings.getTintRgb();
    }

    /**
     * Returns the persisted warmth for a player (0.0-1.0) or null to use the torch's default tint.
     */
    @Nullable
    public Float getWarmth01(@Nonnull UUID playerUuid) {
        BrightnessTweaksConfig.PlayerSettings settings = getPlayerSettings(playerUuid);
        return settings == null ? null : settings.getWarmth01();
    }

    /**
     * Persists a player's brightness scale (null disables the boost).
     */
    public void setBrightness(@Nonnull UUID playerUuid, @Nullable Float brightness) {
        synchronized (mutationLock) {
            BrightnessTweaksConfig.PlayerSettings settings = getOrCreatePlayerSettings(playerUuid);
            settings.setBrightness(clamp(brightness, BrightnessService.MIN_BRIGHTNESS, BrightnessService.MAX_BRIGHTNESS));
            cleanupIfEmpty(playerUuid, settings);
        }
        scheduleSave();
    }

    /**
     * Clears all persisted settings for a player.
     */
    public void clearPlayerSettings(@Nonnull UUID playerUuid) {
        synchronized (mutationLock) {
            BrightnessTweaksConfig loaded = config.get();
            loaded.getPlayers().remove(playerUuid.toString());
        }
        scheduleSave();
    }

    /**
     * Persists a player's tint RGB (null reverts to the torch's default tint).
     */
    public void setTintRgb(@Nonnull UUID playerUuid, @Nullable Integer tintRgb) {
        synchronized (mutationLock) {
            BrightnessTweaksConfig.PlayerSettings settings = getOrCreatePlayerSettings(playerUuid);
            settings.setWarmth01(null);
            settings.setTintRgb(tintRgb);
            cleanupIfEmpty(playerUuid, settings);
        }
        scheduleSave();
    }

    /**
     * Persists a player's warmth (null reverts to the torch's default tint).
     */
    public void setWarmth01(@Nonnull UUID playerUuid, @Nullable Float warmth01) {
        synchronized (mutationLock) {
            BrightnessTweaksConfig.PlayerSettings settings = getOrCreatePlayerSettings(playerUuid);
            settings.setTintRgb(null);
            settings.setWarmth01(clamp(warmth01, 0.0f, 1.0f));
            cleanupIfEmpty(playerUuid, settings);
        }
        scheduleSave();
    }

    @Nullable
    private BrightnessTweaksConfig.PlayerSettings getPlayerSettings(@Nonnull UUID playerUuid) {
        BrightnessTweaksConfig loaded = config.get();
        Map<String, BrightnessTweaksConfig.PlayerSettings> players = loaded.getPlayers();
        return players.get(playerUuid.toString());
    }

    @Nonnull
    private BrightnessTweaksConfig.PlayerSettings getOrCreatePlayerSettings(@Nonnull UUID playerUuid) {
        BrightnessTweaksConfig loaded = config.get();
        Map<String, BrightnessTweaksConfig.PlayerSettings> players = loaded.getPlayers();
        return players.computeIfAbsent(playerUuid.toString(), ignored -> new BrightnessTweaksConfig.PlayerSettings());
    }

    private void cleanupIfEmpty(@Nonnull UUID playerUuid, @Nonnull BrightnessTweaksConfig.PlayerSettings settings) {
        if (!settings.isEmpty()) {
            return;
        }
        BrightnessTweaksConfig loaded = config.get();
        loaded.getPlayers().remove(playerUuid.toString());
    }

    private void scheduleSave() {
        synchronized (saveLock) {
            pendingSave =
                pendingSave
                    .handle((ignored, throwable) -> null)
                    .thenCompose(ignored -> config.save())
                    .exceptionally(throwable -> {
                        LOGGER.atWarning().log("Unable to save Brightness Tweaks config at " + configPath + ": " + throwable.getMessage());
                        return null;
                    });
        }
    }

    @Nullable
    private static Float clamp(@Nullable Float value, float min, float max) {
        if (value == null) {
            return null;
        }
        return Math.max(min, Math.min(max, value));
    }
}
