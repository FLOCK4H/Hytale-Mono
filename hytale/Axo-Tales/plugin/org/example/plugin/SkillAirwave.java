package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import org.joml.Vector3d;


final class SkillAirwave {
    static final String EFFECT="AxoTales_Skill_Airwave";
    private SkillAirwave(){}
    static int emit(Store<EntityStore> store,Vector3d at){
        var nearby=SpatialResource.<EntityStore>getThreadLocalReferenceList();
        store.getResource(EntityModule.get().getPlayerSpatialResourceType()).getSpatialStructure().collect(at,ParticleUtil.DEFAULT_PARTICLE_DISTANCE,nearby);
        if(nearby.isEmpty())return 0;
        var viewers=List.copyOf(nearby);
        ParticleUtil.spawnParticleEffect(EFFECT,at,0,0,0,1,null,viewers,store);
        return viewers.size();
    }
}
