package org.example.plugin;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.*;
import org.joml.Vector3d;
import org.joml.Vector3dc;


final class GrayCatTerrain {
    static boolean clear(World world,int x,int y,int z){
        if(y<=Integer.MIN_VALUE+2 || y>=Integer.MAX_VALUE-2)return false;
        var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(x,z));if(column==null)return false;
        var floor=column.getBlockType(x,y-1,z);if(floor==null || floor.isUnknown() || floor.getDrawType()!=DrawType.Cube)return false;
        for(int dy=0;dy<2;dy++){
            var ref=world.getChunkStore().getChunkSectionReferenceAtBlock(x,y+dy,z);if(ref==null || !ref.isValid())return false;
            var block=column.getBlockType(x,y+dy,z);if(block==null || block.isUnknown() || block!=BlockType.EMPTY && block.getDrawType()!=DrawType.Empty)return false;
            var fluid=world.getChunkStore().getStore().getComponent(ref,FluidSection.getComponentType());if(fluid!=null && fluid.getFluidId(x,y+dy,z)!=0)return false;
        }
        return true;
    }
    static Vector3d near(World world,Vector3dc owner){
        if(owner==null || !owner.isFinite())return null;
        int base=(int)Math.floor(owner.y());
        for(int radius:new int[]{2,3,4,6})for(int step=0;step<12;step++){
            double angle=step*Math.PI/6;int x=(int)Math.floor(owner.x()+Math.cos(angle)*radius),z=(int)Math.floor(owner.z()+Math.sin(angle)*radius);
            for(int dy:new int[]{0,-1,1,-2,2,-3,3,-4,4})if(clear(world,x,base+dy,z))return new Vector3d(x+.5,base+dy,z+.5);
        }
        return null;
    }
}
