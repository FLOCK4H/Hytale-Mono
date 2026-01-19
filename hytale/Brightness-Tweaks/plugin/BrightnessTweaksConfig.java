package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores persisted per-player settings for Brightness Tweaks.
 */
public final class BrightnessTweaksConfig {

    /**
     * Codec used by the Hytale config system to load and save {@link BrightnessTweaksConfig}.
     */
    public static final BuilderCodec<BrightnessTweaksConfig> CODEC =
        BuilderCodec.builder(BrightnessTweaksConfig.class, BrightnessTweaksConfig::new)
            .append(
                new KeyedCodec<>("Players", new MapCodec<>(PlayerSettings.CODEC, ConcurrentHashMap::new, false), false),
                (cfg, value, info) -> cfg.players = value == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(value),
                (cfg, info) -> cfg.players
            )
            .add()
            .build();

    private Map<String, PlayerSettings> players = new ConcurrentHashMap<>();

    /**
     * Returns the player settings map keyed by player UUID string.
     */
    @Nonnull
    public Map<String, PlayerSettings> getPlayers() {
        return players;
    }

    /**
     * A single player's persisted brightness preferences.
     */
    public static final class PlayerSettings {

        /**
         * Codec used by the Hytale config system to load and save {@link PlayerSettings}.
         */
        public static final BuilderCodec<PlayerSettings> CODEC =
            BuilderCodec.builder(PlayerSettings.class, PlayerSettings::new)
                .append(
                    new KeyedCodec<>("Brightness", Codec.FLOAT, false),
                    (settings, value, info) -> settings.brightness = value,
                    (settings, info) -> settings.brightness
                )
                .add()
                .append(
                    new KeyedCodec<>("TintRgb", Codec.INTEGER, false),
                    (settings, value, info) -> settings.tintRgb = value == null ? null : (value & 0xFFFFFF),
                    (settings, info) -> settings.tintRgb
                )
                .add()
                .append(
                    new KeyedCodec<>("Warmth", Codec.FLOAT, false),
                    (settings, value, info) -> settings.warmth01 = value,
                    (settings, info) -> settings.warmth01
                )
                .add()
                .build();

        private Float brightness;
        private Integer tintRgb;
        private Float warmth01;

        /**
         * Returns the brightness scale (null means disabled).
         */
        @Nullable
        public Float getBrightness() {
            return brightness;
        }

        /**
         * Sets the brightness scale (null means disabled).
         */
        public void setBrightness(@Nullable Float brightness) {
            this.brightness = brightness;
        }

        /**
         * Returns the tint RGB (0xRRGGBB) or null to use the torch's default tint.
         */
        @Nullable
        public Integer getTintRgb() {
            return tintRgb;
        }

        /**
         * Sets the tint RGB (0xRRGGBB) or null to use the torch's default tint.
         */
        public void setTintRgb(@Nullable Integer tintRgb) {
            this.tintRgb = tintRgb == null ? null : (tintRgb & 0xFFFFFF);
        }

        /**
         * Returns the warmth (0.0-1.0) or null to use the torch's default tint.
         */
        @Nullable
        public Float getWarmth01() {
            return warmth01;
        }

        /**
         * Sets the warmth (0.0-1.0) or null to use the torch's default tint.
         */
        public void setWarmth01(@Nullable Float warmth01) {
            this.warmth01 = warmth01;
        }

        /**
         * Returns {@code true} if no setting is currently stored.
         */
        public boolean isEmpty() {
            return brightness == null && tintRgb == null && warmth01 == null;
        }
    }
}
