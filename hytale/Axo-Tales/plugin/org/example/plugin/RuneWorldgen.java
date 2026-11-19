package org.example.plugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import java.util.*;
import javax.annotation.Nonnull;
import org.joml.Vector3d;


final class RuneWorldgen {
    static final String PREFIX="AxoTales_Rune_";
    static final String[] NAMES={"Alfa","Beta","Gamma","Delta","Epsilon","Zeta","Eta","Theta","Iota","Kappa","Lambda","Mu","Nu","Xi","Omicron","Pi","Rho","Sigma","Tau","Upsilon","Phi","Chi","Psi","Omega"};
    static final class Seeded implements Component<ChunkStore> {
        static final int CURRENT=1;
        int densityVersion;
        static final BuilderCodec<Seeded> CODEC=BuilderCodec.builder(Seeded.class,Seeded::new)
            .append(new KeyedCodec<>("DensityVersion",Codec.INTEGER),(s,v)->s.densityVersion=v,s->s.densityVersion).add().build();
        @Override public Seeded clone(){var copy=new Seeded();copy.densityVersion=densityVersion;return copy;}
    }
    record Plan(long seed,int surface,int attached,boolean wood){}
    static Plan plan(long worldSeed,int x,int z){
        long seed=worldSeed^ChunkUtil.indexChunk(x,z)^0xA711F19D5L;var random=new SplittableRandom(seed);
        return new Plan(seed,random.nextDouble()<.5?1:0,random.nextDouble()<.25?1:0,random.nextBoolean());
    }
    static Set<org.joml.Vector3i> retained(long worldSeed,List<org.joml.Vector3i> runes){

        var candidates=new ArrayList<org.joml.Vector3i>();
        for(var at:runes)if((rank(worldSeed,at)&7)==0)candidates.add(at);
        candidates.sort((a,b)->Long.compareUnsigned(rank(worldSeed,a),rank(worldSeed,b)));
        return new HashSet<>(candidates.subList(0,Math.min(2,candidates.size())));
    }
    private static long rank(long seed,org.joml.Vector3i at){
        long h=seed^at.x*0x9E3779B97F4A7C15L^at.y*0xBF58476D1CE4E5B9L^at.z*0x94D049BB133111EBL;
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L;h=(h^(h>>>27))*0x94D049BB133111EBL;return h^(h>>>31);
    }
    private final ComponentType<ChunkStore,Seeded> type;
    private final PluginErrorReporter errors;
    RuneWorldgen(ComponentType<ChunkStore,Seeded> type,PluginErrorReporter errors){this.type=type;this.errors=errors;}
    void onChunkPreLoad(ChunkPreLoadProcessEvent event){
        var saved=event.getHolder().getComponent(type);if(saved!=null&&saved.densityVersion>=Seeded.CURRENT)return;
        boolean legacy=saved!=null;var marker=saved==null?new Seeded():saved;
        event.getHolder().putComponent(type,marker);
        var column=WorldBlocks.preLoad(event);var world=event.getWorld();
        Runnable seed=()->{try{
            long worldSeed=world.getWorldConfig().getSeed();
            if(legacy){
                var ids=new it.unimi.dsi.fastutil.ints.IntArrayList();
                for(String name:NAMES){int id=BlockType.getAssetMap().getIndex(AxoItems.rune(name));if(id>=0)ids.add(id);int legacyIndex=BlockType.getAssetMap().getIndex(PREFIX+name);if(legacyIndex>=0)ids.add(legacyIndex);}
                var existing=column.findBlocks(ids);var keep=retained(worldSeed,existing);
                for(var at:existing)if(!keep.contains(at)&&rune(column.getBlockType(at.x,at.y,at.z))&&!column.clearBlock(at.x,at.y,at.z))throw new IllegalStateException("Could not thin legacy rune at "+at);
                finish(event,marker);return;
            }
            var plan=plan(worldSeed,column.getX(),column.getZ());var random=new SplittableRandom(plan.seed());
            int placed=0;
            for(int attempt=0;attempt<32&&placed<plan.surface();attempt++){
                int x=column.getX()*ChunkUtil.SIZE+1+random.nextInt(ChunkUtil.SIZE-2),z=column.getZ()*ChunkUtil.SIZE+1+random.nextInt(ChunkUtil.SIZE-2);
                int surface=column.getHeight(x,z);var below=column.getBlockType(x,surface,z);
                if(natural(below)&&empty(column,x,surface+1,z)&&column.setBlock(x,surface+1,z,AxoItems.rune(NAMES[random.nextInt(NAMES.length)])))placed++;
            }
            if(plan.attached()>0){
                int attached=0;
                for(int attempt=0;attempt<96&&attached<plan.attached();attempt++){
                    int x=column.getX()*ChunkUtil.SIZE+1+random.nextInt(ChunkUtil.SIZE-2),z=column.getZ()*ChunkUtil.SIZE+1+random.nextInt(ChunkUtil.SIZE-2);
                    int surface=column.getHeight(x,z),y=Math.max(2,surface-random.nextInt(plan.wood()?24:144));
                    var host=column.getBlockType(x,y,z);if(host==null||!host(host.getId()))continue;
                    String id=host.getId().toLowerCase(Locale.ROOT);boolean wood=id.contains("wood")||id.contains("log");if(plan.wood()!=wood)continue;
                    int[][] faces={{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0}};
                    int[] face=faces[random.nextInt(faces.length)];int rx=x+face[0],ry=y+face[1],rz=z+face[2];
                    if(empty(column,rx,ry,rz)&&column.setBlock(rx,ry,rz,AxoItems.rune(NAMES[random.nextInt(NAMES.length)])))attached++;
                }
            }
            finish(event,marker);
        }catch(RuntimeException failure){errors.report((com.hypixel.hytale.server.core.universe.PlayerRef)null,"Rune world generation failed",failure);}};
        if(world.isInThread())seed.run();else world.execute(seed);
    }
    private void finish(ChunkPreLoadProcessEvent event,Seeded marker){
        marker.densityVersion=Seeded.CURRENT;
        var chunks=event.getWorld().getChunkStore();var ref=chunks.getChunkReference(ChunkUtil.indexChunk(event.getChunk().getX(),event.getChunk().getZ()));
        if(ref!=null&&ref.isValid())chunks.getStore().putComponent(ref,type,marker.clone());
        else event.getHolder().putComponent(type,marker);
        event.getChunk().markNeedsSaving();
    }
    private static boolean empty(WorldBlocks.Column column,int x,int y,int z){return y>0&&column.getBlock(x,y,z)==BlockType.EMPTY_ID;}
    static boolean host(String id){String s=id.toLowerCase(Locale.ROOT);return s.startsWith("wood_")&&s.contains("trunk")||s.contains("log")||s.startsWith("ore_")||s.startsWith("rock_ore")||s.equals("arcane_matter_volcanic")||s.equals("ore_stone_parent");}
    private static boolean natural(BlockType block){if(block==null)return false;String id=AxoItems.modifierId(AxoItems.canonical(block.getDefaultStateKey())).toLowerCase(Locale.ROOT);return id.startsWith("soil_")&&(id.contains("grass")||id.contains("dirt"))||host(id);}
    static boolean rune(BlockType block){return AxoItems.rune(block);}
    static final class Collect extends EntityEventSystem<EntityStore,BreakBlockEvent> {
        private final SkillTreeSystem skills;
        Collect(SkillTreeSystem skills){super(BreakBlockEvent.class);this.skills=skills;}
        @Override public Query<EntityStore> getQuery(){return Player.getComponentType();}
        @Override public void handle(int i,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> buffer,@Nonnull BreakBlockEvent event){
            if(event.isCancelled()||!rune(event.getBlockType()))return;
            var ref=chunk.getReferenceTo(i);var at=new org.joml.Vector3i(event.getTargetBlock());


            skills.confirmBreakLater(store,ref,at,event);
        }
    }
}
