package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

/**
 * Interactive settings page for Brightness Tweaks.
 */
public final class BrightnessTweaksPage extends InteractiveCustomUIPage<BrightnessTweaksPage.PageEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String UI_DOCUMENT = "Pages/BrightnessTweaksPage.ui";

    private static final String EVENT_TYPE = "Type";
    private static final String EVENT_TYPE_BRIGHTNESS = "Brightness";
    private static final String EVENT_TYPE_WARMTH = "Warmth";
    private static final String EVENT_TYPE_APPLY_TINT = "ApplyTint";
    private static final String EVENT_TYPE_CLEAR_TINT = "ClearTint";
    private static final String EVENT_TYPE_CLEAR_WARMTH = "ClearWarmth";
    private static final String EVENT_TYPE_RESET_ALL = "ResetAll";

    private static final String KEY_BRIGHTNESS = "@Brightness";
    private static final String KEY_WARMTH = "@Warmth";
    private static final String KEY_TINT = "@Tint";

    private static final String ID_STATUS_TEXT = "#Status.Text";
    private static final String ID_FEEDBACK_TEXT = "#Feedback.Text";
    private static final String ID_BRIGHTNESS_SLIDER_VALUE = "#BrightnessSlider.Value";
    private static final String ID_BRIGHTNESS_VALUE_TEXT = "#BrightnessValue.Text";
    private static final String ID_WARMTH_SLIDER_VALUE = "#WarmthSlider.Value";
    private static final String ID_WARMTH_VALUE_TEXT = "#WarmthValue.Text";
    private static final String ID_TINT_INPUT_VALUE = "#TintInput.Value";

    private final BrightnessService brightnessService;
    private final BrightnessTweaksConfigStore configStore;
    private final UUID playerUuid;

    private String tintInputValueToSet;
    private String feedbackText = "";

    public BrightnessTweaksPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull BrightnessService brightnessService,
        @Nonnull BrightnessTweaksConfigStore configStore
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.brightnessService = brightnessService;
        this.configStore = configStore;
        this.playerUuid = playerRef.getUuid();
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder uiCommandBuilder,
        @Nonnull UIEventBuilder uiEventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        uiCommandBuilder.append(UI_DOCUMENT);
        applyInitialUiState(uiCommandBuilder);

        buildEventBindings(uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageEventData data) {
        super.handleDataEvent(ref, store, data);

        String type = data.type;
        if (type == null || type.isBlank()) {
            feedbackText = "Unknown UI event.";
            sendUpdate(buildUpdate(), buildUpdateEvents(), false);
            return;
        }

        World world = resolvePlayerWorld(ref, store);

        switch (type) {
            case EVENT_TYPE_BRIGHTNESS -> handleBrightness(world, data.brightness);
            case EVENT_TYPE_WARMTH -> handleWarmth(world, data.warmth);
            case EVENT_TYPE_APPLY_TINT -> handleTint(world, data.tint);
            case EVENT_TYPE_CLEAR_TINT -> clearTint(world);
            case EVENT_TYPE_CLEAR_WARMTH -> clearWarmth(world);
            case EVENT_TYPE_RESET_ALL -> resetAll(world);
            default -> feedbackText = "Unknown UI event: " + type;
        }

        sendUpdate(buildUpdate(), buildUpdateEvents(), false);
        tintInputValueToSet = null;
    }

    private void handleBrightness(@Nullable World world, @Nullable Float sliderValue) {
        if (sliderValue == null) {
            feedbackText = "Brightness value missing.";
            return;
        }

        LOGGER.atInfo().log("Brightness UI slider raw value: " + sliderValue);
        float normalized01 = clamp(sliderValue, 0.0f, 100.0f) / 100.0f;

        Float desired = normalized01 <= 0.0001f
            ? null
            : clamp(normalized01, BrightnessService.MIN_BRIGHTNESS, BrightnessService.MAX_BRIGHTNESS);
        brightnessService.setDesiredBrightness(playerUuid, desired);
        configStore.setBrightness(playerUuid, desired);
        if (world != null) {
            brightnessService.syncPlayer(world, playerUuid, false);
        }

        feedbackText = desired == null ? "Boost disabled." : "Brightness set to " + format2(desired) + ".";
    }

    private void handleWarmth(@Nullable World world, @Nullable Float sliderValue) {
        if (sliderValue == null) {
            feedbackText = "Warmth value missing.";
            return;
        }

        LOGGER.atInfo().log("Warmth UI slider raw value: " + sliderValue);
        float clamped = clamp(sliderValue, 0.0f, 100.0f) / 100.0f;

        brightnessService.setDesiredWarmth(playerUuid, clamped);
        configStore.setWarmth01(playerUuid, clamped);
        if (world != null) {
            brightnessService.syncPlayer(world, playerUuid, false);
        }

        feedbackText = "Warmth set to " + format2(clamped) + " (clears custom tint).";
    }

    private void handleTint(@Nullable World world, @Nullable String tintText) {
        String trimmed = tintText == null ? "" : tintText.trim();
        if (trimmed.isEmpty()) {
            clearTint(world);
            feedbackText = "Tint cleared.";
            return;
        }

        Integer rgb = parseHexRgb(trimmed);
        if (rgb == null) {
            feedbackText = "Invalid tint. Use a 6-digit hex color like #FFAA00.";
            return;
        }

        int masked = rgb & 0xFFFFFF;
        brightnessService.setDesiredTintRgb(playerUuid, masked);
        configStore.setTintRgb(playerUuid, masked);
        if (world != null) {
            brightnessService.syncPlayer(world, playerUuid, false);
        }

        tintInputValueToSet = String.format(Locale.ROOT, "#%06X", masked);
        feedbackText = "Tint set to " + String.format(Locale.ROOT, "#%06X", masked) + " (clears warmth).";
    }

    private void clearTint(@Nullable World world) {
        brightnessService.setDesiredTintRgb(playerUuid, null);
        configStore.setTintRgb(playerUuid, null);
        if (world != null) {
            brightnessService.syncPlayer(world, playerUuid, false);
        }
        tintInputValueToSet = "";
        feedbackText = "Tint cleared (reverts to torch tint).";
    }

    private void clearWarmth(@Nullable World world) {
        brightnessService.setDesiredWarmth(playerUuid, null);
        configStore.setWarmth01(playerUuid, null);
        if (world != null) {
            brightnessService.syncPlayer(world, playerUuid, false);
        }
        feedbackText = "Warmth cleared (reverts to torch tint).";
    }

    private void resetAll(@Nullable World world) {
        brightnessService.setDesiredBrightness(playerUuid, null);
        brightnessService.setDesiredTintRgb(playerUuid, null);
        brightnessService.setDesiredWarmth(playerUuid, null);
        configStore.clearPlayerSettings(playerUuid);

        if (world != null) {
            brightnessService.syncPlayer(world, playerUuid, false);
        }
        feedbackText = "Disabled and reset all settings.";
    }

    @Nullable
    private static World resolvePlayerWorld(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        return player == null ? null : player.getWorld();
    }

    private UICommandBuilder buildUpdate() {
        UICommandBuilder uiCommandBuilder = new UICommandBuilder();
        applyUiState(uiCommandBuilder);
        return uiCommandBuilder;
    }

    private void applyInitialUiState(@Nonnull UICommandBuilder uiCommandBuilder) {
        Float brightness = configStore.getBrightness(playerUuid);
        int brightnessSlider = brightness == null ? 0 : Math.round(clamp(brightness, 0.0f, 1.0f) * 100.0f);
        uiCommandBuilder.set(ID_BRIGHTNESS_SLIDER_VALUE, brightnessSlider);

        Float warmth = configStore.getWarmth01(playerUuid);
        int warmthSlider = warmth == null ? 0 : Math.round(clamp(warmth, 0.0f, 1.0f) * 100.0f);
        uiCommandBuilder.set(ID_WARMTH_SLIDER_VALUE, warmthSlider);

        Integer tintRgb = configStore.getTintRgb(playerUuid);
        uiCommandBuilder.set(ID_TINT_INPUT_VALUE, tintRgb == null ? "" : String.format(Locale.ROOT, "#%06X", tintRgb & 0xFFFFFF));

        applyUiState(uiCommandBuilder);
    }

    private UIEventBuilder buildUpdateEvents() {
        UIEventBuilder uiEventBuilder = new UIEventBuilder();
        buildEventBindings(uiEventBuilder);
        return uiEventBuilder;
    }

    private void buildEventBindings(@Nonnull UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(
            CustomUIEventBindingType.MouseButtonReleased,
            "#BrightnessSlider",
            new EventData().append(EVENT_TYPE, EVENT_TYPE_BRIGHTNESS).append(KEY_BRIGHTNESS, "#BrightnessSlider.Value"),
            false
        );

        uiEventBuilder.addEventBinding(
            CustomUIEventBindingType.MouseButtonReleased,
            "#WarmthSlider",
            new EventData().append(EVENT_TYPE, EVENT_TYPE_WARMTH).append(KEY_WARMTH, "#WarmthSlider.Value"),
            false
        );

        uiEventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ApplyTint",
            new EventData().append(EVENT_TYPE, EVENT_TYPE_APPLY_TINT).append(KEY_TINT, "#TintInput.Value"),
            false
        );

        uiEventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ClearTint",
            new EventData().append(EVENT_TYPE, EVENT_TYPE_CLEAR_TINT),
            false
        );

        uiEventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ClearWarmth",
            new EventData().append(EVENT_TYPE, EVENT_TYPE_CLEAR_WARMTH),
            false
        );

        uiEventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ResetAll",
            new EventData().append(EVENT_TYPE, EVENT_TYPE_RESET_ALL),
            false
        );
    }

    private void applyUiState(@Nonnull UICommandBuilder uiCommandBuilder) {
        Float brightness = configStore.getBrightness(playerUuid);
        uiCommandBuilder.set(ID_BRIGHTNESS_VALUE_TEXT, brightness == null ? "Off" : format2(brightness));

        Float warmth = configStore.getWarmth01(playerUuid);
        uiCommandBuilder.set(ID_WARMTH_VALUE_TEXT, warmth == null ? "Torch" : format2(warmth));

        if (tintInputValueToSet != null) {
            uiCommandBuilder.set(ID_TINT_INPUT_VALUE, tintInputValueToSet);
        }

        uiCommandBuilder.set(ID_STATUS_TEXT, buildStatusText());
        uiCommandBuilder.set(ID_FEEDBACK_TEXT, feedbackText);
    }

    private String buildStatusText() {
        Float persistedBrightness = configStore.getBrightness(playerUuid);
        String boost =
            persistedBrightness == null
                ? "Boost: Off"
                : "Boost: " + format2(clamp(persistedBrightness, BrightnessService.MIN_BRIGHTNESS, BrightnessService.MAX_BRIGHTNESS));

        Integer tintRgb = configStore.getTintRgb(playerUuid);
        Float warmth = configStore.getWarmth01(playerUuid);
        String tint;
        if (tintRgb != null) {
            tint = "Tint: " + String.format(Locale.ROOT, "#%06X", tintRgb & 0xFFFFFF);
        } else if (warmth != null) {
            tint = "Tint: Warmth " + format2(clamp(warmth, 0.0f, 1.0f));
        } else {
            tint = "Tint: Torch";
        }

        return boost + " • " + tint;
    }

    private static String format2(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Nullable
    private static Integer parseHexRgb(@Nonnull String input) {
        String trimmed = input.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.length() != 6) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean isHex =
                (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return null;
            }
        }
        try {
            return Integer.parseInt(trimmed, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static final class PageEventData {
        public static final BuilderCodec<PageEventData> CODEC =
            BuilderCodec.builder(PageEventData.class, PageEventData::new)
                .append(
                    new KeyedCodec<>(EVENT_TYPE, Codec.STRING, false),
                    (data, value, info) -> data.type = value,
                    (data, info) -> data.type
                )
                .add()
                .append(
                    new KeyedCodec<>(KEY_BRIGHTNESS, Codec.FLOAT, false),
                    (data, value, info) -> data.brightness = value,
                    (data, info) -> data.brightness
                )
                .add()
                .append(
                    new KeyedCodec<>(KEY_WARMTH, Codec.FLOAT, false),
                    (data, value, info) -> data.warmth = value,
                    (data, info) -> data.warmth
                )
                .add()
                .append(
                    new KeyedCodec<>(KEY_TINT, Codec.STRING, false),
                    (data, value, info) -> data.tint = value,
                    (data, info) -> data.tint
                )
                .add()
                .build();

        private String type;
        private Float brightness;
        private Float warmth;
        private String tint;

        public PageEventData() {
        }
    }
}
