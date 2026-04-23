package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Persistent owner binding stored directly on bonded Kudu Adept entities.
 */
public final class KuduAdeptBondPersistedComponent implements Component<EntityStore> {

    public static final BuilderCodec<KuduAdeptBondPersistedComponent> CODEC = BuilderCodec
        .builder(KuduAdeptBondPersistedComponent.class, KuduAdeptBondPersistedComponent::new)
        .append(
            new KeyedCodec<>("OwnerUuid", Codec.UUID_STRING),
            (data, value) -> data.ownerUuid = value,
            data -> data.ownerUuid
        )
        .addValidator(Validators.nonNull())
        .add()
        .append(
            new KeyedCodec<>("BondedAtEpochMillis", Codec.LONG),
            (data, value) -> data.bondedAtEpochMillis = value != null ? value : 0L,
            data -> data.bondedAtEpochMillis
        )
        .add()
        .build();

    @Nullable
    private UUID ownerUuid;
    private long bondedAtEpochMillis;

    public KuduAdeptBondPersistedComponent() {
    }

    public KuduAdeptBondPersistedComponent(@Nonnull UUID ownerUuid, long bondedAtEpochMillis) {
        this.ownerUuid = ownerUuid;
        this.bondedAtEpochMillis = bondedAtEpochMillis;
    }

    public KuduAdeptBondPersistedComponent(@Nonnull KuduAdeptBondPersistedComponent other) {
        this.ownerUuid = other.ownerUuid;
        this.bondedAtEpochMillis = other.bondedAtEpochMillis;
    }

    public @Nullable UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(@Nonnull UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public long getBondedAtEpochMillis() {
        return bondedAtEpochMillis;
    }

    public void setBondedAtEpochMillis(long bondedAtEpochMillis) {
        this.bondedAtEpochMillis = bondedAtEpochMillis;
    }

    public boolean hasOwnerUuid() {
        return ownerUuid != null;
    }

    @Override
    public @Nonnull Component<EntityStore> clone() {
        return new KuduAdeptBondPersistedComponent(this);
    }
}
