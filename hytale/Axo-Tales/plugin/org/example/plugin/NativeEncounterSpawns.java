package org.example.plugin;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.server.spawning.assets.spawns.config.RoleSpawnParameters;
import com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn;

final class NativeEncounterSpawns {
    private static final java.lang.reflect.Field WEIGHT = weightField();
    static final String WHISPERION = "AxoTales_Whisperion_Surface";
    static final String TOM_TOM = "AxoTales_TomTom_Surface";
    static final String GRAY_CAT = "AxoTales_GrayCat_AllBiomes";
    private final AxoTalesServerConfig config;

    NativeEncounterSpawns(AxoTalesServerConfig config) { this.config = config; }

    void loaded(LoadedAssetsEvent<?, ?, ?> event) {
        if (event.getAssetClass() != WorldNPCSpawn.class) return;
        for (Object asset : event.getLoadedAssets().values()) {
            if (asset instanceof WorldNPCSpawn spawn) configure(spawn);
        }
    }

    void configure(WorldNPCSpawn spawn) {
        double weight;
        String role;
        if (WHISPERION.equals(spawn.getId())) {
            role = WhisperionSystem.ROLE;
            weight = config.whisperion.enabled ? weight(config.whisperion.spawnWeight, 2) : 0;
        } else if (TOM_TOM.equals(spawn.getId())) {
            role = TomTomCombat.ROLE;
            weight = config.encounters.tomTomEnabled ? weight(config.encounters.tomTomSpawnWeight, 2.5) : 0;
        } else if (GRAY_CAT.equals(spawn.getId())) {
            role = GrayCatState.ROLE;
            weight = config.encounters.grayCatEnabled ? weight(config.encounters.grayCatSpawnWeight, 8) : 0;
        } else return;
        var entries = spawn.getNPCs();
        if (entries == null || entries.length != 1 || !role.equals(entries[0].getId()))
            throw new IllegalStateException("Unexpected encounter definition: " + spawn.getId());
        try {
            WEIGHT.setDouble(entries[0], weight);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot configure native encounter weight", failure);
        }
    }

    static double weight(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0, Math.min(100, value)) : fallback;
    }

    private static java.lang.reflect.Field weightField() {
        try {
            var field = RoleSpawnParameters.class.getDeclaredField("weight");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pinned native spawn weight field is unavailable", failure);
        }
    }
}
