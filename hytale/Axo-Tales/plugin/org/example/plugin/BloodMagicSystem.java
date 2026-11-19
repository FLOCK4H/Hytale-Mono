package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.collision.*;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import javax.annotation.Nonnull;
import org.joml.Vector3d;


public final class BloodMagicSystem extends TickingSystem<EntityStore> {
    static final float EXTRACTION_RANGE=8f;
    private static final String BLOODY="Axo_Bloody_Book",LIFE="Axo_Book_Of_Life";
    private final ComponentType<EntityStore,BloodTaken> takenType;
    private final ComponentType<EntityStore,BloodPlayerState> playerType;
    private final ComponentType<EntityStore,BloodboundState> boundType;
    private final SpellbookPouchService pouches;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final Map<Ref<EntityStore>,Channel> channels=new HashMap<>();
    private final Map<Ref<EntityStore>,Integer> lastChain=new HashMap<>();
    private final Map<Ref<EntityStore>,Long> nextCast=new HashMap<>();
    static final class Channel {
        String book,hand;short slot;UUID target;long damageAtStart;float seconds;int announced;
        boolean advance(float delta) {
            if(Float.isFinite(delta))seconds+=Math.clamp(delta,0,.25f);
            return seconds+.0001f>=(target==null?.35f:8f);
        }
    }
    record Vial(ItemContainer container,short slot,ItemStack item,BloodSample sample){}
    record Placement(BloodSample sample,Vector3d at,Box bounds){}
    BloodMagicSystem(ComponentType<EntityStore,BloodTaken> taken,ComponentType<EntityStore,BloodPlayerState> player,
            ComponentType<EntityStore,BloodboundState> bound,SpellbookPouchService pouches,PluginDebugReporter debug,PluginErrorReporter errors) {
        takenType=taken;playerType=player;boundType=bound;this.pouches=pouches;this.debug=debug;this.errors=errors;
    }
    static boolean book(String id){return BLOODY.equals(id)||LIFE.equals(id);}
    void cast(Store<EntityStore> store,Ref<EntityStore> player,String book,String physical,int chain) {
        if(!book(book))return;var who=store.getComponent(player,PlayerRef.getComponentType());
        if(Objects.equals(lastChain.get(player),chain))return;lastChain.put(player,chain);
        long now=System.nanoTime();if(channels.containsKey(player)||now<nextCast.getOrDefault(player,0L)){trace(store,player,book,false,"ritualAlreadyActiveOrCooldown");return;}
        if(store.getComponent(player,DeathComponent.getComponentType())!=null)return;
        if(!held(store,player,book,physical)){trace(store,player,book,false,"heldItemOrBookChanged");return;}
        var channel=new Channel();channel.book=book;channel.hand=physical;channel.slot=InventoryComponentAccess.activeHotbarSlot(store,player);
        var combat=store.getComponent(player,playerType);channel.damageAtStart=combat==null?0:combat.revision;
        if(BLOODY.equals(book)) {
            var target=TargetUtil.getTargetEntity(player,EXTRACTION_RANGE,store);
            String denied=eligible(store,player,target);
            traceTarget(store,player,target,denied==null,denied==null?"eligibleBloodTarget":denied);
            if(denied!=null){trace(store,player,book,false,denied);who.sendMessage(Message.raw(message(denied)));return;}
            channel.target=store.getComponent(target,UUIDComponent.getComponentType()).getUuid();

            if(channels.values().stream().anyMatch(v->channel.target.equals(v.target))){who.sendMessage(Message.raw("Someone is already extracting blood from this creature."));return;}
            who.sendMessage(Message.raw("Extracting blood: keep this creature in sight within eight blocks for eight seconds."));
        } else {
            var mana=mana(store,player);if(mana==null || mana.get()<=mana.getMin()){trace(store,player,book,false,"noMana");who.sendMessage(Message.raw("The Book of Life needs mana to begin."));return;}
            if(vials(SpellbookPouchService.inventorySections(store,player)).isEmpty()){trace(store,player,book,false,"noDNA");who.sendMessage(Message.raw("Carry DNA samples in your inventory first."));return;}
        }
        nextCast.put(player,now+1_000_000_000L);channels.put(player,channel);trace(store,player,book,true,"ritualStarted");
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store) {
        channels.keySet().removeIf(ref->!ref.isValid());
        for(var entry:new ArrayList<>(channels.entrySet())) {
            var player=entry.getKey();if(player.getStore()!=store)continue;var channel=entry.getValue();
            try {
                if(!player.isValid()){channels.remove(player);continue;}
                var combat=store.getComponent(player,playerType);
                if(store.getComponent(player,DeathComponent.getComponentType())!=null || !held(store,player,channel.book,channel.hand)
                        || channel.slot!=InventoryComponentAccess.activeHotbarSlot(store,player) || combat!=null && combat.revision!=channel.damageAtStart) {
                    cancel(store,player,channel,"Ritual interrupted by damage or changing the held item.");continue;
                }
                Ref<EntityStore> target=null;
                if(channel.target!=null) {
                    target=store.getExternalData().getRefFromUUID(channel.target);
                    String denied=eligible(store,player,target);
                    if(denied!=null || !target.equals(TargetUtil.getTargetEntity(player,EXTRACTION_RANGE,store))) {
                        cancel(store,player,channel,denied==null?"Keep aiming at the creature to extract its blood.":message(denied));continue;
                    }
                }
                if(!channel.advance(delta)) {
                    int second=(int)channel.seconds;
                    if(second>channel.announced){channel.announced=second;store.getComponent(player,PlayerRef.getComponentType()).sendMessage(Message.raw("Blood extraction: "+second+" / 8 seconds"));}
                    continue;
                }
                channels.remove(player);
                if(target!=null)extract(store,player,target);else incarnate(store,player);
            }catch(RuntimeException failure){channels.remove(player);errors.report((PlayerRef)null,"Blood magic ritual failed",failure);}
        }
        lastChain.keySet().removeIf(ref->!ref.isValid());nextCast.keySet().removeIf(ref->!ref.isValid());
    }
    private void cancel(Store<EntityStore> store,Ref<EntityStore> player,Channel channel,String reason) {
        channels.remove(player);trace(store,player,channel.book,false,reason);
        store.getComponent(player,PlayerRef.getComponentType()).sendMessage(Message.raw(reason));
    }
    private boolean held(Store<EntityStore> store,Ref<EntityStore> player,String book,String physical) {
        var item=InventoryComponentAccess.itemInHand(store,player);if(ItemStack.isEmpty(item)||!physical.equals(item.getItemId()))return false;
        var spell=SpellbookCatalog.fromWand(physical);return spell==null?book.equals(physical):book.equals(spell.itemId()) && pouches.owns(store,player,spell);
    }
    private String eligible(Store<EntityStore> store,Ref<EntityStore> player,Ref<EntityStore> target) {
        if(target==null || !target.isValid() || store.getComponent(target,NPCEntity.getComponentType())==null
            || store.getComponent(target,UUIDComponent.getComponentType())==null || store.getComponent(target,ModelComponent.getComponentType())==null)return "noCreature";
        if(store.getComponent(target,DeathComponent.getComponentType())!=null)return "deadCreature";
        var stats=store.getComponent(target,EntityStatMap.getComponentType());int health=DefaultEntityStatTypes.getHealth();
        var hp=stats==null || health<0?null:stats.get(health);if(hp!=null && hp.get()<=hp.getMin())return "deadCreature";
        var taken=store.getComponent(target,takenType);if(taken!=null && taken.taken || store.getComponent(target,boundType)!=null)return "alreadyTaken";
        var a=store.getComponent(player,TransformComponent.getComponentType());var b=store.getComponent(target,TransformComponent.getComponentType());
        if(!withinExtractionRange(a,b))return "outOfRange";
        var look=TargetUtil.getLook(player,store);if(look==null || !sight(store,look.getPosition(),new Vector3d(b.getPosition()).add(0,.7,0)))return "blockedSight";
        return null;
    }
    static boolean withinExtractionRange(TransformComponent player,TransformComponent target) {
        return player!=null && target!=null
            && player.getPosition().distanceSquared(target.getPosition())<=EXTRACTION_RANGE*EXTRACTION_RANGE;
    }
    private void extract(Store<EntityStore> store,Ref<EntityStore> player,Ref<EntityStore> target) {
        var denied=eligible(store,player,target);if(denied!=null){trace(store,player,BLOODY,false,denied);return;}
        traceTarget(store,player,target,true,"eightSecondCompletionRevalidated");
        var npc=store.getComponent(target,NPCEntity.getComponentType());var model=store.getComponent(target,ModelComponent.getComponentType()).getModel();
        var sample=new BloodSample();sample.source=store.getComponent(target,UUIDComponent.getComponentType()).getUuid();sample.species=npc.getRoleName();
        sample.name=switch(sample.species){case "Rex_Cave"->"Rex";case "Kudu_Rune_Knight","Cursed_Knight"->"Cursed Knight";case "Vesperwraith","Chiro"->"Chiro";case "Kudu_Adept_Magician"->"Elder Mage";default->sample.species.replace('_',' ');};sample.model=model.toReference();
        var motion=npc.getRole()==null?null:npc.getRole().getActiveMotionController();
        sample.motion=motion instanceof com.hypixel.hytale.server.npc.movement.controllers.MotionControllerFly?"Fly"
            :motion instanceof com.hypixel.hytale.server.npc.movement.controllers.MotionControllerDive?"Dive":"Walk";
        var stats=store.getComponent(target,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());if(hp!=null)sample.health=hp.getMax();
        if(!sample.valid())throw new IllegalStateException("Creature has no persistent model/species");
        var claim=new BloodTaken();claim.taken=true;claim.collector=store.getComponent(player,PlayerRef.getComponentType()).getUuid();

        store.putComponent(target,takenType,claim);SoulSystem.dirty(store,target);
        try{give(store,player,sample.item(BloodSample.BLOOD));}
        catch(RuntimeException failure){store.removeComponent(target,takenType);SoulSystem.dirty(store,target);throw failure;}
        trace(store,player,BLOODY,true,"extracted source="+sample.source+" species="+sample.species+" duration=8");
        store.getComponent(player,PlayerRef.getComponentType()).sendMessage(Message.raw("Collected blood from "+sample.name+". This creature cannot be sampled again."));
    }
    static void give(Store<EntityStore> store,Ref<EntityStore> player,ItemStack item) {
        for(var inventory:SpellbookPouchService.inventorySections(store,player))if(inventory.canAddItemStack(item) && inventory.addItemStack(item).succeeded())return;
        var at=new Vector3d(store.getComponent(player,TransformComponent.getComponentType()).getPosition()).add(0,.6,0);
        var drop=ItemComponent.generateItemDrop(store,item,at,new Rotation3f(),0,.2f,0);if(drop==null)throw new IllegalStateException("Cannot deliver blood vial");store.addEntity(drop,AddReason.SPAWN);
    }
    static List<Vial> vials(List<ItemContainer> inventories) {
        var result=new ArrayList<Vial>();for(var items:inventories)for(short slot=0;slot<items.getCapacity();slot++) {
            var item=items.getItemStack(slot);var sample=BloodSample.read(item,BloodSample.DNA);
            if(sample!=null)for(int q=0;q<item.getQuantity();q++)result.add(new Vial(items,slot,item,sample));
        }return result;
    }
    private void incarnate(Store<EntityStore> store,Ref<EntityStore> player) {
        var owner=store.getComponent(player,PlayerRef.getComponentType());var stats=store.getComponent(player,EntityStatMap.getComponentType());var mana=mana(store,player);
        var vials=vials(SpellbookPouchService.inventorySections(store,player));if(vials.isEmpty() || mana==null || mana.get()<=mana.getMin()){trace(store,player,LIFE,false,"missingDNAOrMana");return;}
        var plans=new ArrayList<Placement>();
        for(var vial:vials) {
            if(NPCPlugin.get()==null || !NPCPlugin.get().hasRoleName(vial.sample.species)
                    || com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset(vial.sample.model.getModelAssetId())==null){owner.sendMessage(Message.raw("A DNA species is unavailable. No DNA or mana was consumed."));return;}
            var at=placement(store,player,vial.sample,plans);
            if(at==null){owner.sendMessage(Message.raw("More clear space is needed for all DNA samples; aquatic creatures need nearby water. Nothing was consumed."));trace(store,player,LIFE,false,"noSafeSpaceForBatch");return;}
            plans.add(at);
        }
        try {
            BloodRitualBatch.commit(vials,index->{var plan=plans.get(index);
                var bound=new BloodboundState();bound.owner=owner.getUuid();bound.sample=plan.sample;
                int role=NPCPlugin.get().getIndex("AxoTales_Bloodbound_"+plan.sample.motion);
                if(role<0)throw new IllegalStateException("Missing bloodbound role");
                var pair=NPCPlugin.get().spawnEntity(store,role,plan.at,new Rotation3f(),plan.sample.model.toModel(),
                    (npc,holder,accessor)->holder.putComponent(boundType,bound),
                    (npc,ref,accessor)->BloodboundSystem.refresh(accessor,ref,bound,true));
                if(pair==null || pair.left()==null || !pair.left().isValid())throw new IllegalStateException("Companion creation failed");return pair.left();
            },ref->{if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);},()->stats.setStatValue(DefaultEntityStatTypes.getMana(),mana.getMin()));
        }catch(RuntimeException failure) {
            trace(store,player,LIFE,false,"batchRolledBack "+failure.getMessage());errors.report(owner,"Bloodbound ritual rolled back",failure);return;
        }
        owner.sendMessage(Message.raw("Book of Life: "+vials.size()+" loyal companions awakened. All available mana consumed."));
        trace(store,player,LIFE,true,"spawned="+vials.size()+" allCurrentManaConsumed=true persistent=true");
    }
    static Placement placement(Store<EntityStore> store,Ref<EntityStore> player,BloodSample sample,List<Placement> plans) {
        var model=sample.model.toModel();if(model==null)return null;var shape=model.getBoundingBox();if(shape==null)return null;
        var box=new Box(shape);double half=Math.max(Math.max(Math.abs(box.min.x),Math.abs(box.max.x)),Math.max(Math.abs(box.min.z),Math.abs(box.max.z)))+.12;
        box.min.x=box.min.z=-half;box.max.x=box.max.z=half;
        var origin=store.getComponent(player,TransformComponent.getComponentType()).getPosition();var world=store.getExternalData().getWorld();
        for(int attempt=0;attempt<192;attempt++) {
            double angle=attempt*2.399963229728653;double distance=3+half+Math.sqrt(attempt)*1.4;
            int x=(int)Math.floor(origin.x+Math.cos(angle)*distance),z=(int)Math.floor(origin.z+Math.sin(angle)*distance);
            var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(x,z));if(column==null)continue;
            for(int dy=3;dy>=-6;dy--) {
                double y=Math.floor(origin.y)+dy;var foot=column.getBlockType(x,(int)y-1,z);
                if(sample.motion.equals("Walk") && (foot==null || foot.getMaterial()!=BlockMaterial.Solid))continue;
                var at=new Vector3d(x+.5,y-box.min.y+.03,z+.5);var bounds=new Box(box).offset(at);
                if(plans.stream().anyMatch(p->p.bounds.isIntersecting(bounds)))continue;
                if(!clear(store,bounds,sample.motion.equals("Dive")))continue;
                if(!TargetUtil.getAllEntitiesInBox(bounds.min,bounds.max,store).isEmpty())continue;
                return new Placement(sample,at,bounds);
            }
        }return null;
    }
    private static boolean clear(Store<EntityStore> store,Box bounds,boolean water) {
        var world=store.getExternalData().getWorld();
        for(int x=(int)Math.floor(bounds.min.x);x<=Math.floor(bounds.max.x);x++)for(int z=(int)Math.floor(bounds.min.z);z<=Math.floor(bounds.max.z);z++) {
            var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(x,z));if(column==null)return false;
            for(int y=(int)Math.floor(bounds.min.y);y<=Math.floor(bounds.max.y);y++) {
                var block=column.getBlockType(x,y,z);if(block==null || block.getDrawType()!=DrawType.Empty)return false;
                var section=world.getChunkStore().getChunkSectionReference(ChunkUtil.chunkCoordinate(x),ChunkUtil.indexSection(y),ChunkUtil.chunkCoordinate(z));
                if(section==null || !section.isValid())return false;
                var fluid=world.getChunkStore().getStore().getComponent(section,com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection.getComponentType());
                boolean submerged=fluid!=null && fluid.getFluidId(x,y,z)!=0;
                if(water!=submerged)return false;
                if(water) {
                    var material=com.hypixel.hytale.server.core.asset.type.fluid.Fluid.getAssetMap().getAsset(fluid.getFluidId(x,y,z));
                    if(material==null || material.isUnknown() || !(material.getId().startsWith("Water") || material.getId().startsWith("Fluid_Water")))return false;
                }
            }
        }return true;
    }
    static boolean sight(Store<EntityStore> store,Vector3d from,Vector3d to) {
        var result=new CollisionResult(false,false);result.setCollisionByMaterial(CollisionMaterial.MATERIAL_SOLID);
        CollisionModule.findBlockCollisionsIterative(store.getExternalData().getWorld(),new Box(-.02,-.02,-.02,.02,.02,.02),from,new Vector3d(to).sub(from),true,result);
        return result.getFirstBlockCollision()==null;
    }
    private static com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue mana(Store<EntityStore> store,Ref<EntityStore> ref){var stats=store.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getMana();return stats==null || index<0?null:stats.get(index);}
    void trace(Store<EntityStore> store,Ref<EntityStore> player,String book,boolean allow,String reason) {
        var value=mana(store,player);debug.traceFileOnly(store.getComponent(player,PlayerRef.getComponentType()),"BloodMagic event=NativeSpellInteraction cancelled=false itemSource=serverHeldAndCarriedBook itemId="+book
            +" physicalItem="+InventoryComponentAccess.itemInHand(store,player)+" slot="+InventoryComponentAccess.activeHotbarSlot(store,player)
            +" mana.index="+DefaultEntityStatTypes.getMana()+" mana.current="+(value==null?"missing":value.get())+" mana.min="+(value==null?"missing":value.getMin())+" mana.max="+(value==null?"missing":value.getMax())+" allow="+allow+" reason="+reason);
    }
    private void traceTarget(Store<EntityStore> store,Ref<EntityStore> player,Ref<EntityStore> target,boolean allow,String reason) {
        boolean valid=target!=null && target.isValid();var id=valid?store.getComponent(target,UUIDComponent.getComponentType()):null;
        var stats=valid?store.getComponent(target,EntityStatMap.getComponentType()):null;int index=DefaultEntityStatTypes.getHealth();var hp=stats==null || index<0?null:stats.get(index);
        debug.traceFileOnly(store.getComponent(player,PlayerRef.getComponentType()),"BloodMagic event=targetEligibility cancelled=false itemSource=serverHeldAndNativeTarget itemId="+BLOODY
            +" slot="+InventoryComponentAccess.activeHotbarSlot(store,player)+" target="+(id==null?"none":id.getUuid())+" health.index="+index
            +" health.current="+(hp==null?"missing":hp.get())+" health.min="+(hp==null?"missing":hp.getMin())+" health.max="+(hp==null?"missing":hp.getMax())+" rangeBlocks="+EXTRACTION_RANGE+" allow="+allow+" reason="+reason);
    }
    private static String message(String reason){return switch(reason){case "alreadyTaken"->"This creature has already given its blood.";case "deadCreature"->"Blood must come from a living creature.";case "outOfRange"->"Move within eight blocks of the creature.";case "blockedSight"->"The creature must stay in clear sight.";default->"Aim at a living creature within eight blocks.";};}
}
