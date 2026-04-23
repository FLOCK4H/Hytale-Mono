package org.example.plugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet-level input handling for Sa'r Warfists.
 *
 * <p>The armor itself is worn in the hands slot, so it cannot rely on held-item interactions. We therefore listen to
 * {@link SyncInteractionChains} and, when the player is wearing Sa'r Warfists and is unarmed, turn the initial
 * {@link InteractionType#Primary} into a short-cooldown projectile cast. Every processed input writes a
 * player-reproducible debug trace to the persistent plugin log.</p>
 */
public final class SarsWarfistsInputInterceptor implements PlayerPacketWatcher {

    public static final String WARFISTS_ITEM_ID = "Sar_Warfists";
    public static final String WARFISTS_PROJECTILE_ASSET_ID = "Sar_Warfist_Bolt";

    private static final long CAST_COOLDOWN_NANOS = 500_000_000L;

    private record InteractionSnapshot(
        @Nonnull InteractionType interactionType,
        boolean initial,
        boolean desync,
        @Nullable InteractionState state,
        @Nullable String itemInHandId,
        @Nullable String utilityItemId,
        @Nullable String toolsItemId,
        int activeHotbarSlot,
        int activeUtilitySlot,
        int activeToolsSlot,
        int chainId
    ) {}

    private record StatSnapshot(boolean present, int index, float current, float min, float max) {}

    private record CastDecision(
        boolean allow,
        @Nonnull String reason,
        @Nullable Vector3d origin,
        @Nullable Vector3d direction,
        @Nullable UUID projectileUuid
    ) {}

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Map<UUID, Integer> lastProcessedPrimaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCastAttemptAtNanos = new ConcurrentHashMap<>();

    private volatile PacketFilter inboundWatcher;

    public SarsWarfistsInputInterceptor(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug
    ) {
        this.errors = errors;
        this.debug = debug;
    }

    public void register() {
        if (inboundWatcher != null) {
            return;
        }
        inboundWatcher = PacketAdapters.registerInbound(this);
    }

    public void deregister() {
        PacketFilter watcher = inboundWatcher;
        inboundWatcher = null;
        if (watcher != null) {
            PacketAdapters.deregisterInbound(watcher);
        }
    }

    public void onPlayerDisconnect(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return;
        }
        lastProcessedPrimaryChainId.remove(uuid);
        lastCastAttemptAtNanos.remove(uuid);
    }

    @Override
    public void accept(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (!(packet instanceof SyncInteractionChains chains)) {
            return;
        }

        SyncInteractionChain[] updates = chains.updates;
        if (updates == null || updates.length == 0) {
            return;
        }

        List<InteractionSnapshot> snapshots = new ArrayList<>(updates.length);
        for (SyncInteractionChain chain : updates) {
            if (chain == null || chain.interactionType != InteractionType.Primary || !chain.initial) {
                continue;
            }

            snapshots.add(new InteractionSnapshot(
                chain.interactionType,
                chain.initial,
                chain.desync,
                chain.state,
                chain.itemInHandId,
                chain.utilityItemId,
                chain.toolsItemId,
                chain.activeHotbarSlot,
                chain.activeUtilitySlot,
                chain.activeToolsSlot,
                chain.chainId
            ));
        }

        if (snapshots.isEmpty()) {
            return;
        }

        handleOnWorldThread(playerRef, snapshots);
    }

    private void handleOnWorldThread(@Nonnull PlayerRef playerRef, @Nonnull List<InteractionSnapshot> snapshots) {
        try {
            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return;
            }

            Store<EntityStore> store = playerEntityRef.getStore();
            if (store == null) {
                return;
            }

            EntityStore external = store.getExternalData();
            if (external == null) {
                return;
            }

            World world = external.getWorld();
            if (world == null) {
                return;
            }

            world.execute(() -> onWorldThread(playerRef, store, playerEntityRef, snapshots));
        } catch (Throwable t) {
            errors.report(playerRef, "SarsWarfistsInputInterceptor: failed to schedule world-thread handling.", t);
        }
    }

    private void onWorldThread(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull List<InteractionSnapshot> snapshots
    ) {
        try {
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) {
                return;
            }

            UUID uuid = playerRef.getUuid();
            if (uuid == null) {
                return;
            }

            String handsArmorItemId = InventoryComponentAccess.armorItemId(
                store,
                playerEntityRef,
                com.hypixel.hytale.protocol.ItemArmorSlot.Hands
            );
            if (!WARFISTS_ITEM_ID.equals(handsArmorItemId)) {
                return;
            }

            String serverItemInHand = itemIdOrNull(InventoryComponentAccess.itemInHand(store, playerEntityRef));
            String serverUtilityItem = itemIdOrNull(InventoryComponentAccess.utilityItem(store, playerEntityRef));
            String serverToolsItem = itemIdOrNull(InventoryComponentAccess.toolsItem(store, playerEntityRef));

            EntityStatMap stats = store.getComponent(playerEntityRef, EntityStatMap.getComponentType());
            StatSnapshot stamina = snapshotStat(stats, DefaultEntityStatTypes.getStamina());
            long nowNanos = System.nanoTime();

            for (InteractionSnapshot snapshot : snapshots) {
                CastDecision decision = tryCastProjectile(
                    playerRef,
                    store,
                    playerEntityRef,
                    uuid,
                    nowNanos,
                    snapshot,
                    serverItemInHand
                );

                String detectionSource = resolveDetectionSource(snapshot, serverItemInHand, handsArmorItemId, decision.allow);
                debug.traceFileOnly(
                    playerRef,
                    "SarsWarfists event=SyncInteractionChains(id=290)"
                        + " cancelled=false"
                        + " interactionType=" + snapshot.interactionType
                        + " initial=" + snapshot.initial
                        + " desync=" + snapshot.desync
                        + " state=" + snapshot.state
                        + " chainId=" + snapshot.chainId
                        + " detected.itemId=" + (snapshot.itemInHandId != null ? snapshot.itemInHandId : "null")
                        + " detected.from=" + detectionSource
                        + " armor.hands.itemId=" + handsArmorItemId
                        + " client.itemInHandId=" + (snapshot.itemInHandId != null ? snapshot.itemInHandId : "null")
                        + " client.utilityItemId=" + (snapshot.utilityItemId != null ? snapshot.utilityItemId : "null")
                        + " client.toolsItemId=" + (snapshot.toolsItemId != null ? snapshot.toolsItemId : "null")
                        + " server.itemInHand=" + (serverItemInHand != null ? serverItemInHand : "null")
                        + " server.utilityItem=" + (serverUtilityItem != null ? serverUtilityItem : "null")
                        + " server.toolsItem=" + (serverToolsItem != null ? serverToolsItem : "null")
                        + " stamina.index=" + stamina.index
                        + " stamina.present=" + stamina.present
                        + " stamina.current=" + stamina.current
                        + " stamina.min=" + stamina.min
                        + " stamina.max=" + stamina.max
                        + " projectile.id=" + WARFISTS_PROJECTILE_ASSET_ID
                        + " decision=" + (decision.allow ? "allow" : "deny")
                        + " reason=" + decision.reason
                        + (decision.projectileUuid != null ? " projectile.uuid=" + decision.projectileUuid : "")
                        + (decision.origin != null ? " projectile.origin=" + decision.origin : "")
                        + (decision.direction != null ? " projectile.direction=" + decision.direction : "")
                );
            }
        } catch (Throwable t) {
            errors.report(playerRef, "SarsWarfistsInputInterceptor: failed to process warfists input.", t);
        }
    }

    private @Nonnull CastDecision tryCastProjectile(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull UUID uuid,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand
    ) {
        Integer lastChainId = lastProcessedPrimaryChainId.get(uuid);
        if (lastChainId != null && lastChainId == snapshot.chainId) {
            return new CastDecision(false, "dedupe.primary.chainId", null, null, null);
        }
        lastProcessedPrimaryChainId.put(uuid, snapshot.chainId);

        boolean clientUnarmed = isBlank(snapshot.itemInHandId);
        boolean serverUnarmed = isBlank(serverItemInHand);
        if (!clientUnarmed || !serverUnarmed) {
            return new CastDecision(false, "holdingItem", null, null, null);
        }

        long lastCastAtNanos = lastCastAttemptAtNanos.getOrDefault(uuid, 0L);
        if (lastCastAtNanos > 0L && nowNanos - lastCastAtNanos < CAST_COOLDOWN_NANOS) {
            return new CastDecision(false, "cooldownActive", null, null, null);
        }
        lastCastAttemptAtNanos.put(uuid, nowNanos);

        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return new CastDecision(false, "timeResourceMissing", null, null, null);
        }

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new CastDecision(false, "lookTransformMissing", null, null, null);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new CastDecision(false, "originInvalid", origin, null, null);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new CastDecision(false, "directionInvalid", origin, direction, null);
        }

        Vector3f rotation = look.getRotation();
        if (rotation == null || !rotation.isFinite()) {
            rotation = Vector3f.ZERO;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, WARFISTS_PROJECTILE_ASSET_ID, origin, rotation);
        if (holder == null) {
            return new CastDecision(false, "projectileAssembleFailed", origin, direction, null);
        }

        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return new CastDecision(false, "projectileComponentMissing", origin, direction, null);
        }

        if (!projectile.initialize()) {
            return new CastDecision(false, "projectileAssetNotFound", origin, direction, null);
        }

        try {
            projectile.shoot(holder, uuid, origin.x, origin.y, origin.z, rotation.getYaw(), rotation.getPitch());
        } catch (Throwable t) {
            errors.report(playerRef, "SarsWarfists: projectile.shoot failed (assetId=" + WARFISTS_PROJECTILE_ASSET_ID + ").", t);
            return new CastDecision(false, "projectileShootException", origin, direction, null);
        }

        Ref<EntityStore> projectileRef;
        try {
            projectileRef = store.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            errors.report(playerRef, "SarsWarfists: store.addEntity failed (assetId=" + WARFISTS_PROJECTILE_ASSET_ID + ").", t);
            return new CastDecision(false, "projectileSpawnException", origin, direction, null);
        }

        UUID projectileUuid = null;
        try {
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best-effort debug info.
        }

        return new CastDecision(true, "castApplied", origin, direction, projectileUuid);
    }

    @Nonnull
    private static String resolveDetectionSource(
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String handsArmorItemId,
        boolean allow
    ) {
        if (allow) {
            return "inventory.armor[Hands]+primaryUnarmed";
        }
        if (!isBlank(serverItemInHand)) {
            return "server.itemInHand";
        }
        if (!isBlank(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (!isBlank(handsArmorItemId)) {
            return "inventory.armor[Hands]";
        }
        return "unknown";
    }

    @Nullable
    private static String itemIdOrNull(@Nullable ItemStack stack) {
        if (stack == null || !stack.isValid() || stack.isEmpty()) {
            return null;
        }
        return stack.getItemId();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static @Nonnull StatSnapshot snapshotStat(@Nullable EntityStatMap stats, int index) {
        if (stats == null || index == Integer.MIN_VALUE || index < 0) {
            return new StatSnapshot(false, index, 0f, 0f, 0f);
        }

        EntityStatValue stat = stats.get(index);
        if (stat == null) {
            return new StatSnapshot(false, index, 0f, 0f, 0f);
        }

        return new StatSnapshot(true, index, stat.get(), stat.getMin(), stat.getMax());
    }
}
