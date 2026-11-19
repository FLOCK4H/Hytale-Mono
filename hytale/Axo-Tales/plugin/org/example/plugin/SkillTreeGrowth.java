package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import org.joml.Vector3i;


final class SkillTreeGrowth implements Component<ChunkStore> {
    static final String ITEM="Axo_Skill_Tree";
    static final String[] STAGES={"default","Sapling","Young","Awakening","Mature"};
    static final BuilderCodec<SkillTreeGrowth> CODEC=BuilderCodec.builder(SkillTreeGrowth.class,SkillTreeGrowth::new)
        .append(new KeyedCodec<>("PlantedAt",Codec.LONG),(s,v)->s.plantedAt=Math.max(0,v),s->s.plantedAt).add().build();
    long plantedAt;
    static int stage(long plantedAt,long now){long age=Math.max(0,now-plantedAt);return age>=900_000?4:age>=600_000?3:age>=360_000?2:age>=180_000?1:0;}
    @Override public SkillTreeGrowth clone(){var copy=new SkillTreeGrowth();copy.plantedAt=plantedAt;return copy;}
    static final class GrowthSystem extends TickingSystem<ChunkStore> {
        private final ComponentType<ChunkStore,SkillTreeGrowth> type;
        private final PluginErrorReporter errors;
        GrowthSystem(ComponentType<ChunkStore,SkillTreeGrowth> type,PluginErrorReporter errors){this.type=type;this.errors=errors;}
        @Override public void tick(float delta,int tick,@Nonnull Store<ChunkStore> store){
            long now=System.currentTimeMillis();
            store.forEachChunk(Query.and(type,BlockModule.BlockStateInfo.getComponentType()),(chunk,buffer)->{
                for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);buffer.run(access->{
                    if(!ref.isValid())return;
                    try{
                        var state=access.getComponent(ref,type);var info=access.getComponent(ref,BlockModule.BlockStateInfo.getComponentType());
                        var at=new Vector3i();if(state==null||info==null||!info.fillWorldPos(access,at))return;
                        if(state.plantedAt==0){state=state.clone();state.plantedAt=now;access.putComponent(ref,type,state);info.markNeedsSaving(access);}
                        var grid=access.getExternalData();var column=WorldBlocks.loaded(grid.getWorld(),ChunkUtil.indexChunkFromBlock(at.x,at.z));
                        var block=column==null?null:column.getBlockType(at.x,at.y,at.z);
                        if(!AxoItems.block(block,ITEM))return;
                        String target=STAGES[stage(state.plantedAt,now)];var desired=block.getBlockForState(target);
                        if(desired==null||desired.getId().equals(block.getId()))return;
                        var section=grid.getChunkSectionReference(ChunkUtil.chunkCoordinate(at.x),ChunkUtil.indexSection(at.y),ChunkUtil.chunkCoordinate(at.z));
                        if(section!=null&&section.isValid())BlockOperations.setBlockInteractionState(grid,section,at.x,at.y,at.z,block,target,true);
                    }catch(RuntimeException failure){errors.report((com.hypixel.hytale.server.core.universe.PlayerRef)null,"Skill Tree growth failed",failure);}
                });}
            });
        }
    }
}
