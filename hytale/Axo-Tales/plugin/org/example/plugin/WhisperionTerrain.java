package org.example.plugin;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.*;


final class WhisperionTerrain {
    @FunctionalInterface interface Ground {boolean clear(int x,int y,int z);}
    static List<Vector3d> candidates(Vector3dc owner,float yaw) {
        if(owner==null || !owner.isFinite() || !Float.isFinite(yaw))return List.of();
        var result=new ArrayList<Vector3d>();
        for(int radius:new int[]{4,6,8,10})for(double angle:new double[]{0,Math.PI/6,-Math.PI/6,Math.PI/3,-Math.PI/3}) {
            double rear=yaw+angle;
            result.add(new Vector3d(owner).add(Math.sin(rear)*radius,0,Math.cos(rear)*radius));
        }
        return result;
    }
    static Vector3d near(World world,Vector3dc owner,float yaw) {
        return find(owner,yaw,(x,y,z)->clear(world,x,y,z));
    }
    static Vector3d find(Vector3dc owner,float yaw,Ground ground) {
        for(var at:candidates(owner,yaw)) {
            int x=(int)Math.floor(at.x),z=(int)Math.floor(at.z),base=(int)Math.floor(owner.y());
            for(int dy:new int[]{0,-1,1,-2,2,-3,3,-4,4,-6,6,-8,8})
                if(ground.clear(x,base+dy,z))return new Vector3d(x+.5,base+dy,z+.5);
        }
        return null;
    }
    static boolean supported(World world,Vector3dc feet) {
        int y=(int)Math.floor(feet.y()-.15);
        var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock((int)Math.floor(feet.x()),(int)Math.floor(feet.z())));
        var block=column==null?null:column.getBlockType((int)Math.floor(feet.x()),y,(int)Math.floor(feet.z()));
        return block!=null && !block.isUnknown() && block.getDrawType()==DrawType.Cube && feet.y()-(y+1)<=.3;
    }
    private static boolean clear(World world,int x,int y,int z) {
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(x+dx,z+dz));if(column==null)return false;
            var floor=column.getBlockType(x+dx,y-1,z+dz);
            if(floor==null || floor.isUnknown() || floor.getDrawType()!=DrawType.Cube)return false;
            for(int dy=0;dy<4;dy++) {
                var section=world.getChunkStore().getChunkSectionReferenceAtBlock(x+dx,y+dy,z+dz);
                if(section==null || !section.isValid())return false;
                var block=column.getBlockType(x+dx,y+dy,z+dz);
                if(block==null || block.isUnknown() || block!=BlockType.EMPTY && block.getDrawType()!=DrawType.Empty)return false;
                var fluid=world.getChunkStore().getStore().getComponent(section,FluidSection.getComponentType());
                if(fluid!=null && fluid.getFluidId(x+dx,y+dy,z+dz)!=0)return false;
            }
        }
        return true;
    }
}
