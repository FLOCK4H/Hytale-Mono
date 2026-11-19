package org.example.plugin;

import org.joml.Vector3d;
import java.util.List;


final class SoulFormation {
    static final double FOLLOW_DISTANCE=8.5;
    static Vector3d follow(Vector3d owner,int slot){
        return follow(owner,new Vector3d(0,0,-1),slot);
    }
    static Vector3d follow(Vector3d owner,Vector3d forward,int slot){
        var behind=new Vector3d(-forward.x,0,-forward.z);
        if(behind.lengthSquared()<.001)behind.set(0,0,1);
        behind.normalize().rotateY((slot-1)*.64).mul(FOLLOW_DISTANCE);
        return new Vector3d(owner).add(behind).add(0,1.2,0);
    }
    static Vector3d combat(Vector3d enemy,Vector3d owner,int slot){
        double angle=Math.atan2(owner.z-enemy.z,owner.x-enemy.x)+(slot-1)*.8;
        return new Vector3d(enemy).add(Math.cos(angle)*5,1.3,Math.sin(angle)*5);
    }
    static Vector3d avoid(Vector3d self,Vector3d desired,List<Vector3d> others,int slot){




        if(self.distanceSquared(desired)<=2*2)return new Vector3d(desired);
        var result=new Vector3d(desired);var push=new Vector3d();
        for(var other:others){
            var away=new Vector3d(self).sub(other);double d=away.length();
            if(d>=1.65)continue;
            if(d<.01){double a=slot*2.399;away.set(Math.cos(a),.25,Math.sin(a));d=1;}
            push.add(away.div(d).mul((1.65-Math.min(1.64,d))*2));
        }
        return result.add(push);
    }
    static boolean laneClear(Vector3d from,Vector3d to,List<Vector3d> others){
        var line=new Vector3d(to).sub(from);double length=line.lengthSquared();if(length<.001)return false;
        for(var other:others){
            var center=new Vector3d(other).add(0,.8,0);
            double t=new Vector3d(center).sub(from).dot(line)/length;
            if(t>0 && t<1 && new Vector3d(from).fma(t,line).distanceSquared(center)<.75*.75)return false;
        }
        return true;
    }
}
