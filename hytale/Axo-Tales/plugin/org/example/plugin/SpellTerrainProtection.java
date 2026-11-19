package org.example.plugin;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;


final class SpellTerrainProtection {
    private SpellTerrainProtection() {}

    static boolean stateful(BlockType type) {
        return type == null || type.isUnknown() || type.getBlockEntity() != null || type.getBench() != null;
    }

    static boolean protectedAt(World world, int x, int y, int z, BlockType type) {
        if (stateful(type)) return true;
        try {

            var base = BlockOperations.resolveBaseBlockPosition(world.getChunkStore(), new BlockPosition(x, y, z));
            var column = WorldBlocks.loaded(world, com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(base.x, base.z));
            if (column == null || stateful(column.getBlockType(base.x, base.y, base.z))) return true;
            return BlockModule.getBlockEntity(world, base.x, base.y, base.z) != null;
        } catch (RuntimeException unavailable) {

            return true;
        }
    }
}
