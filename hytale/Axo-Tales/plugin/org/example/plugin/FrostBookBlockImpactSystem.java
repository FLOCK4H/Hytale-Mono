package org.example.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.collision.BlockCollisionData;
import com.hypixel.hytale.server.core.modules.collision.CollisionMaterial;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Frost Book projectile terrain impacts by replacing the impacted block with Snow Bricks.
 *
 * <p>This is intentionally implemented for the legacy {@link ProjectileComponent} system by raycasting between the
 * last in-flight position and the current position every tick. Relying on {@code SimplePhysicsProvider.isImpacted()}
 * proved unreliable for some projectile configurations.</p>
 */
public final class FrostBookBlockImpactSystem extends TickingSystem<EntityStore> {

    private static final String FROST_PROJECTILE_ID = FrostBookProjectileHitSystem.FROST_PROJECTILE_ID;
    private static final String SNOW_BRICK_BLOCK_ITEM_ID = "Soil_Snow_Brick";

    private static final int CHUNK_WIDTH_BLOCKS = ChunkUtil.SIZE;
    private static final Box RAY_POINT_BOX = new Box(0, 0, 0, 0.01, 0.01, 0.01);

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final FrostBookImpactTracker impactTracker;

    private final Map<UUID, Vector3d> lastPositionByProjectile = new ConcurrentHashMap<>();

    public FrostBookBlockImpactSystem(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull FrostBookImpactTracker impactTracker
    ) {
        this.errors = errors;
        this.debug = debug;
        this.impactTracker = impactTracker;
    }

