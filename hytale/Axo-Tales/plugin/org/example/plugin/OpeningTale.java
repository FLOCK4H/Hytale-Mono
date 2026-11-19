package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


final class OpeningTale {
    private final Set<UUID> completed = new HashSet<>();
    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private StarterBookClaims acknowledgements;

    OpeningTale(PluginErrorReporter errors, PluginDebugReporter debug) { this.errors = errors; this.debug = debug; }

    synchronized void show(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player) {
        UUID id = player.getUuid();
        if (completed.contains(id)) return;
        try {
            if (acknowledgements == null) acknowledgements = new StarterBookClaims(Universe.get().getPath(), "opening-tales");
            if (acknowledgements.isClaimed(id)) { completed.add(id); return; }
            Player entity = store.getComponent(ref, Player.getComponentType());

            if (entity == null || entity.isWaitingForClientReady()
                    || store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType()) != null
                    || entity.getPageManager().getCustomPage() != null
                    || entity.getPageManager().getActiveServersidePage() != null) return;
            entity.getPageManager().openCustomPage(ref, store, new OpeningTalePage(player, () -> {
                acknowledge(player);
            }));
            debug.traceFileOnly(player, "OpeningTale event=open allow=true itemId=Book_Silver_Texture rewardReceipt=existingStarterClaim");
        } catch (Exception failure) { errors.report(player, "OpeningTale: could not open introduction.", failure); }
    }

    private synchronized void acknowledge(PlayerRef player) {
        try {
            acknowledgements.reserve(player.getUuid());
            completed.add(player.getUuid());
            debug.traceFileOnly(player, "OpeningTale event=dismiss allow=true scope=playerPerSave persisted=true");
        } catch (Exception failure) { errors.report(player, "OpeningTale: could not save acknowledgement.", failure); }
    }
}
