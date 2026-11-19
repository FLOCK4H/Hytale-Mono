package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public final class ArcaneRobeSystem extends TickingSystem<EntityStore> {
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final Map<Ref<EntityStore>,Boolean> lastWorn=new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>,Map<Ref<EntityStore>,Attitude>> stealthClaims=new ConcurrentHashMap<>();
    private final Map<Store<EntityStore>,Long> nextSweep=Collections.synchronizedMap(new WeakHashMap<>());
    public ArcaneRobeSystem(PluginErrorReporter errors,PluginDebugReporter debug) {
        this.errors=errors;this.debug=debug;
    }
    @Override public void tick(float delta,int tickCount,@Nonnull Store<EntityStore> store) {
        long now=System.nanoTime();if(nextSweep.getOrDefault(store,0L)>now)return;nextSweep.put(store,now+200_000_000L);
        try {
            store.forEachChunk(Query.and(PlayerRef.getComponentType(),Player.getComponentType(),EntityStatMap.getComponentType()),(chunk,buffer)->{
                for(int i=0;i<chunk.size();i++) {
                    var ref=chunk.getReferenceTo(i);

                    buffer.run(accessor->{if(ref.isValid())refresh(accessor,ref);});
                }
            });
            lastWorn.keySet().removeIf(ref->!ref.isValid());
            stealthClaims.entrySet().removeIf(entry->!entry.getKey().isValid());
        } catch(Throwable failure){errors.report((PlayerRef)null,"Arcane robe refresh failed.",failure);}
    }
    private void refresh(Store<EntityStore> store,Ref<EntityStore> ref) {
        boolean worn=ArcaneArmorSets.robe(store,ref);
        boolean relic=ArcaneArmorSets.relic(store,ref);
        boolean ancient=ArcaneArmorSets.ancient(store,ref);
        boolean chest=AncientArmor.worn(store,ref,com.hypixel.hytale.protocol.ItemArmorSlot.Chest,AncientArmor.CHEST);
        PlayerRef player=store.getComponent(ref,PlayerRef.getComponentType());
        EntityStatMap stats=store.getComponent(ref,EntityStatMap.getComponentType());
        int skillMask=SkillProgression.mask(store,ref);
        float multiplier=chest?1.20f:relic?1.30f:worn?1.25f:1;
        multiplier*=(1+SkillCatalog.health(skillMask))*SkillCatalog.superHealth(skillMask);
        boolean statChanged=AncientArmor.capacity(stats,DefaultEntityStatTypes.getHealth(),"axotales:armor_set_health",multiplier,"axotales:ancient_set_health",((ancient?400:0)+(SkillCatalog.has(skillMask,13)?25:0))*SkillCatalog.superHealth(skillMask))
            | percentage(stats,DefaultEntityStatTypes.getMana(),"axotales:armor_set_mana",ancient?1.5f:relic?1.3f:worn?1.25f:1)
            | AncientArmor.capacity(stats,DefaultEntityStatTypes.getStamina(),"axotales:veil_stamina",(worn?1.25f:1)*(SkillCatalog.has(skillMask,5)?1.05f:1),"axotales:ancient_set_stamina",ancient?100:0);
        if(statChanged)stats.update();
        Boolean before=lastWorn.put(ref,worn);
        if(before==null || before!=worn || statChanged) {
            debug.traceFileOnly(player,"ArcaneRobe event=equipmentRefresh cancelled=false itemSource=serverArmorSlots fullSet="+worn
                +" allow="+(worn||relic||chest)+" health="+snapshot(stats,DefaultEntityStatTypes.getHealth())+" mana="+snapshot(stats,DefaultEntityStatTypes.getMana())
                +" stamina="+snapshot(stats,DefaultEntityStatTypes.getStamina())+" relicFullSet="+relic+" ancientFullSet="+ancient+" ancientChest="+chest+" healthPercentFactor="+multiplier+" ancientFlatHealth="+((ancient?400:0)+(SkillCatalog.has(skillMask,13)?25:0))+" ancientFlatStamina="+(ancient?100:0)+" detectionBlocks="+ArcaneArmorSets.detectionBlocks(store,ref));
        }
        stealth(store,ref,ArcaneArmorSets.detectionBlocks(store,ref));
        relicAura(store,ref,relic);
    }
    static boolean percentage(EntityStatMap stats,int index,String key,float multiplier) {
        if(stats==null || index<0 || stats.get(index)==null)return false;
        var existing=stats.getModifier(index,key);
        if(multiplier==1) {if(existing==null)return false;stats.removeModifier(index,key);return true;}



        float foreignFactor=0;boolean foreignScale=false;
        var modifiers=stats.get(index).getModifiers();
        if(modifiers!=null)for(var entry:modifiers.entrySet()) {
            if(entry.getKey().equals(key))continue;
            if(entry.getValue() instanceof StaticModifier value && value.getTarget()==Modifier.ModifierTarget.MAX
                    && value.getCalculationType()==StaticModifier.CalculationType.MULTIPLICATIVE) {
                foreignFactor+=value.getAmount();foreignScale=true;
            }
        }
        float amount=foreignScale?foreignFactor*(multiplier-1):multiplier;
        if(existing instanceof StaticModifier modifier && modifier.getAmount()==amount
                && modifier.getTarget()==Modifier.ModifierTarget.MAX
                && modifier.getCalculationType()==StaticModifier.CalculationType.MULTIPLICATIVE)return false;
        stats.putModifier(index,key,new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.MULTIPLICATIVE,amount));return true;
    }
    private void stealth(Store<EntityStore> store,Ref<EntityStore> player,int detectionBlocks) {
        boolean worn=detectionBlocks>0;
        if(!worn && !stealthClaims.containsKey(player))return;
        var claims=stealthClaims.computeIfAbsent(player,key->new HashMap<>());
        int before=claims.size(),restored=0;
        Set<Ref<EntityStore>> hidden=new HashSet<>();
        var transform=store.getComponent(player,TransformComponent.getComponentType());
        if(worn && transform!=null) {
            var origin=transform.getPosition();
            for(var npcRef:TargetUtil.getAllEntitiesInSphere(origin,64,store)) {
                if(!npcRef.isValid())continue;
                var npc=store.getComponent(npcRef,NPCEntity.getComponentType());
                if(npc==null || npc.getRole()==null)continue;
                var pos=store.getComponent(npcRef,TransformComponent.getComponentType());
                if(pos==null || pos.getPosition().distanceSquared(origin)<=detectionBlocks*detectionBlocks)continue;
                var support=WorldSupport.get(npcRef,store);if(support==null)continue;
                var previous=support.getOverriddenAttitude(player);
                boolean ours=claims.containsKey(npcRef) && previous==Attitude.IGNORE;
                if(previous!=null && previous!=Attitude.HOSTILE && !ours)continue;


                support.requireAttitudeCache();
                Attitude natural=support.getAttitude(npcRef,player,store);
                if(!ours && natural!=Attitude.HOSTILE)continue;




                if(previous!=null) {
                    claims.putIfAbsent(npcRef,natural);
                    support.overrideAttitude(player,Attitude.IGNORE,0.45);
                }
                hidden.add(npcRef);
                var marked=MarkedEntitySupport.get(npcRef,store);
                if(marked!=null)for(int slot=0;slot<marked.getMarkedEntitySlotCount();slot++)
                    if(player.equals(marked.getMarkedEntityRef(slot)))marked.clearMarkedEntity(slot);
            }
        }
        for(var entry:new ArrayList<>(claims.entrySet())) {
            if(hidden.contains(entry.getKey()))continue;
            if(entry.getKey().isValid()) {
                var npc=store.getComponent(entry.getKey(),NPCEntity.getComponentType());
                var support=npc!=null && npc.getRole()!=null?WorldSupport.get(entry.getKey(),store):null;
                if(support!=null && support.getOverriddenAttitude(player)==Attitude.IGNORE)
                    support.overrideAttitude(player,entry.getValue(),0.01);
                restored++;
            }
            claims.remove(entry.getKey());
        }
        if(claims.isEmpty())stealthClaims.remove(player);
        if(before!=hidden.size() || restored>0)
            debug.traceFileOnly(store.getComponent(player,PlayerRef.getComponentType()),
                "ArcaneRobe event=stealthBoundary cancelled=false itemSource=serverArmorSlots fullSet="+worn
                    +" allow="+worn+" detectionBlocks="+detectionBlocks+" hiddenObservers="+hidden.size()+" releasedObservers="+restored);
    }
    private static void relicAura(Store<EntityStore> store,Ref<EntityStore> ref,boolean worn) {
        String id="AxoTales_Sar_Aura";
        var effect=EntityEffect.getAssetMap().getAsset(id);
        int index=EntityEffect.getAssetMap().getIndexOrDefault(id,-1);
        var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());
        if(effect==null || index<0 || effects==null)return;
        var active=effects.getActiveEffects().get(index);
        if(!worn) {if(active!=null)effects.removeEffect(ref,index,store);return;}
        if(active==null || active.getRemainingDuration()<.45f)
            effects.addEffect(ref,index,effect,1.1f,OverlapBehavior.OVERWRITE,store);
    }
    private static String snapshot(EntityStatMap stats,int index) {
        var value=stats!=null && index>=0?stats.get(index):null;
        return value==null?"missing":"{index="+index+",current="+value.get()+",min="+value.getMin()+",max="+value.getMax()+"}";
    }
}
