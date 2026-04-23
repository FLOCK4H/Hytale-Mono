package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
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
 * Prevents Kudu Adepts from dealing direct/melee damage so their effective damage is projectile-based.
 */
public final class KuduAdeptNoMeleeDamageSystem extends DamageEventSystem {

    private static final long DEBUG_INTERVAL_NANOS = 5_000_000_000L;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final ConcurrentMap<UUID, Long> nextDebugAtNanosByAttacker = new ConcurrentHashMap<>();

    public KuduAdeptNoMeleeDamageSystem(
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
        return Query.or(UUIDComponent.getComponentType(), PlayerRef.getComponentType());
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

            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.EntitySource entitySource)) {
                return; // only cancel direct/melee-like damage; projectiles remain allowed
            }

            String causeId = getCauseId(damage);
            if ("Projectile".equals(causeId)) {
                return;
            }

            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef == null || !attackerRef.isValid()) {
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

            UUID attackerUuid = null;
            try {
                UUIDComponent attackerUuidComponent = store.getComponent(attackerRef, UUIDComponent.getComponentType());
                if (attackerUuidComponent != null) {
                    attackerUuid = attackerUuidComponent.getUuid();
                }
            } catch (Throwable ignored) {
                attackerUuid = null;
            }

            float amountBefore = damage.getAmount();
            damage.setAmount(0f);
            damage.setCancelled(true);

            if (attackerUuid == null) {
                return;
            }

            long nowNanos = System.nanoTime();
            long nextDebugAt = nextDebugAtNanosByAttacker.getOrDefault(attackerUuid, 0L);
            if (nextDebugAt > nowNanos) {
                return;
            }
            nextDebugAtNanosByAttacker.put(attackerUuid, nowNanos + DEBUG_INTERVAL_NANOS);

            debug.traceFileOnly(
                null,
                "KuduAdeptMeleeDisabled event=Damage"
                    + " causeId=" + causeId
                    + " cancelled=true"
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=0.0"
                    + " attackerUuid=" + attackerUuid
                    + " attacker.roleName=" + attackerRoleName
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "KuduAdeptNoMeleeDamageSystem: failed to handle damage.", t);
        }
    }

    private static @Nullable String getCauseId(@Nonnull Damage damage) {
        try {
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex < 0) {
                return null;
            }
            DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
            return cause != null ? cause.getId() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
