package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Invisibility Cloak: while worn, keeps the wearer invisible and best-effort prevents nearby NPCs from attacking them.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>Uses the existing {@code AxoTales_Invisibility} EntityEffect (ModelOverride) to avoid stacking model overrides.</li>
 *   <li>Refreshes the effect only when missing or near expiry so it doesn't override long potion durations.</li>
 *   <li>For nearby NPCs, continuously applies a short-lived {@link Attitude#FRIENDLY} override.</li>
 * </ul>
 *
 * <p>Debug traces are written to the persistent plugin debug log.</p>
 */
public final class InvisibilityCloakSystem extends TickingSystem<EntityStore> {

    public static final String CLOAK_ITEM_ID = "Invisibility_Cloak";
    public static final String INVISIBILITY_EFFECT_ID = InvisibilityPotionEffectDebugSystem.INVISIBILITY_EFFECT_ID;
    public static final String INVISIBILITY_AURA_EFFECT_ID = "AxoTales_Invisibility_Aura";

    private static final long TICK_INTERVAL_NANOS = 500_000_000L;
    private static final float REFRESH_WHEN_BELOW_SECONDS = 2.0f;
    private static final float REFRESH_DURATION_SECONDS = 3.0f;
    private static final float AURA_REFRESH_WHEN_BELOW_SECONDS = 1.5f;
    private static final float AURA_REFRESH_DURATION_SECONDS = 2.5f;

    private static final double AGGRO_RADIUS_BLOCKS = 24.0;
    private static final double FRIENDLY_OVERRIDE_SECONDS = 1.25;
    private static final int MAX_ENTITIES_CONSIDERED = 64;
    private static final long DEBUG_INTERVAL_NANOS = 30_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;

    private final Map<UUID, Boolean> lastWearingByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextDebugAtNanosByPlayer = new ConcurrentHashMap<>();

    private volatile int invisEffectIndex = -1;
    private volatile int auraEffectIndex = -1;
    private volatile long nextTickAtNanos;

    public InvisibilityCloakSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug
    ) {
        this.errors = errors;
        this.debug = debug;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            long nowNanos = System.nanoTime();
            long next = nextTickAtNanos;
            if (next > 0 && nowNanos < next) {
                return;
            }
            nextTickAtNanos = nowNanos + TICK_INTERVAL_NANOS;

            int effectIndex = resolveInvisibilityEffectIndex();
            if (effectIndex < 0) {
                return;
            }

            int auraIndex = resolveAuraEffectIndex();

            EntityEffect invisEffect = EntityEffect.getAssetMap().getAsset(INVISIBILITY_EFFECT_ID);
            if (invisEffect == null) {
                return;
            }

            EntityEffect auraEffect = auraIndex >= 0 ? EntityEffect.getAssetMap().getAsset(INVISIBILITY_AURA_EFFECT_ID) : null;

            store.forEachChunk(
                Query.and(PlayerRef.getComponentType(), Player.getComponentType(), EffectControllerComponent.getComponentType()),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                        if (entityRef == null || !entityRef.isValid()) {
                            continue;
                        }

                        PlayerRef playerRef = chunk.getComponent(i, PlayerRef.getComponentType());
                        UUID uuid = playerRef != null ? playerRef.getUuid() : null;
                        if (uuid == null) {
                            continue;
                        }

                        Player player = chunk.getComponent(i, Player.getComponentType());
                        if (player == null) {
                            continue;
                        }

                        EffectControllerComponent effects = chunk.getComponent(i, EffectControllerComponent.getComponentType());
                        if (effects == null) {
                            continue;
                        }

                        String chestItemId = ArmorManaMaxBonusEffect.getArmorItemId(player, ItemArmorSlot.Chest);
                        boolean wearing = CLOAK_ITEM_ID.equals(chestItemId);

                        Boolean last = lastWearingByPlayer.put(uuid, wearing);
                        if (last == null || last != wearing) {
                            ActiveEntityEffect active = effects.getActiveEffects().get(effectIndex);
                            float remaining = active != null ? active.getRemainingDuration() : 0f;
                            ActiveEntityEffect auraActive = auraIndex >= 0 ? effects.getActiveEffects().get(auraIndex) : null;
                            float auraRemaining = auraActive != null ? auraActive.getRemainingDuration() : 0f;
                            debug.traceFileOnly(
                                playerRef,
                                "InvisibilityCloak event=wearChange"
                                    + " wearing=" + wearing
                                    + " armor.slot=Chest"
                                    + " armor.itemId=" + (chestItemId != null ? chestItemId : "null")
                                    + " invis.effectId=" + INVISIBILITY_EFFECT_ID
                                    + " invis.effectIndex=" + effectIndex
                                    + " invis.active=" + (active != null)
                                    + " invis.remaining=" + remaining
                                    + " aura.effectId=" + INVISIBILITY_AURA_EFFECT_ID
                                    + " aura.effectIndex=" + auraIndex
                                    + " aura.active=" + (auraActive != null)
                                    + " aura.remaining=" + auraRemaining
                            );
                        }

                        if (!wearing) {
                            continue;
                        }

                        refreshInvisibilityIfNeeded(store, entityRef, playerRef, effects, invisEffect, effectIndex, nowNanos, chestItemId);
                        if (auraIndex >= 0 && auraEffect != null) {
                            refreshAuraIfNeeded(store, entityRef, playerRef, effects, auraEffect, auraIndex, nowNanos, chestItemId);
                        }
                        suppressNearbyNpcAggro(store, entityRef, playerRef, uuid, nowNanos);
                    }
                }
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "InvisibilityCloakSystem: tick failed.", t);
        }
    }

    private void refreshInvisibilityIfNeeded(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull PlayerRef playerRef,
        @Nonnull EffectControllerComponent effects,
        @Nonnull EntityEffect invisEffect,
        int effectIndex,
        long nowNanos,
        @Nullable String chestItemId
    ) {
        ActiveEntityEffect active = effects.getActiveEffects().get(effectIndex);
        float remaining = active != null ? active.getRemainingDuration() : 0f;
        boolean needsRefresh = active == null || remaining <= REFRESH_WHEN_BELOW_SECONDS;
        if (!needsRefresh) {
            return;
        }

        boolean applied = effects.addEffect(
            playerEntityRef,
            effectIndex,
            invisEffect,
            REFRESH_DURATION_SECONDS,
            OverlapBehavior.OVERWRITE,
            store
        );

        if (applied) {
            return;
        }

        long nextDebugAt = nextDebugAtNanosByPlayer.getOrDefault(playerRef.getUuid(), 0L);
        if (nextDebugAt > nowNanos) {
            return;
        }
        nextDebugAtNanosByPlayer.put(playerRef.getUuid(), nowNanos + DEBUG_INTERVAL_NANOS);

        debug.traceFileOnly(
            playerRef,
            "InvisibilityCloak event=invisRefresh"
                + " applied=false"
                + " reason=" + (active == null ? "missing" : "lowDuration")
                + " armor.slot=Chest"
                + " armor.itemId=" + (chestItemId != null ? chestItemId : "null")
                + " invis.effectId=" + INVISIBILITY_EFFECT_ID
                + " invis.effectIndex=" + effectIndex
                + " invis.remainingBefore=" + remaining
                + " invis.refreshWhenBelow=" + REFRESH_WHEN_BELOW_SECONDS
                + " invis.refreshDuration=" + REFRESH_DURATION_SECONDS
        );
    }

    private void refreshAuraIfNeeded(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull PlayerRef playerRef,
        @Nonnull EffectControllerComponent effects,
        @Nonnull EntityEffect auraEffect,
        int effectIndex,
        long nowNanos,
        @Nullable String chestItemId
    ) {
        ActiveEntityEffect active = effects.getActiveEffects().get(effectIndex);
        float remaining = active != null ? active.getRemainingDuration() : 0f;
        boolean needsRefresh = active == null || remaining <= AURA_REFRESH_WHEN_BELOW_SECONDS;
        if (!needsRefresh) {
            return;
        }

        boolean applied = effects.addEffect(
            playerEntityRef,
            effectIndex,
            auraEffect,
            AURA_REFRESH_DURATION_SECONDS,
            OverlapBehavior.OVERWRITE,
            store
        );

        if (applied) {
            return;
        }

        long nextDebugAt = nextDebugAtNanosByPlayer.getOrDefault(playerRef.getUuid(), 0L);
        if (nextDebugAt > nowNanos) {
            return;
        }
        nextDebugAtNanosByPlayer.put(playerRef.getUuid(), nowNanos + DEBUG_INTERVAL_NANOS);

        debug.traceFileOnly(
            playerRef,
            "InvisibilityCloak event=auraRefresh"
                + " applied=false"
                + " reason=" + (active == null ? "missing" : "lowDuration")
                + " armor.slot=Chest"
                + " armor.itemId=" + (chestItemId != null ? chestItemId : "null")
                + " aura.effectId=" + INVISIBILITY_AURA_EFFECT_ID
                + " aura.effectIndex=" + effectIndex
                + " aura.remainingBefore=" + remaining
                + " aura.refreshWhenBelow=" + AURA_REFRESH_WHEN_BELOW_SECONDS
                + " aura.refreshDuration=" + AURA_REFRESH_DURATION_SECONDS
        );
    }

    private void suppressNearbyNpcAggro(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID playerUuid,
        long nowNanos
    ) {
        Transform look = TargetUtil.getLook(playerEntityRef, store);
        if (look == null || look.getPosition() == null || !look.getPosition().isFinite()) {
            return;
        }
        Vector3d pos = look.getPosition();

        List<Ref<EntityStore>> nearby = TargetUtil.getAllEntitiesInSphere(pos, AGGRO_RADIUS_BLOCKS, store);
        if (nearby == null || nearby.isEmpty()) {
            return;
        }

        int considered = 0;
        int hostile = 0;
        int unknownAttitude = 0;
        int overridden = 0;
        for (Ref<EntityStore> candidate : nearby) {
            if (candidate == null || !candidate.isValid() || candidate.equals(playerEntityRef)) {
                continue;
            }

            considered++;
            if (considered > MAX_ENTITIES_CONSIDERED) {
                break;
            }

            NPCEntity npc = store.getComponent(candidate, NPCEntity.getComponentType());
            if (npc == null) {
                continue;
            }

            Role role = npc.getRole();
            if (role == null || role.getWorldSupport() == null) {
                continue;
            }

            try {
                Attitude current = role.getWorldSupport().getAttitude(candidate, playerEntityRef, store);
                if (current == Attitude.HOSTILE) {
                    hostile++;
                    continue;
                }
                role.getWorldSupport().overrideAttitude(playerEntityRef, Attitude.FRIENDLY, FRIENDLY_OVERRIDE_SECONDS);
                overridden++;
            } catch (Throwable ignored) {
                unknownAttitude++;
            }
        }

        if (hostile <= 0 && unknownAttitude <= 0) {
            return;
        }

        long nextDebugAt = nextDebugAtNanosByPlayer.getOrDefault(playerUuid, 0L);
        if (nextDebugAt > nowNanos) {
            return;
        }
        nextDebugAtNanosByPlayer.put(playerUuid, nowNanos + DEBUG_INTERVAL_NANOS);

        debug.traceFileOnly(
            playerRef,
            "InvisibilityCloak event=aggroSuppress"
                + " radiusBlocks=" + AGGRO_RADIUS_BLOCKS
                + " friendlyOverrideSeconds=" + FRIENDLY_OVERRIDE_SECONDS
                + " entities.considered=" + considered
                + " npcs.hostile=" + hostile
                + " npcs.unknownAttitude=" + unknownAttitude
                + " npcs.overridden=" + overridden
        );
    }

    private int resolveInvisibilityEffectIndex() {
        int cached = invisEffectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(INVISIBILITY_EFFECT_ID, -1);
        if (resolved >= 0) {
            invisEffectIndex = resolved;
        }
        return resolved;
    }

    private int resolveAuraEffectIndex() {
        int cached = auraEffectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(INVISIBILITY_AURA_EFFECT_ID, -1);
        if (resolved >= 0) {
            auraEffectIndex = resolved;
        }
        return resolved;
    }

    public void onPlayerDisconnect(@Nullable PlayerRef playerRef) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        lastWearingByPlayer.remove(uuid);
        nextDebugAtNanosByPlayer.remove(uuid);
    }
}
