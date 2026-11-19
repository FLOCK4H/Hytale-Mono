package org.example.plugin;

import com.google.gson.JsonObject;


final class EncounterTuning {
    private EncounterTuning() {}

    static double interval(double seconds, double fallback) {
        return Double.isFinite(seconds) ? Math.max(10, Math.min(3600, seconds)) : fallback;
    }

    static boolean knightRole(String actual, String configured) {
        return actual != null && (actual.equals(configured)
            || actual.equals("Kudu_Rune_Knight") || actual.equals("Cursed_Knight"));
    }

    static void migrate(JsonObject user) {
        int version = user.has("encounterDefaultsVersion") ? user.get("encounterDefaultsVersion").getAsInt() : 0;
        if (version >= 3) return;
        if (version >= 2) {
            user.addProperty("encounterDefaultsVersion", 3);
            return;
        }
        if (version < 1) migrateFirstDefaults(user);


        replace(object(object(user, "runeKnight"), "spawn"), "intervalSeconds", 75, 62.5);
        replace(object(object(user, "kuduAdept"), "spawn"), "intervalSeconds", 60, 50);
        replace(object(user, "encounters"), "tomTomIntervalSeconds", 120, 100);
        replace(object(user, "whisperion"), "intervalSeconds", 600, 330);
        user.addProperty("encounterDefaultsVersion", 3);
    }

    private static void migrateFirstDefaults(JsonObject user) {
        JsonObject knight = object(user, "runeKnight");
        if (!knight.has("roleName") || "Kudu_Rune_Knight".equals(knight.get("roleName").getAsString()))
            knight.addProperty("roleName", "Cursed_Knight");
        JsonObject spawn = object(knight, "spawn");
        replace(spawn, "intervalSeconds", 300, 75);
        replace(spawn, "maxActivePerWorld", 4, 8);
        replace(object(knight, "despawn"), "afterSeconds", 300, 600);

        object(knight, "loot").remove("kuduBootsDropChancePercent");
        JsonObject mage = object(object(user, "kuduAdept"), "spawn");
        replace(mage, "intervalSeconds", 120, 60);
        replace(mage, "maxAttemptsPerInterval", 3, 12);
        replace(mage, "densityCellSizeBlocks", 256, 192);
        replace(mage, "cellSpawnChancePercent", 33, 50);
        replace(mage, "minDistanceFromPlayersBlocks", 8, 16);
        replace(mage, "radiusMinBlocks", 8, 24);
        replace(mage, "radiusMaxBlocks", 280, 112);
        replace(object(user, "whisperion"), "maxWildPerWorld", 8, 3);
        user.addProperty("encounterDefaultsVersion", 1);
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) parent.add(key, new JsonObject());
        return parent.getAsJsonObject(key);
    }

    private static void replace(JsonObject object, String key, double oldValue, double value) {
        if (!object.has(key) || (object.get(key).isJsonPrimitive()
                && object.getAsJsonPrimitive(key).isNumber() && object.get(key).getAsDouble() == oldValue))
            object.addProperty(key, value);
    }
}
