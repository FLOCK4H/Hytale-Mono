package org.example.plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks bonded Kudu Adepts (owner-to-adept mapping).
 *
 * <p>State is keyed by UUID and safe to access from multiple threads, but intended to be mutated on the world thread.</p>
 */
public final class KuduAdeptBondState {

    public record BondedAdept(@Nonnull UUID adeptUuid, @Nonnull UUID ownerUuid, long bondedAtNanos) {
    }

    private final ConcurrentHashMap<UUID, BondedAdept> byAdept = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, BondedAdept>> byOwner = new ConcurrentHashMap<>();

    public void bond(@Nonnull UUID adeptUuid, @Nonnull UUID ownerUuid, long nowNanos) {
        BondedAdept bonded = new BondedAdept(adeptUuid, ownerUuid, nowNanos);
        BondedAdept previous = byAdept.put(adeptUuid, bonded);
        if (previous != null && previous.ownerUuid != null && !previous.ownerUuid.equals(ownerUuid)) {
            ConcurrentHashMap<UUID, BondedAdept> previousOwner = byOwner.get(previous.ownerUuid);
            if (previousOwner != null) {
                previousOwner.remove(adeptUuid);
                if (previousOwner.isEmpty()) {
                    byOwner.remove(previous.ownerUuid, previousOwner);
                }
            }
        }
        byOwner
            .computeIfAbsent(ownerUuid, ignored -> new ConcurrentHashMap<>())
            .put(adeptUuid, bonded);
    }

    public @Nullable BondedAdept getByAdept(@Nonnull UUID adeptUuid) {
        return byAdept.get(adeptUuid);
    }

    public @Nonnull List<BondedAdept> snapshotForOwner(@Nonnull UUID ownerUuid) {
        ConcurrentHashMap<UUID, BondedAdept> map = byOwner.get(ownerUuid);
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(map.values());
    }

    public @Nonnull List<BondedAdept> snapshotAll() {
        return new ArrayList<>(byAdept.values());
    }

    public void removeAdept(@Nonnull UUID adeptUuid) {
        BondedAdept bonded = byAdept.remove(adeptUuid);
        if (bonded == null) {
            return;
        }
        ConcurrentHashMap<UUID, BondedAdept> map = byOwner.get(bonded.ownerUuid);
        if (map == null) {
            return;
        }
        map.remove(adeptUuid);
        if (map.isEmpty()) {
            byOwner.remove(bonded.ownerUuid, map);
        }
    }

    public void clearOwner(@Nonnull UUID ownerUuid) {
        ConcurrentHashMap<UUID, BondedAdept> map = byOwner.remove(ownerUuid);
        if (map == null || map.isEmpty()) {
            return;
        }
        for (UUID adeptUuid : map.keySet()) {
            if (adeptUuid != null) {
                byAdept.remove(adeptUuid);
            }
        }
    }
}

