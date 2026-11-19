package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.UUID;


final class OraculumState implements Component<ChunkStore> {
    static final String ITEM="Axo_Arcane_Oraculum";
    static final long FUSE_MS=5_000;
    static final BuilderCodec<OraculumState> CODEC=BuilderCodec.builder(OraculumState.class,OraculumState::new)
        .append(new KeyedCodec<>("Owner",Codec.UUID_BINARY),(s,v)->s.owner=v,s->s.owner).add()
        .append(new KeyedCodec<>("Deadline",Codec.LONG),(s,v)->s.deadline=Math.max(0,v),s->s.deadline).add()
        .append(new KeyedCodec<>("SpentMana",Codec.FLOAT),(s,v)->s.spentMana=Float.isFinite(v)?Math.max(0,v):0,s->s.spentMana).add()
        .append(new KeyedCodec<>("Fired",Codec.BOOLEAN),(s,v)->s.fired=v,s->s.fired).add().build();
    UUID owner;
    long deadline;
    float spentMana;
    boolean fired;
    transient long nextSpark;
    boolean armed(){return deadline>0;}
    boolean arm(UUID actor,EntityStatMap stats,long now){
        int index=DefaultEntityStatTypes.getMana();var mana=stats==null||index<0?null:stats.get(index);
        if(armed()||fired||actor==null||now<=0||now>Long.MAX_VALUE-FUSE_MS||mana==null||!Float.isFinite(mana.get())||!Float.isFinite(mana.getMin())||!Float.isFinite(mana.getMax())||mana.get()<=mana.getMin())return false;
        float before=mana.get();stats.setStatValue(index,mana.getMin());

        if(mana.get()!=mana.getMin())return false;
        spentMana=before-mana.get();owner=actor;deadline=now+FUSE_MS;stats.update();return true;
    }
    boolean spend(long now){if(!armed()||fired||now<deadline)return false;fired=true;return true;}
    @Override public OraculumState clone(){var c=new OraculumState();c.owner=owner;c.deadline=deadline;c.spentMana=spentMana;c.fired=fired;c.nextSpark=nextSpark;return c;}
}
