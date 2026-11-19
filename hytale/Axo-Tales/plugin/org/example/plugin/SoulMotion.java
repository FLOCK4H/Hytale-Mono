package org.example.plugin;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import org.joml.Vector3d;


final class SoulMotion {
    static final double FOLLOW_LIMIT=20;

    static Vector3d roam(Vector3d self,Vector3d owner,SoulState state,float delta) {
        state.wanderSeconds+=Math.max(0,delta);
        var travel=state.previousOwner==null?new Vector3d():new Vector3d(owner).sub(state.previousOwner);
        state.previousOwner=new Vector3d(owner);travel.y=0;
        if(self.distanceSquared(owner)>16*16) {
            var away=travel.lengthSquared()>.0001?travel.negate():new Vector3d(self).sub(owner);
            away.y=0;if(away.lengthSquared()<.001)away.set(0,0,1);
            state.wanderAt=new Vector3d(owner).add(away.normalize().rotateY((state.formationSlot-1)*.4).mul(12)).add(0,1.2,0);
            state.wanderSeconds=0;
        } else if(state.wanderAt==null || state.wanderSeconds>=5 || state.wanderAt.distanceSquared(owner)>18*18) {
            double angle=(++state.wanderStep)*2.399+state.formationSlot*2.094;
            double radius=8+(state.wanderStep%3)*2;
            state.wanderAt=new Vector3d(owner).add(Math.cos(angle)*radius,1.2,Math.sin(angle)*radius);state.wanderSeconds=0;
        }
        return new Vector3d(state.wanderAt);
    }

    static float yaw(Vector3d from,Vector3d to){return (float)Math.atan2(-(to.x-from.x),-(to.z-from.z));}
    static void face(TransformComponent body,HeadRotation head,Vector3d at,float delta) {
        if(at==null || !at.isFinite())return;
        var from=body.getPosition();double dx=at.x-from.x,dz=at.z-from.z;if(dx*dx+dz*dz<.0001)return;
        double target=yaw(from,at),current=body.getRotation().yaw();
        double turn=Math.atan2(Math.sin(target-current),Math.cos(target-current));
        double next=current+turn*Math.min(1,Math.max(0,delta)*12);
        var rotation=new Rotation3f(0,(float)Math.atan2(Math.sin(next),Math.cos(next)),0);
        body.setRotation(rotation);if(head!=null)head.setRotation(rotation);
    }
    static Vector3d combatDestination(Vector3d self,Vector3d owner,Vector3d enemy,SoulState state) {
        double dx=self.x-enemy.x,dz=self.z-enemy.z,range=self.distanceSquared(enemy);
        if(!state.combatMoving && (range>8*8 || range<3*3))state.combatMoving=true;
        if(state.combatMoving && range>=4*4 && range<=6*6)state.combatMoving=false;
        if(!state.combatMoving)return new Vector3d(self);
        var away=new Vector3d(owner.x-enemy.x,0,owner.z-enemy.z);
        if(away.lengthSquared()<.01)away.set(dx,0,dz);
        if(away.lengthSquared()<.01)away.set(0,0,1);
        return new Vector3d(enemy).add(away.normalize().mul(5)).add(0,1,0);
    }
}
