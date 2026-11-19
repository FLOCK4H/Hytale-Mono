package org.example.plugin;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.SelectInteraction;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.function.*;
import org.joml.Vector4d;


public final class SkillMeleeSelector extends HorizontalSelector {
    static final String TYPE="AxoTales_Melee";
    static final BuilderCodec<SkillMeleeSelector> CODEC=BuilderCodec.builder(SkillMeleeSelector.class,SkillMeleeSelector::new,HorizontalSelector.CODEC).build();
    private Selector expanded(int blocks){
        var copy=new SkillMeleeSelector();copy.extendTop=extendTop;copy.extendBottom=extendBottom;
        copy.yawLength=yawLength;copy.yawStartOffset=yawStartOffset;copy.pitchOffset=pitchOffset;copy.rollOffset=rollOffset;
        copy.startDistance=startDistance;copy.endDistance=endDistance+blocks;copy.direction=direction;
        copy.testLineOfSight=testLineOfSight;copy.ignorePitch=ignorePitch;copy.ignoreYaw=ignoreYaw;copy.anchor=anchor;
        return copy.nativeSelector();
    }
    private Selector nativeSelector(){return super.newSelector();}
    @Override public Selector newSelector(){return runtime(this::expanded,nativeSelector());}
    private static Selector runtime(java.util.function.IntFunction<Selector> factory,Selector blocks){
        return new Selector(){
            Selector delegate;Map<String,Float> attributes;
            @Override public void setAbilityAttributes(Map<String,Float> values){attributes=values;if(delegate!=null)delegate.setAbilityAttributes(values);}
            private Selector resolve(CommandBuffer<EntityStore> buffer,Ref<EntityStore> ref){
                if(delegate==null){delegate=factory.apply(SkillCatalog.reach(SkillProgression.mask(buffer,ref)));if(attributes!=null)delegate.setAbilityAttributes(attributes);}return delegate;
            }
            @Override public void tick(CommandBuffer<EntityStore> b,Ref<EntityStore> r,float from,float to){resolve(b,r).tick(b,r,from,to);blocks.tick(b,r,from,to);}
            @Override public void selectTargetEntities(CommandBuffer<EntityStore> b,Ref<EntityStore> r,BiConsumer<Ref<EntityStore>,Vector4d> consumer,Predicate<Ref<EntityStore>> predicate){resolve(b,r).selectTargetEntities(b,r,consumer,predicate);}
            @Override public void selectTargetBlocks(CommandBuffer<EntityStore> b,Ref<EntityStore> r,Selector.BlockConsumer consumer){blocks.selectTargetBlocks(b,r,consumer);}
        };
    }
    public static final class Stab extends StabSelector {
        static final String TYPE="AxoTales_MeleeStab";
        static final BuilderCodec<Stab> CODEC=BuilderCodec.builder(Stab.class,Stab::new,StabSelector.CODEC).build();
        private Selector expanded(int blocks){
            var copy=new Stab();copy.extendTop=extendTop;copy.extendBottom=extendBottom;copy.extendLeft=extendLeft;copy.extendRight=extendRight;
            copy.yawOffset=yawOffset;copy.pitchOffset=pitchOffset;copy.rollOffset=rollOffset;copy.startDistance=startDistance;copy.endDistance=endDistance+blocks;
            copy.testLineOfSight=testLineOfSight;copy.ignorePitch=ignorePitch;copy.ignoreYaw=ignoreYaw;copy.anchor=anchor;copy.sizeAttribute=sizeAttribute;
            return copy.nativeSelector();
        }
        private Selector nativeSelector(){return super.newSelector();}
        @Override public Selector newSelector(){return runtime(this::expanded,nativeSelector());}
    }



    public static final class Interaction extends SelectInteraction {
        static final String TYPE="AxoTales_SkillSelector";
        static final BuilderCodec<Interaction> CODEC=BuilderCodec.builder(Interaction.class,Interaction::new,SelectInteraction.CODEC).build();
        @Override public WaitForDataFrom getWaitForDataFrom(){return WaitForDataFrom.Client;}
        @Override public boolean needsRemoteSync(){return true;}
    }
}
