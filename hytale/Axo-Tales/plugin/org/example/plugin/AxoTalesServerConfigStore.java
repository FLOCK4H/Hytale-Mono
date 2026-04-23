package org.example.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.math.util.ChunkUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads {@code server-config.json} from the plugin data directory and creates a default if missing.
 */
public final class AxoTalesServerConfigStore {

    private static final String CONFIG_FILE_NAME = "server-config.json";
    private static final double OLD_ARCANE_CRYSTAL_DEFAULT_CHANCE_PER_CHUNK = 0.25 / 3.0;
    private static final double OLD_ARCANE_CRYSTAL_CELL_DEFAULT_CHANCE_PER_CHUNK = 1.0;
    private static final double DEFAULT_ARCANE_CRYSTAL_CHANCE_PER_CHUNK = 0.33;
    private static final int OLD_ARCANE_CRYSTAL_DEBUG_PLACEMENTS_PER_CHUNK = 12;
    private static final int DEFAULT_ARCANE_CRYSTAL_PLACEMENTS_PER_CHUNK = 1;
    private static final int DEFAULT_ARCANE_CRYSTAL_DENSITY_RADIUS_BLOCKS = 64;
    private static final int OLD_ARCANE_CRYSTAL_MAX_PLACEMENTS_PER_RADIUS = 3;
    private static final int DEFAULT_ARCANE_CRYSTAL_MAX_PLACEMENTS_PER_RADIUS = 1;
    private static final int CURRENT_ARCANE_CRYSTAL_DEFAULTS_VERSION = 3;
    private static final double OLD_KUDU_ADEPT_INTERVAL_SECONDS = 30.0;
    private static final double PREVIOUS_KUDU_ADEPT_INTERVAL_SECONDS = 150.0;
    private static final double LAST_KUDU_ADEPT_INTERVAL_SECONDS = 5.0;
    private static final double DEFAULT_KUDU_ADEPT_INTERVAL_SECONDS = 120.0;
    private static final int DEFAULT_KUDU_ADEPT_MAX_ACTIVE_PER_WORLD = 500;
    private static final int LAST_KUDU_ADEPT_SPAWNS_PER_INTERVAL = 8;
    private static final int DEFAULT_KUDU_ADEPT_SPAWNS_PER_INTERVAL = 1;
    private static final int LAST_KUDU_ADEPT_MAX_ATTEMPTS_PER_INTERVAL = 128;
    private static final int DEFAULT_KUDU_ADEPT_MAX_ATTEMPTS_PER_INTERVAL = 3;
    private static final double PREVIOUS_KUDU_ADEPT_DENSITY_CELL_SIZE_BLOCKS = 200.0;
    private static final double LAST_KUDU_ADEPT_DENSITY_CELL_SIZE_BLOCKS = 32.0;
    private static final double DEFAULT_KUDU_ADEPT_DENSITY_CELL_SIZE_BLOCKS = 256.0;
    private static final int LAST_KUDU_ADEPT_CELL_SPAWN_CHANCE_PERCENT = 100;
    private static final int DEFAULT_KUDU_ADEPT_CELL_SPAWN_CHANCE_PERCENT = 33;
    private static final double PREVIOUS_KUDU_ADEPT_MIN_DISTANCE_FROM_PLAYERS_BLOCKS = 12.0;
    private static final double DEFAULT_KUDU_ADEPT_MIN_DISTANCE_FROM_PLAYERS_BLOCKS = 8.0;
    private static final double PREVIOUS_KUDU_ADEPT_RADIUS_MIN_BLOCKS = 18.0;
    private static final double DEFAULT_KUDU_ADEPT_RADIUS_MIN_BLOCKS = 8.0;
    private static final double PREVIOUS_KUDU_ADEPT_RADIUS_MAX_BLOCKS = 96.0;
    private static final double LAST_KUDU_ADEPT_RADIUS_MAX_BLOCKS = 48.0;
    private static final double DEFAULT_KUDU_ADEPT_RADIUS_MAX_BLOCKS = 280.0;
    private static final double PREVIOUS_KUDU_ADEPT_DESPAWN_AFTER_SECONDS = 600.0;
    private static final double DEFAULT_KUDU_ADEPT_DESPAWN_AFTER_SECONDS = 0.0;
    private static final int CURRENT_KUDU_ADEPT_DEFAULTS_VERSION = 6;
    private static final double PREVIOUS_CLOUD_BLOCK_IMPULSE_VELOCITY = 5.0;
    private static final double PREVIOUS_CLOUD_BLOCK_MAX_VERTICAL_SPEED = 18.0;
    private static final double DEFAULT_CLOUD_BLOCK_TARGET_HEIGHT_BLOCKS = 6.0;
    private static final double DEFAULT_CLOUD_BLOCK_MAX_VERTICAL_SPEED = 32.0;
    private static final double DEFAULT_CLOUD_BLOCK_CHAIN_VELOCITY_MULTIPLIER = 1.5;
    private static final double DEFAULT_CLOUD_BLOCK_CHAIN_RESET_SECONDS = 4.0;
    private static final double DEFAULT_BOUNCE_BLOCK_BASE_TARGET_HEIGHT_BLOCKS = 4.0;
    private static final double DEFAULT_BOUNCE_BLOCK_HEIGHT_GAIN_PER_BOUNCE_BLOCKS = 2.0;
    private static final double DEFAULT_BOUNCE_BLOCK_MAX_TARGET_HEIGHT_BLOCKS = 18.0;
    private static final double DEFAULT_BOUNCE_BLOCK_MAX_VERTICAL_SPEED = 48.0;
    private static final double DEFAULT_BOUNCE_BLOCK_COOLDOWN_SECONDS = 0.2;
    private static final double DEFAULT_BOUNCE_BLOCK_STREAK_RESET_SECONDS = 8.0;

    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final Path configPath;
    private final Gson gson;

    public AxoTalesServerConfigStore(@Nonnull Path pluginDataDirectory, @Nonnull PluginErrorReporter errors, @Nonnull PluginDebugReporter debug) {
        this.debug = debug;
        this.errors = errors;
        this.configPath = pluginDataDirectory.resolve(CONFIG_FILE_NAME);
        this.gson = new GsonBuilder()
            .registerTypeAdapter(AxoTalesServerConfig.FullOrInt.class, new AxoTalesServerConfig.FullOrIntAdapter())
            .setPrettyPrinting()
            .create();
    }

    public Path getConfigPath() {
        return configPath;
    }

    public @Nonnull AxoTalesServerConfig loadOrCreateDefault() {
        ensureExists();

        AxoTalesServerConfig config = loadMergedBestEffort();
        sanitize(config);
        persistBestEffort(config);
        return config;
    }

    private void ensureExists() {
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.exists(configPath)) {
                return;
            }

            String defaultJson = readBundledDefault();
            if (defaultJson == null) {
                defaultJson = gson.toJson(new AxoTalesServerConfig());
            }

