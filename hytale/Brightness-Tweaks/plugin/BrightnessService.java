package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages a torch-only brightness boost by overriding the player {@link DynamicLight} while a torch exists in the
 * utility belt.
 */
public final class BrightnessService {

    static final float MIN_BRIGHTNESS = 0.01f;
    static final float MAX_BRIGHTNESS = 1.0f;

    private static final int MIN_LIGHT_RADIUS = 6;
    private static final int MAX_LIGHT_RADIUS = 32;

    private static final int MAX_LIGHT_INTENSITY = 255;

    private final ConcurrentMap<UUID, Float> desiredBrightness = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> desiredTintRgb = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Float> desiredWarmth = new ConcurrentHashMap<>();
    private final Set<UUID> activeBoost = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, ColorLight> torchBaseline = new ConcurrentHashMap<>();

    /**
     * Sets the desired boost for a player. Use {@code null} to clear and revert to vanilla behavior.
     */
    public void setDesiredBrightness(@Nonnull UUID playerUuid, @Nullable Float value) {
        if (value == null) {
            desiredBrightness.remove(playerUuid);
            return;
        }
        desiredBrightness.put(playerUuid, value);
    }

    /**
     * Sets the desired tint for the boosted dynamic light using an RGB integer (0xRRGGBB). Use {@code null} to clear.
     */
    public void setDesiredTintRgb(@Nonnull UUID playerUuid, @Nullable Integer rgb) {
        if (rgb == null) {
            desiredTintRgb.remove(playerUuid);
            return;
        }
        desiredWarmth.remove(playerUuid);
        desiredTintRgb.put(playerUuid, rgb & 0xFFFFFF);
    }

    /**
     * Sets the desired warmth for the boosted dynamic light (0.0 = vanilla torch tint, 1.0 = warmer torch tint). Use
     * {@code null} to clear.
     */
    public void setDesiredWarmth(@Nonnull UUID playerUuid, @Nullable Float warmth01) {
        if (warmth01 == null) {
            desiredWarmth.remove(playerUuid);
            return;
        }
        desiredTintRgb.remove(playerUuid);
        desiredWarmth.put(playerUuid, clamp(warmth01, 0.0f, 1.0f));
    }

    /**
     * Returns {@code true} if this player has any tracked brightness state.
     */
    public boolean hasState(@Nonnull UUID playerUuid) {
        return desiredBrightness.containsKey(playerUuid) || activeBoost.contains(playerUuid);
    }

    /**
     * Clears all state for a player (used on disconnect).
     */
    public void clearPlayer(@Nonnull UUID playerUuid) {
        desiredBrightness.remove(playerUuid);
        desiredTintRgb.remove(playerUuid);
        desiredWarmth.remove(playerUuid);
        activeBoost.remove(playerUuid);
        torchBaseline.remove(playerUuid);
    }

    /**
     * Syncs the player's light based on the current utility-slot item. Must be safe to call from any thread.
     */
    public void syncPlayer(@Nonnull World world, @Nonnull UUID playerUuid, boolean announce) {
        world.execute(() -> syncPlayerOnWorldThread(world, playerUuid, announce));
    }

    private void syncPlayerOnWorldThread(@Nonnull World world, @Nonnull UUID playerUuid, boolean announce) {
        EntityStore entityStore = world.getEntityStore();
        if (entityStore == null) {
            return;
        }

        Store<EntityStore> store = entityStore.getStore();
        if (store == null) {
            return;
        }

        Ref<EntityStore> playerRef = world.getEntityRef(playerUuid);
        if (playerRef == null) {
            playerRef = entityStore.getRefFromUUID(playerUuid);
        }
        if (playerRef == null) {
            return;
        }

        Player playerEntity = store.getComponent(playerRef, Player.getComponentType());
        if (playerEntity == null) {
            return;
        }

        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());

        Float desired = desiredBrightness.get(playerUuid);
        if (desired == null) {
            boolean wasActive = activeBoost.remove(playerUuid);
            if (wasActive) {
                store.tryRemoveComponent(playerRef, DynamicLight.getComponentType());
            }
            torchBaseline.remove(playerUuid);
            if (announce && player != null) {
                player.sendMessage(Message.raw("Brightness boost disabled."));
            }
            return;
        }

        Inventory inventory = playerEntity.getInventory();
        boolean hasTorch = hasTorchInUtilityBelt(inventory);

        if (!hasTorch) {
            if (activeBoost.remove(playerUuid)) {
                store.tryRemoveComponent(playerRef, DynamicLight.getComponentType());
                torchBaseline.remove(playerUuid);
                if (player != null) {
                    player.sendMessage(Message.raw("No torch in your utility belt. Brightness reverted to normal."));
                }
            } else if (announce && player != null) {
                player.sendMessage(Message.raw("Keep a torch in your utility belt to enable the brightness boost."));
            }
            return;
        }

        float clamped = clamp(desired, MIN_BRIGHTNESS, MAX_BRIGHTNESS);

        if (!activeBoost.contains(playerUuid)) {
            DynamicLight existing = store.getComponent(playerRef, DynamicLight.getComponentType());
            if (existing != null) {
                ColorLight existingLight = existing.getColorLight();
                if (existingLight != null) {
                    torchBaseline.put(playerUuid, existingLight);
                }
            }
        }

        ColorLight baseline = torchBaseline.get(playerUuid);
        Integer tintRgb = desiredTintRgb.get(playerUuid);
        Float warmth01 = desiredWarmth.get(playerUuid);
        ColorLight requested = toLight(clamped, baseline, tintRgb, warmth01);
        ColorLight target = baseline == null ? requested : maxLight(baseline, requested);

