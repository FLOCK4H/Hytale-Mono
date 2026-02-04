package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.registry.Registration;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies passive side-effects when a player is wearing the Sa'r Boots item.
 *
 * <p>Note: movement buffs are applied by {@link MovementBuffSystem} so they can stack cleanly with other effects.</p>
 */
public final class SarsBootsPassiveEffect extends TickingSystem<EntityStore> {

    private static final float STAMINA_MAX_MULTIPLIER = 2f;
    private static final String MESSAGE = "you suddenly feel light now...";
    private static final String STAMINA_MAX_MODIFIER_KEY = "axotales:sars_boots_stamina_max";
    private static final String SAR_BOOTS_ITEM_ID = "Sars_Legs";

    private final PluginErrorReporter errors;
    private final Path feltLightPlayersFile;
    private final Map<UUID, Registration> armorChangeRegistrations = new ConcurrentHashMap<>();
    private final Set<UUID> feltLightPlayers = ConcurrentHashMap.newKeySet();

    public SarsBootsPassiveEffect(@Nonnull PluginErrorReporter errors, @Nonnull Path dataDirectory) {
        this.errors = errors;
        this.feltLightPlayersFile = dataDirectory.resolve("sars_boots").resolve("felt_light_players.txt");
        loadFeltLightPlayers();
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        // Intentionally unused: Sa'r Boots currently only apply persistent movement settings.
    }

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        world.execute(() -> {
            try {
                EntityStore entityStore = world.getEntityStore();
                if (entityStore == null) {
                    return;
                }

                Store<EntityStore> store = entityStore.getStore();
                if (store == null) {
                    return;
                }

                Ref<EntityStore> playerEntityRef = event.getPlayerRef();
                if (playerEntityRef == null) {
                    return;
                }

                PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }

                UUID playerUuid = playerRef.getUuid();
                if (playerUuid == null) {
                    return;
                }

                Inventory inventory = player.getInventory();
                if (inventory == null) {
                    return;
                }

                ItemContainer armor = inventory.getArmor();
                if (armor == null) {
                    return;
                }

                Registration previous = armorChangeRegistrations.remove(playerUuid);
                if (previous != null) {
                    previous.unregister();
                }

                Registration registration = armor.registerChangeEvent(changeEvent -> refresh(playerUuid));
                armorChangeRegistrations.put(playerUuid, registration);

                applyOrRemove(world, playerUuid);
            } catch (Throwable t) {
                errors.report((PlayerRef) null, "Failed to register Sa'r Boots passive effect.", t);
            }
        });
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }

        Registration registration = armorChangeRegistrations.remove(playerUuid);
        if (registration != null) {
            registration.unregister();
        }
    }

    public void shutdown() {
        for (Registration registration : armorChangeRegistrations.values()) {
            try {
                if (registration != null) {
                    registration.unregister();
                }
            } catch (Throwable ignored) {
                // Best-effort cleanup on shutdown.
            }
        }
        armorChangeRegistrations.clear();
        feltLightPlayers.clear();
    }

    private void refresh(@Nonnull UUID playerUuid) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null || !playerRef.isValid()) {
                return;
            }

            World world = Universe.get().getWorld(playerRef.getWorldUuid());
            if (world == null) {
                return;
            }

            world.execute(() -> applyOrRemove(world, playerUuid));
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "Failed to refresh Sa'r Boots passive effect.", t);
        }
    }

    private void applyOrRemove(@Nonnull World world, @Nonnull UUID playerUuid) {
        try {
            EntityStore entityStore = world.getEntityStore();
            if (entityStore == null) {
                return;
            }

            Store<EntityStore> store = entityStore.getStore();
            if (store == null) {
                return;
            }

            Ref<EntityStore> playerEntityRef = world.getEntityRef(playerUuid);
            if (playerEntityRef == null) {
                playerEntityRef = entityStore.getRefFromUUID(playerUuid);
            }
            if (playerEntityRef == null) {
                return;
            }

            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) {
                return;
            }

            PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }

            boolean wearing = isWearingSarsBoots(player);
            if (wearing) {
                maybeSendFeltLightMessage(playerRef, playerUuid);
            }

            EntityStatMap stats = store.getComponent(playerEntityRef, EntityStatMap.getComponentType());
            if (stats != null) {
                int staminaIndex = DefaultEntityStatTypes.getStamina();
                float staminaPercentageBefore = staminaIndex == Integer.MIN_VALUE || staminaIndex < 0 ? 0f : readStatPercentage(stats, staminaIndex);

                boolean staminaUpdated = applyOrRemoveStaminaMaxModifier(stats, wearing);
                boolean statsUpdated = staminaUpdated;
                if (statsUpdated) {
                    stats.update();
                    if (staminaUpdated && staminaIndex != Integer.MIN_VALUE && staminaIndex >= 0) {
                        restoreStatPercentage(stats, staminaIndex, staminaPercentageBefore);
                        stats.update();
                    }
                }
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "Failed to apply Sa'r Boots passive effect.", t);
        }
    }

    private void loadFeltLightPlayers() {
        try {
            if (!Files.exists(feltLightPlayersFile)) {
                return;
            }

            for (String line : Files.readAllLines(feltLightPlayersFile, StandardCharsets.UTF_8)) {
                if (line == null) {
                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    feltLightPlayers.add(UUID.fromString(trimmed));
                } catch (IllegalArgumentException ignored) {
                    // Ignore invalid entries.
                }
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "Failed to load Sa'r Boots message cache.", t);
        }
    }

    private void maybeSendFeltLightMessage(@Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid) {
        if (!feltLightPlayers.add(playerUuid)) {
            return;
        }

        try {
            Files.createDirectories(feltLightPlayersFile.getParent());
            Files.writeString(
                feltLightPlayersFile,
                playerUuid + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "Failed to persist Sa'r Boots message cache.", t);
        }

        try {
            playerRef.sendMessage(Message.raw(MESSAGE));
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "Failed to send Sa'r Boots equip message.", t);
        }
    }

    static boolean isWearingSarsBoots(@Nonnull Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }

        ItemContainer armor = inventory.getArmor();
        if (armor == null) {
            return false;
        }

        short slot = (short) ItemArmorSlot.Legs.getValue();
        if (slot < 0 || slot >= armor.getCapacity()) {
            return false;
        }

        ItemStack stack = armor.getItemStack(slot);
        if (stack == null || !stack.isValid()) {
            return false;
        }

        return SAR_BOOTS_ITEM_ID.equals(stack.getItemId());
    }

    private static boolean applyOrRemoveStaminaMaxModifier(@Nonnull EntityStatMap stats, boolean wearing) {
        int staminaIndex = DefaultEntityStatTypes.getStamina();
        if (staminaIndex == Integer.MIN_VALUE || staminaIndex < 0) {
            return false;
        }

        if (wearing) {
            Modifier existing = stats.getModifier(staminaIndex, STAMINA_MAX_MODIFIER_KEY);
            if (existing instanceof StaticModifier staticModifier
                && staticModifier.getTarget() == Modifier.ModifierTarget.MAX
                && staticModifier.getCalculationType() == StaticModifier.CalculationType.MULTIPLICATIVE
                && Math.abs(staticModifier.getAmount() - STAMINA_MAX_MULTIPLIER) < 0.0001f) {
                return false;
            }

            stats.putModifier(
                staminaIndex,
                STAMINA_MAX_MODIFIER_KEY,
                new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.MULTIPLICATIVE, STAMINA_MAX_MULTIPLIER)
            );
            return true;
        }

        return stats.removeModifier(staminaIndex, STAMINA_MAX_MODIFIER_KEY) != null;
    }

    private static float readStatPercentage(@Nonnull EntityStatMap stats, int statIndex) {
        var stat = stats.get(statIndex);
        if (stat == null) {
            return 0f;
        }

        float max = stat.getMax();
        if (max <= 0.0001f) {
            return 0f;
        }

        return stat.get() / max;
    }

    private static void restoreStatPercentage(@Nonnull EntityStatMap stats, int statIndex, float percentage) {
        if (percentage <= 0f) {
            return;
        }

        var stat = stats.get(statIndex);
        if (stat == null) {
            return;
        }

        float max = stat.getMax();
        if (max <= 0.0001f) {
            return;
        }

        stats.setStatValue(statIndex, Math.min(max, max * percentage));
    }
}
