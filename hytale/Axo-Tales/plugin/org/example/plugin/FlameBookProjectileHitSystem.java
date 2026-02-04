package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Flame Book projectile effects:
 * - Applies {@code Burn} status to hit entities.
 * - Converts impacted terrain blocks (best effort) to Volcanic Rock.
 * - Marks projectiles that hit an entity so the block-impact system doesn't also convert terrain for the same shot.
 */
public final class FlameBookProjectileHitSystem extends DamageEventSystem {

    public static final String FLAME_PROJECTILE_ID = "Flame_Bolt";
    private static final String BURN_EFFECT_ID = "Burn";
    private static final String VOLCANIC_ROCK_BLOCK_ITEM_ID = "Rock_Volcanic";
    private static final float FLAME_DAMAGE = 25f;
    private static final int CHUNK_WIDTH_BLOCKS = ChunkUtil.SIZE;

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final FlameBookImpactTracker impactTracker;

    private volatile int burnEffectIndex = -1;

    public FlameBookProjectileHitSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull FlameBookImpactTracker impactTracker
    ) {
        this.errors = errors;
        this.debug = debug;
        this.impactTracker = impactTracker;
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
            Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
            if (targetRef == null || !targetRef.isValid()) {
                return;
            }

            PlayerRef targetPlayerRef = store.getComponent(targetRef, PlayerRef.getComponentType());
            UUIDComponent targetUuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
            UUID targetUuid = targetUuidComponent != null ? targetUuidComponent.getUuid() : null;
            if (targetUuid == null && targetPlayerRef != null) {
                targetUuid = targetPlayerRef.getUuid();
            }

            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.ProjectileSource projectileSource)) {
                return;
            }

            Ref<EntityStore> projectileRef = projectileSource.getProjectile();
            if (projectileRef == null || !projectileRef.isValid()) {
                return;
            }

            ProjectileComponent projectileComponent = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
            if (projectileComponent == null) {
                return;
            }

            String projectileAssetName = projectileComponent.getProjectileAssetName();
            if (!FLAME_PROJECTILE_ID.equals(projectileAssetName)) {
                return;
            }

            String causeId = null;
            int causeIndex = damage.getDamageCauseIndex();
            if (causeIndex >= 0) {
                DamageCause cause = DamageCause.getAssetMap().getAsset(causeIndex);
                if (cause != null) {
                    causeId = cause.getId();
                }
            }

            if (causeId != null && !causeId.equalsIgnoreCase("PROJECTILE")) {
                return;
            }

            UUID projectileUuid = null;
            UUIDComponent projectileUuidComponent = store.getComponent(projectileRef, UUIDComponent.getComponentType());
            if (projectileUuidComponent != null) {
                projectileUuid = projectileUuidComponent.getUuid();
            }

            Ref<EntityStore> shooterRef = projectileSource.getRef();
            PlayerRef shooterPlayerRef = shooterRef != null ? store.getComponent(shooterRef, PlayerRef.getComponentType()) : null;
            UUID shooterUuid = shooterPlayerRef != null ? shooterPlayerRef.getUuid() : null;
            if (shooterUuid == null && shooterRef != null) {
                UUIDComponent shooterUuidComponent = store.getComponent(shooterRef, UUIDComponent.getComponentType());
                if (shooterUuidComponent != null) {
                    shooterUuid = shooterUuidComponent.getUuid();
                }
            }

            if (projectileUuid != null) {
                impactTracker.markEntityHit(projectileUuid);
            }

            float amountBefore = damage.getAmount();
            damage.setAmount(FLAME_DAMAGE);

            boolean volcanicPlaced = false;
            String volcanicReason = "skip";
            Integer volcanicX = null;
            Integer volcanicY = null;
            Integer volcanicZ = null;
            if (projectileUuid != null) {
                World world = null;
                try {
                    EntityStore external = store.getExternalData();
                    world = external != null ? external.getWorld() : null;
                } catch (Throwable ignored) {
                    world = null;
                }

                if (world == null) {
                    volcanicReason = "worldMissing";
                } else {
                    String posSource = "projectile";
                    TransformComponent projectileTransform = store.getComponent(projectileRef, TransformComponent.getComponentType());
                    Vector3d pos = projectileTransform != null ? projectileTransform.getPosition() : null;

                    if (pos == null || !pos.isFinite()) {
                        posSource = "target";
                        TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
                        pos = targetTransform != null ? targetTransform.getPosition() : null;
                    }
                    if (pos == null || !pos.isFinite()) {
                        volcanicReason = "posInvalid";
                    } else {
                        int x = (int) Math.floor(pos.x);
                        int y = (int) Math.floor(pos.y);
                        int z = (int) Math.floor(pos.z);
                        volcanicX = x;
                        volcanicY = y;
                        volcanicZ = z;

                        int placedY = y;
                        boolean placed = false;
                        String attempt = "none";

                        // Entity hit positions are often in-air; walk down a few blocks to find terrain to convert.
                        for (int dy = 0; dy <= 8; dy++) {
                            int candidateY = y - dy;
                            if (convertBlockAt(world, x, candidateY, z)) {
                                placed = true;
                                placedY = candidateY;
                                attempt = dy == 0 ? "at" : "below" + dy;
                                break;
                            }
                        }

                        volcanicY = placedY;
                        volcanicPlaced = placed;
                        volcanicReason = "posSource=" + posSource + " attempt=" + attempt;
                    }
                }
            }

            boolean burnApplied = false;
            String burnReason = "skip";
            int burnIndex = resolveBurnEffectIndex();
            if (burnIndex < 0) {
                burnReason = "effectNotFound";
            } else {
                EntityEffect burnEffect = EntityEffect.getAssetMap().getAsset(burnIndex);
                if (burnEffect == null) {
                    burnReason = "effectNull";
                } else {
                    try {
                        EffectControllerComponent effects = store.ensureAndGetComponent(targetRef, EffectControllerComponent.getComponentType());
                        burnApplied = effects != null && effects.addEffect(targetRef, burnIndex, burnEffect, store);
                        burnReason = burnApplied ? "applied" : "addEffectFalse";
                    } catch (Throwable t) {
                        burnApplied = false;
                        burnReason = "addEffectException";
                    }
                }
            }

            PlayerRef logPlayer = shooterPlayerRef != null ? shooterPlayerRef : targetPlayerRef;
            debug.traceFileOnly(
                logPlayer,
                "FlameBook event=Damage"
                    + " projectileId=" + projectileAssetName
                    + (causeId != null ? " causeId=" + causeId : "")
                    + " cancelled=" + damage.isCancelled()
                    + (projectileUuid != null ? " projectile.uuid=" + projectileUuid : "")
                    + (shooterUuid != null ? " shooter.uuid=" + shooterUuid : "")
                    + (targetUuid != null ? " target.uuid=" + targetUuid : "")
                    + " damage.amount.before=" + amountBefore
                    + " damage.amount.after=" + damage.getAmount()
                    + " volcanic.blockId=" + VOLCANIC_ROCK_BLOCK_ITEM_ID
                    + (volcanicX != null && volcanicY != null && volcanicZ != null ? " volcanic.targetBlock=[" + volcanicX + "," + volcanicY + "," + volcanicZ + "]" : "")
                    + " volcanic.placed=" + volcanicPlaced
                    + " volcanic.reason=" + volcanicReason
                    + " burn.effectId=" + BURN_EFFECT_ID
                    + " burn.effectIndex=" + burnIndex
                    + " burn.applied=" + burnApplied
                    + " burn.reason=" + burnReason
            );
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "FlameBookProjectileHitSystem: failed to handle damage.", t);
        }
    }

    private static boolean convertBlockAt(@Nonnull World world, int x, int y, int z) {
        if (y < 1 || y > ChunkUtil.HEIGHT_MINUS_1) {
            return false;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfInMemory(chunkIndex);
        }
        if (chunk == null) {
            return false;
        }

        BlockType blockType;
        try {
            blockType = chunk.getBlockType(x, y, z);
        } catch (Throwable ignored) {
            return false;
        }
        if (blockType == null || blockType.isUnknown()) {
            return false;
        }
        if (blockType == BlockType.EMPTY || blockType.getDrawType() == com.hypixel.hytale.protocol.DrawType.Empty) {
            return false;
        }
        if (VOLCANIC_ROCK_BLOCK_ITEM_ID.equals(blockType.getId())) {
            return false;
        }

        int localX = x - (chunk.getX() * CHUNK_WIDTH_BLOCKS);
        int localZ = z - (chunk.getZ() * CHUNK_WIDTH_BLOCKS);

        try {
            return chunk.setBlock(localX, y, localZ, VOLCANIC_ROCK_BLOCK_ITEM_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int resolveBurnEffectIndex() {
        int cached = burnEffectIndex;
        if (cached >= 0) {
            return cached;
        }

        int resolved = EntityEffect.getAssetMap().getIndexOrDefault(BURN_EFFECT_ID, -1);
        if (resolved >= 0) {
            burnEffectIndex = resolved;
        }
        return resolved;
    }
}
