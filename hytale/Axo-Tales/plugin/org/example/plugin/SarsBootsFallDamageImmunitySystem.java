package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Cancels fall damage for players wearing Sa'r Boots.
 */
public final class SarsBootsFallDamageImmunitySystem extends DamageEventSystem {

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        int causeIndex = damage.getDamageCauseIndex();
        if (causeIndex < 0) {
            return;
        }

        DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
        if (cause == null) {
            return;
        }

        String causeId = cause.getId();
        boolean isFallDamage = causeId != null && causeId.equalsIgnoreCase("FALL");
        if (!isFallDamage) {
            return;
        }

        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            return;
        }

        if (!SarsBootsPassiveEffect.isWearingSarsBoots(player)) {
            return;
        }

        damage.setAmount(0f);
        damage.setCancelled(true);
    }
}
