package org.example.plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Tracks bonded Kudu Adepts (owner-to-adept mapping).
 *
 * <p>State is keyed by UUID and safe to access from multiple threads, but intended to be mutated on the world thread.</p>
 */
public final class KuduAdeptBondState {

    public record BondedAdept(@Nonnull UUID adeptUuid, @Nonnull UUID ownerUuid, long bondedAtNanos) {
    }

    public record OwnerTarget(@Nonnull UUID ownerUuid, @Nonnull UUID targetUuid, long markedAtNanos) {
    }

    public record CrystalDropOwner(
        @Nonnull UUID ownerUuid,
        @Nonnull String itemId,
        double x,
        double y,
        double z,
        long droppedAtNanos
    ) {
    }

    private static final int MAX_CRYSTAL_DROP_OWNER_RECORDS = 64;

    private final ConcurrentHashMap<UUID, BondedAdept> byAdept = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, BondedAdept>> byOwner = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, OwnerTarget> targetByOwner = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<CrystalDropOwner> crystalDropOwners = new ConcurrentLinkedDeque<>();

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

    public @Nullable OwnerTarget getOwnerTarget(@Nonnull UUID ownerUuid) {
        return targetByOwner.get(ownerUuid);
    }

    public void recordOwnerTarget(@Nonnull UUID ownerUuid, @Nonnull UUID targetUuid, long nowNanos) {
        targetByOwner.put(ownerUuid, new OwnerTarget(ownerUuid, targetUuid, nowNanos));
    }

    public void clearOwnerTarget(@Nonnull UUID ownerUuid) {
        targetByOwner.remove(ownerUuid);
    }

    public void clearOwnerTargetIfMatches(@Nonnull UUID ownerUuid, @Nonnull UUID targetUuid) {
        OwnerTarget current = targetByOwner.get(ownerUuid);
        if (current != null && current.targetUuid != null && current.targetUuid.equals(targetUuid)) {
            targetByOwner.remove(ownerUuid, current);
        }
    }

    public void recordCrystalDropOwner(
        @Nonnull UUID ownerUuid,
        @Nonnull String itemId,
        double x,
        double y,
        double z,
        long nowNanos
    ) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return;
        }
        crystalDropOwners.addLast(new CrystalDropOwner(ownerUuid, itemId, x, y, z, nowNanos));
        while (crystalDropOwners.size() > MAX_CRYSTAL_DROP_OWNER_RECORDS) {
            crystalDropOwners.pollFirst();
        }
    }

    public @Nullable CrystalDropOwner consumeCrystalDropOwner(
        @Nonnull String itemId,
        double x,
        double y,
        double z,
        long nowNanos,
        long maxAgeNanos,
        double maxDistanceSq
    ) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        crystalDropOwners.removeIf(record -> isExpiredCrystalDropOwner(record, nowNanos, maxAgeNanos));

        CrystalDropOwner best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        long bestDroppedAt = Long.MIN_VALUE;
        for (CrystalDropOwner record : crystalDropOwners) {
            if (record == null || record.ownerUuid == null || record.itemId == null || !record.itemId.equals(itemId)) {
                continue;
            }
            double dx = record.x - x;
            double dy = record.y - y;
            double dz = record.z - z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (!Double.isFinite(distanceSq) || distanceSq > maxDistanceSq) {
                continue;
            }
            if (distanceSq < bestDistanceSq || (distanceSq == bestDistanceSq && record.droppedAtNanos > bestDroppedAt)) {
                best = record;
                bestDistanceSq = distanceSq;
                bestDroppedAt = record.droppedAtNanos;
            }
        }
        if (best != null) {
            crystalDropOwners.remove(best);
        }
        return best;
    }

    private static boolean isExpiredCrystalDropOwner(
        @Nullable CrystalDropOwner record,
        long nowNanos,
        long maxAgeNanos
    ) {
        if (record == null || record.ownerUuid == null || record.itemId == null) {
            return true;
        }
        long age = nowNanos - record.droppedAtNanos;
        return age < 0 || age > maxAgeNanos;
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
            targetByOwner.remove(bonded.ownerUuid);
        }
    }

    public void clearOwner(@Nonnull UUID ownerUuid) {
        ConcurrentHashMap<UUID, BondedAdept> map = byOwner.remove(ownerUuid);
        targetByOwner.remove(ownerUuid);
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
