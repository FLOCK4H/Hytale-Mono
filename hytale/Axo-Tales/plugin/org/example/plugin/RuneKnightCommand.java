package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Debug helpers for the Rune Knight spawner.
 *
 * <p>Commands:</p>
 * <ul>
 *   <li>{@code /runeknight spawn} - spawns one Rune Knight near you</li>
 *   <li>{@code /runeknight clear} - despawns tracked Rune Knights in your world</li>
 * </ul>
 */
public final class RuneKnightCommand extends AbstractCommandCollection {

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    private final RuneKnightSpawnState spawnState;

    public RuneKnightCommand(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull AxoTalesServerConfig config,
        @Nonnull RuneKnightSpawnState spawnState
    ) {
        super("runeknight", "Rune Knight debug helpers.");
        this.errors = errors;
        this.debug = debug;
        this.config = config;
        this.spawnState = spawnState;
        this.setPermissionGroup(GameMode.Creative);
        this.addSubCommand(new Spawn(errors, debug, config, spawnState));
        this.addSubCommand(new Clear(errors, debug, spawnState));
    }

    private static final class Spawn extends CommandBase {

        private static final double TARGET_HOSTILE_OVERRIDE_SECONDS = 4.0;

        private final PluginErrorReporter errors;
        private final PluginDebugReporter debug;
        private final AxoTalesServerConfig config;
        private final RuneKnightSpawnState spawnState;

