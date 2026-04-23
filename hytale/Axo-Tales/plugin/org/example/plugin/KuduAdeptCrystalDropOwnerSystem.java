package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Records who dropped an Arcane Crystal item so the bonding system can assign that player as the Kudu Adept's master.
 */
public final class KuduAdeptCrystalDropOwnerSystem extends EntityEventSystem<EntityStore, DropItemEvent.Drop> {

    private static final String ARCANE_CRYSTAL_SHARD_ITEM_ID = "Ingredient_Crystal_Arcane";
    private static final String ARCANE_CRYSTAL_BLOCK_ITEM_ID = "Rock_Crystal_Arcane_Large";

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final KuduAdeptBondState bondState;

    public KuduAdeptCrystalDropOwnerSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull KuduAdeptBondState bondState
    ) {
        super(DropItemEvent.Drop.class);
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.bondState = bondState;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            UUIDComponent.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType()
        );
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull DropItemEvent.Drop event
    ) {
        try {
            if (config == null || config.kuduAdept == null || !config.kuduAdept.enabled) {
                return;
            }

            ItemStack stack = event.getItemStack();
            String itemId = stack != null ? stack.getItemId() : null;
            if (!isBondingItemId(itemId)) {
                return;
            }

            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            UUID ownerUuid = playerRef != null ? playerRef.getUuid() : null;
            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
            Vector3d position = transform != null ? transform.getPosition() : null;

            String decision = "recorded";
            String reason = "ok";
            if (event.isCancelled()) {
                decision = "skipped";
                reason = "cancelled";
            } else if (ownerUuid == null) {
                decision = "skipped";
                reason = "missingOwnerUuid";
            } else if (position == null || !position.isFinite()) {
                decision = "skipped";
                reason = "missingPosition";
            } else {
                bondState.recordCrystalDropOwner(ownerUuid, itemId, position.x, position.y, position.z, System.nanoTime());
            }

            debug.traceFileOnly(
                playerRef,
                "KuduAdeptCrystalDrop event=DropItem"
                    + " cancelled=" + event.isCancelled()
                    + " item.id=" + itemId
                    + " item.source=DropItemEvent.Drop.getItemStack"
                    + (stack != null ? " item.quantity=" + stack.getQuantity() : "")
                    + (ownerUuid != null ? " ownerUuid=" + ownerUuid : "")
                    + (position != null && position.isFinite()
                        ? " position=" + position.x + "," + position.y + "," + position.z
                        : "")
                    + " decision=" + decision
                    + " reason=" + reason
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduAdeptCrystalDropOwnerSystem: failed to record crystal drop owner.", t);
        }
    }

    private static boolean isBondingItemId(String itemId) {
        return ARCANE_CRYSTAL_SHARD_ITEM_ID.equals(itemId) || ARCANE_CRYSTAL_BLOCK_ITEM_ID.equals(itemId);
    }
}
