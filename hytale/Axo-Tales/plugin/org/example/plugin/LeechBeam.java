package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.*;
import org.joml.Vector3d;


final class LeechBeam {
    static Vector3d target(ComponentAccessor<EntityStore> store,Ref<EntityStore> target) {
        var at=new Vector3d(store.getComponent(target,TransformComponent.getComponentType()).getPosition());
        var model=store.getComponent(target,ModelComponent.getComponentType());double height=1;
        if(model!=null&&model.getModel()!=null&&model.getModel().getBoundingBox()!=null)height=Math.clamp(model.getModel().getBoundingBox().max.y*.55,.3,2.5);
        return at.add(0,height,0);
    }
    static Vector3d curve(Vector3d from,Vector3d to,Vector3d forward,double phase,double t) {
        var direction=new Vector3d(to).sub(from);double length=direction.length();
        var side=new Vector3d(-direction.z,0,direction.x);if(side.lengthSquared()<.0001)side.set(1,0,0);else side.normalize();
        double bend=Math.min(.28,length*.04);
        var c1=new Vector3d(from).fma(Math.min(1.2,length*.28),forward).fma(bend*.55*Math.sin(phase*.9),side).add(0,.08,0);
        var c2=new Vector3d(to).fma(-length*.25,new Vector3d(direction).normalize()).fma(-bend*.45*Math.cos(phase*.9),side).add(0,.08,0);
        double u=1-t;
        return new Vector3d(from).mul(u*u*u).fma(3*u*u*t,c1).fma(3*u*t*t,c2).fma(t*t*t,to);
    }
    private static Rotation3f along(Vector3d direction){return along(direction,0);}
    private static Rotation3f along(Vector3d direction,float roll){
        direction.normalize();
        return new Rotation3f((float)-Math.asin(Math.clamp(direction.y,-1,1)),(float)Math.atan2(-direction.x,-direction.z),roll);
    }
    static int flowCount(double length){return Math.clamp((int)Math.ceil(length/2.4),2,7);}
    static List<Vector3d> path(Vector3d from,Vector3d to,Vector3d forward,double phase) {
        if(!from.isFinite()||!to.isFinite()||!forward.isFinite()||from.distanceSquared(to)<.0001)return List.of();
        int count=Math.clamp((int)Math.ceil(from.distance(to)/.4),4,42);var points=new ArrayList<Vector3d>();
        for(int i=0;i<=count;i++)points.add(curve(from,to,forward,phase,(double)i/count));
        return points;
    }
    static void emit(Store<EntityStore> store,Ref<EntityStore> caster,Ref<EntityStore> target,boolean wand,double time) {
        var look=TargetUtil.getLook(caster,store);if(look==null)return;
        var forward=new Vector3d(look.getDirection()).normalize();var right=new Vector3d(-forward.z,0,forward.x);
        if(right.lengthSquared()>.001)right.normalize();else right.set(1,0,0);
        var from=new Vector3d(look.getPosition()).fma(wand?.7:.45,forward).fma(wand?.28:.08,right).add(0,wand?-.18:-.35,0);
        var to=target(store,target);var points=path(from,to,forward,time);if(points.isEmpty())return;


        var nearby=SpatialResource.<EntityStore>getThreadLocalReferenceList();
        store.getResource(EntityModule.get().getPlayerSpatialResourceType()).getSpatialStructure().collect(from,ParticleUtil.DEFAULT_PARTICLE_DISTANCE,nearby);
        if(nearby.isEmpty())return;
        var viewers=List.copyOf(nearby);
        for(int i=1;i<points.size();i++) {
            var a=points.get(i-1);var b=points.get(i);var direction=new Vector3d(b).sub(a).normalize();
            var rotation=along(direction,(float)(time*2+i*.7));
            ParticleUtil.spawnParticleEffect("AxoTales_Leech_Strand",new Vector3d(a).lerp(b,.5),rotation,viewers,store);
        }
        double length=from.distance(to);int count=flowCount(length);
        for(int i=0;i<count;i++) {
            double t=1-((time*2.8/Math.max(.5,length)+(double)i/count)%1);
            if(t*length<.78)continue;
            var at=curve(from,to,forward,time,t);
            var inward=curve(from,to,forward,time,Math.max(0,t-.01)).sub(curve(from,to,forward,time,Math.min(1,t+.01)));
            ParticleUtil.spawnParticleEffect("AxoTales_Leech_Flow",at,along(inward),viewers,store);
        }
        float pulse=(float)(.88+.12*Math.cos(time*Math.PI*2));
        ParticleUtil.spawnParticleEffect("AxoTales_Leech_Anchor",from,0,0,0,pulse*.8f,null,viewers,store);
        ParticleUtil.spawnParticleEffect("AxoTales_Leech_Anchor",to,0,0,0,pulse,null,viewers,store);
    }
}
