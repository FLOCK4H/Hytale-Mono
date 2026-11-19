package org.example.plugin;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;






public final class WorldBlocks {
    private WorldBlocks() {}


    @Nullable
    public static Column loaded(@Nonnull World world, long index) {
        Ref<ChunkStore> ref = world.getChunkStore().getChunkReference(index);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return new Column(world, ChunkUtil.xOfChunkIndex(index), ChunkUtil.zOfChunkIndex(index), null);
    }


    @Nonnull
    public static Column preLoad(@Nonnull ChunkPreLoadProcessEvent event) {
        WorldChunk chunk = event.getChunk();
        return new Column(event.getWorld(), chunk.getX(), chunk.getZ(), event.getHolder());
    }


    public static final class Column {
        private final World world;
        private final int chunkX;
        private final int chunkZ;
        private final Holder<ChunkStore> preLoadHolder;

        private Column(World world, int chunkX, int chunkZ, @Nullable Holder<ChunkStore> preLoadHolder) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.preLoadHolder = preLoadHolder;
        }


        public World getWorld() { return world; }


        public int getX() { return chunkX; }


        public int getZ() { return chunkZ; }


        public int getBlock(int x, int y, int z) {
            BlockSection section = blockSection(y);
            return section == null ? BlockType.EMPTY_ID : section.get(x, y, z);
        }


        @Nullable
        public BlockType getBlockType(int x, int y, int z) {
            BlockSection section = blockSection(y);
            return section == null ? null : BlockType.getAssetMap().getAsset(section.get(x, y, z));
        }


        public int getHeight(int x, int z) {
            HeightmapColumn heightmap = heightmap();
            if (heightmap == null) {
                throw new IllegalStateException("Column heightmap is not available");
            }
            return heightmap.getHeight(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK);
        }


        public boolean setBlock(int x, int y, int z, int blockId) {
            BlockType type = BlockType.getAssetMap().getAsset(blockId);
            if (type == null) {
                return false;
            }
            String canonical=AxoItems.blockId(type);String original=type.getDefaultStateKey()==null?type.getId():type.getDefaultStateKey();
            if(!canonical.equals(original)){
                var base=BlockType.getAssetMap().getAsset(canonical);if(base==null)return false;
                var state=base.getBlockForState(type.getCurrentInteractionState());type=state==null?base:state;
                blockId=BlockType.getAssetMap().getIndex(type.getId());if(blockId<0)return false;
            }
            ChunkStore grid = world.getChunkStore();
            Ref<ChunkStore> sectionRef = sectionReference(y);
            if (sectionRef != null && sectionRef.isValid()) {
                return BlockOperations.setBlock(grid, sectionRef, x, y, z, blockId, type, 0, 0, 0);
            }

            Holder<ChunkStore> holder = sectionHolder(y);
            if (holder == null) {
                return false;
            }
            BlockSection blocks = holder.getComponent(BlockSection.getComponentType());
            ChunkSection section = holder.getComponent(ChunkSection.getComponentType());
            if (blocks == null || section == null) {
                return false;
            }
            boolean changed = BlockOperations.setBlockPreLoad(section, blocks, x, y, z, blockId, 0, 0);
            if (changed) {
                HeightmapColumn heightmap = heightmap();
                if (heightmap != null) {
                    heightmap.onSectionGenerated(ChunkUtil.indexSection(y), blocks);
                }
            }
            return changed;
        }


        public boolean setBlock(int x, int y, int z, @Nonnull String blockId) {
            int index = BlockType.getAssetMap().getIndex(AxoItems.canonical(blockId));
            return index >= 0 && setBlock(x, y, z, index);
        }


        public boolean clearBlock(int x, int y, int z) {
            return setBlock(x, y, z, BlockType.EMPTY_ID);
        }


        public int getFluidId(int x,int y,int z) {
            var ref=sectionReference(y);
            var type=com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection.getComponentType();
            var holder=sectionHolder(y);
            var fluid=ref!=null&&ref.isValid()?world.getChunkStore().getStore().getComponent(ref,type):holder==null?null:holder.getComponent(type);
            return fluid==null?0:fluid.getFluidId(x,y,z);
        }


        @SuppressWarnings("deprecation")
        public java.util.List<org.joml.Vector3i> findBlocks(it.unimi.dsi.fastutil.ints.IntList ids) {


            var expanded=new it.unimi.dsi.fastutil.ints.IntArrayList(ids);
            for(int id:ids){var type=BlockType.getAssetMap().getAsset(id);if(type==null)continue;
                String key=AxoItems.blockId(type);
                for(var entry:AxoItems.LEGACY.entrySet())if(entry.getValue().equals(key)){
                    int legacy=BlockType.getAssetMap().getIndex(entry.getKey()),current=BlockType.getAssetMap().getIndex(key);
                    if(legacy>=0&&!expanded.contains(legacy))expanded.add(legacy);if(current>=0&&!expanded.contains(current))expanded.add(current);
                }
            }
            var result=new java.util.ArrayList<org.joml.Vector3i>();
            for(int sectionIndex=0;sectionIndex<ChunkUtil.HEIGHT_SECTIONS;sectionIndex++) {
                BlockSection blocks=blockSection(sectionIndex*ChunkUtil.SIZE);
                if(blocks==null||!blocks.containsAny(expanded))continue;
                int baseY=sectionIndex*ChunkUtil.SIZE;
                blocks.find(expanded,(java.util.function.IntConsumer)index->result.add(new org.joml.Vector3i(
                    chunkX*ChunkUtil.SIZE+ChunkUtil.xFromIndex(index),baseY+ChunkUtil.yFromIndex(index),chunkZ*ChunkUtil.SIZE+ChunkUtil.zFromIndex(index))));
            }
            return result;
        }

        @Nullable
        private Ref<ChunkStore> sectionReference(int y) {
            return world.getChunkStore().getChunkSectionReference(chunkX, ChunkUtil.indexSection(y), chunkZ);
        }

        @Nullable
        private BlockSection blockSection(int y) {
            Ref<ChunkStore> ref = sectionReference(y);
            if (ref != null && ref.isValid()) {
                return world.getChunkStore().getStore().getComponent(ref, BlockSection.getComponentType());
            }
            Holder<ChunkStore> holder = sectionHolder(y);
            return holder == null ? null : holder.getComponent(BlockSection.getComponentType());
        }

        @Nullable
        @SuppressWarnings("deprecation")
        private Holder<ChunkStore> sectionHolder(int y) {
            if (preLoadHolder == null) {
                return null;
            }
            ChunkColumn column = preLoadHolder.getComponent(ChunkColumn.getComponentType());
            Holder<ChunkStore>[] sections = column == null ? null : column.getSectionHolders();
            int index = ChunkUtil.indexSection(y);
            return sections == null || index < 0 || index >= sections.length ? null : sections[index];
        }

        @Nullable
        private HeightmapColumn heightmap() {
            ChunkStore grid = world.getChunkStore();
            Ref<ChunkStore> ref = grid.getChunkReference(ChunkUtil.indexChunk(chunkX, chunkZ));
            if (ref != null && ref.isValid()) {
                return grid.getStore().getComponent(ref, HeightmapColumn.getComponentType());
            }
            return preLoadHolder == null ? null : preLoadHolder.getComponent(HeightmapColumn.getComponentType());
        }
    }
}
