package org.example.plugin;

import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active Rune Knights spawned by this plugin so they can be managed (aggro/cleanup).
 */
public final class RuneKnightSpawnState {

    public record ActiveRuneKnight(@Nonnull UUID uuid, long spawnedAtNanos, long expiresAtNanos) {
    }

    private final ConcurrentHashMap<String, ConcurrentHashMap<UUID, ActiveRuneKnight>> byWorld = new ConcurrentHashMap<>();

    public void trackSpawn(@Nonnull World world, @Nonnull UUID uuid, long spawnedAtNanos, long expiresAtNanos) {
        byWorld
            .computeIfAbsent(worldKey(world), ignored -> new ConcurrentHashMap<>())
            .put(uuid, new ActiveRuneKnight(uuid, spawnedAtNanos, expiresAtNanos));
    }

    public void remove(@Nonnull World world, @Nonnull UUID uuid) {
        String key = worldKey(world);
        ConcurrentHashMap<UUID, ActiveRuneKnight> map = byWorld.get(key);
        if (map == null) {
            return;
        }
        map.remove(uuid);
        if (map.isEmpty()) {
            byWorld.remove(key, map);
        }
    }

    public int count(@Nonnull World world) {
        ConcurrentHashMap<UUID, ActiveRuneKnight> map = byWorld.get(worldKey(world));
        return map != null ? map.size() : 0;
    }

    public @Nonnull List<ActiveRuneKnight> snapshot(@Nonnull World world) {
        ConcurrentHashMap<UUID, ActiveRuneKnight> map = byWorld.get(worldKey(world));
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(map.values());
    }

    public void clearWorld(@Nonnull World world) {
        byWorld.remove(worldKey(world));
    }

    private static @Nullable UUID worldUuid(@Nonnull World world) {
        try {
            var config = world.getWorldConfig();
            return config != null ? config.getUuid() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String worldKey(@Nonnull World world) {
        UUID uuid = worldUuid(world);
        return uuid != null ? uuid.toString() : world.getName();
    }
}

