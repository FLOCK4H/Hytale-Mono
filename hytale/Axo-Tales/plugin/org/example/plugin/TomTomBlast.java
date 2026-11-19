package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.modules.collision.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.function.Predicate;
import org.joml.Vector3d;


final class TomTomBlast {
    static final double RADIUS=4.25, VERTICAL_RADIUS=12.75;
    static float damage(Vector3d center,Vector3d target){
        double x=target.x-center.x,y=(target.y-center.y)/3,z=target.z-center.z;
        return (float)(52*Math.max(0,1-Math.sqrt(x*x+y*y+z*z)/RADIUS));
    }
    static void apply(Store<EntityStore> store,Ref<EntityStore> creator,Ref<EntityStore> projectile,Vector3d center,Predicate<Vector3d> visible){
        var cause=DamageCause.getAssetMap().getAsset("Environment");
        if(cause==null)throw new IllegalStateException("Native Environment damage cause missing");
        Damage.Source source=creator==null||!creator.isValid()?new Damage.EnvironmentSource("server.general.deathCause.explosion")
            :projectile!=null&&projectile.isValid()?new Damage.ProjectileSource(creator,projectile):new Damage.EntitySource(creator);
        store.forEachChunk(Query.and(TransformComponent.getComponentType(),EntityStatMap.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var target=chunk.getReferenceTo(i);if(target.equals(creator)||buffer.getComponent(target,DeathComponent.getComponentType())!=null)continue;
                var at=chunk.getComponent(i,TransformComponent.getComponentType()).getPosition();float amount=damage(center,at);
                if(amount>0&&visible.test(new Vector3d(at).add(0,.6,0)))DamageSystems.executeDamage(target,buffer,new Damage(source,cause,amount));
            }
        });
    }
    static boolean visible(Store<EntityStore> store,Vector3d center,Vector3d target){
        var collision=new CollisionResult(false,false);collision.setCollisionByMaterial(CollisionMaterial.MATERIAL_SOLID);
        CollisionModule.findBlockCollisionsIterative(store.getExternalData().getWorld(),new Box(-.01,-.01,-.01,.01,.01,.01),center,new Vector3d(target).sub(center),true,collision);
        return collision.getFirstBlockCollision()==null;
    }
}