    @Override
    public void tick(float delta, int tickCount, @Nonnull Store<EntityStore> store) {
        try {
            EntityStore external = store.getExternalData();
            World world = external != null ? external.getWorld() : null;
            if (world == null) {
                return;
            }

            java.util.ArrayList<Ref<EntityStore>> removeRefs = new java.util.ArrayList<>();
            store.forEachChunk(
                Query.and(
                    ProjectileComponent.getComponentType(),
                    TransformComponent.getComponentType(),
                    UUIDComponent.getComponentType()
                ),
                (@Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> projectileRef = chunk.getReferenceTo(i);
                        if (projectileRef == null || !projectileRef.isValid()) {
                            continue;
                        }

                        ProjectileComponent projectile = chunk.getComponent(i, ProjectileComponent.getComponentType());
                        if (projectile == null) {
                            continue;
                        }

                        String projectileAssetName = projectile.getProjectileAssetName();
                        if (!FROST_PROJECTILE_ID.equals(projectileAssetName)) {
                            continue;
                        }

                        UUIDComponent uuidComponent = chunk.getComponent(i, UUIDComponent.getComponentType());
                        UUID projectileUuid = uuidComponent != null ? uuidComponent.getUuid() : null;
                        if (projectileUuid == null) {
                            continue;
                        }

                        TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                        Vector3d position = transform != null ? transform.getPosition() : null;
                        if (position == null || !position.isFinite()) {
                            cleanupProjectile(projectileUuid);
                            continue;
                        }

                        if (impactTracker.hasEntityHit(projectileUuid)) {
                            impactTracker.clearEntityHit(projectileUuid);
                            cleanupProjectile(projectileUuid);
                            removeRefs.add(projectileRef);
                            continue;
                        }

                        Vector3d lastPosition = lastPositionByProjectile.get(projectileUuid);
                        BlockCollisionData hit = null;
                        String hitReason = "raycast";
                        if (lastPosition == null || !lastPosition.isFinite()) {
                            lastPositionByProjectile.put(projectileUuid, new Vector3d(position));
                            continue;
                        }

                        Vector3d deltaVec = new Vector3d(position).subtract(lastPosition);
                        double length = deltaVec.length();
                        if (!Double.isFinite(length) || length <= 1e-6) {
                            int baseX = (int) Math.floor(position.x);
                            int baseY = (int) Math.floor(position.y);
                            int baseZ = (int) Math.floor(position.z);

                            boolean placed = false;
                            int placedX = baseX;
                            int placedY = baseY;
                            int placedZ = baseZ;

                            // When we first observe a projectile after it already impacted and stopped moving, we may
                            // not have an in-flight delta to raycast. Try nearby blocks best-effort.
                            int[][] offsets = new int[][] {
                                new int[] {0, 0, 0},
                                new int[] {0, -1, 0},
                                new int[] {0, 1, 0},
                                new int[] {1, 0, 0},
                                new int[] {-1, 0, 0},
                                new int[] {0, 0, 1},
                                new int[] {0, 0, -1},
                                new int[] {1, -1, 0},
                                new int[] {-1, -1, 0},
                                new int[] {0, -1, 1},
                                new int[] {0, -1, -1},
                            };

                            for (int[] o : offsets) {
                                int x = baseX + o[0];
                                int y = baseY + o[1];
                                int z = baseZ + o[2];
                                if (freezeBlockAt(world, x, y, z)) {
                                    placed = true;
                                    placedX = x;
                                    placedY = y;
                                    placedZ = z;
                                    break;
                                }
                            }

                            if (placed) {
                                debug.traceFileOnly(
                                    (PlayerRef) null,
                                    "FrostBook event=projectileImpact"
                                        + " projectileId=" + projectileAssetName
                                        + " projectile.uuid=" + projectileUuid
                                        + " impact.reason=stationarySearch"
                                        + " impact.block=[" + placedX + "," + placedY + "," + placedZ + "]"
                                        + " snowBlockId=" + SNOW_BRICK_BLOCK_ITEM_ID
                                        + " placed=true"
                                );

                                cleanupProjectile(projectileUuid);
                                removeRefs.add(projectileRef);
                                continue;
                            }

                            lastPositionByProjectile.put(projectileUuid, new Vector3d(position));
                            continue;
                        }

                        hit = raycastSolidBlock(world, lastPosition, deltaVec, length + 0.5);
                        if (hit == null) {
                            lastPositionByProjectile.put(projectileUuid, new Vector3d(position));
                            continue;
                        }

                        int blockX = hit.x;
                        int blockY = hit.y;
                        int blockZ = hit.z;
                        boolean placed = freezeBlockAt(world, blockX, blockY, blockZ);
                        debug.traceFileOnly(
                            (PlayerRef) null,
                            "FrostBook event=projectileImpact"
                                + " projectileId=" + projectileAssetName
                                + " projectile.uuid=" + projectileUuid
                                + " impact.reason=" + hitReason
                                + " impact.block=[" + blockX + "," + blockY + "," + blockZ + "]"
                                + " snowBlockId=" + SNOW_BRICK_BLOCK_ITEM_ID
                                + " placed=" + placed
                        );

                        cleanupProjectile(projectileUuid);
                        removeRefs.add(projectileRef);
                    }
                }
            );

            for (Ref<EntityStore> ref : removeRefs) {
                removeProjectileBestEffort(store, ref);
            }
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "FrostBookBlockImpactSystem: tick failed.", t);
        }
    }

    private static void removeProjectileBestEffort(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> projectileRef) {
        try {
            store.removeEntity(projectileRef, RemoveReason.REMOVE);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private void cleanupProjectile(@Nonnull UUID projectileUuid) {
        lastPositionByProjectile.remove(projectileUuid);
    }

    private boolean freezeBlockAt(@Nonnull World world, int x, int y, int z) {
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
        if (SNOW_BRICK_BLOCK_ITEM_ID.equals(blockType.getId())) {
            return false;
        }

        int localX = x - (chunk.getX() * CHUNK_WIDTH_BLOCKS);
        int localZ = z - (chunk.getZ() * CHUNK_WIDTH_BLOCKS);

        try {
            return chunk.setBlock(localX, y, localZ, SNOW_BRICK_BLOCK_ITEM_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static @Nullable BlockCollisionData raycastSolidBlock(
        @Nonnull World world,
        @Nonnull Vector3d origin,
        @Nonnull Vector3d direction,
        double maxDistance
    ) {
        if (!Double.isFinite(maxDistance) || maxDistance <= 0) {
            return null;
        }
        Vector3d ray = new Vector3d(direction).normalize().scale(maxDistance);
        CollisionResult result = new CollisionResult(false, false);
        result.setCollisionByMaterial(CollisionMaterial.MATERIAL_SOLID);
        CollisionModule.findBlockCollisionsIterative(world, RAY_POINT_BOX, origin, ray, true, result);
        return result.getFirstBlockCollision();
    }

    public void shutdown() {
        lastPositionByProjectile.clear();
    }
}
