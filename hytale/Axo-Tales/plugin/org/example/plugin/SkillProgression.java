package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


final class SkillProgression implements Component<EntityStore> {
    static ComponentType<EntityStore,SkillProgression> TYPE;
    static final BuilderCodec<SkillProgression> CODEC=BuilderCodec.builder(SkillProgression.class,SkillProgression::new)
        .append(new KeyedCodec<>("Runes",Codec.LONG),(s,v)->s.runes=Math.max(0,v),s->s.runes).add()
        .append(new KeyedCodec<>("Unlocked",Codec.INTEGER),(s,v)->s.unlocked=(v&0x7ffff)|1,s->s.unlocked).add()
        .append(new KeyedCodec<>("Incoming",Codec.INTEGER),(s,v)->s.incoming=Math.max(0,v),s->s.incoming).add()
        .append(new KeyedCodec<>("LazarusReadyAt",Codec.LONG),(s,v)->s.readyAt=Math.max(0,v),s->s.readyAt).add()
        .append(new KeyedCodec<>("LazarusImmuneUntil",Codec.LONG),(s,v)->s.immuneUntil=Math.max(0,v),s->s.immuneUntil).add().build();
    long runes,readyAt,immuneUntil;
    int unlocked=1,incoming;
    boolean has(int id){return SkillCatalog.has(unlocked,id);}
    String unlock(int id){
        var node=SkillCatalog.node(id);
        if(node==null)return "Unknown skill";
        if(!SkillCatalog.implemented(id))return "This superskill is not available yet";
        if(has(id))return "Already learned";
        if(!has(node.parent()))return "Learn "+SkillCatalog.node(node.parent()).name()+" first";
        if(runes<node.cost())return "Find "+(node.cost()-runes)+" more rune"+(node.cost()-runes==1?"":"s");
        runes-=node.cost();unlocked|=1<<id;return null;
    }
    boolean saveFromDeath(long now){
        if(!has(16)||now<readyAt)return false;
        readyAt=now+300_000;immuneUntil=now+3_000;return true;
    }
    static SkillProgression read(ComponentAccessor<EntityStore> access,Ref<EntityStore> ref){return TYPE==null||ref==null||!ref.isValid()?null:access.getComponent(ref,TYPE);}
    static int mask(ComponentAccessor<EntityStore> access,Ref<EntityStore> ref){var state=read(access,ref);return state==null?1:state.unlocked;}
    @Override public SkillProgression clone(){var copy=new SkillProgression();copy.runes=runes;copy.unlocked=unlocked;copy.incoming=incoming;copy.readyAt=readyAt;copy.immuneUntil=immuneUntil;return copy;}
}
