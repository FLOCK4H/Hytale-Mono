package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Safety-net: prevents Kudu Adepts from damaging players.
 *
 * <p>This is intended to make the "friendly adept" behavior robust even if the underlying role/template still attempts
 * to swing at players due to attitude/targeting edge cases.</p>
 */
public final class KuduAdeptNoPlayerDamageSystem extends DamageEventSystem {

    private static final long DEBUG_INTERVAL_NANOS = 5_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final ConcurrentMap<UUID, Long> nextDebugAtNanosByVictim = new ConcurrentHashMap<>();

    public KuduAdeptNoPlayerDamageSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config
    ) {
        this.errors = errors;
        this.debug = debug;
        this.config = config;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), PlayerRef.getComponentType());
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        try {
            if (damage.isCancelled()) {
                return;
            }
            float amount = damage.getAmount();
            if (!(amount > 0f) || !Float.isFinite(amount)) {
                return;
            }

            if (config == null || config.kuduAdept == null || !config.kuduAdept.enabled) {
                return;
            }

            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            if (victimRef == null || !victimRef.isValid()) {
                return;
            }

            PlayerRef victimPlayerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            UUID victimUuid = victimPlayerRef != null ? victimPlayerRef.getUuid() : null;
            if (victimUuid == null) {
                return;
            }

            Damage.Source source = damage.getSource();

            Ref<EntityStore> attackerRef;
            Ref<EntityStore> projectileRef = null;
            String sourceType;
            if (source instanceof Damage.EntitySource entitySource) {
                attackerRef = entitySource.getRef();
                sourceType = "EntitySource";
            } else if (source instanceof Damage.ProjectileSource projectileSource) {
                attackerRef = projectileSource.getRef(); // shooter
                projectileRef = projectileSource.getProjectile();
                sourceType = "ProjectileSource";
            } else {
                return;
            }
            if (attackerRef == null || !attackerRef.isValid() || attackerRef.equals(victimRef)) {
                return;
            }

            NPCEntity attackerNpc = store.getComponent(attackerRef, NPCEntity.getComponentType());
            if (attackerNpc == null) {
                return;
            }

            String roleName = config.kuduAdept.roleName != null && !config.kuduAdept.roleName.isBlank()
                ? config.kuduAdept.roleName
                : KuduAdeptSpawnerSystem.DEFAULT_ROLE_NAME;

            String attackerRoleName = null;
            try {
                attackerRoleName = attackerNpc.getRoleName();
            } catch (Throwable ignored) {
                // Best effort.
            }
            if (attackerRoleName == null || !attackerRoleName.equals(roleName)) {
                return;
            }

            float amountBefore = damage.getAmount();
            damage.setAmount(0f);
            damage.setCancelled(true);

            long nowNanos = System.nanoTime();
            long nextDebugAt = nextDebugAtNanosByVictim.getOrDefault(victimUuid, 0L);
            if (nextDebugAt > nowNanos) {
                return;
            }
            nextDebugAtNanosByVictim.put(victimUuid, nowNanos + DEBUG_INTERVAL_NANOS);

            UUID attackerUuid = null;
            try {
                UUIDComponent attackerUuidComponent = store.getComponent(attackerRef, UUIDComponent.getComponentType());
                if (attackerUuidComponent != null) {
                    attackerUuid = attackerUuidComponent.getUuid();
                }
            } catch (Throwable ignored) {
                // Best effort.
            }

            UUID projectileUuid = null;
            if (projectileRef != null && projectileRef.isValid()) {
                try {
                    UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
                    if (projectileUuidComponent != null) {
                        projectileUuid = projectileUuidComponent.getUuid();
                    }
                } catch (Throwable ignored) {
                    // Best effort.
                }
            }

            String causeId = null;
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex >= 0) {
                DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
                if (cause != null) {
                    causeId = cause.getId();
                }
            }

            debug.traceFileOnly(
                victimPlayerRef,
                "KuduAdeptFriendlyFire event=Damage"
                    + " sourceType=" + sourceType
                    + " causeId=" + causeId
                    + " cancelled=true"
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=0.0"
                    + " victimUuid=" + victimUuid
                    + (projectileUuid != null ? " projectileUuid=" + projectileUuid : "")
                    + (attackerUuid != null ? " attackerUuid=" + attackerUuid : "")
                    + " attacker.roleName=" + attackerRoleName
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduAdeptNoPlayerDamageSystem: failed to handle damage.", t);
        }
    }
}
