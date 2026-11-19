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











public final class InvisibilityHiddenPlayersSystem extends TickingSystem<EntityStore> {

    private static final String INVISIBILITY_EFFECT_ID = InvisibilityPotionEffectDebugSystem.INVISIBILITY_EFFECT_ID;
    private static final long SWEEP_INTERVAL_NANOS = 100_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;

    private volatile int effectIndex = -1;
    private final SweepClock<Store<EntityStore>> clock=new SweepClock<>();
    private final InvisibilityVisibility visibility=new InvisibilityVisibility();

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
            if(!clock.due(store,nowNanos,SWEEP_INTERVAL_NANOS))return;

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

            var invisible=new java.util.HashSet<UUID>();
            store.forEachChunk(Query.and(PlayerRef.getComponentType(),EffectControllerComponent.getComponentType()),(chunk,buffer)->{
                for(int i=0;i<chunk.size();i++) {
                    var subject=chunk.getComponent(i,PlayerRef.getComponentType());
                    if(InvisibilityConcealment.hidden(buffer,chunk.getReferenceTo(i)))invisible.add(subject.getUuid());
                }
            });
            for(var viewer:playerRefs) {
                int changed=visibility.reconcile(viewer.getUuid(),invisible,viewer.getHiddenPlayersManager());
                if(changed>0)debug.traceFileOnly(viewer,"InvisibilityHiddenPlayers event=visibilityReconcile effectId="+INVISIBILITY_EFFECT_ID
                    +" effectIndex="+invisIndex+" invisibleSubjects="+invisible.size()+" changed="+changed+" selfHidden=false modelSwap=false");
            }
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
        visibility.forgetViewer(subjectUuid);


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
                            visibility.releaseSubject(viewer.getUuid(),subjectUuid,viewer.getHiddenPlayersManager());
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