        DynamicLight dynamicLight = store.getComponent(playerRef, DynamicLight.getComponentType());
        if (dynamicLight == null) {
            store.putComponent(playerRef, DynamicLight.getComponentType(), new DynamicLight(target));
        } else {
            dynamicLight.setColorLight(target);
        }

        activeBoost.add(playerUuid);

        if (announce && player != null) {
            int unsignedRadius = Byte.toUnsignedInt(target.radius);
            int red = Byte.toUnsignedInt(target.red);
            int green = Byte.toUnsignedInt(target.green);
            int blue = Byte.toUnsignedInt(target.blue);
            player.sendMessage(
                Message.raw(
                    "Brightness tweaked to " + clamped + " (radius " + unsignedRadius + ", rgb " + red + "/" + green + "/" + blue + ")."
                )
            );
        }
    }

    private static boolean hasTorchInUtilityBelt(@Nullable Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        if (isTorchItem(inventory.getUtilityItem())) {
            return true;
        }

        ItemContainer utility = inventory.getUtility();
        if (utility == null) {
            return false;
        }

        final boolean[] hasTorch = {false};
        utility.forEach((slot, stack) -> {
            if (!hasTorch[0] && isTorchItem(stack)) {
                hasTorch[0] = true;
            }
        });
        return hasTorch[0];
    }

    private static boolean isTorchItem(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String itemId = stack.getItemId();
        return itemId != null && itemId.contains("Torch");
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float normalize01(float value, float min, float max) {
        float clamped = clamp(value, min, max);
        if (max == min) {
            return 0.0f;
        }
        return (clamped - min) / (max - min);
    }

    private static int lerpInt(int a, int b, float t) {
        float clamped = clamp(t, 0.0f, 1.0f);
        return Math.round(a + (b - a) * clamped);
    }

    private static ColorLight toLight(float scale, @Nullable ColorLight baseline, @Nullable Integer tintRgb, @Nullable Float warmth01) {
        float t = normalize01(scale, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
        int baselineRadius = baseline == null ? MIN_LIGHT_RADIUS : Byte.toUnsignedInt(baseline.radius);
        int baselineIntensity = baseline == null
            ? Math.max(1, Math.round(MAX_LIGHT_INTENSITY * MIN_BRIGHTNESS))
            : Math.max(
                Math.max(Byte.toUnsignedInt(baseline.red), Byte.toUnsignedInt(baseline.green)),
                Byte.toUnsignedInt(baseline.blue)
            );

        int startRadius = Math.max(MIN_LIGHT_RADIUS, Math.min(MAX_LIGHT_RADIUS, baselineRadius));
        int startIntensity = Math.max(1, Math.min(MAX_LIGHT_INTENSITY, baselineIntensity));

        int radius = lerpInt(startRadius, MAX_LIGHT_RADIUS, t);
        int intensity = lerpInt(startIntensity, MAX_LIGHT_INTENSITY, t);

        int[] tint = resolveTintRgb(baseline, tintRgb, warmth01);
        int maxTint = Math.max(1, Math.max(tint[0], Math.max(tint[1], tint[2])));
        int red = scaleTintChannel(tint[0], intensity, maxTint);
        int green = scaleTintChannel(tint[1], intensity, maxTint);
        int blue = scaleTintChannel(tint[2], intensity, maxTint);

        return new ColorLight((byte) radius, (byte) red, (byte) green, (byte) blue);
    }

    private static ColorLight maxLight(@Nonnull ColorLight a, @Nonnull ColorLight b) {
        int radius = Math.max(Byte.toUnsignedInt(a.radius), Byte.toUnsignedInt(b.radius));
        int red = Math.max(Byte.toUnsignedInt(a.red), Byte.toUnsignedInt(b.red));
        int green = Math.max(Byte.toUnsignedInt(a.green), Byte.toUnsignedInt(b.green));
        int blue = Math.max(Byte.toUnsignedInt(a.blue), Byte.toUnsignedInt(b.blue));
        return new ColorLight((byte) radius, (byte) red, (byte) green, (byte) blue);
    }

    private static int[] resolveTintRgb(@Nullable ColorLight baseline, @Nullable Integer tintRgb, @Nullable Float warmth01) {
        if (tintRgb != null) {
            return new int[] {(tintRgb >> 16) & 0xFF, (tintRgb >> 8) & 0xFF, tintRgb & 0xFF};
        }

        if (warmth01 != null) {
            int baseRed = baseline == null ? 255 : Byte.toUnsignedInt(baseline.red);
            int baseGreen = baseline == null ? 255 : Byte.toUnsignedInt(baseline.green);
            int baseBlue = baseline == null ? 255 : Byte.toUnsignedInt(baseline.blue);

            int warmRed = 255;
            int warmGreen = 220;
            int warmBlue = 170;

            return new int[] {
                lerpInt(baseRed, warmRed, warmth01),
                lerpInt(baseGreen, warmGreen, warmth01),
                lerpInt(baseBlue, warmBlue, warmth01)
            };
        }

        if (baseline == null) {
            return new int[] {255, 255, 255};
        }
        return new int[] {Byte.toUnsignedInt(baseline.red), Byte.toUnsignedInt(baseline.green), Byte.toUnsignedInt(baseline.blue)};
    }

    private static int scaleTintChannel(int tintChannel, int intensity, int maxTint) {
        int clampedTint = Math.max(0, Math.min(255, tintChannel));
        int clampedIntensity = Math.max(0, Math.min(255, intensity));
        int clampedMaxTint = Math.max(1, Math.min(255, maxTint));
        return Math.max(0, Math.min(255, Math.round(clampedTint * (float) clampedIntensity / (float) clampedMaxTint)));
    }
}
