package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


public final class AncientProjectileState implements Component<EntityStore> {
    Ref<EntityStore> owner;
    float damage;int cause;boolean explosion,reflection,spent;
    AncientProjectileState(){}
    AncientProjectileState(Ref<EntityStore> owner,float damage,int cause,boolean explosion,boolean reflection){this.owner=owner;this.damage=damage;this.cause=cause;this.explosion=explosion;this.reflection=reflection;}
    boolean spend(){if(spent)return false;spent=true;return true;}
    @Override public AncientProjectileState clone(){var s=new AncientProjectileState(owner,damage,cause,explosion,reflection);s.spent=spent;return s;}
}
