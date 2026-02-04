package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes invisible players hidden from other players using {@link PlayerRef#getHiddenPlayersManager()}.
 *
 * <p>This mirrors the built-in server {@code /hide} behavior: it does not hide a player from themself.</p>
 *
 * <p>Why this exists: model swapping (ModelChange) is not a safe way to hide player visuals in current builds and
 * can produce broken "spaghetti/shards" rendering because player skin/attachments expect the normal player rig.</p>
 *
 * <p>Debug traces are written to the persistent plugin debug log.</p>
 */
public final class InvisibilityHiddenPlayersSystem extends TickingSystem<EntityStore> {

    private static final String INVISIBILITY_EFFECT_ID = InvisibilityPotionEffectDebugSystem.INVISIBILITY_EFFECT_ID;
    private static final long SWEEP_INTERVAL_NANOS = 750_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Map<UUID, Boolean> lastActiveByPlayer = new ConcurrentHashMap<>();

    private volatile int effectIndex = -1;
    private long nextSweepAtNanos = 0L;

    public InvisibilityHiddenPlayersSystem(
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
            if (nextSweepAtNanos > nowNanos) {
                return;
            }
            nextSweepAtNanos = nowNanos + SWEEP_INTERVAL_NANOS;

            int invisIndex = resolveEffectIndex();
            if (invisIndex < 0) {
                return;
            }

            EntityStore entityStore = store.getExternalData();
            World world = entityStore != null ? entityStore.getWorld() : null;
            if (world == null) {
                return;
            }

            Collection<PlayerRef> playerRefs = world.getPlayerRefs();
            if (playerRefs == null || playerRefs.isEmpty()) {
                return;
            }

            store.forEachChunk(
                Query.and(PlayerRef.getComponentType(), Player.getComponentType(), EffectControllerComponent.getComponentType()),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                        if (entityRef == null || !entityRef.isValid()) {
                            continue;
                        }

                        PlayerRef subjectRef = chunk.getComponent(i, PlayerRef.getComponentType());
                        if (subjectRef == null || subjectRef.getUuid() == null) {
                            continue;
                        }

                        EffectControllerComponent effects = chunk.getComponent(i, EffectControllerComponent.getComponentType());
                        if (effects == null) {
                            continue;
                        }

                        UUID subjectUuid = subjectRef.getUuid();
                        boolean active = effects.getActiveEffects().containsKey(invisIndex);
                        Boolean last = lastActiveByPlayer.put(subjectUuid, active);
                        if (last != null && last == active) {
                            continue;
                        }

                        int viewersConsidered = 0;
                        int viewersChanged = 0;
                        for (PlayerRef viewer : playerRefs) {
                            if (viewer == null || viewer.getUuid() == null) {
                                continue;
                            }
                            if (viewer.getUuid().equals(subjectUuid)) {
                                continue; // matches /hide: don't hide from self
                            }
                            viewersConsidered++;
                            try {
                                if (active) {
                                    viewer.getHiddenPlayersManager().hidePlayer(subjectUuid);
                                } else {
                                    viewer.getHiddenPlayersManager().showPlayer(subjectUuid);
                                }
                                viewersChanged++;
                            } catch (Throwable ignored) {
                                // best-effort
                            }
                        }

                        debug.traceFileOnly(
                            subjectRef,
                            "InvisibilityHiddenPlayers event=visibilityChange"
                                + " effectId=" + INVISIBILITY_EFFECT_ID
                                + " effectIndex=" + invisIndex
                                + " active=" + active
                                + " viewers.considered=" + viewersConsidered
                                + " viewers.changed=" + viewersChanged
                                + " selfHidden=false"
                        );
                    }
                }
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "InvisibilityHiddenPlayersSystem: tick failed.", t);
        }
    }

    private int resolveEffectIndex() {
        int cached = effectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(INVISIBILITY_EFFECT_ID, -1);
        if (resolved >= 0) {
            effectIndex = resolved;
        }
        return resolved;
    }

    public void onPlayerDisconnect(@Nullable PlayerRef playerRef) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }

        UUID subjectUuid = playerRef.getUuid();
        lastActiveByPlayer.remove(subjectUuid);

        // Ensure this UUID is not left hidden for other players across worlds.
        try {
            Universe universe = Universe.get();
            if (universe == null || universe.getWorlds() == null) {
                return;
            }

            universe.getWorlds().values().forEach(world -> {
                if (world == null) {
                    return;
                }
                world.execute(() -> {
                    Collection<PlayerRef> refs = world.getPlayerRefs();
                    if (refs == null) {
                        return;
                    }
                    for (PlayerRef viewer : refs) {
                        if (viewer == null || viewer.getUuid() == null) {
                            continue;
                        }
                        if (viewer.getUuid().equals(subjectUuid)) {
                            continue;
                        }
                        try {
                            viewer.getHiddenPlayersManager().showPlayer(subjectUuid);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            });
        } catch (Throwable t) {
            errors.report(playerRef, "InvisibilityHiddenPlayersSystem: disconnect cleanup failed.", t);
        }
    }
}

