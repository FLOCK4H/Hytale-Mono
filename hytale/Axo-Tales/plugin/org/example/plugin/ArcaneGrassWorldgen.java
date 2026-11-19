package org.example.plugin;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import java.util.*;


final class ArcaneGrassWorldgen {
    static final String BLOCK="Axo_Arcane_Grass";
    private static final Set<String> HOSTS=Set.of("Soil_Grass","Soil_Grass_Full","Soil_Grass_Cold","Soil_Grass_Cold_Full",
        "Soil_Grass_Deep","Soil_Grass_Deep_Full","Soil_Grass_Dry","Soil_Grass_Dry_Full","Soil_Grass_Sunny",
        "Soil_Grass_Sunny_Full","Soil_Grass_Wet","Soil_Grass_Wet_Full","Rock_Stone","Rock_Stone_Mossy",
        "Rock_Stone_Aqua","Rock_Slate","Rock_Basalt","Rock_Marble","Rock_Granite","Rock_Sandstone");
    record Cell(int x,int z){}
    interface Surface {
        int height(int x,int z);
        String block(int x,int y,int z);
        boolean dryAir(int x,int y,int z);
        boolean replace(int x,int y,int z);
    }
    static boolean host(String id){return id!=null&&HOSTS.contains(id);}
    static Set<Cell> plan(long seed,int chunkX,int chunkZ,double chance,int patches,int radius) {
        if(!Double.isFinite(chance)||chance<=0)return Set.of();
        var random=new SplittableRandom(seed^((long)chunkX*0x9E3779B97F4A7C15L)^((long)chunkZ*0xBF58476D1CE4E5B9L)^0xA8CA6E6A55L);
        if(random.nextDouble()>=Math.min(1,chance))return Set.of();
        radius=Math.clamp(radius,1,5);patches=Math.clamp(patches,0,3);
        var cells=new LinkedHashSet<Cell>();
        for(int patch=0;patch<patches;patch++) {
            int cx=radius+random.nextInt(32-2*radius),cz=radius+random.nextInt(32-2*radius);
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)
                if(dx*dx+dz*dz<=radius*radius&&random.nextDouble()<.8)cells.add(new Cell(cx+dx,cz+dz));
        }
        return cells;
    }
    static int scatter(boolean newlyGenerated,Surface surface,Set<Cell> cells) {
        if(!newlyGenerated)return 0;
        int placed=0;
        for(var at:cells) {
            int y=surface.height(at.x,at.z);
            if(y<1||!host(surface.block(at.x,y,at.z))||!surface.dryAir(at.x,y+1,at.z))continue;
            if(surface.replace(at.x,y,at.z))placed++;
        }
        return placed;
    }
    private final java.util.concurrent.atomic.AtomicInteger logBudget=new java.util.concurrent.atomic.AtomicInteger(16);
    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final AxoTalesServerConfig config;
    ArcaneGrassWorldgen(PluginErrorReporter errors,PluginDebugReporter debug,AxoTalesServerConfig config){this.errors=errors;this.debug=debug;this.config=config;}
    void onChunkPreLoad(ChunkPreLoadProcessEvent event) {
        var settings=config.worldgen==null?null:config.worldgen.arcaneGrass;
        if(!event.isNewlyGenerated()||settings==null||!settings.enabled)return;
        var world=event.getWorld();var column=WorldBlocks.preLoad(event);
        var cells=plan(world.getWorldConfig().getSeed(),column.getX(),column.getZ(),settings.chancePerNewChunk,settings.patchesPerChunk,settings.radius);
        if(cells.isEmpty())return;
        Runnable work=()->{try{
            if(BlockType.getAssetMap().getIndex(BLOCK)<0)throw new IllegalStateException("Missing Arcane Grass block");
            int placed=scatter(true,new Surface(){
                public int height(int x,int z){return column.getHeight(x,z);}
                public String block(int x,int y,int z){var type=column.getBlockType(x,y,z);return type==null?"":type.getId();}
                public boolean dryAir(int x,int y,int z){return column.getBlock(x,y,z)==BlockType.EMPTY_ID&&column.getFluidId(x,y,z)==0;}
                public boolean replace(int x,int y,int z){return column.setBlock(x,y,z,BLOCK);}
            },cells);
            if(placed>0){event.getChunk().markNeedsSaving();if(logBudget.getAndDecrement()>0)debug.traceFileOnly(null,"Worldgen.ArcaneGrass event=newTerrain chunkX="+column.getX()+" chunkZ="+column.getZ()+" replaced="+placed+" maxCandidates="+cells.size());}
        }catch(RuntimeException failure){errors.report((com.hypixel.hytale.server.core.universe.PlayerRef)null,"Arcane Grass generation failed",failure);}};
        if(world.isInThread())work.run();else world.execute(work);
    }
}
