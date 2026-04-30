package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.collision.BlockCollisionData;
import com.hypixel.hytale.server.core.modules.collision.CollisionMaterial;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Packet-level interception for spellbook input handling.
 *
 * <p>Uses inbound {@link SyncInteractionChains} (id 290) updates and logs both client-reported item ids and the
 * authoritative server inventory snapshot for debugging.</p>
 */
public final class SpellbookInputInterceptor implements PlayerPacketWatcher {

    public static final String HEALING_BOOK_ITEM_ID = "Book_Heal_Texture";
    public static final String IMMUNITY_BOOK_ITEM_ID = "Book_Immunity_Texture";
    public static final String TELEPORT_BOOK_ITEM_ID = "Book_Teleport_Texture";
    public static final String MINING_BOOK_ITEM_ID = "Book_Mining_Texture";
    public static final String TAUNT_BOOK_ITEM_ID = "Book_Taunt_Texture";
    public static final String HORDE_BOOK_ITEM_ID = "Book_Horde_Texture";
    public static final String DOOM_BOOK_ITEM_ID = "Book_Doom_Texture";
    public static final String MORPH_BOOK_ITEM_ID = "Book_Morph_Texture";
    public static final String FROST_BOOK_ITEM_ID = "Book_Frost_Texture";
    public static final String FLAME_BOOK_ITEM_ID = "Book_Flame_Texture";
    public static final String LIGHT_BOOK_ITEM_ID = "Book_Light_Texture";
    public static final String ANCIENT_SWORD_ITEM_ID = "Axo_Ancient_Sword";
    private static final float FLOAT_EPSILON = 0.0001f;
    private static final double MINING_HARD_MAX_DISTANCE = 256.0;
    private static final double TELEPORT_HARD_MAX_DISTANCE = 2048.0;
    private static final double TELEPORT_DEST_Y_OFFSET = 0.10;
    private static final Box TELEPORT_RAY_POINT_BOX = new Box(0, 0, 0, 0.01, 0.01, 0.01);
    private static final String DOOM_PROJECTILE_ASSET_ID = "Doom_Ball";
    private static final String MORPH_PROJECTILE_ASSET_ID = "Morph_Vortex";
    private static final String FROST_PROJECTILE_ASSET_ID = "Frost_Shard";
    private static final String FLAME_PROJECTILE_ASSET_ID = "Flame_Bolt";
    private static final String LIGHT_PROJECTILE_ASSET_ID = LightBookProjectileSystem.LIGHT_PROJECTILE_ID;
    private static final String HEALING_PROJECTILE_ASSET_ID = "Healing_Bolt";
    private static final String ANCIENT_SWORD_PROJECTILE_DEFAULT_ASSET_ID = "Ancient_Slash";
    private static final String IMMUNE_EFFECT_ID = "Immune";
    private static final float MINING_DROP_PICKUP_DELAY_SECONDS = 0.25f;
    private static final float MINING_DROP_VELOCITY_HORIZONTAL_STDDEV = 0.06f;
    private static final float MINING_DROP_VELOCITY_Y = 0.35f;
    private static final long MINING_SHAPE_TOGGLE_DEBOUNCE_NANOS = 150_000_000L;

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

    private record Decision(boolean allow, @Nonnull String reason) {}

    private record DelayScheduleDecision(boolean scheduled, boolean deduped, @Nonnull String reason) {}

    private record MiningChargeState(
        int chainId,
        @Nonnull InteractionType interactionType,
        long startedAtNanos
    ) {}

    private record MiningChargeResolution(
        boolean allowCast,
        @Nonnull String reason,
        @Nullable InteractionState terminalState,
        double chargeSeconds,
        int requestedTunnelBlocks,
        boolean usedFallbackStart
    ) {}

    private enum MiningShape {
        ONE_BY_ONE("1x1", "#55E8FF", 1),
        THREE_BY_THREE("3x3", "#FFD25A", 9),
        CROSS("Cross", "#FF7EC7", 5);

        private final String displayName;
        private final String colorHex;
        private final int blocksPerDepth;

        MiningShape(@Nonnull String displayName, @Nonnull String colorHex, int blocksPerDepth) {
            this.displayName = displayName;
            this.colorHex = colorHex;
            this.blocksPerDepth = blocksPerDepth;
        }

        private @Nonnull MiningShape next() {
            return switch (this) {
                case ONE_BY_ONE -> THREE_BY_THREE;
                case THREE_BY_THREE -> CROSS;
                case CROSS -> ONE_BY_ONE;
            };
        }
    }

    private record MiningShapeChangeDecision(
        boolean allow,
        @Nonnull String reason,
        @Nonnull MiningShape previousShape,
        @Nonnull MiningShape nextShape
    ) {}

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final TauntBookEffectState tauntState;
    private final ImmunityBookEffectState immunityState;
    private final HordeBookSummonState hordeSummonState;
    private final MorphBookModelState morphBookModelState;
    private final LightBookProjectileState lightBookProjectileState;
    private final Map<UUID, Integer> lastProcessedSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedHealingPrimaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedMiningPrimaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedImmunitySecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedImmunityUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedTeleportSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedTeleportUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedMiningSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedMiningUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedTauntSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedTauntUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedHordeSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedHordeUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedDoomSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedDoomUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedMorphSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedMorphUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedMorphPrimaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedFrostSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedFrostUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedFlameSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedFlameUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedLightSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedLightUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastProcessedAncientSwordSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHealingCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMiningShapeToggleAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastImmunityCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTeleportCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMiningCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTauntCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHordeCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDoomCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMorphCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFrostCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlameCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLightCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAncientSwordCastAttemptAtNanos = new ConcurrentHashMap<>();
    private final Map<UUID, MiningChargeState> activeMiningCharges = new ConcurrentHashMap<>();
    private final Map<UUID, MiningShape> miningShapeByPlayer = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> lastScheduledSpellbookSecondaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastScheduledSpellbookUseChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastScheduledHealingPrimaryChainId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastScheduledAncientSwordSecondaryChainId = new ConcurrentHashMap<>();

