package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.spawning.world.WorldEnvironmentSpawnData;
import com.hypixel.hytale.server.spawning.world.component.WorldSpawnData;
import java.lang.reflect.Field;
import java.util.*;
import javax.annotation.Nonnull;


final class SuperskillPopulation extends TickingSystem<EntityStore> {
    private static final Field DENSITY=densityField();
    static final class Control {
        double base,applied;boolean owned;
        boolean refresh(WorldEnvironmentSpawnData environment,boolean active,Store<ChunkStore> chunks){
            double current=density(environment);
            if(!Double.isFinite(current)||current<0)return false;
            if(!active){
                if(!owned)return false;
                owned=false;
                if(Double.compare(current,applied)!=0)return false;
                environment.setDensity(base,chunks);environment.setFullyPopulated(false);return true;
            }
            if(!owned||Double.compare(current,applied)!=0)base=current;
            applied=base*2;owned=true;
            if(Double.compare(current,applied)==0)return false;
            environment.setDensity(applied,chunks);environment.setFullyPopulated(false);return true;
        }
    }
    private static final class Scene {
        final Map<WorldEnvironmentSpawnData,Control> environments=new IdentityHashMap<>();
        float elapsed;boolean active;
    }
    private final Map<Store<EntityStore>,Scene> scenes=new java.util.concurrent.ConcurrentHashMap<>();
    private final PluginDebugReporter debug;private final PluginErrorReporter errors;private boolean closed;
    SuperskillPopulation(PluginDebugReporter debug,PluginErrorReporter errors){this.debug=debug;this.errors=errors;}
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        if(closed||!Float.isFinite(delta)||delta<=0)return;
        var scene=scenes.computeIfAbsent(store,key->new Scene());scene.elapsed+=delta;if(scene.elapsed<.25f)return;scene.elapsed%=.25f;
        boolean[] active={false};
        store.forEachChunk(Query.and(PlayerRef.getComponentType(),SkillProgression.TYPE),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++)if(chunk.getComponent(i,SkillProgression.TYPE).has(SkillCatalog.FEROX)){active[0]=true;break;}
        });
        try{refresh(store,scene,active[0]);}catch(RuntimeException failure){errors.report((PlayerRef)null,"Ferox natural population refresh failed",failure);}
    }
    private void refresh(Store<EntityStore> store,Scene scene,boolean active){
        var world=store.getExternalData().getWorld();var data=store.getResource(WorldSpawnData.getResourceType());
        var chunks=world.getChunkStore().getStore();int phase=store.getResource(WorldTimeResource.getResourceType()).getMoonPhase();
        boolean[] changed={false};
        data.forEachEnvironmentSpawnData(environment->{
            var control=scene.environments.computeIfAbsent(environment,key->new Control());
            if(control.refresh(environment,active,chunks)){environment.updateExpectedNPCs(phase);changed[0]=true;}
        });
        if(changed[0])data.recalculateWorldCount();
        if(scene.active!=active){scene.active=active;if(debug!=null)debug.traceFileOnly((PlayerRef)null,"Superskill event=population world="+world.getName()+" source=nativeEnvironmentDensity active="+active+" multiplier="+(active?2:1)+" stacking=false terrainLoading=false");}
        if(!active)scene.environments.entrySet().removeIf(e->!e.getValue().owned);
    }
    void releaseWorld(World world){scenes.keySet().removeIf(store->store.getExternalData().getWorld()==world);}
    void close(){
        closed=true;
        for(var entry:scenes.entrySet()){
            var store=entry.getKey();var world=store.getExternalData().getWorld();var scene=entry.getValue();
            Runnable restore=()->refresh(store,scene,false);
            if(world.isInThread())restore.run();else world.execute(restore);
        }
        scenes.clear();
    }
    static double density(WorldEnvironmentSpawnData environment){try{return DENSITY.getDouble(environment);}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}}
    private static Field densityField(){try{var field=WorldEnvironmentSpawnData.class.getDeclaredField("density");field.setAccessible(true);return field;}catch(ReflectiveOperationException failure){throw new IllegalStateException("Pinned native environment density is unavailable",failure);}}
}
