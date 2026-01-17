package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * This class serves as the entrypoint for your plugin. Use the setup method to register into game registries or add
 * event listeners.
 */
public class BrightnessTweaksPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final BrightnessService brightnessService = new BrightnessService();
    private EventRegistration<?, ?> inventoryListener;
    private EventRegistration<?, ?> disconnectListener;

    public BrightnessTweaksPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        this.getCommandRegistry().registerCommand(new BrightnessCommand(brightnessService));

        this.inventoryListener = this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, event -> {
            if (!(event.getEntity() instanceof Player playerEntity)) {
                return;
            }

            EntityStore entityStore = playerEntity.getWorld().getEntityStore();
            if (entityStore == null) {
                return;
            }

            Store<EntityStore> store = entityStore.getStore();
            Ref<EntityStore> ref = playerEntity.getReference();
            if (store == null || ref == null) {
                return;
            }

            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }

            if (!brightnessService.hasState(playerRef.getUuid())) {
                return;
            }
            brightnessService.syncPlayer(playerEntity.getWorld(), playerRef.getUuid(), false);
        });

        this.disconnectListener = this.getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            brightnessService.clearPlayer(event.getPlayerRef().getUuid());
        });
    }

    @Override
    protected void shutdown() {
        if (inventoryListener != null) {
            inventoryListener.unregister();
            inventoryListener = null;
        }
        if (disconnectListener != null) {
            disconnectListener.unregister();
            disconnectListener = null;
        }
    }
}
