package org.example.plugin;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;


public final class AncientArrowState implements Component<EntityStore> {
    Ref<EntityStore> owner,target;
    double elapsed;
    boolean finished;
    AncientArrowState(){}
    AncientArrowState(Ref<EntityStore> owner,Ref<EntityStore> target){this.owner=owner;this.target=target;}
    static final double SPEED=72,RADIUS=4,MAX_FLIGHT=5;
    static final int DIRECT_DAMAGE=180,BLAST_DAMAGE=60;
    static Vector3d step(Vector3d position,Vector3d target,double seconds){
        var delta=new Vector3d(target).sub(position);double distance=delta.length();
        if(!Double.isFinite(distance)||!Double.isFinite(seconds)||seconds<=0)return new Vector3d(position);
        return distance<=SPEED*seconds?new Vector3d(target):new Vector3d(position).add(delta.mul(SPEED*seconds/distance));
    }
    boolean spend(){if(finished)return false;finished=true;return true;}
    @Override public AncientArrowState clone(){var copy=new AncientArrowState(owner,target);copy.elapsed=elapsed;copy.finished=finished;return copy;}
}