            Files.writeString(configPath, defaultJson, StandardCharsets.UTF_8);
            debug.trace(null, "Created default " + CONFIG_FILE_NAME + " at: " + configPath);
        } catch (Throwable t) {
            errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to create default " + CONFIG_FILE_NAME + ".", t);
        }
    }

    private String readBundledDefault() {
        try (InputStream in = AxoTalesServerConfigStore.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private @Nonnull AxoTalesServerConfig loadMergedBestEffort() {
        try {
            String raw = Files.readString(configPath, StandardCharsets.UTF_8);

            JsonObject defaults = loadBundledDefaultsAsJsonObject();
            JsonObject merged = defaults != null ? defaults.deepCopy() : new JsonObject();

            JsonObject user = parseObjectBestEffort(raw);
            if (user != null) {
                migrateRuneKnightConfig(user);
                migrateArcaneCrystalConfig(user);
                migrateKuduAdeptConfig(user);
                migrateCloudBlockConfig(user);
                deepMerge(merged, user);
            }

            AxoTalesServerConfig config = gson.fromJson(merged, AxoTalesServerConfig.class);
            return config != null ? config : new AxoTalesServerConfig();
        } catch (Throwable t) {
            errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to load " + CONFIG_FILE_NAME + " from " + configPath + ".", t);
            return new AxoTalesServerConfig();
        }
    }

    private @Nullable JsonObject loadBundledDefaultsAsJsonObject() {
        String defaultJson = readBundledDefault();
        if (defaultJson == null || defaultJson.isBlank()) {
            // Best-effort fallback: use the Java defaults if the resource isn't present for some reason.
            defaultJson = gson.toJson(new AxoTalesServerConfig());
        }
        return parseObjectBestEffort(defaultJson);
    }

    private static @Nullable JsonObject parseObjectBestEffort(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void deepMerge(@Nonnull JsonObject target, @Nonnull JsonObject overrides) {
        for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
            String key = entry.getKey();
            JsonElement overrideValue = entry.getValue();

            if (!target.has(key)) {
                target.add(key, overrideValue);
                continue;
            }

            JsonElement existing = target.get(key);
            if (existing != null && existing.isJsonObject() && overrideValue != null && overrideValue.isJsonObject()) {
                deepMerge(existing.getAsJsonObject(), overrideValue.getAsJsonObject());
                continue;
            }

            target.add(key, overrideValue);
        }
    }

    private void migrateRuneKnightConfig(@Nonnull JsonObject root) {
        if (!root.has("runeKnight") || !root.get("runeKnight").isJsonObject()) {
            return;
        }

        JsonObject rk = root.getAsJsonObject("runeKnight");
        if (rk == null) {
            return;
        }

        boolean changed = false;

        JsonObject spawn = ensureObject(rk, "spawn");
        changed |= moveIfPresent(rk, "maxActivePerWorld", spawn, "maxActivePerWorld");
        changed |= moveIfPresent(rk, "spawnsPerInterval", spawn, "spawnsPerInterval");
        changed |= moveIfPresent(rk, "spawnIntervalSeconds", spawn, "intervalSeconds");
        changed |= moveIfPresent(rk, "maxSpawnAttemptsPerInterval", spawn, "maxAttemptsPerInterval");
        changed |= moveIfPresent(rk, "minDistanceFromPlayersBlocks", spawn, "minDistanceFromPlayersBlocks");
        changed |= moveIfPresent(rk, "spawnRadiusMinBlocks", spawn, "radiusMinBlocks");
        changed |= moveIfPresent(rk, "spawnRadiusMaxBlocks", spawn, "radiusMaxBlocks");
        changed |= moveIfPresent(rk, "allowInMemoryChunks", spawn, "allowInMemoryChunks");
        changed |= moveIfPresent(rk, "nightSunlightThreshold", spawn, "nightSunlightThreshold");

        JsonObject despawn = ensureObject(rk, "despawn");
        changed |= moveIfPresent(rk, "despawnOnDay", despawn, "onDay");
        changed |= moveIfPresent(rk, "despawnAfterSeconds", despawn, "afterSeconds");

        JsonObject aggro = ensureObject(rk, "aggro");
        changed |= moveIfPresent(rk, "aggroRadiusBlocks", aggro, "radiusBlocks");

        JsonObject projectiles = ensureObject(rk, "projectiles");
        changed |= moveIfPresent(rk, "projectilesEnabled", projectiles, "enabled");
        changed |= moveIfPresent(rk, "projectileId", projectiles, "projectileId");
        changed |= moveIfPresent(rk, "projectileCooldownSeconds", projectiles, "cooldownSeconds");
        changed |= moveIfPresent(rk, "projectileRangeBlocks", projectiles, "rangeBlocks");
        changed |= moveIfPresent(rk, "projectileAimHeightBlocks", projectiles, "aimHeightBlocks");

        JsonObject loot = ensureObject(rk, "loot");
        changed |= moveIfPresent(rk, "kuduBootsDropChancePercent", loot, "kuduBootsDropChancePercent");
        changed |= moveIfPresent(rk, "frostBookDropChancePercent", loot, "frostBookDropChancePercent");

        boolean hadFrostDropChance = loot.has("frostBookDropChancePercent");
        if (!hadFrostDropChance) {
            loot.addProperty("frostBookDropChancePercent", 5);
            changed = true;

            // Previous default was 85%. If the user never customized it, align the new defaults.
            try {
                if (loot.has("kuduBootsDropChancePercent")
                    && loot.get("kuduBootsDropChancePercent").isJsonPrimitive()
                    && loot.getAsJsonPrimitive("kuduBootsDropChancePercent").isNumber()
                    && loot.getAsJsonPrimitive("kuduBootsDropChancePercent").getAsInt() == 85) {
                    loot.addProperty("kuduBootsDropChancePercent", 5);
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }

        if (changed) {
            debug.traceFileOnly(null, "Config migrate: updated runeKnight config (grouping + loot defaults).");
        }
    }

    private void migrateArcaneCrystalConfig(@Nonnull JsonObject root) {
        if (!root.has("worldgen") || !root.get("worldgen").isJsonObject()) {
            return;
        }

        JsonObject worldgen = root.getAsJsonObject("worldgen");
        boolean hadNewPlacementKey = worldgen.has("arcaneCrystalPlacementsPerChunk");
        boolean hadProcessExistingKey = worldgen.has("arcaneCrystalProcessExistingChunks");
        boolean hadPruneLegacyKey = worldgen.has("arcaneCrystalPruneLegacyClusters");
        boolean hadDensityRadiusKey = worldgen.has("arcaneCrystalDensityRadiusBlocks");
        boolean hadMaxPerRadiusKey = worldgen.has("arcaneCrystalMaxPlacementsPerRadius");
        int defaultsVersion = 0;
        try {
            if (worldgen.has("arcaneCrystalDefaultsVersion")) {
                var defaultsVersionValue = worldgen.getAsJsonPrimitive("arcaneCrystalDefaultsVersion");
                if (defaultsVersionValue != null && defaultsVersionValue.isNumber()) {
                    defaultsVersion = defaultsVersionValue.getAsInt();
                }
            }
        } catch (Throwable ignored) {
            defaultsVersion = 0;
        }
        boolean changed = false;

        if (!hadNewPlacementKey) {
            worldgen.addProperty("arcaneCrystalPlacementsPerChunk", DEFAULT_ARCANE_CRYSTAL_PLACEMENTS_PER_CHUNK);
            changed = true;
        }
        if (!hadProcessExistingKey) {
            worldgen.addProperty("arcaneCrystalProcessExistingChunks", false);
            changed = true;
        }
        if (!hadPruneLegacyKey) {
            worldgen.addProperty("arcaneCrystalPruneLegacyClusters", true);
            changed = true;
        }
        if (!hadDensityRadiusKey) {
            worldgen.addProperty("arcaneCrystalDensityRadiusBlocks", DEFAULT_ARCANE_CRYSTAL_DENSITY_RADIUS_BLOCKS);
            changed = true;
        }
        if (!hadMaxPerRadiusKey) {
            worldgen.addProperty("arcaneCrystalMaxPlacementsPerRadius", DEFAULT_ARCANE_CRYSTAL_MAX_PLACEMENTS_PER_RADIUS);
            changed = true;
        }
        if (defaultsVersion < CURRENT_ARCANE_CRYSTAL_DEFAULTS_VERSION
            && hadMaxPerRadiusKey
            && worldgen.has("arcaneCrystalMaxPlacementsPerRadius")) {
            try {
                var maxPerRadius = worldgen.getAsJsonPrimitive("arcaneCrystalMaxPlacementsPerRadius");
                if (maxPerRadius != null && maxPerRadius.isNumber()
                    && maxPerRadius.getAsInt() == OLD_ARCANE_CRYSTAL_MAX_PLACEMENTS_PER_RADIUS) {
                    worldgen.addProperty("arcaneCrystalMaxPlacementsPerRadius", DEFAULT_ARCANE_CRYSTAL_MAX_PLACEMENTS_PER_RADIUS);
                    changed = true;
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
        if (defaultsVersion < CURRENT_ARCANE_CRYSTAL_DEFAULTS_VERSION) {
            try {
                var chance = worldgen.getAsJsonPrimitive("arcaneCrystalChancePerNewChunk");
                if (chance != null && chance.isNumber()
                    && Math.abs(chance.getAsDouble() - OLD_ARCANE_CRYSTAL_CELL_DEFAULT_CHANCE_PER_CHUNK) < 0.000000001) {
                    worldgen.addProperty("arcaneCrystalChancePerNewChunk", DEFAULT_ARCANE_CRYSTAL_CHANCE_PER_CHUNK);
                    changed = true;
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
            try {
                var processExisting = worldgen.getAsJsonPrimitive("arcaneCrystalProcessExistingChunks");
                if (processExisting != null && processExisting.isBoolean() && processExisting.getAsBoolean()) {
                    worldgen.addProperty("arcaneCrystalProcessExistingChunks", false);
                    changed = true;
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
            worldgen.addProperty("arcaneCrystalDefaultsVersion", CURRENT_ARCANE_CRYSTAL_DEFAULTS_VERSION);
            changed = true;
        }

        if (!hadNewPlacementKey && worldgen.has("arcaneCrystalChancePerNewChunk")) {
            try {
                var chance = worldgen.getAsJsonPrimitive("arcaneCrystalChancePerNewChunk");
                if (chance != null && chance.isNumber()) {
                    double value = chance.getAsDouble();
                    if (Math.abs(value - OLD_ARCANE_CRYSTAL_DEFAULT_CHANCE_PER_CHUNK) < 0.000000001) {
                        worldgen.addProperty("arcaneCrystalChancePerNewChunk", DEFAULT_ARCANE_CRYSTAL_CHANCE_PER_CHUNK);
                        changed = true;
                    }
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
        if (!hadMaxPerRadiusKey && worldgen.has("arcaneCrystalPlacementsPerChunk")) {
            try {
                var placements = worldgen.getAsJsonPrimitive("arcaneCrystalPlacementsPerChunk");
                if (placements != null && placements.isNumber()
                    && placements.getAsInt() == OLD_ARCANE_CRYSTAL_DEBUG_PLACEMENTS_PER_CHUNK) {
                    worldgen.addProperty("arcaneCrystalPlacementsPerChunk", DEFAULT_ARCANE_CRYSTAL_PLACEMENTS_PER_CHUNK);
                    changed = true;
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }

        if (changed) {
            debug.traceFileOnly(
                null,
                "Config migrate: updated arcane crystal worldgen defaults (chance="
                    + DEFAULT_ARCANE_CRYSTAL_CHANCE_PER_CHUNK
                    + ", placementsPerChunk="
                    + DEFAULT_ARCANE_CRYSTAL_PLACEMENTS_PER_CHUNK
                    + ", densityRadiusBlocks="
                    + DEFAULT_ARCANE_CRYSTAL_DENSITY_RADIUS_BLOCKS
                    + ", maxPlacementsPerRadius="
                    + DEFAULT_ARCANE_CRYSTAL_MAX_PLACEMENTS_PER_RADIUS
                    + ", processExistingChunks=false)."
            );
        }
    }

    private void migrateKuduAdeptConfig(@Nonnull JsonObject root) {
        if (!root.has("kuduAdept") || !root.get("kuduAdept").isJsonObject()) {
            return;
        }

        JsonObject adept = root.getAsJsonObject("kuduAdept");
        int defaultsVersion = 0;
        try {
            if (adept.has("defaultsVersion")) {
                var defaultsVersionValue = adept.getAsJsonPrimitive("defaultsVersion");
                if (defaultsVersionValue != null && defaultsVersionValue.isNumber()) {
                    defaultsVersion = defaultsVersionValue.getAsInt();
                }
            }
        } catch (Throwable ignored) {
            defaultsVersion = 0;
        }

        if (defaultsVersion >= CURRENT_KUDU_ADEPT_DEFAULTS_VERSION) {
            return;
        }

        boolean changed = false;
        if (!adept.has("enabled")
            || adept.get("enabled").isJsonNull()
            || (adept.get("enabled").isJsonPrimitive() && !adept.getAsJsonPrimitive("enabled").getAsBoolean())) {
            adept.addProperty("enabled", true);
            changed = true;
        }

        JsonObject spawn = ensureObject(adept, "spawn");
        if (!spawn.has("maxActivePerWorld") || jsonNumberEquals(spawn, "maxActivePerWorld", 20)) {
            spawn.addProperty("maxActivePerWorld", DEFAULT_KUDU_ADEPT_MAX_ACTIVE_PER_WORLD);
            changed = true;
        }
        if (!spawn.has("spawnsPerInterval")
            || jsonNumberEquals(spawn, "spawnsPerInterval", LAST_KUDU_ADEPT_SPAWNS_PER_INTERVAL)) {
            spawn.addProperty("spawnsPerInterval", DEFAULT_KUDU_ADEPT_SPAWNS_PER_INTERVAL);
            changed = true;
        }
        if (!spawn.has("maxAttemptsPerInterval")
            || jsonNumberEquals(spawn, "maxAttemptsPerInterval", 24)
            || jsonNumberEquals(spawn, "maxAttemptsPerInterval", LAST_KUDU_ADEPT_MAX_ATTEMPTS_PER_INTERVAL)) {
            spawn.addProperty("maxAttemptsPerInterval", DEFAULT_KUDU_ADEPT_MAX_ATTEMPTS_PER_INTERVAL);
            changed = true;
        }
        if (!spawn.has("daySunlightThreshold") || jsonNumberEquals(spawn, "daySunlightThreshold", 0.25)) {
            spawn.addProperty("daySunlightThreshold", 0.0);
            changed = true;
        }
        if (!spawn.has("intervalSeconds")
            || jsonNumberEquals(spawn, "intervalSeconds", OLD_KUDU_ADEPT_INTERVAL_SECONDS)
            || jsonNumberEquals(spawn, "intervalSeconds", PREVIOUS_KUDU_ADEPT_INTERVAL_SECONDS)
            || jsonNumberEquals(spawn, "intervalSeconds", LAST_KUDU_ADEPT_INTERVAL_SECONDS)) {
            spawn.addProperty("intervalSeconds", DEFAULT_KUDU_ADEPT_INTERVAL_SECONDS);
            changed = true;
        }
        if (!spawn.has("densityCellSizeBlocks")
            || jsonNumberEquals(spawn, "densityCellSizeBlocks", PREVIOUS_KUDU_ADEPT_DENSITY_CELL_SIZE_BLOCKS)
            || jsonNumberEquals(spawn, "densityCellSizeBlocks", LAST_KUDU_ADEPT_DENSITY_CELL_SIZE_BLOCKS)) {
            spawn.addProperty("densityCellSizeBlocks", DEFAULT_KUDU_ADEPT_DENSITY_CELL_SIZE_BLOCKS);
            changed = true;
        }
        if (!spawn.has("cellSpawnChancePercent")
            || jsonNumberEquals(spawn, "cellSpawnChancePercent", LAST_KUDU_ADEPT_CELL_SPAWN_CHANCE_PERCENT)) {
            spawn.addProperty("cellSpawnChancePercent", DEFAULT_KUDU_ADEPT_CELL_SPAWN_CHANCE_PERCENT);
            changed = true;
        }
        if (!spawn.has("minDistanceFromPlayersBlocks")
            || jsonNumberEquals(spawn, "minDistanceFromPlayersBlocks", PREVIOUS_KUDU_ADEPT_MIN_DISTANCE_FROM_PLAYERS_BLOCKS)) {
            spawn.addProperty("minDistanceFromPlayersBlocks", DEFAULT_KUDU_ADEPT_MIN_DISTANCE_FROM_PLAYERS_BLOCKS);
            changed = true;
        }
        if (!spawn.has("radiusMinBlocks")
            || jsonNumberEquals(spawn, "radiusMinBlocks", PREVIOUS_KUDU_ADEPT_RADIUS_MIN_BLOCKS)) {
            spawn.addProperty("radiusMinBlocks", DEFAULT_KUDU_ADEPT_RADIUS_MIN_BLOCKS);
            changed = true;
        }
        if (!spawn.has("radiusMaxBlocks")
            || jsonNumberEquals(spawn, "radiusMaxBlocks", PREVIOUS_KUDU_ADEPT_RADIUS_MAX_BLOCKS)
            || jsonNumberEquals(spawn, "radiusMaxBlocks", LAST_KUDU_ADEPT_RADIUS_MAX_BLOCKS)) {
            spawn.addProperty("radiusMaxBlocks", DEFAULT_KUDU_ADEPT_RADIUS_MAX_BLOCKS);
            changed = true;
        }
        if (!spawn.has("allowInMemoryChunks")) {
            spawn.addProperty("allowInMemoryChunks", true);
            changed = true;
        }

        JsonObject despawn = ensureObject(adept, "despawn");
        if (!despawn.has("onNight")
            || (despawn.get("onNight").isJsonPrimitive() && despawn.getAsJsonPrimitive("onNight").getAsBoolean())) {
            despawn.addProperty("onNight", false);
            changed = true;
        }
        if (!despawn.has("afterSeconds") || jsonNumberEquals(despawn, "afterSeconds", PREVIOUS_KUDU_ADEPT_DESPAWN_AFTER_SECONDS)) {
            despawn.addProperty("afterSeconds", DEFAULT_KUDU_ADEPT_DESPAWN_AFTER_SECONDS);
            changed = true;
        }

        adept.addProperty("defaultsVersion", CURRENT_KUDU_ADEPT_DEFAULTS_VERSION);
        if (changed) {
            debug.traceFileOnly(
                null,
                "Config migrate: Kudu Adept defaults now use 120s intervals, 1 spawn with 3 attempts, 256-block density cells, 33% cell chance, 8-280 block sampling, all-time spawning, no night despawn, and no lifetime despawn."
            );
        }
    }

    private void migrateCloudBlockConfig(@Nonnull JsonObject root) {
        if (!root.has("cloudBlock") || !root.get("cloudBlock").isJsonObject()) {
            return;
        }

        JsonObject cloud = root.getAsJsonObject("cloudBlock");
        boolean changed = false;
        if (!cloud.has("targetHeightBlocks")) {
            cloud.addProperty("targetHeightBlocks", DEFAULT_CLOUD_BLOCK_TARGET_HEIGHT_BLOCKS);
            changed = true;
        }
        if (jsonNumberEquals(cloud, "maxVerticalSpeed", PREVIOUS_CLOUD_BLOCK_MAX_VERTICAL_SPEED)) {
            cloud.addProperty("maxVerticalSpeed", DEFAULT_CLOUD_BLOCK_MAX_VERTICAL_SPEED);
            changed = true;
        }
        if (jsonNumberEquals(cloud, "impulseVelocity", PREVIOUS_CLOUD_BLOCK_IMPULSE_VELOCITY)) {
            cloud.remove("impulseVelocity");
            changed = true;
        }

        if (changed) {
            debug.traceFileOnly(
                null,
                "Config migrate: Cloud Block now uses targetHeightBlocks="
                    + DEFAULT_CLOUD_BLOCK_TARGET_HEIGHT_BLOCKS
                    + " and maxVerticalSpeed="
                    + DEFAULT_CLOUD_BLOCK_MAX_VERTICAL_SPEED
                    + "."
            );
        }
    }

    private static @Nonnull JsonObject ensureObject(@Nonnull JsonObject parent, @Nonnull String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        JsonObject obj = new JsonObject();
        parent.add(key, obj);
        return obj;
    }

    private static boolean jsonNumberEquals(@Nonnull JsonObject object, @Nonnull String key, double expected) {
        try {
            if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) {
                return false;
            }
            return Math.abs(object.getAsJsonPrimitive(key).getAsDouble() - expected) < 0.0000001;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean moveIfPresent(@Nonnull JsonObject from, @Nonnull String fromKey, @Nonnull JsonObject to, @Nonnull String toKey) {
        if (!from.has(fromKey)) {
            return false;
        }
        if (!to.has(toKey)) {
            to.add(toKey, from.get(fromKey));
        }
        from.remove(fromKey);
        return true;
    }

    private void sanitize(@Nonnull AxoTalesServerConfig config) {
        if (config.spellbooks == null) {
            config.spellbooks = new AxoTalesServerConfig.Spellbooks();
        }
        if (config.worldgen == null) {
            config.worldgen = new AxoTalesServerConfig.Worldgen();
        }
        if (config.worldgen.arcaneMatterOres == null) {
            config.worldgen.arcaneMatterOres = new AxoTalesServerConfig.Worldgen.ArcaneMatterOres();
        }
        if (config.worldgen.arcaneMatterOres.stone == null) {
            config.worldgen.arcaneMatterOres.stone = new AxoTalesServerConfig.Worldgen.ArcaneMatterOres.Stone();
        }
        if (config.worldgen.arcaneMatterOres.volcanic == null) {
            config.worldgen.arcaneMatterOres.volcanic = new AxoTalesServerConfig.Worldgen.ArcaneMatterOres.Volcanic();
        }
        if (config.workarounds == null) {
            config.workarounds = new AxoTalesServerConfig.Workarounds();
        }
        if (config.cloudBlock == null) {
            config.cloudBlock = new AxoTalesServerConfig.CloudBlock();
        }
        if (config.bounceBlock == null) {
            config.bounceBlock = new AxoTalesServerConfig.BounceBlock();
        }
        if (config.runeKnight == null) {
            config.runeKnight = new AxoTalesServerConfig.RuneKnight();
        }
        if (config.runeKnight.spawn == null) {
            config.runeKnight.spawn = new AxoTalesServerConfig.RuneKnight.Spawn();
        }
        if (config.runeKnight.despawn == null) {
            config.runeKnight.despawn = new AxoTalesServerConfig.RuneKnight.Despawn();
        }
        if (config.runeKnight.aggro == null) {
            config.runeKnight.aggro = new AxoTalesServerConfig.RuneKnight.Aggro();
        }
        if (config.runeKnight.projectiles == null) {
            config.runeKnight.projectiles = new AxoTalesServerConfig.RuneKnight.Projectiles();
        }
        if (config.runeKnight.loot == null) {
            config.runeKnight.loot = new AxoTalesServerConfig.RuneKnight.Loot();
        }
        if (config.kuduAdept == null) {
            config.kuduAdept = new AxoTalesServerConfig.KuduAdept();
        }
        if (config.kuduAdept.spawn == null) {
            config.kuduAdept.spawn = new AxoTalesServerConfig.KuduAdept.Spawn();
        }
        if (config.kuduAdept.despawn == null) {
            config.kuduAdept.despawn = new AxoTalesServerConfig.KuduAdept.Despawn();
        }
        if (config.hordeBook == null) {
            config.hordeBook = new AxoTalesServerConfig.HordeBook();
        }
        if (config.doomBook == null) {
            config.doomBook = new AxoTalesServerConfig.DoomBook();
        }
        if (config.morphBook == null) {
            config.morphBook = new AxoTalesServerConfig.MorphBook();
        }
        if (config.frostBook == null) {
            config.frostBook = new AxoTalesServerConfig.FrostBook();
        }
        if (config.flameBook == null) {
            config.flameBook = new AxoTalesServerConfig.FlameBook();
        }
        if (config.teleportBook == null) {
            config.teleportBook = new AxoTalesServerConfig.TeleportBook();
        }
        if (config.miningBook == null) {
            config.miningBook = new AxoTalesServerConfig.MiningBook();
        }
        if (config.healingBook == null) {
            config.healingBook = new AxoTalesServerConfig.HealingBook();
        }
        if (config.immunityBook == null) {
            config.immunityBook = new AxoTalesServerConfig.ImmunityBook();
        }
        if (config.tauntBook == null) {
            config.tauntBook = new AxoTalesServerConfig.TauntBook();
        }
        if (config.ancientSword == null) {
            config.ancientSword = new AxoTalesServerConfig.AncientSword();
        }
        if (config.healingBook.healAmount == null) {
            config.healingBook.healAmount = AxoTalesServerConfig.FullOrInt.full();
        }
        if (config.healingBook.manaCost == null) {
            config.healingBook.manaCost = AxoTalesServerConfig.FullOrInt.of(25);
        }

        if (!Double.isFinite(config.spellbooks.inputDebounceSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: spellbooks.inputDebounceSeconds is not finite; resetting to 0.6.");
            config.spellbooks.inputDebounceSeconds = 0.6;
        }
        if (!Double.isFinite(config.spellbooks.castDebounceSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: spellbooks.castDebounceSeconds is not finite; resetting to 0.6.");
            config.spellbooks.castDebounceSeconds = 0.6;
        }
        if (!Double.isFinite(config.spellbooks.secondaryUseDelaySeconds)) {
            debug.traceFileOnly(null, "Config sanitize: spellbooks.secondaryUseDelaySeconds is not finite; resetting to 0.3.");
            config.spellbooks.secondaryUseDelaySeconds = 0.3;
        }
        if (config.spellbooks.inputDebounceSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: spellbooks.inputDebounceSeconds < 0; clamping to 0.");
            config.spellbooks.inputDebounceSeconds = 0;
        }
        if (config.spellbooks.castDebounceSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: spellbooks.castDebounceSeconds < 0; clamping to 0.");
            config.spellbooks.castDebounceSeconds = 0;
        }
        if (config.spellbooks.secondaryUseDelaySeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: spellbooks.secondaryUseDelaySeconds < 0; clamping to 0.");
            config.spellbooks.secondaryUseDelaySeconds = 0;
        }
        if (config.spellbooks.inputDebounceSeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: spellbooks.inputDebounceSeconds > 5; clamping to 5.");
            config.spellbooks.inputDebounceSeconds = 5;
        }
        if (config.spellbooks.castDebounceSeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: spellbooks.castDebounceSeconds > 5; clamping to 5.");
            config.spellbooks.castDebounceSeconds = 5;
        }
        if (config.spellbooks.secondaryUseDelaySeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: spellbooks.secondaryUseDelaySeconds > 5; clamping to 5.");
            config.spellbooks.secondaryUseDelaySeconds = 5;
        }

        if (!Double.isFinite(config.cloudBlock.targetHeightBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.targetHeightBlocks is not finite; resetting to 6.");
            config.cloudBlock.targetHeightBlocks = DEFAULT_CLOUD_BLOCK_TARGET_HEIGHT_BLOCKS;
        }
        if (config.cloudBlock.targetHeightBlocks < 0.25) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.targetHeightBlocks < 0.25; clamping to 0.25.");
            config.cloudBlock.targetHeightBlocks = 0.25;
        }
        if (config.cloudBlock.targetHeightBlocks > 64) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.targetHeightBlocks > 64; clamping to 64.");
            config.cloudBlock.targetHeightBlocks = 64.0;
        }
        if (!Double.isFinite(config.cloudBlock.maxVerticalSpeed)) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.maxVerticalSpeed is not finite; resetting to 32.");
            config.cloudBlock.maxVerticalSpeed = DEFAULT_CLOUD_BLOCK_MAX_VERTICAL_SPEED;
        }
        if (config.cloudBlock.maxVerticalSpeed < 0.1) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.maxVerticalSpeed < 0.1; clamping to 0.1.");
            config.cloudBlock.maxVerticalSpeed = 0.1;
        }
        if (config.cloudBlock.maxVerticalSpeed > 80) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.maxVerticalSpeed > 80; clamping to 80.");
            config.cloudBlock.maxVerticalSpeed = 80.0;
        }
        if (!Double.isFinite(config.cloudBlock.minContactVelocity)) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.minContactVelocity is not finite; resetting to 0.12.");
            config.cloudBlock.minContactVelocity = 0.12;
        }
        if (config.cloudBlock.minContactVelocity < 0) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.minContactVelocity < 0; clamping to 0.");
            config.cloudBlock.minContactVelocity = 0.0;
        }
        if (config.cloudBlock.minContactVelocity > 10) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.minContactVelocity > 10; clamping to 10.");
            config.cloudBlock.minContactVelocity = 10.0;
        }
        if (!Double.isFinite(config.cloudBlock.cooldownSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.cooldownSeconds is not finite; resetting to 1.0.");
            config.cloudBlock.cooldownSeconds = 1.0;
        }
        if (config.cloudBlock.cooldownSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.cooldownSeconds < 0; clamping to 0.");
            config.cloudBlock.cooldownSeconds = 0.0;
        }
        if (config.cloudBlock.cooldownSeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.cooldownSeconds > 5; clamping to 5.");
            config.cloudBlock.cooldownSeconds = 5.0;
        }
        if (!Double.isFinite(config.cloudBlock.chainVelocityMultiplier)) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.chainVelocityMultiplier is not finite; resetting to 1.5.");
            config.cloudBlock.chainVelocityMultiplier = DEFAULT_CLOUD_BLOCK_CHAIN_VELOCITY_MULTIPLIER;
        }
        if (config.cloudBlock.chainVelocityMultiplier < 1.0) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.chainVelocityMultiplier < 1; clamping to 1.");
            config.cloudBlock.chainVelocityMultiplier = 1.0;
        }
        if (config.cloudBlock.chainVelocityMultiplier > 5.0) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.chainVelocityMultiplier > 5; clamping to 5.");
            config.cloudBlock.chainVelocityMultiplier = 5.0;
        }
        if (!Double.isFinite(config.cloudBlock.chainResetSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.chainResetSeconds is not finite; resetting to 4.");
            config.cloudBlock.chainResetSeconds = DEFAULT_CLOUD_BLOCK_CHAIN_RESET_SECONDS;
        }
        if (config.cloudBlock.chainResetSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.chainResetSeconds < 0; clamping to 0.");
            config.cloudBlock.chainResetSeconds = 0.0;
        }
        if (config.cloudBlock.chainResetSeconds > 30) {
            debug.traceFileOnly(null, "Config sanitize: cloudBlock.chainResetSeconds > 30; clamping to 30.");
            config.cloudBlock.chainResetSeconds = 30.0;
        }

        if (!Double.isFinite(config.bounceBlock.baseTargetHeightBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.baseTargetHeightBlocks is not finite; resetting to 4.");
            config.bounceBlock.baseTargetHeightBlocks = DEFAULT_BOUNCE_BLOCK_BASE_TARGET_HEIGHT_BLOCKS;
        }
        if (config.bounceBlock.baseTargetHeightBlocks < 0.25) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.baseTargetHeightBlocks < 0.25; clamping to 0.25.");
            config.bounceBlock.baseTargetHeightBlocks = 0.25;
        }
        if (config.bounceBlock.baseTargetHeightBlocks > 64) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.baseTargetHeightBlocks > 64; clamping to 64.");
            config.bounceBlock.baseTargetHeightBlocks = 64.0;
        }
        if (!Double.isFinite(config.bounceBlock.heightGainPerBounceBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.heightGainPerBounceBlocks is not finite; resetting to 2.");
            config.bounceBlock.heightGainPerBounceBlocks = DEFAULT_BOUNCE_BLOCK_HEIGHT_GAIN_PER_BOUNCE_BLOCKS;
        }
        if (config.bounceBlock.heightGainPerBounceBlocks < 0) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.heightGainPerBounceBlocks < 0; clamping to 0.");
            config.bounceBlock.heightGainPerBounceBlocks = 0.0;
        }
        if (config.bounceBlock.heightGainPerBounceBlocks > 64) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.heightGainPerBounceBlocks > 64; clamping to 64.");
            config.bounceBlock.heightGainPerBounceBlocks = 64.0;
        }
        if (!Double.isFinite(config.bounceBlock.maxTargetHeightBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.maxTargetHeightBlocks is not finite; resetting to 18.");
            config.bounceBlock.maxTargetHeightBlocks = DEFAULT_BOUNCE_BLOCK_MAX_TARGET_HEIGHT_BLOCKS;
        }
        if (config.bounceBlock.maxTargetHeightBlocks < config.bounceBlock.baseTargetHeightBlocks) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.maxTargetHeightBlocks < baseTargetHeightBlocks; clamping to baseTargetHeightBlocks.");
            config.bounceBlock.maxTargetHeightBlocks = config.bounceBlock.baseTargetHeightBlocks;
        }
        if (config.bounceBlock.maxTargetHeightBlocks > 128) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.maxTargetHeightBlocks > 128; clamping to 128.");
            config.bounceBlock.maxTargetHeightBlocks = 128.0;
        }
        if (!Double.isFinite(config.bounceBlock.maxVerticalSpeed)) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.maxVerticalSpeed is not finite; resetting to 48.");
            config.bounceBlock.maxVerticalSpeed = DEFAULT_BOUNCE_BLOCK_MAX_VERTICAL_SPEED;
        }
        if (config.bounceBlock.maxVerticalSpeed < 0.1) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.maxVerticalSpeed < 0.1; clamping to 0.1.");
            config.bounceBlock.maxVerticalSpeed = 0.1;
        }
        if (config.bounceBlock.maxVerticalSpeed > 120) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.maxVerticalSpeed > 120; clamping to 120.");
            config.bounceBlock.maxVerticalSpeed = 120.0;
        }
        if (!Double.isFinite(config.bounceBlock.cooldownSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.cooldownSeconds is not finite; resetting to 0.2.");
            config.bounceBlock.cooldownSeconds = DEFAULT_BOUNCE_BLOCK_COOLDOWN_SECONDS;
        }
        if (config.bounceBlock.cooldownSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.cooldownSeconds < 0; clamping to 0.");
            config.bounceBlock.cooldownSeconds = 0.0;
        }
        if (config.bounceBlock.cooldownSeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.cooldownSeconds > 5; clamping to 5.");
            config.bounceBlock.cooldownSeconds = 5.0;
        }
        if (!Double.isFinite(config.bounceBlock.streakResetSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.streakResetSeconds is not finite; resetting to 8.");
            config.bounceBlock.streakResetSeconds = DEFAULT_BOUNCE_BLOCK_STREAK_RESET_SECONDS;
        }
        if (config.bounceBlock.streakResetSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.streakResetSeconds < 0; clamping to 0.");
            config.bounceBlock.streakResetSeconds = 0.0;
        }
        if (config.bounceBlock.streakResetSeconds > 60) {
            debug.traceFileOnly(null, "Config sanitize: bounceBlock.streakResetSeconds > 60; clamping to 60.");
            config.bounceBlock.streakResetSeconds = 60.0;
        }

        if (!Double.isFinite(config.worldgen.arcaneCrystalChancePerNewChunk)) {
            debug.traceFileOnly(
                null,
                "Config sanitize: worldgen.arcaneCrystalChancePerNewChunk is not finite; resetting to "
                    + DEFAULT_ARCANE_CRYSTAL_CHANCE_PER_CHUNK
                    + "."
            );
            config.worldgen.arcaneCrystalChancePerNewChunk = DEFAULT_ARCANE_CRYSTAL_CHANCE_PER_CHUNK;
        }
        if (config.worldgen.arcaneCrystalChancePerNewChunk < 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneCrystalChancePerNewChunk < 0; clamping to 0.");
            config.worldgen.arcaneCrystalChancePerNewChunk = 0;
        }
        if (config.worldgen.arcaneCrystalChancePerNewChunk > 1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneCrystalChancePerNewChunk > 1; clamping to 1.");
            config.worldgen.arcaneCrystalChancePerNewChunk = 1;
        }
        if (config.worldgen.arcaneCrystalPlacementsPerChunk < 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneCrystalPlacementsPerChunk < 0; clamping to 0.");
            config.worldgen.arcaneCrystalPlacementsPerChunk = 0;
        }
        if (config.worldgen.arcaneCrystalPlacementsPerChunk > 128) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneCrystalPlacementsPerChunk > 128; clamping to 128.");
            config.worldgen.arcaneCrystalPlacementsPerChunk = 128;
        }
        if (config.worldgen.arcaneCrystalDensityRadiusBlocks < 1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneCrystalDensityRadiusBlocks < 1; clamping to 1.");
            config.worldgen.arcaneCrystalDensityRadiusBlocks = 1;
        }
        if (config.worldgen.arcaneCrystalDensityRadiusBlocks > 512) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneCrystalDensityRadiusBlocks > 512; clamping to 512.");
            config.worldgen.arcaneCrystalDensityRadiusBlocks = 512;
        }
        if (config.worldgen.arcaneCrystalMaxPlacementsPerRadius < 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneCrystalMaxPlacementsPerRadius < 0; clamping to 0.");
            config.worldgen.arcaneCrystalMaxPlacementsPerRadius = 0;
        }
        if (config.worldgen.arcaneCrystalMaxPlacementsPerRadius > 128) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneCrystalMaxPlacementsPerRadius > 128; clamping to 128.");
            config.worldgen.arcaneCrystalMaxPlacementsPerRadius = 128;
        }

        // Arcane Matter ore worldgen.
        var arcaneMatter = config.worldgen.arcaneMatterOres;
        if (arcaneMatter.stoneOreBlockId == null || arcaneMatter.stoneOreBlockId.isBlank()) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stoneOreBlockId is blank; resetting to Ore_Stone_Parent.");
            arcaneMatter.stoneOreBlockId = "Ore_Stone_Parent";
        }
        if (arcaneMatter.volcanicOreBlockId == null || arcaneMatter.volcanicOreBlockId.isBlank()) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanicOreBlockId is blank; resetting to Arcane_Matter_Volcanic.");
            arcaneMatter.volcanicOreBlockId = "Arcane_Matter_Volcanic";
        }

        if (!Double.isFinite(arcaneMatter.stone.chancePerNewChunk)) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.chancePerNewChunk is not finite; resetting to 0.5.");
            arcaneMatter.stone.chancePerNewChunk = 0.5;
        }
        if (arcaneMatter.stone.chancePerNewChunk < 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.chancePerNewChunk < 0; clamping to 0.");
            arcaneMatter.stone.chancePerNewChunk = 0;
        }
        if (arcaneMatter.stone.chancePerNewChunk > 1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.chancePerNewChunk > 1; clamping to 1.");
            arcaneMatter.stone.chancePerNewChunk = 1;
        }
        if (arcaneMatter.stone.targetPlacementsPerChunk < 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.targetPlacementsPerChunk < 0; clamping to 0.");
            arcaneMatter.stone.targetPlacementsPerChunk = 0;
        }
        if (arcaneMatter.stone.targetPlacementsPerChunk > 4096) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.targetPlacementsPerChunk > 4096; clamping to 4096.");
            arcaneMatter.stone.targetPlacementsPerChunk = 4096;
        }
        if (arcaneMatter.stone.maxAttemptsPerChunk < 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.maxAttemptsPerChunk < 0; clamping to 0.");
            arcaneMatter.stone.maxAttemptsPerChunk = 0;
        }
        if (arcaneMatter.stone.maxAttemptsPerChunk > 8192) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.maxAttemptsPerChunk > 8192; clamping to 8192.");
            arcaneMatter.stone.maxAttemptsPerChunk = 8192;
        }
        if (arcaneMatter.stone.minY < 1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.minY < 1; clamping to 1.");
            arcaneMatter.stone.minY = 1;
        }
        if (arcaneMatter.stone.maxY > ChunkUtil.HEIGHT_MINUS_1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.maxY > worldMax; clamping to " + ChunkUtil.HEIGHT_MINUS_1 + ".");
            arcaneMatter.stone.maxY = ChunkUtil.HEIGHT_MINUS_1;
        }
        if (arcaneMatter.stone.minY > ChunkUtil.HEIGHT_MINUS_1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.minY > worldMax; clamping to " + ChunkUtil.HEIGHT_MINUS_1 + ".");
            arcaneMatter.stone.minY = ChunkUtil.HEIGHT_MINUS_1;
        }
        if (arcaneMatter.stone.maxY < 1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.maxY < 1; clamping to 1.");
            arcaneMatter.stone.maxY = 1;
        }
        if (arcaneMatter.stone.hostBlockIds == null || arcaneMatter.stone.hostBlockIds.length == 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.stone.hostBlockIds is empty; resetting defaults.");
            arcaneMatter.stone.hostBlockIds = new String[] { "Rock_Stone", "Rock_Shale", "Rock_Marble", "Rock_Quartzite" };
        }

        if (!Double.isFinite(arcaneMatter.volcanic.chancePerNewChunk)) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.chancePerNewChunk is not finite; resetting to 0.5.");
            arcaneMatter.volcanic.chancePerNewChunk = 0.5;
        }
        if (arcaneMatter.volcanic.chancePerNewChunk < 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.chancePerNewChunk < 0; clamping to 0.");
            arcaneMatter.volcanic.chancePerNewChunk = 0;
        }
        if (arcaneMatter.volcanic.chancePerNewChunk > 1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.chancePerNewChunk > 1; clamping to 1.");
            arcaneMatter.volcanic.chancePerNewChunk = 1;
        }
        if (arcaneMatter.volcanic.targetPlacementsPerChunk < 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.targetPlacementsPerChunk < 0; clamping to 0.");
            arcaneMatter.volcanic.targetPlacementsPerChunk = 0;
        }
        if (arcaneMatter.volcanic.targetPlacementsPerChunk > 4096) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.targetPlacementsPerChunk > 4096; clamping to 4096.");
            arcaneMatter.volcanic.targetPlacementsPerChunk = 4096;
        }
        if (arcaneMatter.volcanic.maxAttemptsPerChunk < 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.maxAttemptsPerChunk < 0; clamping to 0.");
            arcaneMatter.volcanic.maxAttemptsPerChunk = 0;
        }
        if (arcaneMatter.volcanic.maxAttemptsPerChunk > 8192) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.maxAttemptsPerChunk > 8192; clamping to 8192.");
            arcaneMatter.volcanic.maxAttemptsPerChunk = 8192;
        }
        if (arcaneMatter.volcanic.minY < 1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.minY < 1; clamping to 1.");
            arcaneMatter.volcanic.minY = 1;
        }
        if (arcaneMatter.volcanic.maxY > ChunkUtil.HEIGHT_MINUS_1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.maxY > worldMax; clamping to " + ChunkUtil.HEIGHT_MINUS_1 + ".");
            arcaneMatter.volcanic.maxY = ChunkUtil.HEIGHT_MINUS_1;
        }
        if (arcaneMatter.volcanic.minY > ChunkUtil.HEIGHT_MINUS_1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.minY > worldMax; clamping to " + ChunkUtil.HEIGHT_MINUS_1 + ".");
            arcaneMatter.volcanic.minY = ChunkUtil.HEIGHT_MINUS_1;
        }
        if (arcaneMatter.volcanic.maxY < 1) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.maxY < 1; clamping to 1.");
            arcaneMatter.volcanic.maxY = 1;
        }
        if (arcaneMatter.volcanic.hostBlockIds == null || arcaneMatter.volcanic.hostBlockIds.length == 0) {
            debug.traceFileOnly(null, "Config sanitize: worldgen.arcaneMatterOres.volcanic.hostBlockIds is empty; resetting defaults.");
            arcaneMatter.volcanic.hostBlockIds = new String[] { "Rock_Volcanic" };
        }

        if (!Double.isFinite(config.runeKnight.spawn.intervalSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.intervalSeconds is not finite; resetting to 60.");
            config.runeKnight.spawn.intervalSeconds = 60.0;
        }
        if (config.runeKnight.spawn.intervalSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.intervalSeconds < 0; clamping to 0.");
            config.runeKnight.spawn.intervalSeconds = 0;
        }
        if (config.runeKnight.spawn.intervalSeconds > 300) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.intervalSeconds > 300; clamping to 300.");
            config.runeKnight.spawn.intervalSeconds = 300;
        }
        if (config.runeKnight.spawn.maxActivePerWorld < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.maxActivePerWorld < 0; clamping to 0.");
            config.runeKnight.spawn.maxActivePerWorld = 0;
        }
        if (config.runeKnight.spawn.maxActivePerWorld > 200) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.maxActivePerWorld > 200; clamping to 200.");
            config.runeKnight.spawn.maxActivePerWorld = 200;
        }
        if (config.runeKnight.spawn.spawnsPerInterval < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.spawnsPerInterval < 0; clamping to 0.");
            config.runeKnight.spawn.spawnsPerInterval = 0;
        }
        if (config.runeKnight.spawn.spawnsPerInterval > 50) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.spawnsPerInterval > 50; clamping to 50.");
            config.runeKnight.spawn.spawnsPerInterval = 50;
        }
        if (config.runeKnight.spawn.maxAttemptsPerInterval < 1) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.maxAttemptsPerInterval < 1; clamping to 1.");
            config.runeKnight.spawn.maxAttemptsPerInterval = 1;
        }
        if (config.runeKnight.spawn.maxAttemptsPerInterval > 500) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.maxAttemptsPerInterval > 500; clamping to 500.");
            config.runeKnight.spawn.maxAttemptsPerInterval = 500;
        }
        if (!Double.isFinite(config.runeKnight.spawn.radiusMinBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.radiusMinBlocks is not finite; resetting to 24.");
            config.runeKnight.spawn.radiusMinBlocks = 24.0;
        }
        if (!Double.isFinite(config.runeKnight.spawn.radiusMaxBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.radiusMaxBlocks is not finite; resetting to 96.");
            config.runeKnight.spawn.radiusMaxBlocks = 96.0;
        }
        if (config.runeKnight.spawn.radiusMinBlocks < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.radiusMinBlocks < 0; clamping to 0.");
            config.runeKnight.spawn.radiusMinBlocks = 0;
        }
        if (config.runeKnight.spawn.radiusMaxBlocks < config.runeKnight.spawn.radiusMinBlocks) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.radiusMaxBlocks < radiusMinBlocks; raising max to min.");
            config.runeKnight.spawn.radiusMaxBlocks = config.runeKnight.spawn.radiusMinBlocks;
        }
        if (config.runeKnight.spawn.radiusMaxBlocks > 512) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.radiusMaxBlocks > 512; clamping to 512.");
            config.runeKnight.spawn.radiusMaxBlocks = 512;
        }
        if (!Double.isFinite(config.runeKnight.spawn.minDistanceFromPlayersBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.minDistanceFromPlayersBlocks is not finite; resetting to 16.");
            config.runeKnight.spawn.minDistanceFromPlayersBlocks = 16.0;
        }
        if (config.runeKnight.spawn.minDistanceFromPlayersBlocks < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.minDistanceFromPlayersBlocks < 0; clamping to 0.");
            config.runeKnight.spawn.minDistanceFromPlayersBlocks = 0;
        }
        if (config.runeKnight.spawn.minDistanceFromPlayersBlocks > 256) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.minDistanceFromPlayersBlocks > 256; clamping to 256.");
            config.runeKnight.spawn.minDistanceFromPlayersBlocks = 256;
        }
        if (!Double.isFinite(config.runeKnight.spawn.nightSunlightThreshold)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.nightSunlightThreshold is not finite; resetting to 0.25.");
            config.runeKnight.spawn.nightSunlightThreshold = 0.25;
        }
        if (config.runeKnight.spawn.nightSunlightThreshold < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.nightSunlightThreshold < 0; clamping to 0.");
            config.runeKnight.spawn.nightSunlightThreshold = 0;
        }
        if (config.runeKnight.spawn.nightSunlightThreshold > 1) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.spawn.nightSunlightThreshold > 1; clamping to 1.");
            config.runeKnight.spawn.nightSunlightThreshold = 1;
        }
        if (!Double.isFinite(config.runeKnight.despawn.afterSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.despawn.afterSeconds is not finite; resetting to 300.");
            config.runeKnight.despawn.afterSeconds = 300.0;
        }
        if (config.runeKnight.despawn.afterSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.despawn.afterSeconds < 0; clamping to 0.");
            config.runeKnight.despawn.afterSeconds = 0;
        }
        if (config.runeKnight.despawn.afterSeconds > 3600) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.despawn.afterSeconds > 3600; clamping to 3600.");
            config.runeKnight.despawn.afterSeconds = 3600;
        }
        if (!Double.isFinite(config.runeKnight.aggro.radiusBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.aggro.radiusBlocks is not finite; resetting to 40.");
            config.runeKnight.aggro.radiusBlocks = 40.0;
        }
        if (config.runeKnight.aggro.radiusBlocks < 1) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.aggro.radiusBlocks < 1; clamping to 1.");
            config.runeKnight.aggro.radiusBlocks = 1;
        }
        if (config.runeKnight.aggro.radiusBlocks > 512) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.aggro.radiusBlocks > 512; clamping to 512.");
            config.runeKnight.aggro.radiusBlocks = 512;
        }
        if (!Double.isFinite(config.runeKnight.projectiles.cooldownSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.cooldownSeconds is not finite; resetting to 1.25.");
            config.runeKnight.projectiles.cooldownSeconds = 1.25;
        }
        if (config.runeKnight.projectiles.cooldownSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.cooldownSeconds < 0; clamping to 0.");
            config.runeKnight.projectiles.cooldownSeconds = 0;
        }
        if (config.runeKnight.projectiles.cooldownSeconds > 60) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.cooldownSeconds > 60; clamping to 60.");
            config.runeKnight.projectiles.cooldownSeconds = 60;
        }
        if (!Double.isFinite(config.runeKnight.projectiles.rangeBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.rangeBlocks is not finite; resetting to 24.");
            config.runeKnight.projectiles.rangeBlocks = 24.0;
        }
        if (config.runeKnight.projectiles.rangeBlocks < 1) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.rangeBlocks < 1; clamping to 1.");
            config.runeKnight.projectiles.rangeBlocks = 1;
        }
        if (config.runeKnight.projectiles.rangeBlocks > 512) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.rangeBlocks > 512; clamping to 512.");
            config.runeKnight.projectiles.rangeBlocks = 512;
        }
        if (!Double.isFinite(config.runeKnight.projectiles.aimHeightBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.aimHeightBlocks is not finite; resetting to 1.25.");
            config.runeKnight.projectiles.aimHeightBlocks = 1.25;
        }
        if (config.runeKnight.projectiles.aimHeightBlocks < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.aimHeightBlocks < 0; clamping to 0.");
            config.runeKnight.projectiles.aimHeightBlocks = 0;
        }
        if (config.runeKnight.projectiles.aimHeightBlocks > 5) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.aimHeightBlocks > 5; clamping to 5.");
            config.runeKnight.projectiles.aimHeightBlocks = 5;
        }
        if (config.runeKnight.projectiles.projectileId == null || config.runeKnight.projectiles.projectileId.isBlank()) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.projectiles.projectileId is blank; resetting to RuneKnight_Bolt.");
            config.runeKnight.projectiles.projectileId = "RuneKnight_Bolt";
        }
        if (config.runeKnight.loot.kuduBootsDropChancePercent < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.loot.kuduBootsDropChancePercent < 0; clamping to 0.");
            config.runeKnight.loot.kuduBootsDropChancePercent = 0;
        }
        if (config.runeKnight.loot.kuduBootsDropChancePercent > 100) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.loot.kuduBootsDropChancePercent > 100; clamping to 100.");
            config.runeKnight.loot.kuduBootsDropChancePercent = 100;
        }
        if (config.runeKnight.loot.frostBookDropChancePercent < 0) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.loot.frostBookDropChancePercent < 0; clamping to 0.");
            config.runeKnight.loot.frostBookDropChancePercent = 0;
        }
        if (config.runeKnight.loot.frostBookDropChancePercent > 100) {
            debug.traceFileOnly(null, "Config sanitize: runeKnight.loot.frostBookDropChancePercent > 100; clamping to 100.");
            config.runeKnight.loot.frostBookDropChancePercent = 100;
        }

        if (!Double.isFinite(config.kuduAdept.spawn.intervalSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.intervalSeconds is not finite; resetting to 120.");
            config.kuduAdept.spawn.intervalSeconds = DEFAULT_KUDU_ADEPT_INTERVAL_SECONDS;
        }
        if (config.kuduAdept.spawn.intervalSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.intervalSeconds < 0; clamping to 0.");
            config.kuduAdept.spawn.intervalSeconds = 0;
        }
        if (config.kuduAdept.spawn.intervalSeconds > 3600) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.intervalSeconds > 3600; clamping to 3600.");
            config.kuduAdept.spawn.intervalSeconds = 3600;
        }
        if (config.kuduAdept.spawn.maxActivePerWorld < 0) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.maxActivePerWorld < 0; clamping to 0.");
            config.kuduAdept.spawn.maxActivePerWorld = 0;
        }
        if (config.kuduAdept.spawn.maxActivePerWorld > 500) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.maxActivePerWorld > 500; clamping to 500.");
            config.kuduAdept.spawn.maxActivePerWorld = 500;
        }
        if (config.kuduAdept.spawn.spawnsPerInterval < 0) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.spawnsPerInterval < 0; clamping to 0.");
            config.kuduAdept.spawn.spawnsPerInterval = 0;
        }
        if (config.kuduAdept.spawn.spawnsPerInterval > 100) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.spawnsPerInterval > 100; clamping to 100.");
            config.kuduAdept.spawn.spawnsPerInterval = 100;
        }
        if (config.kuduAdept.spawn.maxAttemptsPerInterval < 1) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.maxAttemptsPerInterval < 1; clamping to 1.");
            config.kuduAdept.spawn.maxAttemptsPerInterval = 1;
        }
        if (config.kuduAdept.spawn.maxAttemptsPerInterval > 500) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.maxAttemptsPerInterval > 500; clamping to 500.");
            config.kuduAdept.spawn.maxAttemptsPerInterval = 500;
        }
        if (!Double.isFinite(config.kuduAdept.spawn.densityCellSizeBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.densityCellSizeBlocks is not finite; resetting to 256.");
            config.kuduAdept.spawn.densityCellSizeBlocks = DEFAULT_KUDU_ADEPT_DENSITY_CELL_SIZE_BLOCKS;
        }
        if (config.kuduAdept.spawn.densityCellSizeBlocks < 32) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.densityCellSizeBlocks < 32; clamping to 32.");
            config.kuduAdept.spawn.densityCellSizeBlocks = 32;
        }
        if (config.kuduAdept.spawn.densityCellSizeBlocks > 2048) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.densityCellSizeBlocks > 2048; clamping to 2048.");
            config.kuduAdept.spawn.densityCellSizeBlocks = 2048;
        }
        if (config.kuduAdept.spawn.cellSpawnChancePercent < 0) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.cellSpawnChancePercent < 0; clamping to 0.");
            config.kuduAdept.spawn.cellSpawnChancePercent = 0;
        }
        if (config.kuduAdept.spawn.cellSpawnChancePercent > 100) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.cellSpawnChancePercent > 100; clamping to 100.");
            config.kuduAdept.spawn.cellSpawnChancePercent = 100;
        }
        if (!Double.isFinite(config.kuduAdept.spawn.radiusMinBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.radiusMinBlocks is not finite; resetting to 8.");
            config.kuduAdept.spawn.radiusMinBlocks = DEFAULT_KUDU_ADEPT_RADIUS_MIN_BLOCKS;
        }
        if (!Double.isFinite(config.kuduAdept.spawn.radiusMaxBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.radiusMaxBlocks is not finite; resetting to 280.");
            config.kuduAdept.spawn.radiusMaxBlocks = DEFAULT_KUDU_ADEPT_RADIUS_MAX_BLOCKS;
        }
        if (config.kuduAdept.spawn.radiusMinBlocks < 0) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.radiusMinBlocks < 0; clamping to 0.");
            config.kuduAdept.spawn.radiusMinBlocks = 0;
        }
        if (config.kuduAdept.spawn.radiusMaxBlocks < config.kuduAdept.spawn.radiusMinBlocks) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.radiusMaxBlocks < radiusMinBlocks; raising max to min.");
            config.kuduAdept.spawn.radiusMaxBlocks = config.kuduAdept.spawn.radiusMinBlocks;
        }
        if (config.kuduAdept.spawn.radiusMaxBlocks > 512) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.radiusMaxBlocks > 512; clamping to 512.");
            config.kuduAdept.spawn.radiusMaxBlocks = 512;
        }
        if (!Double.isFinite(config.kuduAdept.spawn.minDistanceFromPlayersBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.minDistanceFromPlayersBlocks is not finite; resetting to 8.");
            config.kuduAdept.spawn.minDistanceFromPlayersBlocks = DEFAULT_KUDU_ADEPT_MIN_DISTANCE_FROM_PLAYERS_BLOCKS;
        }
        if (config.kuduAdept.spawn.minDistanceFromPlayersBlocks < 0) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.minDistanceFromPlayersBlocks < 0; clamping to 0.");
            config.kuduAdept.spawn.minDistanceFromPlayersBlocks = 0;
        }
        if (config.kuduAdept.spawn.minDistanceFromPlayersBlocks > 512) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.minDistanceFromPlayersBlocks > 512; clamping to 512.");
            config.kuduAdept.spawn.minDistanceFromPlayersBlocks = 512;
        }
        if (!Double.isFinite(config.kuduAdept.spawn.daySunlightThreshold)) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.daySunlightThreshold is not finite; resetting to 0.");
            config.kuduAdept.spawn.daySunlightThreshold = 0.0;
        }
        if (config.kuduAdept.spawn.daySunlightThreshold < 0) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.daySunlightThreshold < 0; clamping to 0.");
            config.kuduAdept.spawn.daySunlightThreshold = 0;
        }
        if (config.kuduAdept.spawn.daySunlightThreshold > 1) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.spawn.daySunlightThreshold > 1; clamping to 1.");
            config.kuduAdept.spawn.daySunlightThreshold = 1;
        }
        if (!Double.isFinite(config.kuduAdept.despawn.afterSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.despawn.afterSeconds is not finite; resetting to 0.");
            config.kuduAdept.despawn.afterSeconds = DEFAULT_KUDU_ADEPT_DESPAWN_AFTER_SECONDS;
        }
        if (config.kuduAdept.despawn.afterSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.despawn.afterSeconds < 0; clamping to 0.");
            config.kuduAdept.despawn.afterSeconds = 0;
        }
        if (config.kuduAdept.despawn.afterSeconds > 3600) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.despawn.afterSeconds > 3600; clamping to 3600.");
            config.kuduAdept.despawn.afterSeconds = 3600;
        }
        if (config.kuduAdept.roleName == null || config.kuduAdept.roleName.isBlank()) {
            debug.traceFileOnly(null, "Config sanitize: kuduAdept.roleName is blank; resetting to Kudu_Adept_Magician.");
            config.kuduAdept.roleName = "Kudu_Adept_Magician";
        }

        if (config.hordeBook.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: hordeBook.manaCost < 0; clamping to 0.");
            config.hordeBook.manaCost = 0;
        }
        if (config.hordeBook.minionLifetimeSeconds < 1) {
            debug.traceFileOnly(null, "Config sanitize: hordeBook.minionLifetimeSeconds < 1; clamping to 1.");
            config.hordeBook.minionLifetimeSeconds = 1;
        }
        if (config.hordeBook.minionLifetimeSeconds > 600) {
            debug.traceFileOnly(null, "Config sanitize: hordeBook.minionLifetimeSeconds > 600; clamping to 600.");
            config.hordeBook.minionLifetimeSeconds = 600;
        }
        if (config.hordeBook.ownerFriendlySeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: hordeBook.ownerFriendlySeconds < 0; clamping to 0.");
            config.hordeBook.ownerFriendlySeconds = 0;
        }
        if (config.hordeBook.ownerFriendlySeconds > 3600) {
            debug.traceFileOnly(null, "Config sanitize: hordeBook.ownerFriendlySeconds > 3600; clamping to 3600.");
            config.hordeBook.ownerFriendlySeconds = 3600;
        }
        if (!Double.isFinite(config.hordeBook.spawnDistanceBlocks)) {
            debug.traceFileOnly(null, "Config sanitize: hordeBook.spawnDistanceBlocks is not finite; resetting to 3.");
            config.hordeBook.spawnDistanceBlocks = 3.0;
        }
        if (config.hordeBook.spawnDistanceBlocks < 0) {
            debug.traceFileOnly(null, "Config sanitize: hordeBook.spawnDistanceBlocks < 0; clamping to 0.");
            config.hordeBook.spawnDistanceBlocks = 0;
        }
        if (config.hordeBook.spawnDistanceBlocks > 20) {
            debug.traceFileOnly(null, "Config sanitize: hordeBook.spawnDistanceBlocks > 20; clamping to 20.");
            config.hordeBook.spawnDistanceBlocks = 20;
        }

        if (config.doomBook.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: doomBook.manaCost < 0; clamping to 0.");
            config.doomBook.manaCost = 0;
        }
        if (!Double.isFinite(config.doomBook.projectileDelaySeconds)) {
            debug.traceFileOnly(null, "Config sanitize: doomBook.projectileDelaySeconds is not finite; resetting to 0.24.");
            config.doomBook.projectileDelaySeconds = 0.24;
        }
        if (config.doomBook.projectileDelaySeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: doomBook.projectileDelaySeconds < 0; clamping to 0.");
            config.doomBook.projectileDelaySeconds = 0;
        }
        if (config.doomBook.projectileDelaySeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: doomBook.projectileDelaySeconds > 5; clamping to 5.");
            config.doomBook.projectileDelaySeconds = 5;
        }

        if (config.morphBook.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: morphBook.manaCost < 0; clamping to 0.");
            config.morphBook.manaCost = 0;
        }

        if (config.frostBook.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: frostBook.manaCost < 0; clamping to 0.");
            config.frostBook.manaCost = 0;
        }

        if (config.flameBook.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: flameBook.manaCost < 0; clamping to 0.");
            config.flameBook.manaCost = 0;
        }

        if (config.teleportBook.maxDistanceBlocks < 1) {
            debug.traceFileOnly(null, "Config sanitize: teleportBook.maxDistanceBlocks < 1; clamping to 1.");
            config.teleportBook.maxDistanceBlocks = 1;
        }
        if (config.teleportBook.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: teleportBook.manaCost < 0; clamping to 0.");
            config.teleportBook.manaCost = 0;
        }
        if (!Double.isFinite(config.teleportBook.castDelaySeconds)) {
            debug.traceFileOnly(null, "Config sanitize: teleportBook.castDelaySeconds is not finite; resetting to 0.5.");
            config.teleportBook.castDelaySeconds = 0.5;
        }
        if (config.teleportBook.castDelaySeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: teleportBook.castDelaySeconds < 0; clamping to 0.");
            config.teleportBook.castDelaySeconds = 0;
        }
        if (config.teleportBook.castDelaySeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: teleportBook.castDelaySeconds > 5; clamping to 5.");
            config.teleportBook.castDelaySeconds = 5;
        }

        if (config.miningBook.maxDistanceBlocks < 1) {
            debug.traceFileOnly(null, "Config sanitize: miningBook.maxDistanceBlocks < 1; clamping to 1.");
            config.miningBook.maxDistanceBlocks = 1;
        }
        if (config.miningBook.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: miningBook.manaCost < 0; clamping to 0.");
            config.miningBook.manaCost = 0;
        }
        if (config.miningBook.gridSize < 1) {
            debug.traceFileOnly(null, "Config sanitize: miningBook.gridSize < 1; clamping to 1.");
            config.miningBook.gridSize = 1;
        }
        if (config.miningBook.gridSize > 9) {
            debug.traceFileOnly(null, "Config sanitize: miningBook.gridSize > 9; clamping to 9.");
            config.miningBook.gridSize = 9;
        }
        if (config.miningBook.gridSize % 2 == 0) {
            int previous = config.miningBook.gridSize;
            config.miningBook.gridSize = Math.max(1, config.miningBook.gridSize - 1);
            debug.traceFileOnly(null, "Config sanitize: miningBook.gridSize must be odd; " + previous + " -> " + config.miningBook.gridSize + ".");
        }
        int miningGridArea = config.miningBook.gridSize * config.miningBook.gridSize;
        if (config.miningBook.maxBlocks < 1) {
            debug.traceFileOnly(null, "Config sanitize: miningBook.maxBlocks < 1; clamping to 1.");
            config.miningBook.maxBlocks = 1;
        }
        if (config.miningBook.maxBlocks > miningGridArea) {
            debug.traceFileOnly(null, "Config sanitize: miningBook.maxBlocks > gridSize^2; clamping to " + miningGridArea + ".");
            config.miningBook.maxBlocks = miningGridArea;
        }
        if (!Double.isFinite(config.flameBook.projectileDelaySeconds)) {
            debug.traceFileOnly(null, "Config sanitize: flameBook.projectileDelaySeconds is not finite; resetting to 0.2.");
            config.flameBook.projectileDelaySeconds = 0.2;
        }
        if (config.flameBook.projectileDelaySeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: flameBook.projectileDelaySeconds < 0; clamping to 0.");
            config.flameBook.projectileDelaySeconds = 0;
        }
        if (config.flameBook.projectileDelaySeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: flameBook.projectileDelaySeconds > 5; clamping to 5.");
            config.flameBook.projectileDelaySeconds = 5;
        }
        if (!config.healingBook.healAmount.full && config.healingBook.healAmount.value < 0) {
            debug.traceFileOnly(null, "Config sanitize: healingBook.healAmount < 0; clamping to 0.");
            config.healingBook.healAmount.value = 0;
        }
        if (!config.healingBook.manaCost.full && config.healingBook.manaCost.value < 0) {
            debug.traceFileOnly(null, "Config sanitize: healingBook.manaCost < 0; clamping to 0.");
            config.healingBook.manaCost.value = 0;
        }
        if (!Double.isFinite(config.healingBook.projectileDelaySeconds)) {
            debug.traceFileOnly(null, "Config sanitize: healingBook.projectileDelaySeconds is not finite; resetting to 0.15.");
            config.healingBook.projectileDelaySeconds = 0.15;
        }
        if (config.healingBook.projectileDelaySeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: healingBook.projectileDelaySeconds < 0; clamping to 0.");
            config.healingBook.projectileDelaySeconds = 0;
        }
        if (config.healingBook.projectileDelaySeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: healingBook.projectileDelaySeconds > 5; clamping to 5.");
            config.healingBook.projectileDelaySeconds = 5;
        }

        if (config.immunityBook.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: immunityBook.manaCost < 0; clamping to 0.");
            config.immunityBook.manaCost = 0;
        }
        if (config.immunityBook.immunitySeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: immunityBook.immunitySeconds < 0; clamping to 0.");
            config.immunityBook.immunitySeconds = 0;
        }

        if (config.tauntBook.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.manaCost < 0; clamping to 0.");
            config.tauntBook.manaCost = 0;
        }
        if (config.tauntBook.launchHeightBlocks < 1) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.launchHeightBlocks < 1; clamping to 1.");
            config.tauntBook.launchHeightBlocks = 1;
        }
        if (config.tauntBook.fallImmunitySeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.fallImmunitySeconds < 0; clamping to 0.");
            config.tauntBook.fallImmunitySeconds = 0;
        }
        if (config.tauntBook.slamDamage < 0) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.slamDamage < 0; clamping to 0.");
            config.tauntBook.slamDamage = 0;
        }
        if (config.tauntBook.slamRadiusBlocks < 0) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.slamRadiusBlocks < 0; clamping to 0.");
            config.tauntBook.slamRadiusBlocks = 0;
        }
        if (config.tauntBook.groundBreakDepthBlocks < 1) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.groundBreakDepthBlocks < 1; clamping to 1.");
            config.tauntBook.groundBreakDepthBlocks = 1;
        }
        if (config.tauntBook.groundBreakDepthBlocks > 8) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.groundBreakDepthBlocks > 8; clamping to 8.");
            config.tauntBook.groundBreakDepthBlocks = 8;
        }
        if (config.tauntBook.groundBreakDepthPerStack < 0) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.groundBreakDepthPerStack < 0; clamping to 0.");
            config.tauntBook.groundBreakDepthPerStack = 0;
        }
        if (config.tauntBook.groundBreakDepthPerStack > 4) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.groundBreakDepthPerStack > 4; clamping to 4.");
            config.tauntBook.groundBreakDepthPerStack = 4;
        }
        if (!Double.isFinite(config.tauntBook.groundBreakSparingChance)) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.groundBreakSparingChance is not finite; resetting to 0.18.");
            config.tauntBook.groundBreakSparingChance = 0.18;
        }
        if (config.tauntBook.groundBreakSparingChance < 0) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.groundBreakSparingChance < 0; clamping to 0.");
            config.tauntBook.groundBreakSparingChance = 0;
        }
        if (config.tauntBook.groundBreakSparingChance > 0.85) {
            debug.traceFileOnly(null, "Config sanitize: tauntBook.groundBreakSparingChance > 0.85; clamping to 0.85.");
            config.tauntBook.groundBreakSparingChance = 0.85;
        }

        if (config.ancientSword.manaCost < 0) {
            debug.traceFileOnly(null, "Config sanitize: ancientSword.manaCost < 0; clamping to 0.");
            config.ancientSword.manaCost = 0;
        }
        if (config.ancientSword.projectileId == null || config.ancientSword.projectileId.isBlank()) {
            debug.traceFileOnly(null, "Config sanitize: ancientSword.projectileId is blank; resetting to Ancient_Slash.");
            config.ancientSword.projectileId = "Ancient_Slash";
        }
        if (!Double.isFinite(config.ancientSword.cooldownSeconds)) {
            debug.traceFileOnly(null, "Config sanitize: ancientSword.cooldownSeconds is not finite; resetting to 1.25.");
            config.ancientSword.cooldownSeconds = 1.25;
        }
        if (config.ancientSword.cooldownSeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: ancientSword.cooldownSeconds < 0; clamping to 0.");
            config.ancientSword.cooldownSeconds = 0;
        }
        if (config.ancientSword.cooldownSeconds > 5) {
            debug.traceFileOnly(null, "Config sanitize: ancientSword.cooldownSeconds > 5; clamping to 5.");
            config.ancientSword.cooldownSeconds = 5;
        }
        if (!Double.isFinite(config.ancientSword.castDelaySeconds)) {
            debug.traceFileOnly(null, "Config sanitize: ancientSword.castDelaySeconds is not finite; resetting to 0.34.");
            config.ancientSword.castDelaySeconds = 0.34;
        }
        if (config.ancientSword.castDelaySeconds < 0) {
            debug.traceFileOnly(null, "Config sanitize: ancientSword.castDelaySeconds < 0; clamping to 0.");
            config.ancientSword.castDelaySeconds = 0;
        }
        if (config.ancientSword.castDelaySeconds > 2) {
            debug.traceFileOnly(null, "Config sanitize: ancientSword.castDelaySeconds > 2; clamping to 2.");
            config.ancientSword.castDelaySeconds = 2;
        }
    }

    private void persistBestEffort(@Nonnull AxoTalesServerConfig config) {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, gson.toJson(config), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to persist " + CONFIG_FILE_NAME + " to " + configPath + ".", t);
        }
    }

}