    private final ScheduledExecutorService delayedCastExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AxoTales-SpellbookDelay");
        thread.setDaemon(true);
        return thread;
    });

    private volatile int immuneEffectIndex = -1;
    private volatile PacketFilter inboundWatcher;

    public SpellbookInputInterceptor(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull TauntBookEffectState tauntState,
        @Nonnull ImmunityBookEffectState immunityState,
        @Nonnull HordeBookSummonState hordeSummonState,
        @Nonnull MorphBookModelState morphBookModelState,
        @Nonnull LightBookProjectileState lightBookProjectileState
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.tauntState = tauntState;
        this.immunityState = immunityState;
        this.hordeSummonState = hordeSummonState;
        this.morphBookModelState = morphBookModelState;
        this.lightBookProjectileState = lightBookProjectileState;
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

        try {
            delayedCastExecutor.shutdownNow();
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    public void onPlayerDisconnect(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid != null) {
            lastProcessedSecondaryChainId.remove(uuid);
            lastProcessedUseChainId.remove(uuid);
            lastProcessedHealingPrimaryChainId.remove(uuid);
            lastProcessedMiningPrimaryChainId.remove(uuid);
            lastProcessedImmunitySecondaryChainId.remove(uuid);
            lastProcessedImmunityUseChainId.remove(uuid);
            lastProcessedTeleportSecondaryChainId.remove(uuid);
            lastProcessedTeleportUseChainId.remove(uuid);
            lastProcessedMiningSecondaryChainId.remove(uuid);
            lastProcessedMiningUseChainId.remove(uuid);
            lastProcessedTauntSecondaryChainId.remove(uuid);
            lastProcessedTauntUseChainId.remove(uuid);
            lastProcessedHordeSecondaryChainId.remove(uuid);
            lastProcessedHordeUseChainId.remove(uuid);
            lastProcessedDoomSecondaryChainId.remove(uuid);
            lastProcessedDoomUseChainId.remove(uuid);
            lastProcessedMorphSecondaryChainId.remove(uuid);
            lastProcessedMorphUseChainId.remove(uuid);
            lastProcessedMorphPrimaryChainId.remove(uuid);
            lastProcessedFrostSecondaryChainId.remove(uuid);
            lastProcessedFrostUseChainId.remove(uuid);
            lastProcessedFlameSecondaryChainId.remove(uuid);
            lastProcessedFlameUseChainId.remove(uuid);
            lastProcessedLightSecondaryChainId.remove(uuid);
            lastProcessedLightUseChainId.remove(uuid);
            lastProcessedAncientSwordSecondaryChainId.remove(uuid);
            lastHealingCastAttemptAtNanos.remove(uuid);
            lastMiningShapeToggleAtNanos.remove(uuid);
            lastImmunityCastAttemptAtNanos.remove(uuid);
            lastTeleportCastAttemptAtNanos.remove(uuid);
            lastMiningCastAttemptAtNanos.remove(uuid);
            lastTauntCastAttemptAtNanos.remove(uuid);
            lastHordeCastAttemptAtNanos.remove(uuid);
            lastDoomCastAttemptAtNanos.remove(uuid);
            lastMorphCastAttemptAtNanos.remove(uuid);
            lastFrostCastAttemptAtNanos.remove(uuid);
            lastFlameCastAttemptAtNanos.remove(uuid);
            lastLightCastAttemptAtNanos.remove(uuid);
            lastAncientSwordCastAttemptAtNanos.remove(uuid);
            activeMiningCharges.remove(uuid);
            miningShapeByPlayer.remove(uuid);
            lastScheduledSpellbookSecondaryChainId.remove(uuid);
            lastScheduledSpellbookUseChainId.remove(uuid);
            lastScheduledHealingPrimaryChainId.remove(uuid);
            lastScheduledAncientSwordSecondaryChainId.remove(uuid);
            tauntState.clear(uuid);
            immunityState.clear(uuid);
            morphBookModelState.clear(uuid);

            long nowNanos = System.nanoTime();
            HordeBookSummonState.ActiveSummon active = hordeSummonState.getByOwnerIfActive(uuid, nowNanos);
            if (active != null) {
                hordeSummonState.clearOwner(uuid);
                despawnHordeMinionsBestEffort(playerRef, active.minionUuids, active.castChainId, active.castInteractionType);
            } else {
                hordeSummonState.clearOwner(uuid);
            }
        }
    }

    private void despawnHordeMinionsBestEffort(
        @Nonnull PlayerRef playerRef,
        @Nonnull java.util.List<UUID> minionUuids,
        int castChainId,
        @Nonnull InteractionType castInteractionType
    ) {
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

            world.execute(() -> {
                try {
                    EntityStore liveExternal = store.getExternalData();
                    if (liveExternal == null) {
                        return;
                    }

                    int removedCount = 0;
                    if (minionUuids != null) {
                        for (UUID minionUuid : minionUuids) {
                            if (minionUuid == null) {
                                continue;
                            }
                            Ref<EntityStore> minionRef = liveExternal.getRefFromUUID(minionUuid);
                            if (minionRef == null || !minionRef.isValid()) {
                                continue;
                            }

                            try {
                                store.removeEntity(minionRef, RemoveReason.REMOVE);
                                removedCount++;
                            } catch (Throwable ignored) {
                                // Best effort: continue removing others.
                            }
                        }
                    }

                    debug.traceFileOnly(
                        (PlayerRef) null,
                        "HordeBookMinionCleanup event=disconnect"
                            + " ownerUuid=" + playerRef.getUuid()
                            + " minions.total=" + (minionUuids != null ? minionUuids.size() : 0)
                            + " minions.removed=" + removedCount
                            + " cast.chainId=" + castChainId
                            + " cast.interactionType=" + castInteractionType
                    );
                } catch (Throwable ignored) {
                    // Best effort cleanup.
                }
            });
        } catch (Throwable ignored) {
            // Best effort cleanup.
        }
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
            if (chain == null || chain.interactionType == null) {
                continue;
            }
            if (chain.interactionType != InteractionType.Primary
                && chain.interactionType != InteractionType.Secondary
                && chain.interactionType != InteractionType.Use) {
                continue;
            }
            boolean terminalInteractionState = chain.state != null && chain.state != InteractionState.NotFinished;
            if (!chain.initial && !clientReportsAnySpellbook(chain.itemInHandId, chain.utilityItemId, chain.toolsItemId) && !terminalInteractionState) {
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

    private long getSpellbookCastDebounceNanos() {
        AxoTalesServerConfig.Spellbooks spellbooks = config != null ? config.spellbooks : null;
        double seconds = spellbooks != null ? spellbooks.castDebounceSeconds : 0.6;
        return secondsToNanosClamped(seconds);
    }

    private long getSpellbookSecondaryUseDelayNanos() {
        AxoTalesServerConfig.Spellbooks spellbooks = config != null ? config.spellbooks : null;
        double seconds = spellbooks != null ? spellbooks.secondaryUseDelaySeconds : 0.3;
        return secondsToNanosClamped(seconds);
    }

    private long getTeleportBookCastDelayNanos() {
        AxoTalesServerConfig.TeleportBook teleportBook = config != null ? config.teleportBook : null;
        double seconds = teleportBook != null ? teleportBook.castDelaySeconds : 0.5;
        return secondsToNanosClamped(seconds);
    }

    private long getDoomBookProjectileDelayNanos() {
        AxoTalesServerConfig.DoomBook doomBook = config != null ? config.doomBook : null;
        double seconds = doomBook != null ? doomBook.projectileDelaySeconds : 0.24;
        return secondsToNanosClamped(seconds);
    }

    private long getFlameBookProjectileDelayNanos() {
        AxoTalesServerConfig.FlameBook flameBook = config != null ? config.flameBook : null;
        double seconds = flameBook != null ? flameBook.projectileDelaySeconds : 0.2;
        return secondsToNanosClamped(seconds);
    }

    private long getLightBookProjectileDelayNanos() {
        AxoTalesServerConfig.LightBook lightBook = config != null ? config.lightBook : null;
        double seconds = lightBook != null ? lightBook.projectileDelaySeconds : 0.16;
        return secondsToNanosClamped(seconds);
    }

    private long getHealingBookPrimaryDelayNanos() {
        AxoTalesServerConfig.HealingBook healingBook = config != null ? config.healingBook : null;
        double seconds = healingBook != null ? healingBook.projectileDelaySeconds : 0.15;
        return secondsToNanosClamped(seconds);
    }

    private long getSpellbookCastDelayNanos(
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        if (isTeleportBookInSnapshot(snapshot) || isTeleportBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem)) {
            return getTeleportBookCastDelayNanos();
        }
        if (isDoomBookInSnapshot(snapshot) || isDoomBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem)) {
            return getDoomBookProjectileDelayNanos();
        }
        if (isFlameBookInSnapshot(snapshot) || isFlameBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem)) {
            return getFlameBookProjectileDelayNanos();
        }
        if (isLightBookInSnapshot(snapshot) || isLightBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem)) {
            return getLightBookProjectileDelayNanos();
        }
        return getSpellbookSecondaryUseDelayNanos();
    }

    private static long secondsToNanosClamped(double seconds) {
        if (!Double.isFinite(seconds)) {
            return 600_000_000L;
        }
        if (seconds <= 0) {
            return 0L;
        }
        if (seconds > 5) {
            seconds = 5;
        }
        double nanos = seconds * 1_000_000_000.0;
        if (nanos >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) nanos;
    }

    private @Nonnull DelayScheduleDecision scheduleDelayedSpellbookCast(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull InteractionSnapshot snapshot,
        long nowNanos,
        long delayNanos,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem,
        @Nonnull ManaSnapshot mana
    ) {
        Map<UUID, Integer> dedupeMap = snapshot.interactionType == InteractionType.Secondary
            ? lastScheduledSpellbookSecondaryChainId
            : lastScheduledSpellbookUseChainId;
        return scheduleDelayedCast(
            "SpellDelay",
            dedupeMap,
            playerRef,
            store,
            snapshot,
            nowNanos,
            delayNanos,
            serverItemInHand,
            serverUtilityItem,
            serverToolsItem,
            mana
        );
    }

    private @Nonnull DelayScheduleDecision scheduleDelayedHealingPrimaryCast(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull InteractionSnapshot snapshot,
        long nowNanos,
        long delayNanos,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem,
        @Nonnull ManaSnapshot mana
    ) {
        return scheduleDelayedCast(
            "HealingDelay",
            lastScheduledHealingPrimaryChainId,
            playerRef,
            store,
            snapshot,
            nowNanos,
            delayNanos,
            serverItemInHand,
            serverUtilityItem,
            serverToolsItem,
            mana
        );
    }

    private @Nonnull DelayScheduleDecision scheduleDelayedAncientSwordCast(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull InteractionSnapshot snapshot,
        long nowNanos,
        long delayNanos,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem,
        @Nonnull ManaSnapshot mana
    ) {
        return scheduleDelayedCast(
            "AncientSwordDelay",
            lastScheduledAncientSwordSecondaryChainId,
            playerRef,
            store,
            snapshot,
            nowNanos,
            delayNanos,
            serverItemInHand,
            serverUtilityItem,
            serverToolsItem,
            mana
        );
    }

    private @Nonnull DelayScheduleDecision scheduleDelayedCast(
        @Nonnull String delayEventName,
        @Nonnull Map<UUID, Integer> dedupeMap,
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull InteractionSnapshot snapshot,
        long nowNanos,
        long delayNanos,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem,
        @Nonnull ManaSnapshot mana
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new DelayScheduleDecision(false, false, "noPlayerUuid");
        }

        if (snapshot.interactionType != InteractionType.Primary
            && snapshot.interactionType != InteractionType.Secondary
            && snapshot.interactionType != InteractionType.Use) {
            return new DelayScheduleDecision(false, false, "interactionTypeNotSupported");
        }

        Integer lastChainId = dedupeMap.get(uuid);
        if (lastChainId != null && lastChainId == snapshot.chainId) {
            debug.traceFileOnly(
                playerRef,
                delayEventName + " event=dedupe"
                    + " event=SyncInteractionChains(id=290)"
                    + " interactionType=" + snapshot.interactionType
                    + " chainId=" + snapshot.chainId
                    + " allow=false"
                    + " reason=dedupe.chainId"
            );
            return new DelayScheduleDecision(false, true, "dedupe.chainId");
        }
        dedupeMap.put(uuid, snapshot.chainId);

        var external = store.getExternalData();
        World world = external != null ? external.getWorld() : null;
        if (world == null) {
            return new DelayScheduleDecision(false, false, "worldMissing");
        }

        UUID worldUuid = playerRef.getWorldUuid();
        double delaySeconds = delayNanos / 1_000_000_000.0;
        long executeAtNanos = nowNanos + delayNanos;

        debug.traceFileOnly(
            playerRef,
            delayEventName + " event=schedule"
                + " event=SyncInteractionChains(id=290)"
                + " interactionType=" + snapshot.interactionType
                + " chainId=" + snapshot.chainId
                + " initial=" + snapshot.initial
                + " delaySeconds=" + String.format("%.3f", delaySeconds)
                + " scheduledAtNanos=" + nowNanos
                + " executeAtNanos=" + executeAtNanos
                + " usedItemSource=" + resolveItemSource(snapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                + " client.itemInHandId=" + (snapshot.itemInHandId != null ? snapshot.itemInHandId : "null")
                + " client.utilityItemId=" + (snapshot.utilityItemId != null ? snapshot.utilityItemId : "null")
                + " client.toolsItemId=" + (snapshot.toolsItemId != null ? snapshot.toolsItemId : "null")
                + " server.itemInHand=" + (serverItemInHand != null ? serverItemInHand : "null")
                + " server.utilityItem=" + (serverUtilityItem != null ? serverUtilityItem : "null")
                + " server.toolsItem=" + (serverToolsItem != null ? serverToolsItem : "null")
                + " mana.index=" + mana.index
                + " mana.current=" + mana.current
                + " mana.min=" + mana.min
                + " mana.max=" + mana.max
        );

        List<InteractionSnapshot> delayedSnapshots = List.of(snapshot);

        try {
            delayedCastExecutor.schedule(() -> {
                try {
                    world.execute(() -> {
                        try {
                            if (!playerRef.isValid()) {
                                return;
                            }
                            if (worldUuid != null && playerRef.getWorldUuid() != null && !worldUuid.equals(playerRef.getWorldUuid())) {
                                return;
                            }

                            Ref<EntityStore> playerEntityRef = playerRef.getReference();
                            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                                return;
                            }

                            Store<EntityStore> playerStore = playerEntityRef.getStore();
                            if (playerStore == null) {
                                return;
                            }

                            debug.traceFileOnly(
                                playerRef,
                                delayEventName + " event=execute"
                                    + " event=SyncInteractionChains(id=290)"
                                    + " interactionType=" + snapshot.interactionType
                                    + " chainId=" + snapshot.chainId
                                    + " delaySeconds=" + String.format("%.3f", delaySeconds)
                            );

                            onWorldThread(playerRef, playerStore, playerEntityRef, delayedSnapshots, false);
                        } catch (Throwable t) {
                            errors.report(playerRef, "SpellbookInputInterceptor: delayed cast execution failed.", t);
                        }
                    });
                } catch (Throwable t) {
                    errors.report(playerRef, "SpellbookInputInterceptor: delayed cast world.execute failed.", t);
                }
            }, delayNanos, TimeUnit.NANOSECONDS);
        } catch (Throwable t) {
            errors.report(playerRef, "SpellbookInputInterceptor: delayed cast scheduling failed.", t);
            return new DelayScheduleDecision(false, false, "scheduleException");
        }

        return new DelayScheduleDecision(true, false, "scheduled");
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

            var external = store.getExternalData();
            if (external == null) {
                return;
            }

            World world = external.getWorld();
            if (world == null) {
                return;
            }

            world.execute(() -> onWorldThread(playerRef, store, playerEntityRef, snapshots, true));
        } catch (Throwable t) {
            errors.report(playerRef, "SpellbookInputInterceptor: failed to schedule world-thread handling.", t);
        }
    }

    private void onWorldThread(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull List<InteractionSnapshot> snapshots,
        boolean allowCastDelay
    ) {
        try {
            Player playerComponent = store.getComponent(playerEntityRef, Player.getComponentType());
            if (playerComponent == null) {
                return;
            }

            EntityStatMap stats = store.getComponent(playerEntityRef, EntityStatMap.getComponentType());

            String serverItemInHand = itemIdOrNull(InventoryComponentAccess.itemInHand(store, playerEntityRef));
            String serverActiveHotbarItem = itemIdOrNull(InventoryComponentAccess.activeHotbarItem(store, playerEntityRef));
            String serverUtilityItem = itemIdOrNull(InventoryComponentAccess.utilityItem(store, playerEntityRef));
            String serverToolsItem = itemIdOrNull(InventoryComponentAccess.toolsItem(store, playerEntityRef));
            String serverActiveToolItem = itemIdOrNull(InventoryComponentAccess.toolsItem(store, playerEntityRef));

            ManaSnapshot mana = snapshotMana(stats);
            StatSnapshot health = snapshotStat(stats, DefaultEntityStatTypes.getHealth());

            UUID uuid = playerRef.getUuid();
            InteractionSnapshot ancientSwordSnapshot = selectSecondarySnapshot(snapshots);
            boolean anySpellbookInPacket = snapshots.stream().anyMatch(this::clientReportsAnySpellbook);
            boolean anySpellbookInServerSlots = isSpellbookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem);
            boolean holdingAnySpellbookNow = anySpellbookInPacket || anySpellbookInServerSlots;
            boolean holdingAncientSwordNow = ancientSwordSnapshot != null
                && (isAncientSwordInSnapshot(ancientSwordSnapshot) || ANCIENT_SWORD_ITEM_ID.equals(serverItemInHand));

            if (allowCastDelay && holdingAnySpellbookNow) {
                for (InteractionSnapshot snapshot : snapshots) {
                    debug.traceFileOnly(playerRef, formatTraceLine(
                        snapshot,
                        false,
                        serverItemInHand,
                        serverActiveHotbarItem,
                        serverUtilityItem,
                        serverToolsItem,
                        serverActiveToolItem,
                        mana,
                        holdingAnySpellbookNow,
                        true
                    ));
                }
            }

            if (!holdingAnySpellbookNow && !holdingAncientSwordNow) {
                return;
            }

            long nowNanos = uuid != null ? System.nanoTime() : 0L;

            InteractionSnapshot primarySnapshot = selectPrimarySnapshot(snapshots);
            if (primarySnapshot != null && uuid != null) {
                if (isHealingBookInSnapshot(primarySnapshot) || HEALING_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    boolean delayedHealing = false;
                    if (allowCastDelay) {
                        long delayNanos = getHealingBookPrimaryDelayNanos();
                        if (delayNanos > 0) {
                            DelayScheduleDecision scheduled = scheduleDelayedHealingPrimaryCast(
                                playerRef,
                                store,
                                primarySnapshot,
                                nowNanos,
                                delayNanos,
                                serverItemInHand,
                                serverUtilityItem,
                                serverToolsItem,
                                mana
                            );
                            delayedHealing = scheduled.scheduled || scheduled.deduped;
                        }
                    }

                    if (!delayedHealing) {
                        HealingProjectileDecision decision = tryCastHealingBookPrimary(
                            playerRef,
                            store,
                            playerEntityRef,
                            stats,
                            mana,
                            nowNanos,
                            primarySnapshot,
                            serverItemInHand,
                            serverUtilityItem,
                            serverToolsItem
                        );
                        debug.traceFileOnly(
                            playerRef,
                            "SpellDecision itemId=" + HEALING_BOOK_ITEM_ID
                                + " event=SyncInteractionChains(id=290)"
                                + " interactionType=" + primarySnapshot.interactionType
                                + " chainId=" + primarySnapshot.chainId
                                + " initial=" + primarySnapshot.initial
                                + " cancelled=false"
                                + " usedItemSource=" + resolveItemSource(primarySnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                                + " mana.index=" + mana.index
                                + " mana.current=" + mana.current
                                + " mana.min=" + mana.min
                                + " mana.max=" + mana.max
                                + " cost.mana=" + config.healingBook.manaCost
                                + " projectile.delaySeconds=" + config.healingBook.projectileDelaySeconds
                                + " projectile.assetId=" + decision.projectileAssetName
                                + (decision.projectileUuid != null ? " projectile.uuid=" + decision.projectileUuid : "")
                                + (decision.origin != null ? " projectile.origin=" + Vector3d.formatShortString(decision.origin) : "")
                                + (decision.direction != null ? " projectile.direction=" + Vector3d.formatShortString(decision.direction) : "")
                                + " allow=" + decision.allow
                                + " reason=" + decision.reason
                        );
                    }
                } else if (MINING_BOOK_ITEM_ID.equals(primarySnapshot.itemInHandId) || MINING_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    MiningShapeChangeDecision decision = cycleMiningBookShape(
                        playerRef,
                        primarySnapshot,
                        nowNanos,
                        serverItemInHand
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + MINING_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + primarySnapshot.interactionType
                            + " chainId=" + primarySnapshot.chainId
                            + " initial=" + primarySnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(primarySnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " mining.shape.previous=" + decision.previousShape.displayName
                            + " mining.shape.current=" + decision.nextShape.displayName
                            + " mining.shape.blocksPerDepth=" + decision.nextShape.blocksPerDepth
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                } else if (isMorphBookInSnapshot(primarySnapshot) || MORPH_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    MorphResetDecision decision = tryResetMorphBookPrimary(
                        playerRef,
                        store,
                        playerEntityRef,
                        primarySnapshot,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + MORPH_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + primarySnapshot.interactionType
                            + " chainId=" + primarySnapshot.chainId
                            + " initial=" + primarySnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(primarySnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + (decision.currentModelAssetId != null ? " current.modelAssetId=" + decision.currentModelAssetId : "")
                            + (decision.baselineModelAssetId != null ? " baseline.modelAssetId=" + decision.baselineModelAssetId : "")
                            + " morphReset.applied=" + decision.applied
                            + " baseline.skin.present=" + decision.baselineSkinPresent
                            + " baseline.skin.applied=" + decision.skinApplied
                            + " baseline.skin.reason=" + decision.skinReason
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                }
            }

            InteractionSnapshot miningChargeStartSnapshot = selectMiningChargeStartSnapshot(
                snapshots,
                serverItemInHand,
                serverUtilityItem,
                serverToolsItem
            );
            if (miningChargeStartSnapshot != null && uuid != null) {
                boolean miningDetectedNow = isMiningBookInSnapshot(miningChargeStartSnapshot)
                    || isMiningBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem);
                if (miningDetectedNow) {
                    trackMiningChargeStart(
                        playerRef,
                        miningChargeStartSnapshot,
                        nowNanos,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem,
                        mana
                    );
                }
            }

            InteractionSnapshot miningChargeTerminalSnapshot = selectMiningChargeTerminalSnapshot(
                snapshots,
                serverItemInHand,
                serverUtilityItem,
                serverToolsItem
            );
            if (miningChargeTerminalSnapshot != null && uuid != null) {
                MiningChargeResolution chargeResolution = resolveMiningChargeRelease(
                    playerRef,
                    miningChargeTerminalSnapshot,
                    nowNanos,
                    serverItemInHand,
                    serverUtilityItem,
                    serverToolsItem,
                    mana
                );
                if (chargeResolution.allowCast) {
                    MiningDecision decision = tryCastMiningBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        stats,
                        mana,
                        nowNanos,
                        miningChargeTerminalSnapshot,
                        serverItemInHand,
                        chargeResolution.chargeSeconds,
                        chargeResolution.requestedTunnelBlocks
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + MINING_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + miningChargeTerminalSnapshot.interactionType
                            + " chainId=" + miningChargeTerminalSnapshot.chainId
                            + " initial=" + miningChargeTerminalSnapshot.initial
                            + " state=" + miningChargeTerminalSnapshot.state
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(miningChargeTerminalSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " cost.mana=" + config.miningBook.manaCost
                            + " mining.chargeSeconds=" + String.format("%.3f", decision.chargeSeconds)
                            + " mining.requestedTunnelBlocks=" + decision.requestedTunnelBlocks
                            + " mining.shape=" + decision.miningShape
                            + " mining.shape.blocksPerDepth=" + decision.miningShapeBlocksPerDepth
                            + " mining.minChargeBlocks=" + config.miningBook.minChargeBlocks
                            + " mining.blocksPerChargeTier=" + config.miningBook.blocksPerChargeTier
                            + " mining.chargeTierSeconds=" + config.miningBook.chargeTierSeconds
                            + " mining.maxChargeSeconds=" + config.miningBook.maxChargeSeconds
                            + " mining.maxTunnelBlocks=" + config.miningBook.maxTunnelBlocks
                            + " limit.maxDistanceBlocks=" + getEffectiveMiningMaxDistanceBlocks()
                            + " ray.maxDistance=" + decision.rayMaxDistance
                            + " ray.hit=" + decision.rayHit
                            + (decision.rayHit != null
                                ? " ray.hit.block=(" + decision.rayHit.x + "," + decision.rayHit.y + "," + decision.rayHit.z + ")"
                                    + " ray.hit.point=" + Vector3d.formatShortString(decision.rayHit.collisionPoint)
                                    + " ray.hit.normal=" + Vector3d.formatShortString(decision.rayHit.collisionNormal)
                                : "")
                            + (decision.hitDistanceBlocks > 0 ? " ray.hit.distanceBlocks=" + String.format("%.2f", decision.hitDistanceBlocks) : "")
                            + (decision.faceAxis != null ? " mining.faceAxis=" + decision.faceAxis : "")
                            + (decision.targetBlockTypeId != null ? " targetBlockTypeId=" + decision.targetBlockTypeId : "")
                            + " mining.blocksConsidered=" + decision.blocksConsidered
                            + " mining.blocksBroken=" + decision.blocksBroken
                            + " mining.blocksSkipped.chunkNotLoaded=" + decision.blocksSkippedChunkNotLoaded
                            + " mining.blocksSkipped.unbreakable=" + decision.blocksSkippedUnbreakable
                            + " mining.blocksBreakFailed=" + decision.blocksBreakFailed
                            + " mining.blocksBreakExceptions=" + decision.blocksBreakExceptions
                            + " mining.dropEntitiesSpawned=" + decision.dropEntitiesSpawned
                            + " mining.dropItemsTotal=" + decision.dropItemsTotal
                            + " mining.dropsSkipped.noDropData=" + decision.dropsSkippedNoDropData
                            + " mining.dropsSkipped.missingDropList=" + decision.dropsSkippedMissingDropList
                            + " mining.dropSpawnExceptions=" + decision.dropSpawnExceptions
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                } else {
                    debug.traceFileOnly(
                        playerRef,
                        "MiningCharge event=end"
                            + " interactionType=" + miningChargeTerminalSnapshot.interactionType
                            + " chainId=" + miningChargeTerminalSnapshot.chainId
                            + " initial=" + miningChargeTerminalSnapshot.initial
                            + " state=" + miningChargeTerminalSnapshot.state
                            + " usedItemSource=" + resolveItemSource(miningChargeTerminalSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " charge.seconds=" + String.format("%.3f", chargeResolution.chargeSeconds)
                            + " charge.requestedTunnelBlocks=" + chargeResolution.requestedTunnelBlocks
                            + " charge.usedFallbackStart=" + chargeResolution.usedFallbackStart
                            + " allow=false"
                            + " reason=" + chargeResolution.reason
                    );
                }
            }

            InteractionSnapshot castSnapshot = selectCastSnapshot(
                snapshots,
                serverItemInHand,
                serverUtilityItem,
                serverToolsItem
            );
            if (castSnapshot != null && uuid != null) {
                boolean delayed = false;
                if (allowCastDelay && holdingAnySpellbookNow) {
                    long delayNanos = getSpellbookCastDelayNanos(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem);
                    if (delayNanos > 0) {
                        DelayScheduleDecision scheduled = scheduleDelayedSpellbookCast(
                            playerRef,
                            store,
                            castSnapshot,
                            nowNanos,
                            delayNanos,
                            serverItemInHand,
                            serverUtilityItem,
                            serverToolsItem,
                            mana
                        );
                        delayed = scheduled.scheduled || scheduled.deduped;
                    }
                }

                if (!delayed) {
                    if (isTauntBookInSnapshot(castSnapshot) || TAUNT_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    TauntDecision decision = tryCastTauntBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        playerComponent,
                        stats,
                        mana,
                        nowNanos,
                        castSnapshot,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem
                    );
                     debug.traceFileOnly(
                         playerRef,
                         "SpellDecision itemId=" + TAUNT_BOOK_ITEM_ID
                             + " event=SyncInteractionChains(id=290)"
                             + " interactionType=" + castSnapshot.interactionType
                             + " chainId=" + castSnapshot.chainId
                             + " initial=" + castSnapshot.initial
                             + " cancelled=false"
                             + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                             + " mana.index=" + mana.index
                             + " mana.current=" + mana.current
                             + " mana.min=" + mana.min
                             + " mana.max=" + mana.max
                             + " cost.mana=" + config.tauntBook.manaCost
                             + " launch.heightBlocks=" + config.tauntBook.launchHeightBlocks
                             + " launch.source=" + decision.launchSource
                             + " launch.velocityY=" + String.format("%.3f", decision.launchVelocityY)
                             + " fallImmunity.seconds=" + config.tauntBook.fallImmunitySeconds
                             + " taunt.stackCount=" + decision.stackCount
                             + " slam.damage=" + decision.slamDamage
                             + " slam.damage.cap=" + TauntBookEffectState.STACK_DAMAGE_CAP
                             + " slam.damage.multiplier=" + TauntBookEffectState.STACK_DAMAGE_MULTIPLIER
                             + " slam.radiusBlocks=" + decision.damageRadiusBlocks
                             + " slam.breakRadiusBlocks=" + decision.groundBreakRadiusBlocks
                             + " slam.breakBlockBelow=" + config.tauntBook.breakBlockBelow
                             + (decision.launchVelocity != null ? " launch.velocity=" + Vector3d.formatShortString(decision.launchVelocity) : "")
                             + (decision.fallImmunityExpiresAtNanos > 0 ? " fallImmunity.expiresAtNanos=" + decision.fallImmunityExpiresAtNanos : "")
                             + " allow=" + decision.allow
                             + " reason=" + decision.reason
                     );
                } else if (isHordeBookInSnapshot(castSnapshot) || HORDE_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    HordeDecision decision = tryCastHordeBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        playerComponent,
                        stats,
                        mana,
                        nowNanos,
                        castSnapshot,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + HORDE_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + castSnapshot.interactionType
                            + " chainId=" + castSnapshot.chainId
                            + " initial=" + castSnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " cost.mana=" + config.hordeBook.manaCost
                            + " summon.roleName=" + decision.roleName
                            + " summon.spawnDistanceBlocks=" + String.format("%.2f", decision.spawnDistanceBlocks)
                            + (decision.spawnPosition != null ? " summon.spawnPosition=" + Vector3d.formatShortString(decision.spawnPosition) : "")
                            + " summon.count=" + (decision.minionUuids != null ? decision.minionUuids.size() : 0)
                            + (decision.minionUuids != null && !decision.minionUuids.isEmpty() ? " summon.minionUuids=" + decision.minionUuids : "")
                            + " attitude.ownerFriendlySeconds=" + config.hordeBook.ownerFriendlySeconds
                            + (decision.minionExpiresAtNanos > 0 ? " minion.expiresAtNanos=" + decision.minionExpiresAtNanos : "")
                            + " allow=" + decision.allow
                             + " reason=" + decision.reason
                     );
                } else if (isDoomBookInSnapshot(castSnapshot) || DOOM_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    DoomDecision decision = tryCastDoomBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        stats,
                        mana,
                        nowNanos,
                        castSnapshot,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + DOOM_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + castSnapshot.interactionType
                            + " chainId=" + castSnapshot.chainId
                            + " initial=" + castSnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " cost.mana=" + getDoomBookManaCost()
                            + " projectile.delaySeconds=" + (config != null && config.doomBook != null ? config.doomBook.projectileDelaySeconds : 0.24)
                            + " projectile.assetId=" + decision.projectileAssetName
                            + (decision.origin != null ? " projectile.origin=" + Vector3d.formatShortString(decision.origin) : "")
                            + (decision.direction != null ? " projectile.direction=" + Vector3d.formatShortString(decision.direction) : "")
                            + (decision.projectileUuid != null ? " projectile.uuid=" + decision.projectileUuid : "")
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                } else if (isFlameBookInSnapshot(castSnapshot) || FLAME_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    FlameDecision decision = tryCastFlameBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        stats,
                        mana,
                        nowNanos,
                        castSnapshot,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + FLAME_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + castSnapshot.interactionType
                            + " chainId=" + castSnapshot.chainId
                            + " initial=" + castSnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " cost.mana=" + getFlameBookManaCost()
                            + " projectile.delaySeconds=" + (config != null && config.flameBook != null ? config.flameBook.projectileDelaySeconds : 0.2)
                            + " projectile.assetId=" + decision.projectileAssetName
                            + (decision.origin != null ? " projectile.origin=" + Vector3d.formatShortString(decision.origin) : "")
                            + (decision.direction != null ? " projectile.direction=" + Vector3d.formatShortString(decision.direction) : "")
                            + (decision.projectileUuid != null ? " projectile.uuid=" + decision.projectileUuid : "")
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                } else if (isLightBookInSnapshot(castSnapshot) || LIGHT_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    LightDecision decision = tryCastLightBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        stats,
                        mana,
                        nowNanos,
                        castSnapshot,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + LIGHT_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + castSnapshot.interactionType
                            + " chainId=" + castSnapshot.chainId
                            + " initial=" + castSnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " cost.mana=" + getLightBookManaCost()
                            + " projectile.delaySeconds=" + (config != null && config.lightBook != null ? config.lightBook.projectileDelaySeconds : 0.16)
                            + " projectile.assetId=" + decision.projectileAssetName
                            + (decision.origin != null ? " projectile.origin=" + Vector3d.formatShortString(decision.origin) : "")
                            + (decision.direction != null ? " projectile.direction=" + Vector3d.formatShortString(decision.direction) : "")
                            + (decision.projectileUuid != null ? " projectile.uuid=" + decision.projectileUuid : "")
                            + " projectile.dynamicLightApplied=" + decision.dynamicLightApplied
                            + " projectile.maxDistanceBlocks=" + (config != null && config.lightBook != null ? config.lightBook.maxDistanceBlocks : 100.0)
                            + " projectile.initialSpeed=" + (config != null && config.lightBook != null ? config.lightBook.initialSpeedBlocksPerSecond : 55.0)
                            + " projectile.cruiseSpeed=" + (config != null && config.lightBook != null ? config.lightBook.cruiseSpeedBlocksPerSecond : 0.75)
                            + " projectile.slowdownSeconds=" + (config != null && config.lightBook != null ? config.lightBook.slowdownSeconds : 1.2)
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                } else if (isFrostBookInSnapshot(castSnapshot) || FROST_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    FrostDecision decision = tryCastFrostBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        stats,
                        mana,
                        nowNanos,
                        castSnapshot,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + FROST_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + castSnapshot.interactionType
                            + " chainId=" + castSnapshot.chainId
                            + " initial=" + castSnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " cost.mana=" + getFrostBookManaCost()
                            + " projectile.assetId=" + decision.projectileAssetName
                            + (decision.origin != null ? " projectile.origin=" + Vector3d.formatShortString(decision.origin) : "")
                            + (decision.direction != null ? " projectile.direction=" + Vector3d.formatShortString(decision.direction) : "")
                            + (decision.projectileUuid != null ? " projectile.uuid=" + decision.projectileUuid : "")
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                } else if (isMorphBookInSnapshot(castSnapshot) || MORPH_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    MorphDecision decision = tryCastMorphBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        stats,
                        mana,
                        nowNanos,
                        castSnapshot,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + MORPH_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + castSnapshot.interactionType
                            + " chainId=" + castSnapshot.chainId
                            + " initial=" + castSnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " cost.mana=" + getMorphBookManaCost()
                            + " projectile.assetId=" + decision.projectileAssetName
                            + (decision.origin != null ? " projectile.origin=" + Vector3d.formatShortString(decision.origin) : "")
                            + (decision.direction != null ? " projectile.direction=" + Vector3d.formatShortString(decision.direction) : "")
                            + (decision.projectileUuid != null ? " projectile.uuid=" + decision.projectileUuid : "")
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                } else if (isTeleportBookInSnapshot(castSnapshot) || TELEPORT_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    TeleportDecision decision = tryCastTeleportBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        playerComponent,
                        stats,
                        mana,
                        nowNanos,
                        castSnapshot,
                        serverItemInHand
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + TELEPORT_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + castSnapshot.interactionType
                            + " chainId=" + castSnapshot.chainId
                            + " initial=" + castSnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " cost.mana=" + config.teleportBook.manaCost
                            + " limit.maxDistanceBlocks=" + getEffectiveTeleportMaxDistanceBlocks()
                            + " ray.maxDistance=" + decision.rayMaxDistance
                            + " ray.hit=" + decision.rayHit
                            + (decision.rayHit != null
                                ? " ray.hit.block=(" + decision.rayHit.x + "," + decision.rayHit.y + "," + decision.rayHit.z + ")"
                                    + " ray.hit.point=" + Vector3d.formatShortString(decision.rayHit.collisionPoint)
                                    + " ray.hit.normal=" + Vector3d.formatShortString(decision.rayHit.collisionNormal)
                                : "")
                            + (decision.hitDistanceBlocks > 0 ? " ray.hit.distanceBlocks=" + String.format("%.2f", decision.hitDistanceBlocks) : "")
                            + (decision.destination != null ? " destination=" + Vector3d.formatShortString(decision.destination) : "")
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                } else if (isImmunityBookInSnapshot(castSnapshot) || IMMUNITY_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    ImmunityDecision decision = tryCastImmunityBook(
                        playerRef,
                        store,
                        playerEntityRef,
                        stats,
                        mana,
                        nowNanos,
                        castSnapshot,
                        serverItemInHand,
                        serverUtilityItem,
                        serverToolsItem
                    );
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + IMMUNITY_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + castSnapshot.interactionType
                            + " chainId=" + castSnapshot.chainId
                            + " initial=" + castSnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " cost.mana=" + getImmunityBookManaCost()
                            + " immunity.seconds=" + getImmunityBookImmunitySeconds()
                            + (decision.expiresAtNanos > 0 ? " immunity.expiresAtNanos=" + decision.expiresAtNanos : "")
                            + " effect.id=" + IMMUNE_EFFECT_ID
                            + " effect.index=" + decision.effectIndex
                            + " effect.applied=" + decision.effectApplied
                            + " effect.reason=" + decision.effectReason
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                } else if (isHealingBookInSnapshot(castSnapshot) || HEALING_BOOK_ITEM_ID.equals(serverItemInHand)) {
                    Decision decision = tryCastHealingBook(playerRef, nowNanos, castSnapshot, serverItemInHand, mana, health);
                    debug.traceFileOnly(
                        playerRef,
                        "SpellDecision itemId=" + HEALING_BOOK_ITEM_ID
                            + " event=SyncInteractionChains(id=290)"
                            + " interactionType=" + castSnapshot.interactionType
                            + " chainId=" + castSnapshot.chainId
                            + " initial=" + castSnapshot.initial
                            + " cancelled=false"
                            + " usedItemSource=" + resolveItemSource(castSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                            + " mana.index=" + mana.index
                            + " mana.current=" + mana.current
                            + " mana.min=" + mana.min
                            + " mana.max=" + mana.max
                            + " health.index=" + health.index
                            + " health.current=" + health.current
                            + " health.min=" + health.min
                            + " health.max=" + health.max
                            + " heal.amount=" + config.healingBook.healAmount
                            + " cost.mana=" + config.healingBook.manaCost
                            + " allow=" + decision.allow
                            + " reason=" + decision.reason
                    );
                }
                }
            }

            if (ancientSwordSnapshot != null && uuid != null) {
                boolean clientSaysSword = isAncientSwordInSnapshot(ancientSwordSnapshot);
                boolean serverSaysSword = ANCIENT_SWORD_ITEM_ID.equals(serverItemInHand);
                if (clientSaysSword || serverSaysSword) {
                    boolean delayed = false;
                    if (allowCastDelay) {
                        long castDelayNanos = getAncientSwordCastDelayNanos();
                        if (castDelayNanos > 0) {
                            DelayScheduleDecision scheduled = scheduleDelayedAncientSwordCast(
                                playerRef,
                                store,
                                ancientSwordSnapshot,
                                nowNanos,
                                castDelayNanos,
                                serverItemInHand,
                                serverUtilityItem,
                                serverToolsItem,
                                mana
                            );
                            delayed = scheduled.scheduled || scheduled.deduped;
                        }
                    }

                    if (!delayed) {
                        AncientSwordDecision decision = tryCastAncientSwordSecondary(
                            playerRef,
                            store,
                            playerEntityRef,
                            stats,
                            mana,
                            nowNanos,
                            ancientSwordSnapshot,
                            serverItemInHand,
                            serverUtilityItem,
                            serverToolsItem
                        );
                        debug.traceFileOnly(
                            playerRef,
                            "SpellDecision itemId=" + ANCIENT_SWORD_ITEM_ID
                                + " event=SyncInteractionChains(id=290)"
                                + " interactionType=" + ancientSwordSnapshot.interactionType
                                + " chainId=" + ancientSwordSnapshot.chainId
                                + " initial=" + ancientSwordSnapshot.initial
                                + " cancelled=false"
                                + " usedItemSource=" + resolveItemSource(ancientSwordSnapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                                + " mana.index=" + mana.index
                                + " mana.current=" + mana.current
                                + " mana.min=" + mana.min
                                + " mana.max=" + mana.max
                                + " cost.mana=" + getAncientSwordManaCost()
                                + " cooldown.seconds=" + getAncientSwordCooldownSeconds()
                                + " cast.delaySeconds=" + String.format("%.3f", getAncientSwordCastDelaySeconds())
                                + " projectile.assetId=" + decision.projectileAssetName
                                + (decision.projectileUuid != null ? " projectile.uuid=" + decision.projectileUuid : "")
                                + (decision.origin != null ? " projectile.origin=" + Vector3d.formatShortString(decision.origin) : "")
                                + (decision.direction != null ? " projectile.direction=" + Vector3d.formatShortString(decision.direction) : "")
                                + " allow=" + decision.allow
                                + " reason=" + decision.reason
                        );
                    }
                }
            }
        } catch (Throwable t) {
            errors.report(playerRef, "SpellbookInputInterceptor: failed to process SyncInteractionChains update.", t);
        }
    }

    private @Nullable InteractionSnapshot selectCastSnapshot(
        @Nonnull List<InteractionSnapshot> snapshots,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        InteractionSnapshot secondary = null;
        InteractionSnapshot use = null;
        for (InteractionSnapshot snapshot : snapshots) {
            if (!snapshot.initial) {
                continue;
            }
            boolean miningBookDetected = isMiningBookInSnapshot(snapshot)
                || isMiningBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem);
            if (miningBookDetected) {
                continue;
            }
            if (snapshot.interactionType == InteractionType.Secondary) {
                secondary = snapshot;
            } else if (snapshot.interactionType == InteractionType.Use) {
                use = snapshot;
            }
        }
        return secondary != null ? secondary : use;
    }

    private @Nullable InteractionSnapshot selectSecondarySnapshot(@Nonnull List<InteractionSnapshot> snapshots) {
        for (InteractionSnapshot snapshot : snapshots) {
            if (!snapshot.initial) {
                continue;
            }
            if (snapshot.interactionType == InteractionType.Secondary) {
                return snapshot;
            }
        }
        return null;
    }

    private @Nullable InteractionSnapshot selectPrimarySnapshot(@Nonnull List<InteractionSnapshot> snapshots) {
        for (InteractionSnapshot snapshot : snapshots) {
            if (!snapshot.initial) {
                continue;
            }
            if (snapshot.interactionType == InteractionType.Primary) {
                return snapshot;
            }
        }
        return null;
    }

    private @Nullable InteractionSnapshot selectMiningChargeStartSnapshot(
        @Nonnull List<InteractionSnapshot> snapshots,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        InteractionSnapshot selected = null;
        for (InteractionSnapshot snapshot : snapshots) {
            if (!snapshot.initial) {
                continue;
            }
            boolean miningBookDetected = isMiningBookInSnapshot(snapshot)
                || isMiningBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem);
            if (!miningBookDetected) {
                continue;
            }
            if (snapshot.interactionType == InteractionType.Secondary || snapshot.interactionType == InteractionType.Use) {
                selected = snapshot;
            }
        }
        return selected;
    }

    private @Nullable InteractionSnapshot selectMiningChargeTerminalSnapshot(
        @Nonnull List<InteractionSnapshot> snapshots,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        InteractionSnapshot terminal = null;
        for (InteractionSnapshot snapshot : snapshots) {
            if (snapshot.initial) {
                continue;
            }
            if (snapshot.state == null || snapshot.state == InteractionState.NotFinished) {
                continue;
            }
            boolean miningBookDetected = isMiningBookInSnapshot(snapshot)
                || isMiningBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem);
            if (!miningBookDetected) {
                continue;
            }
            if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
                continue;
            }
            if (snapshot.state == InteractionState.Finished) {
                return snapshot;
            }
            terminal = snapshot;
        }
        return terminal;
    }

    private @Nonnull HealingProjectileDecision tryCastHealingBookPrimary(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new HealingProjectileDecision(false, "noPlayerUuid", HEALING_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (snapshot.interactionType != InteractionType.Primary) {
            return new HealingProjectileDecision(false, "interactionTypeNotSupported", HEALING_PROJECTILE_ASSET_ID, null, null, null);
        }

        Integer lastChainId = lastProcessedHealingPrimaryChainId.get(uuid);
        if (lastChainId != null && lastChainId == snapshot.chainId) {
            return new HealingProjectileDecision(false, "dedupe.primary.chainId", HEALING_PROJECTILE_ASSET_ID, null, null, null);
        }

        boolean clientSaysBook = isHealingBookInSnapshot(snapshot);
        boolean serverSaysBook = isHealingBook(serverItemInHand)
            || isHealingBook(serverUtilityItem)
            || isHealingBook(serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new HealingProjectileDecision(false, "notHoldingBook", HEALING_PROJECTILE_ASSET_ID, null, null, null);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents repeated gating logs).
        lastProcessedHealingPrimaryChainId.put(uuid, snapshot.chainId);

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastHealingCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new HealingProjectileDecision(false, debounce.reason, HEALING_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        if (!mana.present) {
            return new HealingProjectileDecision(false, "manaStatMissing", HEALING_PROJECTILE_ASSET_ID, null, null, null);
        }

        AxoTalesServerConfig.FullOrInt manaCost = config.healingBook.manaCost;
        if (manaCost != null && manaCost.full) {
            if (mana.max <= FLOAT_EPSILON) {
                return new HealingProjectileDecision(false, "manaMaxTooLow", HEALING_PROJECTILE_ASSET_ID, null, null, null);
            }
            if (Math.abs(mana.current - mana.max) > FLOAT_EPSILON) {
                return new HealingProjectileDecision(false, "manaNotFull", HEALING_PROJECTILE_ASSET_ID, null, null, null);
            }
        } else {
            int cost = manaCost != null ? manaCost.value : 0;
            if (cost < 0) {
                cost = 0;
            }
            if (mana.max < cost - FLOAT_EPSILON) {
                return new HealingProjectileDecision(false, "manaMaxTooLow", HEALING_PROJECTILE_ASSET_ID, null, null, null);
            }
            if (mana.current < cost - FLOAT_EPSILON) {
                return new HealingProjectileDecision(false, "manaTooLow", HEALING_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        if (stats == null) {
            return new HealingProjectileDecision(false, "entityStatMapMissing", HEALING_PROJECTILE_ASSET_ID, null, null, null);
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return new HealingProjectileDecision(false, "timeResourceMissing", HEALING_PROJECTILE_ASSET_ID, null, null, null);
        }

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new HealingProjectileDecision(false, "lookTransformMissing", HEALING_PROJECTILE_ASSET_ID, null, null, null);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new HealingProjectileDecision(false, "originNotFinite", HEALING_PROJECTILE_ASSET_ID, origin, null, null);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new HealingProjectileDecision(false, "directionInvalid", HEALING_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Vector3f rotation = look.getRotation();
        if (rotation == null || !rotation.isFinite()) {
            rotation = Vector3f.ZERO;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, HEALING_PROJECTILE_ASSET_ID, origin, rotation);
        if (holder == null) {
            return new HealingProjectileDecision(false, "projectileAssembleFailed", HEALING_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return new HealingProjectileDecision(false, "projectileComponentMissing", HEALING_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        if (!projectile.initialize()) {
            return new HealingProjectileDecision(false, "projectileAssetNotFound", HEALING_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        try {
            projectile.shoot(holder, uuid, origin.x, origin.y, origin.z, rotation.getYaw(), rotation.getPitch());
        } catch (Throwable t) {
            errors.report(playerRef, "HealingBook: projectile.shoot failed (assetId=" + HEALING_PROJECTILE_ASSET_ID + ").", t);
            return new HealingProjectileDecision(false, "projectileShootException", HEALING_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Ref<EntityStore> projectileRef;
        try {
            projectileRef = store.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            errors.report(playerRef, "HealingBook: store.addEntity failed (assetId=" + HEALING_PROJECTILE_ASSET_ID + ").", t);
            return new HealingProjectileDecision(false, "projectileSpawnException", HEALING_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        UUID projectileUuid = null;
        try {
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort debug info.
        }

        float newMana;
        if (manaCost != null && manaCost.full) {
            newMana = mana.min;
        } else {
            int cost = manaCost != null ? manaCost.value : 0;
            if (cost < 0) {
                cost = 0;
            }
            newMana = Math.max(mana.min, mana.current - cost);
        }

        stats.setStatValue(mana.index, newMana);
        stats.update();

        return new HealingProjectileDecision(true, "castApplied", HEALING_PROJECTILE_ASSET_ID, origin, direction, projectileUuid);
    }

    private @Nonnull MorphResetDecision tryResetMorphBookPrimary(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new MorphResetDecision(false, "noPlayerUuid", null, null, false, false, false, "skip");
        }

        if (snapshot.interactionType != InteractionType.Primary) {
            return new MorphResetDecision(false, "interactionTypeNotSupported", null, null, false, false, false, "skip");
        }

        Integer lastChainId = lastProcessedMorphPrimaryChainId.get(uuid);
        if (lastChainId != null && lastChainId == snapshot.chainId) {
            return new MorphResetDecision(false, "dedupe.primary.chainId", null, null, false, false, false, "skip");
        }

        boolean clientSaysBook = isMorphBookInSnapshot(snapshot);
        boolean serverSaysBook = MORPH_BOOK_ITEM_ID.equals(serverItemInHand)
            || MORPH_BOOK_ITEM_ID.equals(serverUtilityItem)
            || MORPH_BOOK_ITEM_ID.equals(serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new MorphResetDecision(false, "notHoldingBook", null, null, false, false, false, "skip");
        }

        // De-dupe repeated initial chain updates even when the reset is denied (prevents repeated gating logs).
        lastProcessedMorphPrimaryChainId.put(uuid, snapshot.chainId);

        ModelComponent currentModelComponent = store.getComponent(playerEntityRef, ModelComponent.getComponentType());
        Model currentModel = currentModelComponent != null ? currentModelComponent.getModel() : null;
        String currentModelAssetId = currentModel != null ? currentModel.getModelAssetId() : null;

        Model baselineModel = morphBookModelState.getBaselineModel(uuid);
        String baselineModelAssetId = baselineModel != null ? baselineModel.getModelAssetId() : null;
        PlayerSkin baselineSkin = morphBookModelState.getBaselineSkin(uuid);
        boolean baselineSkinPresent = baselineSkin != null;

        if (baselineModel == null) {
            return new MorphResetDecision(
                false,
                "baselineMissing",
                baselineModelAssetId,
                currentModelAssetId,
                false,
                baselineSkinPresent,
                false,
                "baselineModelMissing"
            );
        }

        boolean applied = false;
        String reason = "alreadyDefault";
        if (baselineModelAssetId == null || !baselineModelAssetId.equals(currentModelAssetId)) {
            try {
                store.putComponent(playerEntityRef, ModelComponent.getComponentType(), new ModelComponent(new Model(baselineModel)));
                applied = true;
                reason = "resetApplied";
            } catch (Throwable t) {
                errors.report(playerRef, "MorphBook: failed to reset model to baseline.", t);
                return new MorphResetDecision(
                    false,
                    "applyException",
                    baselineModelAssetId,
                    currentModelAssetId,
                    false,
                    baselineSkinPresent,
                    false,
                    "modelApplyException"
                );
            }
        }

        boolean skinApplied = false;
        String skinReason = "skip";
        if (baselineSkinPresent) {
            try {
                PlayerSkinComponent skinComponent = new PlayerSkinComponent(new PlayerSkin(baselineSkin));
                skinComponent.setNetworkOutdated();
                store.putComponent(playerEntityRef, PlayerSkinComponent.getComponentType(), skinComponent);
                skinApplied = true;
                skinReason = "applied";
            } catch (Throwable t) {
                errors.report(playerRef, "MorphBook: failed to restore baseline skin.", t);
                skinApplied = false;
                skinReason = "applyException";
            }
        } else {
            PlayerSkinComponent currentSkin = store.getComponent(playerEntityRef, PlayerSkinComponent.getComponentType());
            if (currentSkin != null) {
                currentSkin.setNetworkOutdated();
                skinApplied = true;
                skinReason = "networkOutdated";
            } else {
                skinApplied = false;
                skinReason = "skinMissing";
            }
        }

        return new MorphResetDecision(true, reason, baselineModelAssetId, currentModelAssetId, applied, baselineSkinPresent, skinApplied, skinReason);
    }

    private @Nonnull Decision tryCastHealingBook(
        @Nonnull PlayerRef playerRef,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nonnull ManaSnapshot mana,
        @Nonnull StatSnapshot health
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new Decision(false, "noPlayerUuid");
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new Decision(false, "dedupe.secondary.chainId");
            }
        } else if (snapshot.interactionType == InteractionType.Use) {
            Integer lastChainId = lastProcessedUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new Decision(false, "dedupe.use.chainId");
            }
        } else {
            return new Decision(false, "interactionTypeNotSupported");
        }

        boolean clientSaysBook = isHealingBookInSnapshot(snapshot);
        boolean serverSaysBook = isHealingBook(serverItemInHand);
        if (!clientSaysBook && !serverSaysBook) {
            return new Decision(false, "notHoldingBook");
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents chat spam / repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastHealingCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new Decision(false, debounce.reason);
            }
        }

        if (!mana.present) {
            return new Decision(false, "manaStatMissing");
        }

        AxoTalesServerConfig.FullOrInt manaCost = config.healingBook.manaCost;
        if (manaCost != null && manaCost.full) {
            if (mana.max <= FLOAT_EPSILON) {
                return new Decision(false, "manaMaxTooLow");
            }
            if (Math.abs(mana.current - mana.max) > FLOAT_EPSILON) {
                return new Decision(false, "manaNotFull");
            }
        } else {
            int cost = manaCost != null ? manaCost.value : 0;
            if (cost < 0) {
                cost = 0;
            }
            if (mana.max < cost - FLOAT_EPSILON) {
                return new Decision(false, "manaMaxTooLow");
            }
            if (mana.current < cost - FLOAT_EPSILON) {
                return new Decision(false, "manaTooLow");
            }
        }

        if (!health.present) {
            return new Decision(false, "healthStatMissing");
        }

        float newMana;
        if (manaCost != null && manaCost.full) {
            newMana = mana.min;
        } else {
            int cost = manaCost != null ? manaCost.value : 0;
            if (cost < 0) {
                cost = 0;
            }
            newMana = Math.max(mana.min, mana.current - cost);
        }

        // Apply the spell effect by mutating stats on the world thread (we are already on it).
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return new Decision(false, "playerEntityRefInvalid");
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        if (store == null) {
            return new Decision(false, "playerEntityStoreMissing");
        }

        EntityStatMap stats = store.getComponent(playerEntityRef, EntityStatMap.getComponentType());
        if (stats == null) {
            return new Decision(false, "entityStatMapMissing");
        }

        int healthIndex = DefaultEntityStatTypes.getHealth();
        if (healthIndex != Integer.MIN_VALUE && healthIndex >= 0) {
            EntityStatValue healthStat = stats.get(healthIndex);
            if (healthStat != null) {
                AxoTalesServerConfig.FullOrInt healAmount = config.healingBook.healAmount;
                if (healAmount != null && healAmount.full) {
                    stats.setStatValue(healthIndex, healthStat.getMax());
                } else {
                    int delta = healAmount != null ? healAmount.value : 0;
                    if (delta < 0) {
                        delta = 0;
                    }
                    float newHealth = Math.min(healthStat.getMax(), healthStat.get() + delta);
                    stats.setStatValue(healthIndex, newHealth);
                }
            }
        }

        stats.setStatValue(mana.index, newMana);
        stats.update();

        return new Decision(true, "castApplied");
    }

    private @Nonnull HordeDecision tryCastHordeBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Player playerComponent,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new HordeDecision(false, "noPlayerUuid", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new HordeDecision(false, "interactionTypeNotSupported", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedHordeSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new HordeDecision(false, "dedupe.secondary.chainId", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
            }
        } else {
            Integer lastChainId = lastProcessedHordeUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new HordeDecision(false, "dedupe.use.chainId", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
            }
        }

        boolean clientSaysBook = isHordeBookInSnapshot(snapshot);
        boolean serverSaysBook = HORDE_BOOK_ITEM_ID.equals(serverItemInHand)
            || HORDE_BOOK_ITEM_ID.equals(serverUtilityItem)
            || HORDE_BOOK_ITEM_ID.equals(serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new HordeDecision(false, "notHoldingBook", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedHordeSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedHordeUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastHordeCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new HordeDecision(false, debounce.reason, "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
            }
        }

        if (!mana.present) {
            return new HordeDecision(false, "manaStatMissing", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        int manaCost = Math.max(0, config.hordeBook.manaCost);
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new HordeDecision(false, "manaTooLow", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        HordeBookSummonState.ActiveSummon already = hordeSummonState.getByOwnerIfActive(uuid, nowNanos);
        if (already != null) {
            return new HordeDecision(false, "summonAlreadyActive", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, already.minionUuids, already.expiresAtNanos);
        }

        if (stats == null) {
            return new HordeDecision(false, "entityStatMapMissing", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            return new HordeDecision(false, "npcPluginMissing", "Outlander_Brute", config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        final String roleName = "Outlander_Brute";
        if (!npcPlugin.hasRoleName(roleName)) {
            return new HordeDecision(false, "npcRoleNotFound", roleName, config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new HordeDecision(false, "lookTransformMissing", roleName, config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new HordeDecision(false, "originNotFinite", roleName, config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new HordeDecision(false, "directionInvalid", roleName, config.hordeBook.spawnDistanceBlocks, null, java.util.List.of(), 0);
        }

        double spawnDistance = config.hordeBook.spawnDistanceBlocks;
        if (!Double.isFinite(spawnDistance) || spawnDistance < 0) {
            spawnDistance = 0;
        }

        Vector3d dir = new Vector3d(direction).normalize();
        Vector3d right = new Vector3d(-dir.z, 0.0, dir.x);
        if (right.squaredLength() > 1e-9) {
            right.normalize();
        } else {
            right = new Vector3d(1.0, 0.0, 0.0);
        }

        Vector3f rotation = look.getRotation();
        if (rotation == null || !rotation.isFinite()) {
            rotation = Vector3f.ZERO;
        }

        final int minionCount = 3;
        final double lateralSpacing = 1.5;
        java.util.List<Ref<EntityStore>> minionRefs = new java.util.ArrayList<>(minionCount);
        java.util.List<UUID> minionUuids = new java.util.ArrayList<>(minionCount);
        Vector3d firstSpawnPosition = null;

        for (int i = 0; i < minionCount; i++) {
            double offset = (i - 1) * lateralSpacing;
            Vector3d spawnPosition = new Vector3d(origin).addScaled(dir, spawnDistance).addScaled(right, offset);
            if (!spawnPosition.isFinite()) {
                cleanupSpawnedMinionsBestEffort(store, minionRefs);
                return new HordeDecision(false, "spawnPositionNotFinite", roleName, spawnDistance, null, java.util.List.of(), 0);
            }
            if (firstSpawnPosition == null) {
                firstSpawnPosition = spawnPosition;
            }

            it.unimi.dsi.fastutil.Pair<Ref<EntityStore>, ?> spawned;
            try {
                spawned = npcPlugin.spawnNPC(store, roleName, null, spawnPosition, rotation);
            } catch (Throwable t) {
                cleanupSpawnedMinionsBestEffort(store, minionRefs);
                errors.report(playerRef, "HordeBook: spawnNPC failed (roleName=" + roleName + ").", t);
                return new HordeDecision(false, "spawnException", roleName, spawnDistance, firstSpawnPosition, java.util.List.of(), 0);
            }

            Ref<EntityStore> minionRef = spawned != null ? spawned.left() : null;
            if (minionRef == null || !minionRef.isValid()) {
                cleanupSpawnedMinionsBestEffort(store, minionRefs);
                return new HordeDecision(false, "spawnFailed", roleName, spawnDistance, firstSpawnPosition, java.util.List.of(), 0);
            }

            NPCEntity minionNpc = store.getComponent(minionRef, NPCEntity.getComponentType());
            if (minionNpc == null) {
                try {
                    store.removeEntity(minionRef, RemoveReason.REMOVE);
                } catch (Throwable ignored) {
                    // Best effort.
                }
                cleanupSpawnedMinionsBestEffort(store, minionRefs);
                return new HordeDecision(false, "minionNpcMissing", roleName, spawnDistance, firstSpawnPosition, java.util.List.of(), 0);
            }

            UUIDComponent minionUuidComponent = store.getComponent(minionRef, UUIDComponent.getComponentType());
            UUID minionUuid = minionUuidComponent != null ? minionUuidComponent.getUuid() : null;
            if (minionUuid == null) {
                try {
                    store.removeEntity(minionRef, RemoveReason.REMOVE);
                } catch (Throwable ignored) {
                    // Best effort.
                }
                cleanupSpawnedMinionsBestEffort(store, minionRefs);
                return new HordeDecision(false, "minionUuidMissing", roleName, spawnDistance, firstSpawnPosition, java.util.List.of(), 0);
            }

            minionRefs.add(minionRef);
            minionUuids.add(minionUuid);

            try {
                var role = minionNpc.getRole();
                if (role != null && role.getWorldSupport() != null) {
                    role.getWorldSupport().overrideAttitude(playerEntityRef, Attitude.FRIENDLY, (double) Math.max(0, config.hordeBook.ownerFriendlySeconds));
                }
            } catch (Throwable t) {
                errors.report(playerRef, "HordeBook: failed to override minion attitude.", t);
            }
        }

        // Ensure the summoned brutes don't immediately turn on each other.
        for (Ref<EntityStore> a : minionRefs) {
            if (a == null || !a.isValid()) {
                continue;
            }
            NPCEntity npcA = store.getComponent(a, NPCEntity.getComponentType());
            if (npcA == null || npcA.getRole() == null || npcA.getRole().getWorldSupport() == null) {
                continue;
            }
            for (Ref<EntityStore> b : minionRefs) {
                if (b == null || !b.isValid() || a.equals(b)) {
                    continue;
                }
                try {
                    npcA.getRole().getWorldSupport().overrideAttitude(b, Attitude.FRIENDLY, (double) Math.max(0, config.hordeBook.ownerFriendlySeconds));
                } catch (Throwable ignored) {
                    // Best effort.
                }
            }
        }

        long lifetimeSeconds = Math.max(0L, Math.min(600L, (long) config.hordeBook.minionLifetimeSeconds));
        long lifetimeNanos = lifetimeSeconds * 1_000_000_000L;
        long expiresAtNanos = nowNanos + lifetimeNanos;
        hordeSummonState.activate(uuid, minionUuids, snapshot.interactionType, snapshot.chainId, nowNanos, lifetimeNanos);

        float newMana = Math.max(mana.min, mana.current - manaCost);
        stats.setStatValue(mana.index, newMana);
        stats.update();

        return new HordeDecision(true, "castApplied", roleName, spawnDistance, firstSpawnPosition, minionUuids, expiresAtNanos);
    }

    private @Nonnull DoomDecision tryCastDoomBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new DoomDecision(false, "noPlayerUuid", DOOM_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new DoomDecision(false, "interactionTypeNotSupported", DOOM_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedDoomSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new DoomDecision(false, "dedupe.secondary.chainId", DOOM_PROJECTILE_ASSET_ID, null, null, null);
            }
        } else {
            Integer lastChainId = lastProcessedDoomUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new DoomDecision(false, "dedupe.use.chainId", DOOM_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        boolean clientSaysBook = isDoomBookInSnapshot(snapshot);
        boolean serverSaysBook = DOOM_BOOK_ITEM_ID.equals(serverItemInHand)
            || DOOM_BOOK_ITEM_ID.equals(serverUtilityItem)
            || DOOM_BOOK_ITEM_ID.equals(serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new DoomDecision(false, "notHoldingBook", DOOM_PROJECTILE_ASSET_ID, null, null, null);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedDoomSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedDoomUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastDoomCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new DoomDecision(false, debounce.reason, DOOM_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        if (!mana.present) {
            return new DoomDecision(false, "manaStatMissing", DOOM_PROJECTILE_ASSET_ID, null, null, null);
        }

        int manaCost = getDoomBookManaCost();
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new DoomDecision(false, "manaTooLow", DOOM_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (stats == null) {
            return new DoomDecision(false, "entityStatMapMissing", DOOM_PROJECTILE_ASSET_ID, null, null, null);
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return new DoomDecision(false, "timeResourceMissing", DOOM_PROJECTILE_ASSET_ID, null, null, null);
        }

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new DoomDecision(false, "lookTransformMissing", DOOM_PROJECTILE_ASSET_ID, null, null, null);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new DoomDecision(false, "originNotFinite", DOOM_PROJECTILE_ASSET_ID, origin, null, null);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new DoomDecision(false, "directionInvalid", DOOM_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Vector3f rotation = look.getRotation();
        if (rotation == null || !rotation.isFinite()) {
            rotation = Vector3f.ZERO;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, DOOM_PROJECTILE_ASSET_ID, origin, rotation);
        if (holder == null) {
            return new DoomDecision(false, "projectileAssembleFailed", DOOM_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return new DoomDecision(false, "projectileComponentMissing", DOOM_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        if (!projectile.initialize()) {
            return new DoomDecision(false, "projectileAssetNotFound", DOOM_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        try {
            projectile.shoot(holder, uuid, origin.x, origin.y, origin.z, rotation.getYaw(), rotation.getPitch());
        } catch (Throwable t) {
            errors.report(playerRef, "DoomBook: projectile.shoot failed (assetId=" + DOOM_PROJECTILE_ASSET_ID + ").", t);
            return new DoomDecision(false, "projectileShootException", DOOM_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Ref<EntityStore> projectileRef;
        try {
            projectileRef = store.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            errors.report(playerRef, "DoomBook: store.addEntity failed (assetId=" + DOOM_PROJECTILE_ASSET_ID + ").", t);
            return new DoomDecision(false, "projectileSpawnException", DOOM_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        UUID projectileUuid = null;
        try {
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort debug info.
        }

        float newMana = Math.max(mana.min, mana.current - manaCost);
        stats.setStatValue(mana.index, newMana);
        stats.update();

        return new DoomDecision(true, "castApplied", DOOM_PROJECTILE_ASSET_ID, origin, direction, projectileUuid);
    }

    private @Nonnull FlameDecision tryCastFlameBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new FlameDecision(false, "noPlayerUuid", FLAME_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new FlameDecision(false, "interactionTypeNotSupported", FLAME_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedFlameSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new FlameDecision(false, "dedupe.secondary.chainId", FLAME_PROJECTILE_ASSET_ID, null, null, null);
            }
        } else {
            Integer lastChainId = lastProcessedFlameUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new FlameDecision(false, "dedupe.use.chainId", FLAME_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        boolean clientSaysBook = isFlameBookInSnapshot(snapshot);
        boolean serverSaysBook = FLAME_BOOK_ITEM_ID.equals(serverItemInHand)
            || FLAME_BOOK_ITEM_ID.equals(serverUtilityItem)
            || FLAME_BOOK_ITEM_ID.equals(serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new FlameDecision(false, "notHoldingBook", FLAME_PROJECTILE_ASSET_ID, null, null, null);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedFlameSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedFlameUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastFlameCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new FlameDecision(false, debounce.reason, FLAME_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        if (!mana.present) {
            return new FlameDecision(false, "manaStatMissing", FLAME_PROJECTILE_ASSET_ID, null, null, null);
        }

        int manaCost = getFlameBookManaCost();
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new FlameDecision(false, "manaTooLow", FLAME_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (stats == null) {
            return new FlameDecision(false, "entityStatMapMissing", FLAME_PROJECTILE_ASSET_ID, null, null, null);
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return new FlameDecision(false, "timeResourceMissing", FLAME_PROJECTILE_ASSET_ID, null, null, null);
        }

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new FlameDecision(false, "lookTransformMissing", FLAME_PROJECTILE_ASSET_ID, null, null, null);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new FlameDecision(false, "originNotFinite", FLAME_PROJECTILE_ASSET_ID, origin, null, null);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new FlameDecision(false, "directionInvalid", FLAME_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Vector3f rotation = look.getRotation();
        if (rotation == null || !rotation.isFinite()) {
            rotation = Vector3f.ZERO;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, FLAME_PROJECTILE_ASSET_ID, origin, rotation);
        if (holder == null) {
            return new FlameDecision(false, "projectileAssembleFailed", FLAME_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return new FlameDecision(false, "projectileComponentMissing", FLAME_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        if (!projectile.initialize()) {
            return new FlameDecision(false, "projectileAssetNotFound", FLAME_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        try {
            projectile.shoot(holder, uuid, origin.x, origin.y, origin.z, rotation.getYaw(), rotation.getPitch());
        } catch (Throwable t) {
            errors.report(playerRef, "FlameBook: projectile.shoot failed (assetId=" + FLAME_PROJECTILE_ASSET_ID + ").", t);
            return new FlameDecision(false, "projectileShootException", FLAME_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Ref<EntityStore> projectileRef;
        try {
            projectileRef = store.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            errors.report(playerRef, "FlameBook: store.addEntity failed (assetId=" + FLAME_PROJECTILE_ASSET_ID + ").", t);
            return new FlameDecision(false, "projectileSpawnException", FLAME_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        UUID projectileUuid = null;
        try {
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort debug info.
        }

        float newMana = Math.max(mana.min, mana.current - manaCost);
        stats.setStatValue(mana.index, newMana);
        stats.update();

        return new FlameDecision(true, "castApplied", FLAME_PROJECTILE_ASSET_ID, origin, direction, projectileUuid);
    }

    private @Nonnull LightDecision tryCastLightBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new LightDecision(false, "noPlayerUuid", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new LightDecision(false, "interactionTypeNotSupported", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedLightSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new LightDecision(false, "dedupe.secondary.chainId", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
            }
        } else {
            Integer lastChainId = lastProcessedLightUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new LightDecision(false, "dedupe.use.chainId", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
            }
        }

        boolean clientSaysBook = isLightBookInSnapshot(snapshot);
        boolean serverSaysBook = LIGHT_BOOK_ITEM_ID.equals(serverItemInHand)
            || LIGHT_BOOK_ITEM_ID.equals(serverUtilityItem)
            || LIGHT_BOOK_ITEM_ID.equals(serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new LightDecision(false, "notHoldingBook", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedLightSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedLightUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastLightCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new LightDecision(false, debounce.reason, LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
            }
        }

        if (!mana.present) {
            return new LightDecision(false, "manaStatMissing", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
        }

        int manaCost = getLightBookManaCost();
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new LightDecision(false, "manaTooLow", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
        }

        if (stats == null) {
            return new LightDecision(false, "entityStatMapMissing", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return new LightDecision(false, "timeResourceMissing", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
        }

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new LightDecision(false, "lookTransformMissing", LIGHT_PROJECTILE_ASSET_ID, null, null, null, false);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new LightDecision(false, "originNotFinite", LIGHT_PROJECTILE_ASSET_ID, origin, null, null, false);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new LightDecision(false, "directionInvalid", LIGHT_PROJECTILE_ASSET_ID, origin, direction, null, false);
        }

        Vector3f rotation = look.getRotation();
        if (rotation == null || !rotation.isFinite()) {
            rotation = Vector3f.ZERO;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, LIGHT_PROJECTILE_ASSET_ID, origin, rotation);
        if (holder == null) {
            return new LightDecision(false, "projectileAssembleFailed", LIGHT_PROJECTILE_ASSET_ID, origin, direction, null, false);
        }

        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return new LightDecision(false, "projectileComponentMissing", LIGHT_PROJECTILE_ASSET_ID, origin, direction, null, false);
        }

        if (!projectile.initialize()) {
            return new LightDecision(false, "projectileAssetNotFound", LIGHT_PROJECTILE_ASSET_ID, origin, direction, null, false);
        }

        try {
            projectile.shoot(holder, uuid, origin.x, origin.y, origin.z, rotation.getYaw(), rotation.getPitch());
        } catch (Throwable t) {
            errors.report(playerRef, "LightBook: projectile.shoot failed (assetId=" + LIGHT_PROJECTILE_ASSET_ID + ").", t);
            return new LightDecision(false, "projectileShootException", LIGHT_PROJECTILE_ASSET_ID, origin, direction, null, false);
        }

        Ref<EntityStore> projectileRef;
        try {
            projectileRef = store.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            errors.report(playerRef, "LightBook: store.addEntity failed (assetId=" + LIGHT_PROJECTILE_ASSET_ID + ").", t);
            return new LightDecision(false, "projectileSpawnException", LIGHT_PROJECTILE_ASSET_ID, origin, direction, null, false);
        }

        UUID projectileUuid = null;
        try {
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort debug info.
        }

        boolean dynamicLightApplied = false;
        try {
            store.putComponent(projectileRef, DynamicLight.getComponentType(), LightBookProjectileSystem.createDynamicLight(config));
            dynamicLightApplied = true;
        } catch (Throwable t) {
            errors.report(playerRef, "LightBook: failed to attach DynamicLight to projectile.", t);
        }

        if (projectileUuid != null) {
            lightBookProjectileState.register(projectileUuid, origin, direction);
            debug.traceFileOnly(
                playerRef,
                "LightBookProjectile event=spawn"
                    + " projectile.uuid=" + projectileUuid
                    + " dynamicLightApplied=" + dynamicLightApplied
                    + " origin=" + Vector3d.formatShortString(origin)
                    + " direction=" + Vector3d.formatShortString(direction)
                    + " maxDistanceBlocks=" + (config != null && config.lightBook != null ? config.lightBook.maxDistanceBlocks : 100.0)
                    + " lifetimeSeconds=120"
            );
        }

        float newMana = Math.max(mana.min, mana.current - manaCost);
        stats.setStatValue(mana.index, newMana);
        stats.update();

        return new LightDecision(true, "castApplied", LIGHT_PROJECTILE_ASSET_ID, origin, direction, projectileUuid, dynamicLightApplied);
    }

    private @Nonnull FrostDecision tryCastFrostBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new FrostDecision(false, "noPlayerUuid", FROST_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new FrostDecision(false, "interactionTypeNotSupported", FROST_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedFrostSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new FrostDecision(false, "dedupe.secondary.chainId", FROST_PROJECTILE_ASSET_ID, null, null, null);
            }
        } else {
            Integer lastChainId = lastProcessedFrostUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new FrostDecision(false, "dedupe.use.chainId", FROST_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        boolean clientSaysBook = isFrostBookInSnapshot(snapshot);
        boolean serverSaysBook = FROST_BOOK_ITEM_ID.equals(serverItemInHand)
            || FROST_BOOK_ITEM_ID.equals(serverUtilityItem)
            || FROST_BOOK_ITEM_ID.equals(serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new FrostDecision(false, "notHoldingBook", FROST_PROJECTILE_ASSET_ID, null, null, null);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedFrostSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedFrostUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastFrostCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new FrostDecision(false, debounce.reason, FROST_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        if (!mana.present) {
            return new FrostDecision(false, "manaStatMissing", FROST_PROJECTILE_ASSET_ID, null, null, null);
        }

        int manaCost = getFrostBookManaCost();
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new FrostDecision(false, "manaTooLow", FROST_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (stats == null) {
            return new FrostDecision(false, "entityStatMapMissing", FROST_PROJECTILE_ASSET_ID, null, null, null);
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return new FrostDecision(false, "timeResourceMissing", FROST_PROJECTILE_ASSET_ID, null, null, null);
        }

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new FrostDecision(false, "lookTransformMissing", FROST_PROJECTILE_ASSET_ID, null, null, null);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new FrostDecision(false, "originNotFinite", FROST_PROJECTILE_ASSET_ID, origin, null, null);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new FrostDecision(false, "directionInvalid", FROST_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Vector3f rotation = look.getRotation();
        if (rotation == null || !rotation.isFinite()) {
            rotation = Vector3f.ZERO;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, FROST_PROJECTILE_ASSET_ID, origin, rotation);
        if (holder == null) {
            return new FrostDecision(false, "projectileAssembleFailed", FROST_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return new FrostDecision(false, "projectileComponentMissing", FROST_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        if (!projectile.initialize()) {
            return new FrostDecision(false, "projectileAssetNotFound", FROST_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        try {
            projectile.shoot(holder, uuid, origin.x, origin.y, origin.z, rotation.getYaw(), rotation.getPitch());
        } catch (Throwable t) {
            errors.report(playerRef, "FrostBook: projectile.shoot failed (assetId=" + FROST_PROJECTILE_ASSET_ID + ").", t);
            return new FrostDecision(false, "projectileShootException", FROST_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Ref<EntityStore> projectileRef;
        try {
            projectileRef = store.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            errors.report(playerRef, "FrostBook: store.addEntity failed (assetId=" + FROST_PROJECTILE_ASSET_ID + ").", t);
            return new FrostDecision(false, "projectileSpawnException", FROST_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        UUID projectileUuid = null;
        try {
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort debug info.
        }

        float newMana = Math.max(mana.min, mana.current - manaCost);
        stats.setStatValue(mana.index, newMana);
        stats.update();

        return new FrostDecision(true, "castApplied", FROST_PROJECTILE_ASSET_ID, origin, direction, projectileUuid);
    }

    private @Nonnull MorphDecision tryCastMorphBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new MorphDecision(false, "noPlayerUuid", MORPH_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new MorphDecision(false, "interactionTypeNotSupported", MORPH_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedMorphSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new MorphDecision(false, "dedupe.secondary.chainId", MORPH_PROJECTILE_ASSET_ID, null, null, null);
            }
        } else {
            Integer lastChainId = lastProcessedMorphUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new MorphDecision(false, "dedupe.use.chainId", MORPH_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        boolean clientSaysBook = isMorphBookInSnapshot(snapshot);
        boolean serverSaysBook = MORPH_BOOK_ITEM_ID.equals(serverItemInHand)
            || MORPH_BOOK_ITEM_ID.equals(serverUtilityItem)
            || MORPH_BOOK_ITEM_ID.equals(serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new MorphDecision(false, "notHoldingBook", MORPH_PROJECTILE_ASSET_ID, null, null, null);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedMorphSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedMorphUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastMorphCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new MorphDecision(false, debounce.reason, MORPH_PROJECTILE_ASSET_ID, null, null, null);
            }
        }

        if (!mana.present) {
            return new MorphDecision(false, "manaStatMissing", MORPH_PROJECTILE_ASSET_ID, null, null, null);
        }

        int manaCost = getMorphBookManaCost();
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new MorphDecision(false, "manaTooLow", MORPH_PROJECTILE_ASSET_ID, null, null, null);
        }

        if (stats == null) {
            return new MorphDecision(false, "entityStatMapMissing", MORPH_PROJECTILE_ASSET_ID, null, null, null);
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return new MorphDecision(false, "timeResourceMissing", MORPH_PROJECTILE_ASSET_ID, null, null, null);
        }

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new MorphDecision(false, "lookTransformMissing", MORPH_PROJECTILE_ASSET_ID, null, null, null);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new MorphDecision(false, "originNotFinite", MORPH_PROJECTILE_ASSET_ID, origin, null, null);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new MorphDecision(false, "directionInvalid", MORPH_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Vector3f rotation = look.getRotation();
        if (rotation == null || !rotation.isFinite()) {
            rotation = Vector3f.ZERO;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, MORPH_PROJECTILE_ASSET_ID, origin, rotation);
        if (holder == null) {
            return new MorphDecision(false, "projectileAssembleFailed", MORPH_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return new MorphDecision(false, "projectileComponentMissing", MORPH_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        if (!projectile.initialize()) {
            return new MorphDecision(false, "projectileAssetNotFound", MORPH_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        try {
            projectile.shoot(holder, uuid, origin.x, origin.y, origin.z, rotation.getYaw(), rotation.getPitch());
        } catch (Throwable t) {
            errors.report(playerRef, "MorphBook: projectile.shoot failed (assetId=" + MORPH_PROJECTILE_ASSET_ID + ").", t);
            return new MorphDecision(false, "projectileShootException", MORPH_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        Ref<EntityStore> projectileRef;
        try {
            projectileRef = store.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            errors.report(playerRef, "MorphBook: store.addEntity failed (assetId=" + MORPH_PROJECTILE_ASSET_ID + ").", t);
            return new MorphDecision(false, "projectileSpawnException", MORPH_PROJECTILE_ASSET_ID, origin, direction, null);
        }

        UUID projectileUuid = null;
        try {
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort debug info.
        }

        float newMana = Math.max(mana.min, mana.current - manaCost);
        stats.setStatValue(mana.index, newMana);
        stats.update();

        return new MorphDecision(true, "castApplied", MORPH_PROJECTILE_ASSET_ID, origin, direction, projectileUuid);
    }

    private static void cleanupSpawnedMinionsBestEffort(
        @Nonnull Store<EntityStore> store,
        @Nonnull java.util.List<Ref<EntityStore>> spawnedMinionRefs
    ) {
        for (Ref<EntityStore> ref : spawnedMinionRefs) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            try {
                store.removeEntity(ref, RemoveReason.REMOVE);
            } catch (Throwable ignored) {
                // Best effort cleanup.
            }
        }
    }

    private record TeleportDecision(
        boolean allow,
        @Nonnull String reason,
        @Nullable BlockCollisionData rayHit,
        @Nullable Vector3d destination,
        double rayMaxDistance,
        double hitDistanceBlocks
    ) {}

    private record HordeDecision(
        boolean allow,
        @Nonnull String reason,
        @Nonnull String roleName,
        double spawnDistanceBlocks,
        @Nullable Vector3d spawnPosition,
        @Nonnull java.util.List<UUID> minionUuids,
        long minionExpiresAtNanos
    ) {}

    private record DoomDecision(
        boolean allow,
        @Nonnull String reason,
        @Nonnull String projectileAssetName,
        @Nullable Vector3d origin,
        @Nullable Vector3d direction,
        @Nullable UUID projectileUuid
    ) {}

    private record FlameDecision(
        boolean allow,
        @Nonnull String reason,
        @Nonnull String projectileAssetName,
        @Nullable Vector3d origin,
        @Nullable Vector3d direction,
        @Nullable UUID projectileUuid
    ) {}

    private record LightDecision(
        boolean allow,
        @Nonnull String reason,
        @Nonnull String projectileAssetName,
        @Nullable Vector3d origin,
        @Nullable Vector3d direction,
        @Nullable UUID projectileUuid,
        boolean dynamicLightApplied
    ) {}

    private record FrostDecision(
        boolean allow,
        @Nonnull String reason,
        @Nonnull String projectileAssetName,
        @Nullable Vector3d origin,
        @Nullable Vector3d direction,
        @Nullable UUID projectileUuid
    ) {}

    private record HealingProjectileDecision(
        boolean allow,
        @Nonnull String reason,
        @Nonnull String projectileAssetName,
        @Nullable Vector3d origin,
        @Nullable Vector3d direction,
        @Nullable UUID projectileUuid
    ) {}

    private record MorphDecision(
        boolean allow,
        @Nonnull String reason,
        @Nonnull String projectileAssetName,
        @Nullable Vector3d origin,
        @Nullable Vector3d direction,
        @Nullable UUID projectileUuid
    ) {}

    private record MorphResetDecision(
        boolean allow,
        @Nonnull String reason,
        @Nullable String baselineModelAssetId,
        @Nullable String currentModelAssetId,
        boolean applied,
        boolean baselineSkinPresent,
        boolean skinApplied,
        @Nonnull String skinReason
    ) {}

    private record AncientSwordDecision(
        boolean allow,
        @Nonnull String reason,
        @Nonnull String projectileAssetName,
        @Nullable Vector3d origin,
        @Nullable Vector3d direction,
        @Nullable UUID projectileUuid
    ) {}

    private record MiningDecision(
        boolean allow,
        @Nonnull String reason,
        @Nullable BlockCollisionData rayHit,
        double rayMaxDistance,
        double hitDistanceBlocks,
        double chargeSeconds,
        int requestedTunnelBlocks,
        @Nonnull String miningShape,
        int miningShapeBlocksPerDepth,
        @Nullable String faceAxis,
        @Nullable String targetBlockTypeId,
        int blocksConsidered,
        int blocksBroken,
        int blocksSkippedChunkNotLoaded,
        int blocksSkippedUnbreakable,
        int blocksBreakFailed,
        int blocksBreakExceptions,
        int dropEntitiesSpawned,
        int dropItemsTotal,
        int dropsSkippedNoDropData,
        int dropsSkippedMissingDropList,
        int dropSpawnExceptions
    ) {}

    private static final class MiningAccumulator {
        private int blocksConsidered;
        private int blocksBroken;
        private int blocksSkippedChunkNotLoaded;
        private int blocksSkippedUnbreakable;
        private int blocksBreakFailed;
        private int blocksBreakExceptions;
        private int dropEntitiesSpawned;
        private int dropItemsTotal;
        private int dropsSkippedNoDropData;
        private int dropsSkippedMissingDropList;
        private int dropSpawnExceptions;
    }

    private record ImmunityDecision(
        boolean allow,
        @Nonnull String reason,
        long expiresAtNanos,
        int effectIndex,
        boolean effectApplied,
        @Nonnull String effectReason
    ) {}

    private record TauntDecision(
        boolean allow,
        @Nonnull String reason,
        @Nullable Vector3d launchVelocity,
        double launchVelocityY,
        @Nonnull String launchSource,
        long fallImmunityExpiresAtNanos,
        int stackCount,
        int slamDamage,
        int damageRadiusBlocks,
        int groundBreakRadiusBlocks
    ) {}

    private boolean isAncientSwordEnabled() {
        AxoTalesServerConfig.AncientSword ancientSword = config != null ? config.ancientSword : null;
        return ancientSword == null || ancientSword.enabled;
    }

    private int getAncientSwordManaCost() {
        AxoTalesServerConfig.AncientSword ancientSword = config != null ? config.ancientSword : null;
        if (ancientSword == null) {
            return 20;
        }
        return Math.max(0, ancientSword.manaCost);
    }

    private double getAncientSwordCooldownSeconds() {
        AxoTalesServerConfig.AncientSword ancientSword = config != null ? config.ancientSword : null;
        if (ancientSword == null) {
            return 1.25;
        }
        double seconds = ancientSword.cooldownSeconds;
        if (!Double.isFinite(seconds)) {
            return 1.25;
        }
        return Math.max(0.0, seconds);
    }

    private long getAncientSwordCooldownNanos() {
        return secondsToNanosClamped(getAncientSwordCooldownSeconds());
    }

    private double getAncientSwordCastDelaySeconds() {
        AxoTalesServerConfig.AncientSword ancientSword = config != null ? config.ancientSword : null;
        if (ancientSword == null) {
            return 0.16;
        }
        double seconds = ancientSword.castDelaySeconds;
        if (!Double.isFinite(seconds)) {
            return 0.16;
        }
        return Math.max(0.0, seconds);
    }

    private long getAncientSwordCastDelayNanos() {
        return secondsToNanosClamped(getAncientSwordCastDelaySeconds());
    }

    private @Nonnull String getAncientSwordProjectileId() {
        AxoTalesServerConfig.AncientSword ancientSword = config != null ? config.ancientSword : null;
        String id = ancientSword != null ? ancientSword.projectileId : null;
        return id != null && !id.isBlank() ? id : ANCIENT_SWORD_PROJECTILE_DEFAULT_ASSET_ID;
    }

    private int getDoomBookManaCost() {
        AxoTalesServerConfig.DoomBook doomBook = config != null ? config.doomBook : null;
        if (doomBook == null) {
            return 15;
        }
        return Math.max(0, doomBook.manaCost);
    }

    private int getFrostBookManaCost() {
        AxoTalesServerConfig.FrostBook frostBook = config != null ? config.frostBook : null;
        if (frostBook == null) {
            return 20;
        }
        return Math.max(0, frostBook.manaCost);
    }

    private int getFlameBookManaCost() {
        AxoTalesServerConfig.FlameBook flameBook = config != null ? config.flameBook : null;
        if (flameBook == null) {
            return 20;
        }
        return Math.max(0, flameBook.manaCost);
    }

    private int getLightBookManaCost() {
        AxoTalesServerConfig.LightBook lightBook = config != null ? config.lightBook : null;
        if (lightBook == null) {
            return 15;
        }
        return Math.max(0, lightBook.manaCost);
    }

    private int getMorphBookManaCost() {
        AxoTalesServerConfig.MorphBook morphBook = config != null ? config.morphBook : null;
        if (morphBook == null) {
            return 25;
        }
        return Math.max(0, morphBook.manaCost);
    }

    private int getImmunityBookManaCost() {
        AxoTalesServerConfig.ImmunityBook immunityBook = config != null ? config.immunityBook : null;
        if (immunityBook == null) {
            return 15;
        }
        return Math.max(0, immunityBook.manaCost);
    }

    private int getImmunityBookImmunitySeconds() {
        AxoTalesServerConfig.ImmunityBook immunityBook = config != null ? config.immunityBook : null;
        if (immunityBook == null) {
            return 3;
        }
        return Math.max(0, immunityBook.immunitySeconds);
    }

    private int resolveImmuneEffectIndex() {
        int cached = immuneEffectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(IMMUNE_EFFECT_ID, -1);
        if (resolved >= 0) {
            immuneEffectIndex = resolved;
        }
        return resolved;
    }

    private @Nonnull ImmunityDecision tryCastImmunityBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        int effectIndex = -1;
        boolean effectApplied = false;
        String effectReason = "skip";

        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new ImmunityDecision(false, "noPlayerUuid", 0L, effectIndex, effectApplied, effectReason);
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new ImmunityDecision(false, "interactionTypeNotSupported", 0L, effectIndex, effectApplied, effectReason);
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedImmunitySecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new ImmunityDecision(false, "dedupe.secondary.chainId", 0L, effectIndex, effectApplied, effectReason);
            }
        } else {
            Integer lastChainId = lastProcessedImmunityUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new ImmunityDecision(false, "dedupe.use.chainId", 0L, effectIndex, effectApplied, effectReason);
            }
        }

        boolean clientSaysBook = isImmunityBookInSnapshot(snapshot);
        boolean serverSaysBook = isImmunityBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new ImmunityDecision(false, "notHoldingBook", 0L, effectIndex, effectApplied, effectReason);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents spam / repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedImmunitySecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedImmunityUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastImmunityCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new ImmunityDecision(false, debounce.reason, 0L, effectIndex, effectApplied, effectReason);
            }
        }

        if (!mana.present) {
            return new ImmunityDecision(false, "manaStatMissing", 0L, effectIndex, effectApplied, effectReason);
        }

        int manaCost = getImmunityBookManaCost();
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new ImmunityDecision(false, "manaTooLow", 0L, effectIndex, effectApplied, effectReason);
        }

        int immunitySeconds = getImmunityBookImmunitySeconds();
        long durationNanos = (long) immunitySeconds * 1_000_000_000L;
        immunityState.activate(uuid, snapshot.interactionType, snapshot.chainId, nowNanos, durationNanos);

        // Consume mana on successful cast.
        if (stats != null) {
            float newMana = Math.max(mana.min, mana.current - manaCost);
            stats.setStatValue(mana.index, newMana);
            stats.update();
        }

        if (immunitySeconds <= 0) {
            effectReason = "durationZero";
        } else {
            effectIndex = resolveImmuneEffectIndex();
            if (effectIndex < 0) {
                effectReason = "effectNotFound";
            } else {
                EntityEffect immuneEffect = EntityEffect.getAssetMap().getAsset(effectIndex);
                if (immuneEffect == null) {
                    effectReason = "effectNull";
                } else {
                    try {
                        EffectControllerComponent effects = store.ensureAndGetComponent(playerEntityRef, EffectControllerComponent.getComponentType());
                        if (effects == null) {
                            effectReason = "effectControllerMissing";
                        } else {
                            effectApplied = effects.addEffect(
                                playerEntityRef,
                                effectIndex,
                                immuneEffect,
                                (float) immunitySeconds,
                                OverlapBehavior.OVERWRITE,
                                store
                            );
                            effectReason = effectApplied ? "applied" : "addEffectFalse";
                        }
                    } catch (Throwable t) {
                        errors.report(playerRef, "ImmunityBook: addEffect failed (effectId=" + IMMUNE_EFFECT_ID + ").", t);
                        effectApplied = false;
                        effectReason = "addEffectException";
                    }
                }
            }
        }

        return new ImmunityDecision(true, "castApplied", nowNanos + durationNanos, effectIndex, effectApplied, effectReason);
    }

    private @Nonnull AncientSwordDecision tryCastAncientSwordSecondary(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        String projectileAssetId = getAncientSwordProjectileId();

        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new AncientSwordDecision(false, "noPlayerUuid", projectileAssetId, null, null, null);
        }

        if (!isAncientSwordEnabled()) {
            return new AncientSwordDecision(false, "disabled", projectileAssetId, null, null, null);
        }

        if (snapshot.interactionType != InteractionType.Secondary) {
            return new AncientSwordDecision(false, "interactionTypeNotSupported", projectileAssetId, null, null, null);
        }

        Integer lastChainId = lastProcessedAncientSwordSecondaryChainId.get(uuid);
        if (lastChainId != null && lastChainId == snapshot.chainId) {
            return new AncientSwordDecision(false, "dedupe.secondary.chainId", projectileAssetId, null, null, null);
        }

        boolean clientSaysSword = isAncientSwordInSnapshot(snapshot);
        boolean serverSaysSword = ANCIENT_SWORD_ITEM_ID.equals(serverItemInHand);
        if (!clientSaysSword && !serverSaysSword) {
            return new AncientSwordDecision(false, "notHoldingSword", projectileAssetId, null, null, null);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents spam / repeated gating logs).
        lastProcessedAncientSwordSecondaryChainId.put(uuid, snapshot.chainId);

        long cooldownNanos = getAncientSwordCooldownNanos();
        if (cooldownNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastAncientSwordCastAttemptAtNanos, uuid, nowNanos, cooldownNanos);
            if (!debounce.allow) {
                return new AncientSwordDecision(false, debounce.reason, projectileAssetId, null, null, null);
            }
        }

        if (!mana.present) {
            return new AncientSwordDecision(false, "manaStatMissing", projectileAssetId, null, null, null);
        }

        int manaCost = getAncientSwordManaCost();
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new AncientSwordDecision(false, "manaTooLow", projectileAssetId, null, null, null);
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return new AncientSwordDecision(false, "timeResourceMissing", projectileAssetId, null, null, null);
        }

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new AncientSwordDecision(false, "lookTransformMissing", projectileAssetId, null, null, null);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new AncientSwordDecision(false, "originNotFinite", projectileAssetId, origin, null, null);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new AncientSwordDecision(false, "directionInvalid", projectileAssetId, origin, direction, null);
        }

        Vector3f rotation = look.getRotation();
        if (rotation == null || !rotation.isFinite()) {
            rotation = Vector3f.ZERO;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, projectileAssetId, origin, rotation);
        if (holder == null) {
            return new AncientSwordDecision(false, "projectileAssembleFailed", projectileAssetId, origin, direction, null);
        }

        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return new AncientSwordDecision(false, "projectileComponentMissing", projectileAssetId, origin, direction, null);
        }

        if (!projectile.initialize()) {
            return new AncientSwordDecision(false, "projectileAssetNotFound", projectileAssetId, origin, direction, null);
        }

        try {
            projectile.shoot(holder, uuid, origin.x, origin.y, origin.z, rotation.getYaw(), rotation.getPitch());
        } catch (Throwable t) {
            errors.report(playerRef, "AncientSword: projectile.shoot failed (assetId=" + projectileAssetId + ").", t);
            return new AncientSwordDecision(false, "projectileShootException", projectileAssetId, origin, direction, null);
        }

        Ref<EntityStore> projectileRef;
        try {
            projectileRef = store.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            errors.report(playerRef, "AncientSword: store.addEntity failed (assetId=" + projectileAssetId + ").", t);
            return new AncientSwordDecision(false, "projectileSpawnException", projectileAssetId, origin, direction, null);
        }

        UUID projectileUuid = null;
        try {
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }
        } catch (Throwable ignored) {
            // Best effort debug info.
        }

        // Consume mana on successful cast.
        if (stats != null) {
            float newMana = Math.max(mana.min, mana.current - manaCost);
            stats.setStatValue(mana.index, newMana);
            stats.update();
        }

        return new AncientSwordDecision(true, "castApplied", projectileAssetId, origin, direction, projectileUuid);
    }

    private @Nonnull TauntDecision tryCastTauntBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Player playerComponent,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new TauntDecision(false, "noPlayerUuid", null, 0.0, "none", 0L, 0, 0, 0, 0);
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new TauntDecision(false, "interactionTypeNotSupported", null, 0.0, "none", 0L, 0, 0, 0, 0);
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedTauntSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new TauntDecision(false, "dedupe.secondary.chainId", null, 0.0, "none", 0L, 0, 0, 0, 0);
            }
        } else {
            Integer lastChainId = lastProcessedTauntUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new TauntDecision(false, "dedupe.use.chainId", null, 0.0, "none", 0L, 0, 0, 0, 0);
            }
        }

        boolean clientSaysBook = isTauntBookInSnapshot(snapshot);
        boolean serverSaysBook = isTauntBookInServerSlots(serverItemInHand, serverUtilityItem, serverToolsItem);
        if (!clientSaysBook && !serverSaysBook) {
            return new TauntDecision(false, "notHoldingBook", null, 0.0, "none", 0L, 0, 0, 0, 0);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents spam / repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedTauntSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedTauntUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastTauntCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new TauntDecision(false, debounce.reason, null, 0.0, "none", 0L, 0, 0, 0, 0);
            }
        }

        if (!mana.present) {
            return new TauntDecision(false, "manaStatMissing", null, 0.0, "none", 0L, 0, 0, 0, 0);
        }

        int manaCost = Math.max(0, config.tauntBook.manaCost);
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new TauntDecision(false, "manaTooLow", null, 0.0, "none", 0L, 0, 0, 0, 0);
        }

        var external = store.getExternalData();
        if (external == null || external.getWorld() == null) {
            return new TauntDecision(false, "worldMissing", null, 0.0, "none", 0L, 0, 0, 0, 0);
        }

        Transform currentTransform = playerRef.getTransform();
        if (currentTransform == null) {
            Transform look = TargetUtil.getLook(playerEntityRef, store);
            currentTransform = look != null ? look : null;
        }

        if (currentTransform == null || currentTransform.getPosition() == null || !currentTransform.getPosition().isFinite()) {
            return new TauntDecision(false, "transformMissingOrInvalid", null, 0.0, "none", 0L, 0, 0, 0, 0);
        }

        int launchHeightBlocks = Math.max(1, config.tauntBook.launchHeightBlocks);

        MovementManager movementManager = store.getComponent(playerEntityRef, MovementManager.getComponentType());
        float jumpForce = Float.NaN;
        if (movementManager != null && movementManager.getSettings() != null) {
            jumpForce = movementManager.getSettings().jumpForce;
        }

        double launchVelocityY;
        String launchSource;
        // Approximate launch height by scaling from the player's configured jumpForce (assumes ~2-block base jump height).
        if (Float.isFinite(jumpForce) && jumpForce > 0f) {
            double baseJumpHeightBlocks = 2.0;
            double heightMultiplier = Math.sqrt(Math.max(0.0, (double) launchHeightBlocks) / baseJumpHeightBlocks);
            launchVelocityY = (double) jumpForce * heightMultiplier;
            launchSource = "scaledFromJumpForce";
        } else {
            // Fallback: treat launchHeightBlocks as a direct vertical velocity value.
            launchVelocityY = (double) launchHeightBlocks;
            launchSource = "fallbackHeightAsVelocity";
        }

        Velocity velocity = store.ensureAndGetComponent(playerEntityRef, Velocity.getComponentType());
        Vector3d currentVelocity = velocity != null ? velocity.getVelocity() : null;
        double vx = currentVelocity != null ? currentVelocity.x : 0.0;
        double vz = currentVelocity != null ? currentVelocity.z : 0.0;
        Vector3d launchVelocity = new Vector3d(vx, launchVelocityY, vz);
        if (!launchVelocity.isFinite()) {
            return new TauntDecision(false, "launchVelocityInvalid", null, 0.0, launchSource, 0L, 0, 0, 0, 0);
        }

        velocity.addInstruction(launchVelocity, null, ChangeVelocityType.Set);
        playerComponent.setCurrentFallDistance(0.0);

        // Consume mana on successful cast.
        if (stats != null) {
            float newMana = Math.max(mana.min, mana.current - manaCost);
            stats.setStatValue(mana.index, newMana);
            stats.update();
        }

        long durationNanos = (long) Math.max(0, config.tauntBook.fallImmunitySeconds) * 1_000_000_000L;
        TauntBookEffectState.ActiveTaunt activeTaunt = tauntState.activate(
            uuid,
            snapshot.interactionType,
            snapshot.chainId,
            nowNanos,
            durationNanos,
            Math.max(0, config.tauntBook.slamDamage)
        );

        debug.traceFileOnly(
            playerRef,
            "TauntBookLaunch event=Cast"
                + " interactionType=" + snapshot.interactionType
                + " chainId=" + snapshot.chainId
                + " taunt.stackCount=" + activeTaunt.stackCount
                + " slam.damage=" + activeTaunt.getEffectiveSlamDamage()
                + " slam.damage.cap=" + TauntBookEffectState.STACK_DAMAGE_CAP
                + " slam.damage.multiplier=" + TauntBookEffectState.STACK_DAMAGE_MULTIPLIER
                + " slam.breakRadiusBlocks=" + activeTaunt.getGroundBreakRadiusBlocks()
                + " launch.heightBlocks=" + launchHeightBlocks
                + " launch.source=" + launchSource
                + " launch.jumpForce=" + jumpForce
                + " launch.velocityY=" + String.format("%.3f", launchVelocityY)
                + " launch.velocity=" + Vector3d.formatShortString(launchVelocity)
        );

        return new TauntDecision(
            true,
            "castApplied",
            launchVelocity,
            launchVelocityY,
            launchSource,
            activeTaunt.expiresAtNanos,
            activeTaunt.stackCount,
            activeTaunt.getEffectiveSlamDamage(),
            Math.max(0, config.tauntBook.slamRadiusBlocks),
            activeTaunt.getGroundBreakRadiusBlocks()
        );
    }

    private void trackMiningChargeStart(
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionSnapshot snapshot,
        long nowNanos,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem,
        @Nonnull ManaSnapshot mana
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return;
        }

        activeMiningCharges.put(uuid, new MiningChargeState(snapshot.chainId, snapshot.interactionType, nowNanos));
        debug.traceFileOnly(
            playerRef,
            "MiningCharge event=start"
                + " interactionType=" + snapshot.interactionType
                + " chainId=" + snapshot.chainId
                + " initial=" + snapshot.initial
                + " state=" + snapshot.state
                + " usedItemSource=" + resolveItemSource(snapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                + " mana.index=" + mana.index
                + " mana.current=" + mana.current
                + " mana.min=" + mana.min
                + " mana.max=" + mana.max
                + " minChargeBlocks=" + config.miningBook.minChargeBlocks
                + " blocksPerChargeTier=" + config.miningBook.blocksPerChargeTier
                + " chargeTierSeconds=" + config.miningBook.chargeTierSeconds
                + " maxChargeSeconds=" + config.miningBook.maxChargeSeconds
                + " maxTunnelBlocks=" + config.miningBook.maxTunnelBlocks
                + " allow=true"
                + " reason=tracked"
        );
    }

    private @Nonnull MiningChargeResolution resolveMiningChargeRelease(
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionSnapshot snapshot,
        long nowNanos,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem,
        @Nonnull ManaSnapshot mana
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new MiningChargeResolution(false, "noPlayerUuid", snapshot.state, 0.0, resolveRequestedMiningTunnelBlocks(0.0), true);
        }

        MiningChargeState activeCharge = activeMiningCharges.remove(uuid);
        boolean usedFallbackStart = activeCharge == null
            || activeCharge.chainId != snapshot.chainId
            || activeCharge.interactionType != snapshot.interactionType;
        long startedAtNanos = !usedFallbackStart ? activeCharge.startedAtNanos : nowNanos;
        if (startedAtNanos > nowNanos) {
            startedAtNanos = nowNanos;
            usedFallbackStart = true;
        }

        double chargeSeconds = Math.max(0.0, (double) (nowNanos - startedAtNanos) / 1_000_000_000.0);
        int requestedTunnelBlocks = resolveRequestedMiningTunnelBlocks(chargeSeconds);
        boolean finished = snapshot.state == InteractionState.Finished;

        debug.traceFileOnly(
            playerRef,
            "MiningCharge event=release"
                + " interactionType=" + snapshot.interactionType
                + " chainId=" + snapshot.chainId
                + " initial=" + snapshot.initial
                + " state=" + snapshot.state
                + " usedItemSource=" + resolveItemSource(snapshot, serverItemInHand, serverUtilityItem, serverToolsItem)
                + " mana.index=" + mana.index
                + " mana.current=" + mana.current
                + " mana.min=" + mana.min
                + " mana.max=" + mana.max
                + " charge.seconds=" + String.format("%.3f", chargeSeconds)
                + " charge.requestedTunnelBlocks=" + requestedTunnelBlocks
                + " charge.usedFallbackStart=" + usedFallbackStart
                + " allow=" + finished
                + " reason=" + (finished ? "finished" : "terminalState." + snapshot.state)
        );

        if (!finished) {
            return new MiningChargeResolution(
                false,
                "terminalState." + snapshot.state,
                snapshot.state,
                chargeSeconds,
                requestedTunnelBlocks,
                usedFallbackStart
            );
        }

        return new MiningChargeResolution(true, "finished", snapshot.state, chargeSeconds, requestedTunnelBlocks, usedFallbackStart);
    }

    private int resolveRequestedMiningTunnelBlocks(double chargeSeconds) {
        int minChargeBlocks = Math.max(1, config.miningBook.minChargeBlocks);
        int blocksPerChargeTier = Math.max(1, config.miningBook.blocksPerChargeTier);
        double chargeTierSeconds = Double.isFinite(config.miningBook.chargeTierSeconds) && config.miningBook.chargeTierSeconds > 0
            ? config.miningBook.chargeTierSeconds
            : 1.0;
        double maxChargeSeconds = Double.isFinite(config.miningBook.maxChargeSeconds)
            ? Math.max(chargeTierSeconds, config.miningBook.maxChargeSeconds)
            : 5.0;
        int maxTunnelBlocks = Math.max(minChargeBlocks, config.miningBook.maxTunnelBlocks);

        double effectiveChargeSeconds = Math.max(0.0, Math.min(chargeSeconds, maxChargeSeconds));
        if (effectiveChargeSeconds + FLOAT_EPSILON < chargeTierSeconds) {
            return Math.min(maxTunnelBlocks, minChargeBlocks);
        }

        int fullChargeTiers = (int) Math.floor((effectiveChargeSeconds + FLOAT_EPSILON) / chargeTierSeconds);
        int requestedTunnelBlocks = Math.max(minChargeBlocks, fullChargeTiers * blocksPerChargeTier);
        return Math.min(maxTunnelBlocks, requestedTunnelBlocks);
    }

    private @Nonnull MiningShape resolveMiningShape(@Nullable UUID uuid) {
        if (uuid == null) {
            return MiningShape.ONE_BY_ONE;
        }
        return miningShapeByPlayer.getOrDefault(uuid, MiningShape.ONE_BY_ONE);
    }

    private @Nonnull MiningShapeChangeDecision cycleMiningBookShape(
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionSnapshot snapshot,
        long nowNanos,
        @Nullable String serverItemInHand
    ) {
        UUID uuid = playerRef.getUuid();
        MiningShape currentShape = resolveMiningShape(uuid);
        if (uuid == null) {
            return new MiningShapeChangeDecision(false, "noPlayerUuid", currentShape, currentShape);
        }

        if (snapshot.interactionType != InteractionType.Primary) {
            return new MiningShapeChangeDecision(false, "interactionTypeNotSupported", currentShape, currentShape);
        }

        Integer lastChainId = lastProcessedMiningPrimaryChainId.get(uuid);
        if (lastChainId != null && lastChainId == snapshot.chainId) {
            return new MiningShapeChangeDecision(false, "dedupe.primary.chainId", currentShape, currentShape);
        }

        boolean clientSaysBook = MINING_BOOK_ITEM_ID.equals(snapshot.itemInHandId);
        boolean serverSaysBook = MINING_BOOK_ITEM_ID.equals(serverItemInHand);
        if (!clientSaysBook && !serverSaysBook) {
            return new MiningShapeChangeDecision(false, "notHoldingBook", currentShape, currentShape);
        }

        lastProcessedMiningPrimaryChainId.put(uuid, snapshot.chainId);

        DebounceDecision debounce = checkAndMarkDebounce(
            lastMiningShapeToggleAtNanos,
            uuid,
            nowNanos,
            MINING_SHAPE_TOGGLE_DEBOUNCE_NANOS
        );
        if (!debounce.allow) {
            return new MiningShapeChangeDecision(false, debounce.reason, currentShape, currentShape);
        }

        MiningShape nextShape = currentShape.next();
        miningShapeByPlayer.put(uuid, nextShape);
        sendMiningShapeChangedMessage(playerRef, nextShape);
        return new MiningShapeChangeDecision(true, "shapeChanged", currentShape, nextShape);
    }

    private void sendMiningShapeChangedMessage(@Nonnull PlayerRef playerRef, @Nonnull MiningShape nextShape) {
        playerRef.sendMessage(
            Message.join(
                Message.raw("[AXO] ").color("#55E8FF").bold(true),
                Message.raw("Mining shape changed to ").color("#F7E2A6"),
                Message.raw(nextShape.displayName).color(nextShape.colorHex).bold(true)
            )
        );
    }

    private @Nonnull MiningDecision tryCastMiningBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        double chargeSeconds,
        int requestedTunnelBlocks
    ) {
        UUID uuid = playerRef.getUuid();
        MiningShape miningShape = resolveMiningShape(uuid);
        if (uuid == null) {
            return new MiningDecision(
                false,
                "noPlayerUuid",
                null,
                0,
                0,
                chargeSeconds,
                requestedTunnelBlocks,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new MiningDecision(
                false,
                "interactionTypeNotSupported",
                null,
                0,
                0,
                chargeSeconds,
                requestedTunnelBlocks,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedMiningSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new MiningDecision(
                    false,
                    "dedupe.secondary.chainId",
                    null,
                    0,
                    0,
                    chargeSeconds,
                    requestedTunnelBlocks,
                    miningShape.displayName,
                    miningShape.blocksPerDepth,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
                );
            }
        } else {
            Integer lastChainId = lastProcessedMiningUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new MiningDecision(
                    false,
                    "dedupe.use.chainId",
                    null,
                    0,
                    0,
                    chargeSeconds,
                    requestedTunnelBlocks,
                    miningShape.displayName,
                    miningShape.blocksPerDepth,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
                );
            }
        }

        boolean clientSaysBook = isMiningBookInSnapshot(snapshot);
        boolean serverSaysBook = MINING_BOOK_ITEM_ID.equals(serverItemInHand);
        if (!clientSaysBook && !serverSaysBook) {
            return new MiningDecision(
                false,
                "notHoldingBook",
                null,
                0,
                0,
                chargeSeconds,
                requestedTunnelBlocks,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents chat spam / repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedMiningSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedMiningUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastMiningCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new MiningDecision(
                    false,
                    debounce.reason,
                    null,
                    0,
                    0,
                    chargeSeconds,
                    requestedTunnelBlocks,
                    miningShape.displayName,
                    miningShape.blocksPerDepth,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
                );
            }
        }

        if (!mana.present) {
            return new MiningDecision(
                false,
                "manaStatMissing",
                null,
                0,
                0,
                chargeSeconds,
                requestedTunnelBlocks,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }

        int manaCost = Math.max(0, config.miningBook.manaCost);
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new MiningDecision(
                false,
                "manaTooLow",
                null,
                0,
                0,
                chargeSeconds,
                requestedTunnelBlocks,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }

        var external = store.getExternalData();
        if (external == null || external.getWorld() == null) {
            return new MiningDecision(
                false,
                "worldMissing",
                null,
                0,
                0,
                chargeSeconds,
                requestedTunnelBlocks,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }
        World world = external.getWorld();

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new MiningDecision(
                    false,
                    "lookTransformMissing",
                    null,
                    0,
                    0,
                    chargeSeconds,
                    requestedTunnelBlocks,
                    miningShape.displayName,
                    miningShape.blocksPerDepth,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
                );
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new MiningDecision(
                false,
                "originNotFinite",
                null,
                0,
                0,
                chargeSeconds,
                requestedTunnelBlocks,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new MiningDecision(
                false,
                "directionInvalid",
                null,
                0,
                0,
                chargeSeconds,
                requestedTunnelBlocks,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }

        double maxDistance = getEffectiveMiningMaxDistanceBlocks();
        BlockCollisionData hit = raycastSolidBlock(world, origin, direction, maxDistance);
        if (hit == null) {
            if (maxDistance + 1e-6 < MINING_HARD_MAX_DISTANCE) {
                BlockCollisionData farHit = raycastSolidBlock(world, origin, direction, MINING_HARD_MAX_DISTANCE);
                if (farHit != null) {
                    double distance = distanceBlocks(origin, farHit);
                    if (distance > maxDistance + 1e-4) {
                        playerRef.sendMessage(Message.raw("Too far: mining is limited to " + (int) maxDistance + " blocks."));
                        return new MiningDecision(
                            false,
                            "tooFar",
                            farHit,
                            maxDistance,
                            distance,
                            chargeSeconds,
                            requestedTunnelBlocks,
                            miningShape.displayName,
                            miningShape.blocksPerDepth,
                            null,
                            null,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0
                        );
                    }
                }
            }
            return new MiningDecision(
                false,
                "noBlockHitInRange",
                null,
                maxDistance,
                0,
                chargeSeconds,
                requestedTunnelBlocks,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
        }

        double hitDistance = distanceBlocks(origin, hit);
        int stepX = hit.collisionNormal != null ? -signToInt(hit.collisionNormal.x) : 0;
        int stepY = hit.collisionNormal != null ? -signToInt(hit.collisionNormal.y) : 0;
        int stepZ = hit.collisionNormal != null ? -signToInt(hit.collisionNormal.z) : 0;
        if (stepX == 0 && stepY == 0 && stepZ == 0) {
            double ax = Math.abs(direction.x);
            double ay = Math.abs(direction.y);
            double az = Math.abs(direction.z);
            if (ax >= ay && ax >= az) {
                stepX = direction.x >= 0 ? 1 : -1;
            } else if (ay >= az) {
                stepY = direction.y >= 0 ? 1 : -1;
            } else {
                stepZ = direction.z >= 0 ? 1 : -1;
            }
        }
        if (stepX == 0 && stepY == 0 && stepZ == 0) {
            stepZ = 1;
        }
        String faceAxis = resolveMiningFaceAxis(hit, stepX, stepY, stepZ);

        int tunnelBlocksToAttempt = Math.max(1, Math.min(requestedTunnelBlocks, Math.max(1, config.miningBook.maxTunnelBlocks)));

        WorldChunk centerChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(hit.x, hit.z));
        String targetBlockTypeId = null;
        if (centerChunk != null) {
            var type = centerChunk.getBlockType(hit.x, hit.y, hit.z);
            targetBlockTypeId = type != null ? type.getId() : null;
        }

        MiningAccumulator mining = new MiningAccumulator();

        for (int depth = 0; depth < tunnelBlocksToAttempt; depth++) {
            int centerX = hit.x + (stepX * depth);
            int centerY = hit.y + (stepY * depth);
            int centerZ = hit.z + (stepZ * depth);
            applyMiningShapeDepth(
                playerRef,
                store,
                world,
                miningShape,
                faceAxis,
                centerX,
                centerY,
                centerZ,
                mining
            );
        }

        if (mining.blocksBroken <= 0) {
            return new MiningDecision(
                false,
                "noBlocksBroken",
                hit,
                maxDistance,
                hitDistance,
                chargeSeconds,
                tunnelBlocksToAttempt,
                miningShape.displayName,
                miningShape.blocksPerDepth,
                faceAxis,
                targetBlockTypeId,
                mining.blocksConsidered,
                mining.blocksBroken,
                mining.blocksSkippedChunkNotLoaded,
                mining.blocksSkippedUnbreakable,
                mining.blocksBreakFailed,
                mining.blocksBreakExceptions,
                mining.dropEntitiesSpawned,
                mining.dropItemsTotal,
                mining.dropsSkippedNoDropData,
                mining.dropsSkippedMissingDropList,
                mining.dropSpawnExceptions
            );
        }

        // Consume mana on successful cast.
        if (stats != null) {
            float newMana = Math.max(mana.min, mana.current - manaCost);
            stats.setStatValue(mana.index, newMana);
            stats.update();
        }

        return new MiningDecision(
            true,
            "blocksBroken",
            hit,
            maxDistance,
            hitDistance,
            chargeSeconds,
            tunnelBlocksToAttempt,
            miningShape.displayName,
            miningShape.blocksPerDepth,
            faceAxis,
            targetBlockTypeId,
            mining.blocksConsidered,
            mining.blocksBroken,
            mining.blocksSkippedChunkNotLoaded,
            mining.blocksSkippedUnbreakable,
            mining.blocksBreakFailed,
            mining.blocksBreakExceptions,
            mining.dropEntitiesSpawned,
            mining.dropItemsTotal,
            mining.dropsSkippedNoDropData,
            mining.dropsSkippedMissingDropList,
            mining.dropSpawnExceptions
        );
    }

    private void applyMiningShapeDepth(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull MiningShape miningShape,
        @Nonnull String faceAxis,
        int centerX,
        int centerY,
        int centerZ,
        @Nonnull MiningAccumulator mining
    ) {
        attemptMineMiningBlock(playerRef, store, world, centerX, centerY, centerZ, mining);
        if (miningShape == MiningShape.ONE_BY_ONE) {
            return;
        }

        if ("X".equals(faceAxis)) {
            if (miningShape == MiningShape.THREE_BY_THREE) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dy == 0 && dz == 0) {
                            continue;
                        }
                        attemptMineMiningBlock(playerRef, store, world, centerX, centerY + dy, centerZ + dz, mining);
                    }
                }
                return;
            }
            attemptMineMiningBlock(playerRef, store, world, centerX, centerY - 1, centerZ, mining);
            attemptMineMiningBlock(playerRef, store, world, centerX, centerY + 1, centerZ, mining);
            attemptMineMiningBlock(playerRef, store, world, centerX, centerY, centerZ - 1, mining);
            attemptMineMiningBlock(playerRef, store, world, centerX, centerY, centerZ + 1, mining);
            return;
        }

        if ("Z".equals(faceAxis)) {
            if (miningShape == MiningShape.THREE_BY_THREE) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        attemptMineMiningBlock(playerRef, store, world, centerX + dx, centerY + dy, centerZ, mining);
                    }
                }
                return;
            }
            attemptMineMiningBlock(playerRef, store, world, centerX - 1, centerY, centerZ, mining);
            attemptMineMiningBlock(playerRef, store, world, centerX + 1, centerY, centerZ, mining);
            attemptMineMiningBlock(playerRef, store, world, centerX, centerY - 1, centerZ, mining);
            attemptMineMiningBlock(playerRef, store, world, centerX, centerY + 1, centerZ, mining);
            return;
        }

        if (miningShape == MiningShape.THREE_BY_THREE) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    attemptMineMiningBlock(playerRef, store, world, centerX + dx, centerY, centerZ + dz, mining);
                }
            }
            return;
        }

        attemptMineMiningBlock(playerRef, store, world, centerX - 1, centerY, centerZ, mining);
        attemptMineMiningBlock(playerRef, store, world, centerX + 1, centerY, centerZ, mining);
        attemptMineMiningBlock(playerRef, store, world, centerX, centerY, centerZ - 1, mining);
        attemptMineMiningBlock(playerRef, store, world, centerX, centerY, centerZ + 1, mining);
    }

    private void attemptMineMiningBlock(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull MiningAccumulator mining
    ) {
        mining.blocksConsidered++;

        if (y < 0) {
            mining.blocksSkippedUnbreakable++;
            return;
        }

        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            mining.blocksSkippedChunkNotLoaded++;
            return;
        }

        var blockType = chunk.getBlockType(x, y, z);
        if (blockType == null || blockType.isUnknown()) {
            mining.blocksSkippedUnbreakable++;
            return;
        }
        if (blockType == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
            || blockType.getDrawType() == com.hypixel.hytale.protocol.DrawType.Empty) {
            mining.blocksSkippedUnbreakable++;
            return;
        }

        try {
            boolean broke = chunk.breakBlock(x, y, z);
            if (broke) {
                mining.blocksBroken++;
                try {
                    DropSpawnResult drops = spawnMiningBlockDrops(store, blockType, x, y, z);
                    mining.dropEntitiesSpawned += drops.entitiesSpawned;
                    mining.dropItemsTotal += drops.itemsTotal;
                    mining.dropsSkippedNoDropData += drops.skippedNoDropData;
                    mining.dropsSkippedMissingDropList += drops.skippedMissingDropList;
                } catch (Throwable t) {
                    mining.dropSpawnExceptions++;
                    debug.traceFileOnly(
                        playerRef,
                        "MiningBookDrop event=spawnDrops"
                            + " block=(" + x + "," + y + "," + z + ")"
                            + " exception=" + t.getClass().getSimpleName()
                            + (t.getMessage() != null ? " message=\"" + t.getMessage() + "\"" : "")
                    );
                }
            } else {
                mining.blocksBreakFailed++;
            }
        } catch (Throwable t) {
            mining.blocksBreakExceptions++;
            debug.traceFileOnly(
                playerRef,
                "MiningBookBreak event=breakBlock"
                    + " block=(" + x + "," + y + "," + z + ")"
                    + " exception=" + t.getClass().getSimpleName()
                    + (t.getMessage() != null ? " message=\"" + t.getMessage() + "\"" : "")
            );
        }
    }

    private record DropSpawnResult(
        int entitiesSpawned,
        int itemsTotal,
        int skippedNoDropData,
        int skippedMissingDropList
    ) {}

    private @Nonnull DropSpawnResult spawnMiningBlockDrops(
        @Nonnull Store<EntityStore> store,
        @Nonnull com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType,
        int x,
        int y,
        int z
    ) {
        BlockGathering gathering = blockType.getGathering();
        if (gathering == null) {
            com.hypixel.hytale.server.core.asset.type.item.config.Item item = blockType.getItem();
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                return new DropSpawnResult(0, 0, 1, 0);
            }
            int spawned = spawnItemDrop(store, new ItemStack(item.getId(), 1), x, y, z);
            return new DropSpawnResult(spawned, spawned > 0 ? 1 : 0, 0, 0);
        }

        HarvestingDropType harvest = gathering.getHarvest();
        BlockBreakingDropType breaking = gathering.getBreaking();

        String harvestItemId = harvest != null ? harvest.getItemId() : null;
        String harvestDropListId = harvest != null ? harvest.getDropListId() : null;

        String breakingItemId = breaking != null ? breaking.getItemId() : null;
        String breakingDropListId = breaking != null ? breaking.getDropListId() : null;
        int breakingQuantity = breaking != null ? Math.max(0, breaking.getQuantity()) : 0;

        // Prefer "harvest" drops when present (closer to typical survival mining behavior).
        boolean hasHarvestItem = harvestItemId != null && !harvestItemId.isBlank();
        boolean hasHarvestDropList = harvestDropListId != null && !harvestDropListId.isBlank();
        boolean hasBreakingItem = breakingItemId != null && !breakingItemId.isBlank() && breakingQuantity > 0;
        boolean hasBreakingDropList = breakingDropListId != null && !breakingDropListId.isBlank();

        if (!hasHarvestItem && !hasHarvestDropList && !hasBreakingItem && !hasBreakingDropList) {
            com.hypixel.hytale.server.core.asset.type.item.config.Item item = blockType.getItem();
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                return new DropSpawnResult(0, 0, 1, 0);
            }
            int spawned = spawnItemDrop(store, new ItemStack(item.getId(), 1), x, y, z);
            return new DropSpawnResult(spawned, spawned > 0 ? 1 : 0, 0, 0);
        }

        int entitiesSpawned = 0;
        int itemsTotal = 0;
        int skippedNoDropData = 0;
        int skippedMissingDropList = 0;

        if (hasHarvestDropList) {
            DropListResult result = spawnDropList(store, harvestDropListId, x, y, z);
            entitiesSpawned += result.entitiesSpawned;
            itemsTotal += result.itemsTotal;
            skippedMissingDropList += result.skippedMissingDropList;
        }
        if (hasHarvestItem) {
            int spawned = spawnItemDrop(store, new ItemStack(harvestItemId, 1), x, y, z);
            entitiesSpawned += spawned;
            itemsTotal += spawned > 0 ? 1 : 0;
        }

        if (!hasHarvestItem && !hasHarvestDropList) {
            if (hasBreakingDropList) {
                DropListResult result = spawnDropList(store, breakingDropListId, x, y, z);
                entitiesSpawned += result.entitiesSpawned;
                itemsTotal += result.itemsTotal;
                skippedMissingDropList += result.skippedMissingDropList;
            }
            if (hasBreakingItem) {
                int spawned = spawnItemDrop(store, new ItemStack(breakingItemId, breakingQuantity), x, y, z);
                entitiesSpawned += spawned;
                itemsTotal += spawned > 0 ? breakingQuantity : 0;
            }
        }

        if (entitiesSpawned <= 0 && itemsTotal <= 0 && skippedMissingDropList <= 0) {
            skippedNoDropData = 1;
        }

        return new DropSpawnResult(entitiesSpawned, itemsTotal, skippedNoDropData, skippedMissingDropList);
    }

    private record DropListResult(int entitiesSpawned, int itemsTotal, int skippedMissingDropList) {}

    private @Nonnull DropListResult spawnDropList(
        @Nonnull Store<EntityStore> store,
        @Nonnull String dropListId,
        int x,
        int y,
        int z
    ) {
        ItemDropList dropList = ItemDropList.getAssetMap().getAsset(dropListId);
        if (dropList == null) {
            return new DropListResult(0, 0, 1);
        }

        var container = dropList.getContainer();
        if (container == null) {
            return new DropListResult(0, 0, 0);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemDrop> drops = new ArrayList<>();
        container.populateDrops(drops, random::nextDouble, dropListId);

        int entitiesSpawned = 0;
        int itemsTotal = 0;
        for (ItemDrop drop : drops) {
            if (drop == null) {
                continue;
            }
            String itemId = drop.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }

            int quantity = Math.max(0, drop.getRandomQuantity(random));
            if (quantity <= 0) {
                continue;
            }

            BsonDocument metadata = drop.getMetadata();
            ItemStack stack = metadata != null ? new ItemStack(itemId, quantity, metadata) : new ItemStack(itemId, quantity);
            int spawned = spawnItemDrop(store, stack, x, y, z);
            entitiesSpawned += spawned;
            itemsTotal += spawned > 0 ? quantity : 0;
        }

        return new DropListResult(entitiesSpawned, itemsTotal, 0);
    }

    private int spawnItemDrop(@Nonnull Store<EntityStore> store, @Nonnull ItemStack stack, int x, int y, int z) {
        if (stack == null || !stack.isValid()) {
            return 0;
        }

        Vector3d position = new Vector3d((double) x + 0.5, (double) y + 0.5, (double) z + 0.5);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float vx = (float) (random.nextGaussian() * MINING_DROP_VELOCITY_HORIZONTAL_STDDEV);
        float vz = (float) (random.nextGaussian() * MINING_DROP_VELOCITY_HORIZONTAL_STDDEV);

        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
            store,
            stack,
            position,
            Vector3f.ZERO,
            vx,
            MINING_DROP_VELOCITY_Y,
            vz
        );
        if (holder == null) {
            return 0;
        }

        ItemComponent itemComponent = holder.getComponent(ItemComponent.getComponentType());
        if (itemComponent != null) {
            itemComponent.setPickupDelay(MINING_DROP_PICKUP_DELAY_SECONDS);
        }

        store.addEntity(holder, AddReason.SPAWN);
        return 1;
    }

    private @Nonnull TeleportDecision tryCastTeleportBook(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Player playerComponent,
        @Nullable EntityStatMap stats,
        @Nonnull ManaSnapshot mana,
        long nowNanos,
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand
    ) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return new TeleportDecision(false, "noPlayerUuid", null, null, 0, 0);
        }

        if (snapshot.interactionType != InteractionType.Secondary && snapshot.interactionType != InteractionType.Use) {
            return new TeleportDecision(false, "interactionTypeNotSupported", null, null, 0, 0);
        }

        if (snapshot.interactionType == InteractionType.Secondary) {
            Integer lastChainId = lastProcessedTeleportSecondaryChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new TeleportDecision(false, "dedupe.secondary.chainId", null, null, 0, 0);
            }
        } else {
            Integer lastChainId = lastProcessedTeleportUseChainId.get(uuid);
            if (lastChainId != null && lastChainId == snapshot.chainId) {
                return new TeleportDecision(false, "dedupe.use.chainId", null, null, 0, 0);
            }
        }

        boolean clientSaysBook = isTeleportBookInSnapshot(snapshot);
        boolean serverSaysBook = TELEPORT_BOOK_ITEM_ID.equals(serverItemInHand);
        if (!clientSaysBook && !serverSaysBook) {
            return new TeleportDecision(false, "notHoldingBook", null, null, 0, 0);
        }

        // De-dupe repeated initial chain updates even when the cast is denied (prevents chat spam / repeated gating logs).
        if (snapshot.interactionType == InteractionType.Secondary) {
            lastProcessedTeleportSecondaryChainId.put(uuid, snapshot.chainId);
        } else {
            lastProcessedTeleportUseChainId.put(uuid, snapshot.chainId);
        }

        long castDebounceNanos = getSpellbookCastDebounceNanos();
        if (castDebounceNanos > 0) {
            DebounceDecision debounce = checkAndMarkDebounce(lastTeleportCastAttemptAtNanos, uuid, nowNanos, castDebounceNanos);
            if (!debounce.allow) {
                return new TeleportDecision(false, debounce.reason, null, null, 0, 0);
            }
        }

        if (!mana.present) {
            return new TeleportDecision(false, "manaStatMissing", null, null, 0, 0);
        }

        int manaCost = Math.max(0, config.teleportBook.manaCost);
        if (mana.current < manaCost - FLOAT_EPSILON) {
            return new TeleportDecision(false, "manaTooLow", null, null, 0, 0);
        }

        var external = store.getExternalData();
        if (external == null || external.getWorld() == null) {
            return new TeleportDecision(false, "worldMissing", null, null, 0, 0);
        }
        World world = external.getWorld();

        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null) {
            Transform fallback = playerRef.getTransform();
            if (fallback == null) {
                return new TeleportDecision(false, "lookTransformMissing", null, null, 0, 0);
            }
            look = fallback;
        }

        Vector3d origin = look.getPosition();
        if (origin == null || !origin.isFinite()) {
            return new TeleportDecision(false, "originNotFinite", null, null, 0, 0);
        }

        Vector3d direction = look.getDirection();
        if (direction == null || !direction.isFinite() || direction.squaredLength() < 1e-9) {
            return new TeleportDecision(false, "directionInvalid", null, null, 0, 0);
        }

        double maxDistance = getEffectiveTeleportMaxDistanceBlocks();
        BlockCollisionData hit = raycastSolidBlock(world, origin, direction, maxDistance);
        if (hit == null) {
            // Disambiguate "no block in range" vs "too far": do a second raycast at a hard max for messaging only.
            if (maxDistance + 1e-6 < TELEPORT_HARD_MAX_DISTANCE) {
                BlockCollisionData farHit = raycastSolidBlock(world, origin, direction, TELEPORT_HARD_MAX_DISTANCE);
                if (farHit != null) {
                    double distance = distanceBlocks(origin, farHit);
                    if (distance > maxDistance + 1e-4) {
                        playerRef.sendMessage(Message.raw("Too far: teleportation is limited to " + (int) maxDistance + " blocks."));
                        return new TeleportDecision(false, "tooFar", farHit, null, maxDistance, distance);
                    }
                }
            }
            return new TeleportDecision(false, "noBlockHitInRange", null, null, maxDistance, 0);
        }

        double hitDistance = distanceBlocks(origin, hit);

        Vector3d destination = computeTeleportDestination(hit);
        if (destination == null || !destination.isFinite()) {
            return new TeleportDecision(false, "destinationInvalid", hit, null, maxDistance, hitDistance);
        }

        Transform currentTransform = playerRef.getTransform();
        Vector3f rotation = currentTransform != null && currentTransform.getRotation() != null ? currentTransform.getRotation() : Vector3f.NaN;
        Vector3f headRotation = playerRef.getHeadRotation();

        Teleport teleport = Teleport.createForPlayer(world, destination, rotation);
        if (headRotation != null) {
            teleport.setHeadRotation(headRotation);
        }

        store.addComponent(playerEntityRef, Teleport.getComponentType(), teleport);
        playerComponent.setCurrentFallDistance(0.0);

        // Consume mana on successful teleport.
        if (stats != null) {
            float newMana = Math.max(mana.min, mana.current - manaCost);
            stats.setStatValue(mana.index, newMana);
            stats.update();
        }

        return new TeleportDecision(true, "teleported", hit, destination, maxDistance, hitDistance);
    }

    private double getEffectiveTeleportMaxDistanceBlocks() {
        int configured = config.teleportBook != null ? config.teleportBook.maxDistanceBlocks : 100;
        if (configured < 1) {
            configured = 1;
        }
        return Math.min((double) configured, TELEPORT_HARD_MAX_DISTANCE);
    }

    private double getEffectiveMiningMaxDistanceBlocks() {
        int configured = config.miningBook != null ? config.miningBook.maxDistanceBlocks : 12;
        if (configured < 1) {
            configured = 1;
        }
        return Math.min((double) configured, MINING_HARD_MAX_DISTANCE);
    }

    private static @Nullable BlockCollisionData raycastSolidBlock(
        @Nonnull World world,
        @Nonnull Vector3d origin,
        @Nonnull Vector3d direction,
        double maxDistance
    ) {
        Vector3d ray = new Vector3d(direction).normalize().scale(maxDistance);
        CollisionResult result = new CollisionResult(false, false);
        result.setCollisionByMaterial(CollisionMaterial.MATERIAL_SOLID);
        CollisionModule.findBlockCollisionsIterative(world, TELEPORT_RAY_POINT_BOX, origin, ray, true, result);
        return result.getFirstBlockCollision();
    }

    private static double distanceBlocks(@Nonnull Vector3d origin, @Nonnull BlockCollisionData hit) {
        Vector3d point = hit.collisionPoint != null ? hit.collisionPoint : new Vector3d(hit.x + 0.5, hit.y + 0.5, hit.z + 0.5);
        return new Vector3d(point).subtract(origin).length();
    }

    private static @Nonnull Vector3d computeTeleportDestination(@Nonnull BlockCollisionData hit) {
        int dx = signToInt(hit.collisionNormal.x);
        int dy = signToInt(hit.collisionNormal.y);
        int dz = signToInt(hit.collisionNormal.z);

        if (dx == 0 && dy == 0 && dz == 0) {
            dy = 1;
        }

        int tx = hit.x + dx;
        int ty = hit.y + dy;
        int tz = hit.z + dz;

        return new Vector3d(tx + 0.5, ty + TELEPORT_DEST_Y_OFFSET, tz + 0.5);
    }

    private static int signToInt(double v) {
        if (v > 0.5) {
            return 1;
        }
        if (v < -0.5) {
            return -1;
        }
        return 0;
    }

    private static @Nonnull String resolveMiningFaceAxis(@Nonnull BlockCollisionData hit, int stepX, int stepY, int stepZ) {
        if (stepY != 0) {
            return "Y";
        }
        if (stepX != 0) {
            return "X";
        }
        if (stepZ != 0) {
            return "Z";
        }

        if (hit.collisionNormal == null) {
            return "Y";
        }

        double ax = Math.abs(hit.collisionNormal.x);
        double ay = Math.abs(hit.collisionNormal.y);
        double az = Math.abs(hit.collisionNormal.z);

        if (ay < 0.5 && ax < 0.5 && az < 0.5) {
            return "Y";
        }
        if (ay >= ax && ay >= az) {
            return "Y";
        }
        if (ax >= az) {
            return "X";
        }
        return "Z";
    }

    private boolean clientReportsHealingBook(@Nonnull InteractionSnapshot snapshot) {
        return isHealingBook(snapshot.itemInHandId) || isHealingBook(snapshot.utilityItemId) || isHealingBook(snapshot.toolsItemId);
    }

    private boolean clientReportsAnySpellbook(@Nonnull InteractionSnapshot snapshot) {
        return isHealingBookInSnapshot(snapshot)
            || isTeleportBookInSnapshot(snapshot)
            || isMiningBookInSnapshot(snapshot)
            || isImmunityBookInSnapshot(snapshot)
            || isTauntBookInSnapshot(snapshot)
            || isHordeBookInSnapshot(snapshot)
            || isDoomBookInSnapshot(snapshot)
            || isFlameBookInSnapshot(snapshot)
            || isLightBookInSnapshot(snapshot)
            || isFrostBookInSnapshot(snapshot)
            || isMorphBookInSnapshot(snapshot);
    }

    private record DebounceDecision(boolean allow, @Nonnull String reason) {}

    private static DebounceDecision checkAndMarkDebounce(
        @Nonnull Map<UUID, Long> lastAttemptAtNanos,
        @Nonnull UUID uuid,
        long nowNanos,
        long debounceWindowNanos
    ) {
        Long last = lastAttemptAtNanos.get(uuid);
        if (last != null) {
            long elapsedNanos = nowNanos - last;
            if (elapsedNanos >= 0 && elapsedNanos < debounceWindowNanos) {
                long sinceMs = elapsedNanos / 1_000_000L;
                long windowMs = debounceWindowNanos / 1_000_000L;
                long remainingMs = (debounceWindowNanos - elapsedNanos) / 1_000_000L;
                return new DebounceDecision(false, "debounce.windowMs=" + windowMs + " sinceMs=" + sinceMs + " remainingMs=" + remainingMs);
            }
        }

        lastAttemptAtNanos.put(uuid, nowNanos);
        return new DebounceDecision(true, "ok");
    }

    private boolean clientReportsAnySpellbook(@Nullable String itemInHandId, @Nullable String utilityItemId, @Nullable String toolsItemId) {
        return isHealingBook(itemInHandId)
            || TELEPORT_BOOK_ITEM_ID.equals(itemInHandId)
            || MINING_BOOK_ITEM_ID.equals(itemInHandId)
            || IMMUNITY_BOOK_ITEM_ID.equals(itemInHandId)
            || TAUNT_BOOK_ITEM_ID.equals(itemInHandId)
            || HORDE_BOOK_ITEM_ID.equals(itemInHandId)
            || DOOM_BOOK_ITEM_ID.equals(itemInHandId)
            || FLAME_BOOK_ITEM_ID.equals(itemInHandId)
            || LIGHT_BOOK_ITEM_ID.equals(itemInHandId)
            || FROST_BOOK_ITEM_ID.equals(itemInHandId)
            || MORPH_BOOK_ITEM_ID.equals(itemInHandId)
            || isHealingBook(utilityItemId)
            || TELEPORT_BOOK_ITEM_ID.equals(utilityItemId)
            || MINING_BOOK_ITEM_ID.equals(utilityItemId)
            || IMMUNITY_BOOK_ITEM_ID.equals(utilityItemId)
            || TAUNT_BOOK_ITEM_ID.equals(utilityItemId)
            || HORDE_BOOK_ITEM_ID.equals(utilityItemId)
            || DOOM_BOOK_ITEM_ID.equals(utilityItemId)
            || FLAME_BOOK_ITEM_ID.equals(utilityItemId)
            || LIGHT_BOOK_ITEM_ID.equals(utilityItemId)
            || FROST_BOOK_ITEM_ID.equals(utilityItemId)
            || MORPH_BOOK_ITEM_ID.equals(utilityItemId)
            || isHealingBook(toolsItemId)
            || TELEPORT_BOOK_ITEM_ID.equals(toolsItemId)
            || MINING_BOOK_ITEM_ID.equals(toolsItemId)
            || IMMUNITY_BOOK_ITEM_ID.equals(toolsItemId)
            || TAUNT_BOOK_ITEM_ID.equals(toolsItemId)
            || HORDE_BOOK_ITEM_ID.equals(toolsItemId)
            || DOOM_BOOK_ITEM_ID.equals(toolsItemId)
            || FLAME_BOOK_ITEM_ID.equals(toolsItemId)
            || LIGHT_BOOK_ITEM_ID.equals(toolsItemId)
            || FROST_BOOK_ITEM_ID.equals(toolsItemId)
            || MORPH_BOOK_ITEM_ID.equals(toolsItemId);
    }

    private static boolean isHealingBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return isHealingBook(snapshot.itemInHandId) || isHealingBook(snapshot.utilityItemId) || isHealingBook(snapshot.toolsItemId);
    }

    private static boolean isTeleportBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return TELEPORT_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || TELEPORT_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || TELEPORT_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isMiningBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return MINING_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || MINING_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || MINING_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isImmunityBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return IMMUNITY_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || IMMUNITY_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || IMMUNITY_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isTauntBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return TAUNT_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || TAUNT_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || TAUNT_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isHordeBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return HORDE_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || HORDE_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || HORDE_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isDoomBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return DOOM_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || DOOM_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || DOOM_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isFlameBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return FLAME_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || FLAME_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || FLAME_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isLightBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return LIGHT_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || LIGHT_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || LIGHT_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isFrostBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return FROST_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || FROST_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || FROST_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isMorphBookInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return MORPH_BOOK_ITEM_ID.equals(snapshot.itemInHandId)
            || MORPH_BOOK_ITEM_ID.equals(snapshot.utilityItemId)
            || MORPH_BOOK_ITEM_ID.equals(snapshot.toolsItemId);
    }

    private static boolean isAncientSwordInSnapshot(@Nonnull InteractionSnapshot snapshot) {
        return ANCIENT_SWORD_ITEM_ID.equals(snapshot.itemInHandId);
    }

    private static boolean isHealingBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return HEALING_BOOK_ITEM_ID.equals(itemInHand) || HEALING_BOOK_ITEM_ID.equals(utilityItem) || HEALING_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isTeleportBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return TELEPORT_BOOK_ITEM_ID.equals(itemInHand) || TELEPORT_BOOK_ITEM_ID.equals(utilityItem) || TELEPORT_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isMiningBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return MINING_BOOK_ITEM_ID.equals(itemInHand) || MINING_BOOK_ITEM_ID.equals(utilityItem) || MINING_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isImmunityBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return IMMUNITY_BOOK_ITEM_ID.equals(itemInHand) || IMMUNITY_BOOK_ITEM_ID.equals(utilityItem) || IMMUNITY_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isTauntBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return TAUNT_BOOK_ITEM_ID.equals(itemInHand) || TAUNT_BOOK_ITEM_ID.equals(utilityItem) || TAUNT_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isHordeBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return HORDE_BOOK_ITEM_ID.equals(itemInHand) || HORDE_BOOK_ITEM_ID.equals(utilityItem) || HORDE_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isDoomBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return DOOM_BOOK_ITEM_ID.equals(itemInHand) || DOOM_BOOK_ITEM_ID.equals(utilityItem) || DOOM_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isFlameBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return FLAME_BOOK_ITEM_ID.equals(itemInHand) || FLAME_BOOK_ITEM_ID.equals(utilityItem) || FLAME_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isLightBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return LIGHT_BOOK_ITEM_ID.equals(itemInHand) || LIGHT_BOOK_ITEM_ID.equals(utilityItem) || LIGHT_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isFrostBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return FROST_BOOK_ITEM_ID.equals(itemInHand) || FROST_BOOK_ITEM_ID.equals(utilityItem) || FROST_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isMorphBookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return MORPH_BOOK_ITEM_ID.equals(itemInHand) || MORPH_BOOK_ITEM_ID.equals(utilityItem) || MORPH_BOOK_ITEM_ID.equals(toolsItem);
    }

    private static boolean isSpellbookInServerSlots(@Nullable String itemInHand, @Nullable String utilityItem, @Nullable String toolsItem) {
        return isHealingBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isTeleportBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isMiningBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isImmunityBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isTauntBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isHordeBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isDoomBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isFlameBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isLightBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isFrostBookInServerSlots(itemInHand, utilityItem, toolsItem)
            || isMorphBookInServerSlots(itemInHand, utilityItem, toolsItem);
    }

    private static boolean isHealingBook(@Nullable String itemId) {
        return HEALING_BOOK_ITEM_ID.equals(itemId);
    }

    private static @Nullable String itemIdOrNull(@Nullable ItemStack stack) {
        if (stack == null || !stack.isValid() || stack.isEmpty()) {
            return null;
        }
        return stack.getItemId();
    }

    private record ManaSnapshot(boolean present, int index, float current, float min, float max) {}

    private static ManaSnapshot snapshotMana(@Nullable EntityStatMap stats) {
        int manaIndex = DefaultEntityStatTypes.getMana();
        if (stats == null || manaIndex == Integer.MIN_VALUE || manaIndex < 0) {
            return new ManaSnapshot(false, manaIndex, 0f, 0f, 0f);
        }

        EntityStatValue stat = stats.get(manaIndex);
        if (stat == null) {
            return new ManaSnapshot(false, manaIndex, 0f, 0f, 0f);
        }

        return new ManaSnapshot(true, manaIndex, stat.get(), stat.getMin(), stat.getMax());
    }

    private record StatSnapshot(boolean present, int index, float current, float min, float max) {}

    private static StatSnapshot snapshotStat(@Nullable EntityStatMap stats, int index) {
        if (stats == null || index == Integer.MIN_VALUE || index < 0) {
            return new StatSnapshot(false, index, 0f, 0f, 0f);
        }

        EntityStatValue stat = stats.get(index);
        if (stat == null) {
            return new StatSnapshot(false, index, 0f, 0f, 0f);
        }

        return new StatSnapshot(true, index, stat.get(), stat.getMin(), stat.getMax());
    }

    private static String resolveItemSource(
        @Nonnull InteractionSnapshot snapshot,
        @Nullable String serverItemInHand,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem
    ) {
        if (isHealingBook(serverItemInHand)
            || TELEPORT_BOOK_ITEM_ID.equals(serverItemInHand)
            || MINING_BOOK_ITEM_ID.equals(serverItemInHand)
            || IMMUNITY_BOOK_ITEM_ID.equals(serverItemInHand)
            || TAUNT_BOOK_ITEM_ID.equals(serverItemInHand)
            || HORDE_BOOK_ITEM_ID.equals(serverItemInHand)
            || DOOM_BOOK_ITEM_ID.equals(serverItemInHand)
            || FLAME_BOOK_ITEM_ID.equals(serverItemInHand)
            || LIGHT_BOOK_ITEM_ID.equals(serverItemInHand)
            || FROST_BOOK_ITEM_ID.equals(serverItemInHand)
            || MORPH_BOOK_ITEM_ID.equals(serverItemInHand)
            || ANCIENT_SWORD_ITEM_ID.equals(serverItemInHand)) {
            return "server.itemInHand";
        }
        if (isHealingBook(serverUtilityItem)
            || TELEPORT_BOOK_ITEM_ID.equals(serverUtilityItem)
            || MINING_BOOK_ITEM_ID.equals(serverUtilityItem)
            || IMMUNITY_BOOK_ITEM_ID.equals(serverUtilityItem)
            || TAUNT_BOOK_ITEM_ID.equals(serverUtilityItem)
            || HORDE_BOOK_ITEM_ID.equals(serverUtilityItem)
            || DOOM_BOOK_ITEM_ID.equals(serverUtilityItem)
            || FLAME_BOOK_ITEM_ID.equals(serverUtilityItem)
            || LIGHT_BOOK_ITEM_ID.equals(serverUtilityItem)
            || FROST_BOOK_ITEM_ID.equals(serverUtilityItem)
            || MORPH_BOOK_ITEM_ID.equals(serverUtilityItem)
            || ANCIENT_SWORD_ITEM_ID.equals(serverUtilityItem)) {
            return "server.utilityItem";
        }
        if (isHealingBook(serverToolsItem)
            || TELEPORT_BOOK_ITEM_ID.equals(serverToolsItem)
            || MINING_BOOK_ITEM_ID.equals(serverToolsItem)
            || IMMUNITY_BOOK_ITEM_ID.equals(serverToolsItem)
            || TAUNT_BOOK_ITEM_ID.equals(serverToolsItem)
            || HORDE_BOOK_ITEM_ID.equals(serverToolsItem)
            || DOOM_BOOK_ITEM_ID.equals(serverToolsItem)
            || FLAME_BOOK_ITEM_ID.equals(serverToolsItem)
            || LIGHT_BOOK_ITEM_ID.equals(serverToolsItem)
            || FROST_BOOK_ITEM_ID.equals(serverToolsItem)
            || MORPH_BOOK_ITEM_ID.equals(serverToolsItem)
            || ANCIENT_SWORD_ITEM_ID.equals(serverToolsItem)) {
            return "server.toolsItem";
        }
        if (isHealingBook(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (isHealingBook(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (isHealingBook(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (TELEPORT_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (TELEPORT_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (TELEPORT_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (MINING_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (MINING_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (MINING_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (IMMUNITY_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (IMMUNITY_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (IMMUNITY_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (TAUNT_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (TAUNT_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (TAUNT_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (HORDE_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (HORDE_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (HORDE_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (DOOM_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (DOOM_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (DOOM_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (FLAME_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (FLAME_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (FLAME_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (LIGHT_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (LIGHT_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (LIGHT_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (FROST_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (FROST_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (FROST_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (MORPH_BOOK_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (MORPH_BOOK_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (MORPH_BOOK_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        if (ANCIENT_SWORD_ITEM_ID.equals(snapshot.itemInHandId)) {
            return "packet.itemInHandId";
        }
        if (ANCIENT_SWORD_ITEM_ID.equals(snapshot.utilityItemId)) {
            return "packet.utilityItemId";
        }
        if (ANCIENT_SWORD_ITEM_ID.equals(snapshot.toolsItemId)) {
            return "packet.toolsItemId";
        }
        return "none";
    }

    private static String formatTraceLine(
        @Nonnull InteractionSnapshot snapshot,
        boolean cancelled,
        @Nullable String serverItemInHand,
        @Nullable String serverActiveHotbarItem,
        @Nullable String serverUtilityItem,
        @Nullable String serverToolsItem,
        @Nullable String serverActiveToolItem,
        @Nonnull ManaSnapshot mana,
        boolean holdingBookNow,
        boolean allow
    ) {
        return "Packet=SyncInteractionChains(id=290)"
            + " cancelled=" + cancelled
            + " allow=" + allow
            + " holdingSpellbook=" + holdingBookNow
            + " interactionType=" + snapshot.interactionType
            + " initial=" + snapshot.initial
            + " desync=" + snapshot.desync
            + " state=" + snapshot.state
            + " chainId=" + snapshot.chainId
            + " clientItemInHandId=" + snapshot.itemInHandId
            + " clientUtilityItemId=" + snapshot.utilityItemId
            + " clientToolsItemId=" + snapshot.toolsItemId
            + " activeHotbarSlot=" + snapshot.activeHotbarSlot
            + " activeUtilitySlot=" + snapshot.activeUtilitySlot
            + " activeToolsSlot=" + snapshot.activeToolsSlot
            + " serverItemInHandId=" + serverItemInHand
            + " serverActiveHotbarItemId=" + serverActiveHotbarItem
            + " serverUtilityItemId=" + serverUtilityItem
            + " serverToolsItemId=" + serverToolsItem
            + " serverActiveToolItemId=" + serverActiveToolItem
            + " mana.present=" + mana.present
            + " mana.index=" + mana.index
            + " mana.current=" + mana.current
            + " mana.min=" + mana.min
            + " mana.max=" + mana.max;
    }
}
