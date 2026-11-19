package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.*;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Intersectiond;


final class ScoutTargets {
    static double hit(Vector3d origin,Vector3d direction,Vector3d min,Vector3d max){
        var interval=new Vector2d();return Intersectiond.intersectRayAab(origin,direction,min,max,interval)?Math.max(0,interval.x):Double.POSITIVE_INFINITY;
    }
    static Ref<EntityStore> visible(Ref<EntityStore> viewer,double range,boolean livingOnly,ComponentAccessor<EntityStore> access){
        var look=TargetUtil.getLook(viewer,access);if(look==null)return null;
        var origin=new Vector3d(look.getPosition());var direction=new Vector3d(look.getDirection());
        if(!origin.isFinite()||!direction.isFinite()||direction.lengthSquared()<.5)return null;direction.normalize();
        var candidates=List.copyOf(TargetUtil.getAllEntitiesInSphere(origin,range,access));double nearest=range;Ref<EntityStore> result=null;



        var block=TargetUtil.getTargetBlock(viewer,range,access);
        if(block!=null){var min=new Vector3d(block.x,block.y,block.z);nearest=Math.min(nearest,hit(origin,direction,min,new Vector3d(min).add(1,1,1)));}
        for(var ref:candidates){
            if(ref==null||!ref.isValid()||ref.equals(viewer)||(livingOnly&&!ScoutGearSystem.living(ref,access)))continue;
            var transform=access.getComponent(ref,TransformComponent.getComponentType());var bounds=access.getComponent(ref,BoundingBox.getComponentType());if(transform==null||bounds==null)continue;
            var box=bounds.getBoundingBox();var pos=transform.getPosition();double distance=hit(origin,direction,new Vector3d(box.min).add(pos),new Vector3d(box.max).add(pos));
            if(distance<nearest){nearest=distance;result=ref;}
        }return result;
    }
}