        private Spawn(
            @Nonnull PluginErrorReporter errors,
            @Nonnull PluginDebugReporter debug,
            @Nonnull AxoTalesServerConfig config,
            @Nonnull RuneKnightSpawnState spawnState
        ) {
            super("spawn", "Spawns a Rune Knight near you (debug).");
            this.errors = errors;
            this.debug = debug;
            this.config = config;
            this.spawnState = spawnState;
            this.setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            try {
                if (!ctx.isPlayer()) {
                    ctx.sendMessage(Message.raw("This command can only be used by a player."));
                    return;
                }

                PlayerRef player = Universe.get().getPlayer(ctx.sender().getUuid());
                if (player == null) {
                    ctx.sendMessage(Message.raw("Unable to find your player session."));
                    return;
                }

                World world = Universe.get().getWorld(player.getWorldUuid());
                if (world == null) {
                    ctx.sendMessage(Message.raw("Unable to find your current world."));
                    return;
                }

                world.execute(() -> {
                    try {
                        EntityStore entityStore = world.getEntityStore();
                        if (entityStore == null) {
                            player.sendMessage(Message.raw("Unable to access the world entity store."));
                            return;
                        }

                        Store<EntityStore> store = entityStore.getStore();
                        if (store == null) {
                            player.sendMessage(Message.raw("Unable to access the world entity state."));
                            return;
                        }

                        NPCPlugin npcPlugin = NPCPlugin.get();
                        if (npcPlugin == null) {
                            player.sendMessage(Message.raw("NPCPlugin is not available in this server build."));
                            return;
                        }

                        String roleName = config != null && config.runeKnight != null && config.runeKnight.roleName != null && !config.runeKnight.roleName.isBlank()
                            ? config.runeKnight.roleName
                            : RuneKnightSpawnerSystem.DEFAULT_ROLE_NAME;
                        if (!npcPlugin.hasRoleName(roleName)) {
                            player.sendMessage(Message.raw("NPC role not found: " + roleName));
                            return;
                        }

                        Transform t = player.getTransform();
                        Vector3d pos = t != null ? t.getPosition() : null;
                        Vector3d dir = t != null ? t.getDirection() : null;
                        if (pos == null || !pos.isFinite()) {
                            player.sendMessage(Message.raw("Unable to read your position."));
                            return;
                        }

                        if (dir == null || !dir.isFinite() || dir.squaredLength() < 1e-9) {
                            dir = new Vector3d(1, 0, 0);
                        }

                        int x = (int) Math.floor(pos.x + dir.x * 6.0);
                        int z = (int) Math.floor(pos.z + dir.z * 6.0);

                        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
                        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                        if (chunk == null) {
                            chunk = world.getChunkIfInMemory(chunkIndex);
                        }
                        if (chunk == null) {
                            player.sendMessage(Message.raw("Chunk not loaded; move a bit and try again."));
                            return;
                        }

                        int localX = x - (chunk.getX() * 32);
                        int localZ = z - (chunk.getZ() * 32);
                        int surfaceY = chunk.getHeight(localX, localZ);
                        int y = clampY(surfaceY + 1);
                        y = findAirColumn(chunk, x, y, z);
                        if (y < 1) {
                            player.sendMessage(Message.raw("No valid spawn space at the target location."));
                            return;
                        }

                        Vector3d spawnPos = new Vector3d(x + 0.5, y, z + 0.5);
                        it.unimi.dsi.fastutil.Pair<Ref<EntityStore>, ?> spawned = npcPlugin.spawnNPC(store, roleName, null, spawnPos, Vector3f.ZERO);
                        Ref<EntityStore> npcRef = spawned != null ? spawned.left() : null;
                        if (npcRef == null || !npcRef.isValid()) {
                            player.sendMessage(Message.raw("Spawn failed."));
                            return;
                        }

                        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                        if (npc == null) {
                            try {
                                store.removeEntity(npcRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                            } catch (Throwable ignored) {
                                // Best effort.
                            }
                            player.sendMessage(Message.raw("Spawn failed (missing NPC component)."));
                            return;
                        }

                        UUIDComponent uuidComponent = store.getComponent(npcRef, UUIDComponent.getComponentType());
                        UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
                        if (uuid == null) {
                            try {
                                store.removeEntity(npcRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                            } catch (Throwable ignored) {
                                // Best effort.
                            }
                            player.sendMessage(Message.raw("Spawn failed (missing UUID)."));
                            return;
                        }

                        long nowNanos = System.nanoTime();
                        double lifetimeSeconds = config != null && config.runeKnight != null && config.runeKnight.despawn != null
                            ? config.runeKnight.despawn.afterSeconds
                            : 300.0;
                        long lifetimeNanos = lifetimeSeconds > 0 ? (long) (lifetimeSeconds * 1_000_000_000L) : 0L;
                        long expiresAtNanos = lifetimeNanos > 0 ? nowNanos + lifetimeNanos : 0L;
                        spawnState.trackSpawn(world, uuid, nowNanos, expiresAtNanos);
                        tryAggroOnSpawn(entityStore, npc, uuid, player.getUuid(), roleName, world);

                        player.sendMessage(Message.raw("Spawned Rune Knight (" + roleName + ") at x=" + x + " y=" + y + " z=" + z + "."));
                        debug.traceFileOnly(
                            player,
                            "RuneKnightCommand event=spawn"
                                + " roleName=" + roleName
                                + " uuid=" + uuid
                                + " x=" + x
                                + " y=" + y
                                + " z=" + z
                                + " world=" + world.getName()
                        );
                    } catch (Throwable t2) {
                        errors.report(player, "Failed to run /runeknight spawn.", t2);
                    }
                });
            } catch (Throwable t) {
                errors.report(ctx, "Failed to run /runeknight spawn.", t);
            }
        }

        private void tryAggroOnSpawn(
            @Nonnull EntityStore entityStore,
            @Nonnull NPCEntity npc,
            @Nonnull UUID knightUuid,
            @Nonnull UUID anchorPlayerUuid,
            @Nonnull String roleName,
            @Nonnull World world
        ) {
            Ref<EntityStore> playerEntityRef = entityStore.getRefFromUUID(anchorPlayerUuid);
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return;
            }

            Role role = npc.getRole();
            if (role == null || role.getMarkedEntitySupport() == null || role.getWorldSupport() == null) {
                return;
            }

            try {
                role.getWorldSupport().overrideAttitude(playerEntityRef, Attitude.HOSTILE, TARGET_HOSTILE_OVERRIDE_SECONDS);
            } catch (Throwable ignored) {
                // Best effort.
            }

            try {
                role.setMarkedTarget(MarkedEntitySupport.DEFAULT_TARGET_SLOT, playerEntityRef);
                role.getWorldSupport().requestNewPath();
                role.notifySensorMatch();
            } catch (Throwable ignored) {
                // Best effort.
            }

            debug.traceFileOnly(
                null,
                "RuneKnightCommand event=aggroOnSpawn"
                    + " roleName=" + roleName
                    + " knightUuid=" + knightUuid
                    + " anchorPlayer=" + anchorPlayerUuid
                    + " hostileOverrideSeconds=" + TARGET_HOSTILE_OVERRIDE_SECONDS
                    + " world=" + world.getName()
            );
        }

        private static int findAirColumn(@Nonnull WorldChunk chunk, int x, int y, int z) {
            int base = clampY(y);
            for (int dy = 0; dy <= 4; dy++) {
                int tryY = clampY(base + dy);
                if (tryY < 1 || tryY >= ChunkUtil.HEIGHT_MINUS_1) {
                    continue;
                }
                if (!isAir(chunk, x, tryY, z)) {
                    continue;
                }
                if (!isAir(chunk, x, tryY + 1, z)) {
                    continue;
                }
                if (isAir(chunk, x, tryY - 1, z)) {
                    continue;
                }
                return tryY;
            }
            return -1;
        }

        private static boolean isAir(@Nonnull WorldChunk chunk, int x, int y, int z) {
            try {
                var type = chunk.getBlockType(x, y, z);
                return type == null
                    || type.isUnknown()
                    || type == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
                    || type.getDrawType() == com.hypixel.hytale.protocol.DrawType.Empty;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static int clampY(int y) {
            return Math.max(1, Math.min(ChunkUtil.HEIGHT_MINUS_1, y));
        }
    }

    private static final class Clear extends CommandBase {

        private final PluginErrorReporter errors;
        private final PluginDebugReporter debug;
        private final RuneKnightSpawnState spawnState;

        private Clear(@Nonnull PluginErrorReporter errors, @Nonnull PluginDebugReporter debug, @Nonnull RuneKnightSpawnState spawnState) {
            super("clear", "Despawns tracked Rune Knights in your world (debug).");
            this.errors = errors;
            this.debug = debug;
            this.spawnState = spawnState;
            this.setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            try {
                if (!ctx.isPlayer()) {
                    ctx.sendMessage(Message.raw("This command can only be used by a player."));
                    return;
                }

                PlayerRef player = Universe.get().getPlayer(ctx.sender().getUuid());
                if (player == null) {
                    ctx.sendMessage(Message.raw("Unable to find your player session."));
                    return;
                }

                World world = Universe.get().getWorld(player.getWorldUuid());
                if (world == null) {
                    ctx.sendMessage(Message.raw("Unable to find your current world."));
                    return;
                }

                world.execute(() -> {
                    try {
                        EntityStore entityStore = world.getEntityStore();
                        if (entityStore == null) {
                            player.sendMessage(Message.raw("Unable to access the world entity store."));
                            return;
                        }

                        Store<EntityStore> store = entityStore.getStore();
                        if (store == null) {
                            player.sendMessage(Message.raw("Unable to access the world entity state."));
                            return;
                        }

                        int removed = 0;
                        for (RuneKnightSpawnState.ActiveRuneKnight active : spawnState.snapshot(world)) {
                            if (active == null || active.uuid() == null) {
                                continue;
                            }
                            UUID uuid = active.uuid();
                            var ref = store.getExternalData() != null ? store.getExternalData().getRefFromUUID(uuid) : null;
                            if (ref == null || !ref.isValid()) {
                                spawnState.remove(world, uuid);
                                continue;
                            }
                            try {
                                store.removeEntity(ref, com.hypixel.hytale.component.RemoveReason.REMOVE);
                                removed++;
                            } catch (Throwable ignored) {
                                // Best effort.
                            } finally {
                                spawnState.remove(world, uuid);
                            }
                        }

                        player.sendMessage(Message.raw("Removed " + removed + " tracked Rune Knights."));
                        debug.traceFileOnly(player, "RuneKnightCommand event=clear removed=" + removed + " world=" + world.getName());
                    } catch (Throwable t2) {
                        errors.report(player, "Failed to run /runeknight clear.", t2);
                    }
                });
            } catch (Throwable t) {
                errors.report(ctx, "Failed to run /runeknight clear.", t);
            }
        }
    }
}
