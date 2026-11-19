package org.example.plugin;

import java.util.*;
import org.joml.Vector3d;


final class SoulNeighbours<K> {
    private static final int CELL_SIZE=8;
    private record Cell(int x,int y,int z) {}
    private record Entry(Cell cell,Vector3d position) {}
    private final Map<K,Entry> entries=new HashMap<>();
    private final Map<Cell,Map<K,Vector3d>> cells=new HashMap<>();

    private static Cell cell(Vector3d at){return new Cell(coordinate(at.x),coordinate(at.y),coordinate(at.z));}
    private static int coordinate(double at){return (int)Math.floor(at/CELL_SIZE);}
    void put(K id,Vector3d at){
        remove(id);
        if(at==null||!at.isFinite())return;
        var key=cell(at);var position=new Vector3d(at);
        entries.put(id,new Entry(key,position));
        cells.computeIfAbsent(key,unused->new HashMap<>()).put(id,position);
    }
    void remove(K id){
        var previous=entries.remove(id);if(previous==null)return;
        var bucket=cells.get(previous.cell);bucket.remove(id);
        if(bucket.isEmpty())cells.remove(previous.cell);
    }
    List<Vector3d> near(K self,Vector3d at,double radius){
        var result=box(self,new Vector3d(at).sub(radius,radius,radius),new Vector3d(at).add(radius,radius,radius));
        result.removeIf(other->other.distanceSquared(at)>radius*radius);
        return result;
    }
    List<Vector3d> lane(K self,Vector3d from,Vector3d to){

        return box(self,new Vector3d(Math.min(from.x,to.x)-.75,Math.min(from.y,to.y)-1.55,Math.min(from.z,to.z)-.75),
            new Vector3d(Math.max(from.x,to.x)+.75,Math.max(from.y,to.y)-.05,Math.max(from.z,to.z)+.75));
    }
    private ArrayList<Vector3d> box(K self,Vector3d min,Vector3d max){
        var result=new ArrayList<Vector3d>();
        for(long x=coordinate(min.x);x<=coordinate(max.x);x++)
            for(long y=coordinate(min.y);y<=coordinate(max.y);y++)
                for(long z=coordinate(min.z);z<=coordinate(max.z);z++){
                    var bucket=cells.get(new Cell((int)x,(int)y,(int)z));if(bucket==null)continue;
                    for(var entry:bucket.entrySet())if(!entry.getKey().equals(self)){
                        var at=entry.getValue();
                        if(at.x>=min.x&&at.x<=max.x&&at.y>=min.y&&at.y<=max.y&&at.z>=min.z&&at.z<=max.z)result.add(at);
                    }
                }
        return result;
    }
}
