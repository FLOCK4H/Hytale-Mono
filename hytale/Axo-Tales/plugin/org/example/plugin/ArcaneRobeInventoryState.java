package org.example.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


public final class ArcaneRobeInventoryState implements Component<EntityStore> {
    public static final BuilderCodec<ArcaneRobeInventoryState> CODEC=BuilderCodec.builder(ArcaneRobeInventoryState.class,ArcaneRobeInventoryState::new)
        .append(new KeyedCodec<>("BaseCapacity",Codec.INTEGER),(v,x)->v.baseCapacity=x,v->v.baseCapacity).add()
        .append(new KeyedCodec<>("GrantedCapacity",Codec.INTEGER),(v,x)->v.grantedCapacity=x,v->v.grantedCapacity).add()
        .append(new KeyedCodec<>("ForeignResize",Codec.BOOLEAN),(v,x)->v.foreignResize=x,v->v.foreignResize).add()
        .append(new KeyedCodec<>("RobeUnlocked",Codec.BOOLEAN),(v,x)->v.robeUnlocked=x,v->v.robeUnlocked).add()
        .append(new KeyedCodec<>("BackpackBase",Codec.INTEGER),(v,x)->v.backpackBase=x,v->v.backpackBase).add()
        .append(new KeyedCodec<>("BackpackBonus",Codec.INTEGER),(v,x)->v.backpackBonus=x,v->v.backpackBonus).add()
        .append(new KeyedCodec<>("UpgradeTwoPadding",Codec.INTEGER),(v,x)->v.upgradeTwoPadding=x,v->v.upgradeTwoPadding).add()
        .append(new KeyedCodec<>("ArmorSlots",Codec.INTEGER),(v,x)->v.armorSlots=x,v->v.armorSlots).add().build();
    int baseCapacity;
    int grantedCapacity;
    boolean foreignResize;
    boolean robeUnlocked;
    int backpackBase=-1;
    int backpackBonus;
    int upgradeTwoPadding;
    int armorSlots;
    @Override public Component<EntityStore> clone() {
        var copy=new ArcaneRobeInventoryState();copy.baseCapacity=baseCapacity;copy.grantedCapacity=grantedCapacity;copy.foreignResize=foreignResize;
        copy.robeUnlocked=robeUnlocked;copy.backpackBase=backpackBase;copy.backpackBonus=backpackBonus;copy.upgradeTwoPadding=upgradeTwoPadding;copy.armorSlots=armorSlots;return copy;
    }
}
