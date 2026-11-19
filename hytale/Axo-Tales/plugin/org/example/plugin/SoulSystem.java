package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.collision.*;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import javax.annotation.Nonnull;
import org.joml.Vector3d;


@SuppressWarnings("deprecation")
public final class SoulSystem extends TickingSystem<EntityStore> {
    static final String USE="AxoSoul_Use", BOLT="AxoTales_Cruor_Bolt", CONJURE="AxoTales_Fossor_Conjure";
    private final ComponentType<EntityStore,SoulState> stateType;
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    private final SoulLogistics logistics;
    private final SoulRoster roster;
    private final Map<Store<EntityStore>,SoulChunkLease> workLeases=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Store<EntityStore>,Float> workScan=Collections.synchronizedMap(new WeakHashMap<>());
    SoulSystem(ComponentType<EntityStore,SoulState> stateType,ComponentType<EntityStore,FossorWork> workType,PluginDebugReporter debug,PluginErrorReporter errors,java.nio.file.Path dataDirectory) {
        this.stateType=stateType;this.debug=debug;this.errors=errors;logistics=new SoulLogistics(workType,debug);
        roster=new SoulRoster(dataDirectory);
    }
    SoulSlotsLifecycle slotLifecycle(){return new SoulSlotsLifecycle(stateType,roster,errors);}
    void releaseWorld(com.hypixel.hytale.server.core.universe.world.World world){
        var store=world.getEntityStore().getStore();
        var lease=workLeases.remove(store);if(lease!=null)lease.close();workScan.remove(store);
    }
    void close(){synchronized(workLeases){workLeases.values().forEach(SoulChunkLease::close);workLeases.clear();}workScan.clear();}
    private void retainWorkAreas(Store<EntityStore> store,List<Ref<EntityStore>> souls){
        var grid=store.getExternalData().getWorld().getChunkStore();var chunks=grid.getStore();
        var columns=new HashSet<com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk>();
        var sections=new HashSet<com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection>();
        var indices=new HashSet<Long>();
        for(var ref:souls){
            if(!ref.isValid())continue;var state=store.getComponent(ref,stateType);if(state==null||state.owner==null||state.type()==null)continue;
            var at=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
            var center=state.type()==SoulKind.CRUOR?at:state.anchor();int radius=state.type()==SoulKind.VILLA?50:state.type()==SoulKind.CRUOR?20:2;
            int minX=Math.floorDiv((int)Math.floor(center.x-radius),32),maxX=Math.floorDiv((int)Math.floor(center.x+radius),32);
            int minZ=Math.floorDiv((int)Math.floor(center.z-radius),32),maxZ=Math.floorDiv((int)Math.floor(center.z+radius),32);
            indices.add(ChunkUtil.indexChunkFromBlock((int)Math.floor(at.x),(int)Math.floor(at.z)));
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)indices.add(ChunkUtil.indexChunk(x,z));
        }
        for(long index:indices){
                var column=grid.getChunkComponent(index,com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk.getComponentType());
                if(column==null)continue;columns.add(column);
                for(var sectionRef:grid.getLoadedSectionReferences(ChunkUtil.xOfChunkIndex(index),ChunkUtil.zOfChunkIndex(index))){
                    if(sectionRef==null||!sectionRef.isValid())continue;
                    var section=chunks.getComponent(sectionRef,com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection.getComponentType());
                    if(section!=null){sections.add(section);chunks.tryRemoveComponent(sectionRef,com.hypixel.hytale.server.core.universe.world.storage.ChunkStore.REGISTRY.getNonTickingComponentType());}
                }
        }
        workLeases.computeIfAbsent(store,k->new SoulChunkLease()).refresh(columns,sections);
    }
    static void retainNpc(NPCEntity npc){npc.setDespawning(false);npc.setPlayingDespawnAnim(false);npc.setDespawnRemainingSeconds(0);npc.setDespawnTime(0);}
    private static void preserve(Store<EntityStore> store,Ref<EntityStore> ref){
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        boolean changed=npc.isDespawning()||npc.getDespawnTime()!=0||store.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.DespawnComponent.getComponentType())!=null
            ||store.getComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType())!=null;
        retainNpc(npc);
        store.tryRemoveComponent(ref,com.hypixel.hytale.server.core.modules.entity.DespawnComponent.getComponentType());
        store.tryRemoveComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType());
        if(changed)dirty(store,ref);
    }
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store) {
        if(!Float.isFinite(delta)||delta<=0)return;
        var souls=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(Query.and(NPCEntity.getComponentType(),TransformComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++)if(SoulKind.role(chunk.getComponent(i,NPCEntity.getComponentType()).getRoleName())!=null)souls.add(chunk.getReferenceTo(i));
        });
        var neighbours=souls.isEmpty()?new SoulNeighbours<Ref<EntityStore>>():neighbourhood(store);
        for(var soul:souls){
            try{update(store,soul,Math.clamp(delta,0,.25f),neighbours);}
            catch(RuntimeException failure){errors.report((PlayerRef)null,"Soul behavior failed",failure);}
            finally{
                var body=soul.isValid()?store.getComponent(soul,TransformComponent.getComponentType()):null;
                if(body!=null&&store.getComponent(soul,stateType)!=null)neighbours.put(soul,body.getPosition());else neighbours.remove(soul);
            }
        }
        float elapsed=workScan.getOrDefault(store,0f)+Math.clamp(delta,0,.25f);
        if(elapsed>=1){try{logistics.reconcileWork(store,stateType);}catch(RuntimeException failure){errors.report((PlayerRef)null,"Soul work recovery failed",failure);}elapsed=0;}workScan.put(store,elapsed);
        if(elapsed==0)try{retainWorkAreas(store,souls);}catch(RuntimeException failure){errors.report((PlayerRef)null,"Soul work area retention failed",failure);}
    }
    private SoulState state(Store<EntityStore> store,Ref<EntityStore> ref) {
        var state=store.getComponent(ref,stateType);
        if(state==null) {
            state=new SoulState();state.kind=store.getComponent(ref,NPCEntity.getComponentType()).getRoleName();
            state.anchor(store.getComponent(ref,TransformComponent.getComponentType()).getPosition());store.putComponent(ref,stateType,state);
        }
        return state;
    }
    private void update(Store<EntityStore> store,Ref<EntityStore> ref,float delta,SoulNeighbours<Ref<EntityStore>> neighbours) {
        if(!ref.isValid())return;var state=state(store,ref);
        preserve(store,ref);
        boolean invulnerable=store.getComponent(ref,Invulnerable.getComponentType())!=null;
        boolean intangible=store.getComponent(ref,Intangible.getComponentType())!=null;


        allowMelee(store,ref);
        if(invulnerable || intangible)debug.traceFileOnly(null,"Soul event=meleeEligibilityMigration kind="+state.kind+" owner="+state.owner
            +" nativeInvulnerable="+invulnerable+" intangible="+intangible+" cancelled=false itemSource=none itemId=n/a slot=n/a section=n/a"
            +" mana.index=n/a mana.current=n/a mana.max=n/a mana.min=n/a mana.gated=false allow=true decision=meleeEnabledDamageFiltered");
        if(store.getComponent(ref,Frozen.getComponentType())==null)store.putComponent(ref,Frozen.getComponentType(),Frozen.get());
        store.ensureComponent(ref,Interactable.getComponentType());
        var input=store.getComponent(ref,Interactions.getComponentType());
        if(input==null || !USE.equals(input.getInteractionId(InteractionType.Use))) {
            input=input==null?new Interactions():(Interactions)input.clone();input.setInteractionId(InteractionType.Use,USE);
            input.setInteractionId(InteractionType.Secondary,USE);input.setInteractionHint("server.interactionHints.axoSoul");
            store.putComponent(ref,Interactions.getComponentType(),input);
        }
        if(state.owner==null){animate(store,ref,state,"Idle");return;}
        if(state.formationSlot<0){
            state.formationSlot=claim(store,ref,state.owner,state.type());
            if(state.formationSlot<0){dropExcess(store,ref,state);return;}
        }
        state.scanSeconds+=delta;boolean scan=state.scanSeconds>=1;if(scan)state.scanSeconds=0;
        var type=state.type();if(type==null)return;
        state.lookAt=null;
        switch(type) {
            case VILLA->logistics.villa(store,ref,state,scan);
            case FOSSOR->{
                state.destination=state.anchor();
                state.lookAt=state.anchor().add(0,.65,1.8);
                logistics.fossor(store,ref,state,delta);
                long now=System.nanoTime();
                if(!state.workBlocked && state.workItem!=null && now>=state.nextWorkCast){
                    var npc=store.getComponent(ref,NPCEntity.getComponentType());npc.playAnimation(ref,AnimationSlot.Action,"Cast",store);
                    projectile(store,ref,state.anchor().add(0,.8,0),state.anchor().add(0,.65,1.8),CONJURE,store.getComponent(ref,UUIDComponent.getComponentType()).getUuid());
                    state.nextWorkCast=now+(long)(SoulState.MINING_SECONDS*1_000_000_000L);
                }
            }
            case CRUOR->cruor(store,ref,state,delta,neighbours);
        }
        var self=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        var destination=state.destination==null?new Vector3d(self):state.destination;
        destination=SoulFormation.avoid(self,destination,neighbours.near(ref,self,1.65),state.type().ordinal()*3+state.formationSlot);
        boolean moving=move(store,ref,destination,delta);
        if(state.lookAt==null) {
            if(moving)state.lookAt=destination;
            else if(type!=SoulKind.CRUOR) {
                var owner=store.getExternalData().getRefFromUUID(state.owner);
                var body=owner!=null && owner.isValid()?store.getComponent(owner,TransformComponent.getComponentType()):null;
                if(body!=null)state.lookAt=body.getPosition();
            }
        }
        SoulMotion.face(store.getComponent(ref,TransformComponent.getComponentType()),store.getComponent(ref,HeadRotation.getComponentType()),state.lookAt,delta);
        animate(store,ref,state,baseAnimation(state,moving));
        if(scan){store.putComponent(ref,stateType,state);dirty(store,ref);}
    }
    private static boolean move(Store<EntityStore> store,Ref<EntityStore> ref,Vector3d destination,float delta) {
        if(destination==null || !destination.isFinite())return false;
        var transform=store.getComponent(ref,TransformComponent.getComponentType());var current=transform.getPosition();
        var vector=new Vector3d(destination).sub(current);double distance=vector.length();if(distance<.2)return false;
        var next=new Vector3d(current).add(vector.mul(Math.min(1,delta*7/distance)));
        var world=store.getExternalData().getWorld();if(WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock((int)Math.floor(next.x),(int)Math.floor(next.z)))==null)return false;
        transform.setPosition(next);
        return true;
    }
    static String baseAnimation(SoulState state,boolean moving) {
        if(state.type()==SoulKind.FOSSOR)return moving?"Travel":!state.workBlocked&&state.workItem!=null?"Mine":"Idle";
        if(state.type()==SoulKind.VILLA&&!state.cargo.isEmpty())return "Carry";
        return moving?"Travel":"Idle";
    }
    static void animate(Store<EntityStore> store,Ref<EntityStore> ref,SoulState state,String name) {
        var active=store.ensureAndGetComponent(ref,ActiveAnimationComponent.getComponentType());
        if(name.equals(state.animation)&&name.equals(active.getActiveAnimations()[AnimationSlot.Status.ordinal()]))return;
        store.getComponent(ref,NPCEntity.getComponentType()).playAnimation(ref,AnimationSlot.Status,name,store);state.animation=name;
    }
    private void cruor(Store<EntityStore> store,Ref<EntityStore> ref,SoulState state,float delta,SoulNeighbours<Ref<EntityStore>> neighbours) {
        var self=store.getComponent(ref,TransformComponent.getComponentType());
        var owner=store.getExternalData().getRefFromUUID(state.owner);
        var ownerBody=owner==null||!owner.isValid()?null:store.getComponent(owner,TransformComponent.getComponentType());
        if(ownerBody==null || store.getComponent(owner,DeathComponent.getComponentType())!=null){
            state.target=null;state.combatMoving=false;if(self.getPosition().distanceSquared(state.anchor())>400)state.anchor(self.getPosition());
            state.destination=SoulMotion.roam(self.getPosition(),state.anchor(),state,delta);return;
        }
        state.anchor(ownerBody.getPosition());
        long now=System.nanoTime();var target=state.target==null?null:store.getExternalData().getRefFromUUID(state.target);
        var enemy=target==null||!target.isValid()?null:store.getComponent(target,TransformComponent.getComponentType());
        if(enemy==null || now>state.targetUntil || target.equals(owner) || store.getComponent(target,stateType)!=null
            || store.getComponent(target,DeathComponent.getComponentType())!=null || enemy.getPosition().distanceSquared(ownerBody.getPosition())>1600){
            state.target=null;state.combatMoving=false;state.destination=SoulMotion.roam(self.getPosition(),ownerBody.getPosition(),state,delta);
            if(self.getPosition().distanceSquared(ownerBody.getPosition())>SoulMotion.FOLLOW_LIMIT*SoulMotion.FOLLOW_LIMIT){
                var destination=state.destination;
                if(WorldBlocks.loaded(store.getExternalData().getWorld(),ChunkUtil.indexChunkFromBlock((int)Math.floor(destination.x),(int)Math.floor(destination.z)))==null)destination=new Vector3d(ownerBody.getPosition()).add(0,1.2,0);
                self.setPosition(new Vector3d(destination));
            }
            if(cruorWaiting(store,owner,state))state.destination=new Vector3d(self.getPosition());return;
        }
        var at=new Vector3d(enemy.getPosition()).add(0,.9,0);var origin=new Vector3d(self.getPosition()).add(0,.8,0);
        state.destination=SoulMotion.combatDestination(self.getPosition(),ownerBody.getPosition(),enemy.getPosition(),state);
        if(state.combatMoving)state.destination=SoulFormation.combat(enemy.getPosition(),ownerBody.getPosition(),state.formationSlot);
        state.lookAt=at;
        if(origin.distanceSquared(at)>900)return;
        if(now<state.nextShot || !sight(store,origin,at))return;
        if(!SoulFormation.laneClear(origin,at,neighbours.lane(ref,origin,at))){state.combatMoving=true;return;}
        if(projectile(store,ref,origin,at,BOLT,state.owner)) {
            state.nextShot=now+1_500_000_000L;
            store.getComponent(ref,NPCEntity.getComponentType()).playAnimation(ref,AnimationSlot.Action,"Cast",store);
            debug.traceFileOnly(null,"Soul event=cast kind=Cruor owner="+state.owner+" target="+state.target+" damage=10 intervalSeconds=1.5 allow=true");
        }
    }
    private static Vector3d forward(Store<EntityStore> store,Ref<EntityStore> owner){
        var head=store.getComponent(owner,HeadRotation.getComponentType());return head==null?new Vector3d(0,0,-1):head.getDirection();
    }
    private static boolean cruorWaiting(Store<EntityStore> store,Ref<EntityStore> owner,SoulState state){
        var movement=store.getComponent(owner,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());
        return state.target==null&&movement!=null&&movement.getMovementStates().crouching;
    }
    private static boolean sight(Store<EntityStore> store,Vector3d from,Vector3d to) {
        var result=new CollisionResult(false,false);result.setCollisionByMaterial(CollisionMaterial.MATERIAL_SOLID);
        CollisionModule.findBlockCollisionsIterative(store.getExternalData().getWorld(),new Box(-.02,-.02,-.02,.02,.02,.02),from,new Vector3d(to).sub(from),true,result);
        return result.getFirstBlockCollision()==null;
    }
    private static boolean projectile(Store<EntityStore> store,Ref<EntityStore> source,Vector3d from,Vector3d to,String asset,UUID shooter) {
        var direction=new Vector3d(to).sub(from);if(direction.lengthSquared()<.001)return false;
        if(BOLT.equals(asset) && direction.lengthSquared()<1.21)return false;
        direction.normalize();

        if(BOLT.equals(asset))from=new Vector3d(from).fma(1.05,direction);
        var rotation=new Rotation3f((float)Math.asin(direction.y),(float)Math.atan2(-direction.x,-direction.z),0);
        var holder=ProjectileComponent.assembleDefaultProjectile(store.getResource(TimeResource.getResourceType()),asset,from,rotation);if(holder==null)return false;
        var projectile=holder.getComponent(ProjectileComponent.getComponentType());if(projectile==null || !projectile.initialize())return false;
        projectile.shoot(holder,shooter,from.x,from.y,from.z,rotation.yaw(),rotation.pitch());store.addEntity(holder,AddReason.SPAWN);return true;
    }
    public void onInteraction(InteractionType type,InteractionContext context) {
        var buffer=context.getCommandBuffer();var player=context.getOwningEntity();var target=context.getTargetEntity();
        var event=context.getHeldItem();String eventItem=ItemStack.isEmpty(event)?"empty":event.getItemId();
        short slot=context.getHeldItemSlot();int section=context.getHeldItemSectionId();
        buffer.run(store->{try{use(store,player,target,type,eventItem,section,slot);}catch(RuntimeException failure){errors.report((PlayerRef)null,"Soul interaction failed",failure);}});
    }
    private void use(Store<EntityStore> store,Ref<EntityStore> player,Ref<EntityStore> target,InteractionType type,String eventItem,int section,short slot) {
        if(!player.isValid())return;var owner=store.getComponent(player,PlayerRef.getComponentType());if(owner==null)return;
        var held=InventoryComponentAccess.itemInHand(store,player);String item=ItemStack.isEmpty(held)?"empty":held.getItemId();
        var hotbar=InventoryComponentAccess.hotbarComponent(store,player);
        boolean actual=section==InventoryComponent.HOTBAR_SECTION_ID && hotbar!=null && hotbar.getActiveSlot()==slot && eventItem.equals(item);
        String result="invalidTarget";
        if(store.getComponent(player,DeathComponent.getComponentType())!=null)result="dead";
        else if(actual && type==InteractionType.Secondary && SoulKind.item(item)!=null)result=summon(store,player,owner,held,hotbar.getInventory(),slot);
        else if(target!=null && target.isValid()) {
            var npc=store.getComponent(target,NPCEntity.getComponentType());
            var a=store.getComponent(player,TransformComponent.getComponentType());var b=store.getComponent(target,TransformComponent.getComponentType());
            if(npc!=null && SoulKind.role(npc.getRoleName())!=null && a!=null && b!=null && a.getPosition().distanceSquared(b.getPosition())<=25
                && InteractionValidation.canPlayerInteractWithEntity(player,store,held,target)) {
                var state=state(store,target);long now=System.nanoTime();
                if(now-state.lastUse<350_000_000L)result="debounced";
                else {
                    state.lastUse=now;
                    if(state.owner==null)result="unbound";
                    else if(!state.owner.equals(owner.getUuid()))result="notOwner";
                    else if(actual && SoulState.isCrystal(item)){
                        var movement=store.getComponent(player,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());
                        boolean quick=movement!=null&&movement.getMovementStates().crouching;int before=state.feeds;
                        result=state.feed(owner.getUuid(),hotbar.getInventory(),slot,item,quick);
                        debug.traceFileOnly(owner,"Soul event=feed source=nativeUseEntity item="+item+" slot="+slot+" section="+section+" quick="+quick+" consumed="+(state.feeds-before)+" cancelled=false decision="+result);
                    }
                    else {
                        var movement=store.getComponent(player,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());
                        result=actual && "empty".equals(eventItem) && emptyHand(store,player) && movement!=null && movement.getMovementStates().crouching?recall(store,player,target,state):"status";
                    }
                    if(target.isValid()){store.putComponent(target,stateType,state);dirty(store,target);}
                    if("empowered".equals(result))owner.sendMessage(Message.raw("Fossor empowered ("+state.feeds+"/200): Adamantite/Mithril "+String.format(Locale.ROOT,"%.1f",1+state.feeds*.1)+"% each; Cobalt/Thorium "+String.format(Locale.ROOT,"%.1f",4+state.feeds*.1)+"% each."));
                    if("status".equals(result))owner.sendMessage(Message.raw(state.type().name+" Soul: "+(state.type()==SoulKind.FOSSOR?(state.workBlocked?"clear a little space in front of its workstation; mining paused.":"mining "+(int)state.workSeconds+"/"+(int)SoulState.MINING_SECONDS+"s, crystals "+state.feeds+"/200."):state.type()==SoulKind.VILLA?"collecting within 50 blocks of its station.":"guarding you; 10 damage every 1.5 seconds.")+" LMB with an empty hand returns it as an item; empty hand + Crouch + Interact recalls it. Only you can collect it."));
                }
            }
        }
        trace(store,player,owner,type,eventItem,item,slot,section,result);
        if(!Set.of("status","empowered","debounced","invalidTarget").contains(result))owner.sendMessage(Message.raw(switch(result){
            case "summoned"->"Soul summoned and bound to you. Interact for status. LMB with an empty hand returns it as an item with its cargo and upgrades.";
            case "recalled"->"Soul recalled. Its cargo, mining progress and upgrades are preserved.";
            case "unbound"->"This soul has no summoner and cannot be collected. Summon a soul item to bind it to you.";
            case "notOwner"->"This soul belongs to someone else.";
            case "inventoryFull"->"Make one inventory slot free to recall your soul.";
            case "maxPower"->"Fossor is fully empowered. No crystal consumed.";
            case "soulLimit"->"You can have three active souls of each type (nine total). Return one before summoning another.";
            case "wrongItem"->"Only Fossor accepts Arcane Crystals.";
            default->"Move to a loaded, clear space and try again. No soul was consumed.";
        }));
    }
    private String summon(Store<EntityStore> store,Ref<EntityStore> player,PlayerRef owner,ItemStack item,com.hypixel.hytale.server.core.inventory.container.ItemContainer inventory,short slot) {
        var kind=SoulKind.item(item.getItemId());var saved=item.getFromMetadataOrNull(SoulState.META,SoulState.CODEC);
        if(saved!=null && (saved.owner!=null && !saved.owner.equals(owner.getUuid()) || !kind.role.equals(saved.kind)))return "notOwner";
        int formation=available(owner.getUuid(),kind);if(formation<0)return "soulLimit";
        var head=store.getComponent(player,HeadRotation.getComponentType());var body=store.getComponent(player,TransformComponent.getComponentType());if(body==null)return "unavailable";
        var direction=head==null?new Vector3d(0,0,1):head.getDirection();direction.y=0;if(direction.lengthSquared()<.01)direction.set(0,0,1);direction.normalize();
        var at=new Vector3d(body.getPosition());
        double spread=(kind.ordinal()*3+formation-4)*.43;
        at.set(body.getPosition()).add(new Vector3d(direction).rotateY(spread).mul(3.5)).add(0,.35,0);
        if(kind==SoulKind.CRUOR)at.set(SoulFormation.follow(body.getPosition(),direction,formation));
        var world=store.getExternalData().getWorld();var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock((int)Math.floor(at.x),(int)Math.floor(at.z)));
        if(column==null)return "unavailable";
        for(int dy=0;dy<2;dy++){var block=column.getBlockType((int)Math.floor(at.x),(int)Math.floor(at.y)+dy,(int)Math.floor(at.z));if(block==null || block.getDrawType()!=DrawType.Empty)return "unavailable";}
        if(NPCPlugin.get()==null || !NPCPlugin.get().hasRoleName(kind.role))return "unavailable";
        var pair=NPCPlugin.get().spawnNPC(store,kind.role,null,at,new Rotation3f(0,SoulMotion.yaw(at,body.getPosition()),0));var ref=pair==null?null:pair.left();if(ref==null || !ref.isValid())return "unavailable";
        int claimed;
        try{claimed=claim(store,ref,owner.getUuid(),kind);}catch(RuntimeException failure){store.removeEntity(ref,RemoveReason.REMOVE);throw failure;}
        if(claimed<0){store.removeEntity(ref,RemoveReason.REMOVE);return "soulLimit";}

        if(!inventory.removeItemStackFromSlot(slot,item,1).succeeded()){release(store,ref,owner.getUuid(),kind);store.removeEntity(ref,RemoveReason.REMOVE);return "itemChanged";}
        var state=saved==null?new SoulState():saved.clone();state.owner=owner.getUuid();state.kind=kind.role;state.anchor(at);state.workEntity=null;state.target=null;state.animation=null;
        state.formationSlot=claimed;store.putComponent(ref,stateType,state);dirty(store,ref);update(store,ref,0,neighbourhood(store));return "summoned";
    }
    private String recall(Store<EntityStore> store,Ref<EntityStore> player,Ref<EntityStore> soul,SoulState state) {
        var owner=store.getComponent(player,PlayerRef.getComponentType());
        if(owner==null || !state.mayReturn(owner.getUuid()))return "notOwner";
        if(!emptyHand(store,player))return "handNotEmpty";
        if(state.returning)return "alreadyReturning";
        var token=state.token(state.owner);
        var destination=InventoryComponentAccess.storage(store,player);
        if(destination==null || !destination.canAddItemStack(token))destination=InventoryComponentAccess.hotbar(store,player);
        if(destination==null || !destination.canAddItemStack(token) || !destination.addItemStack(token,true,false,true).succeeded())return "inventoryFull";
        logistics.clearWork(store,state);release(store,soul,state.owner,state.type());store.removeEntity(soul,RemoveReason.REMOVE);return "recalled";
    }
    static boolean emptyHand(ComponentAccessor<EntityStore> access,Ref<EntityStore> player) {
        var hotbar=InventoryComponentAccess.hotbarComponent(access,player);

        return hotbar!=null && ItemStack.isEmpty(hotbar.getActiveItem()) && ItemStack.isEmpty(InventoryComponentAccess.itemInHand(access,player));
    }

    void onMeleePickup(Ref<EntityStore> player,Ref<EntityStore> soul) {
        if(!player.isValid() || !soul.isValid() || player.getStore()!=soul.getStore())return;
        var store=player.getStore();var owner=store.getComponent(player,PlayerRef.getComponentType());if(owner==null)return;
        String result="outOfRangeOrDead";
        var held=InventoryComponentAccess.itemInHand(store,player);String item=ItemStack.isEmpty(held)?"empty":held.getItemId();
        try {
            var state=store.getComponent(soul,stateType);
            var a=store.getComponent(player,TransformComponent.getComponentType());var b=store.getComponent(soul,TransformComponent.getComponentType());
            if(state==null || !state.mayReturn(owner.getUuid()))result=state==null || state.owner==null?"unbound":"notOwner";
            else if(!emptyHand(store,player))result="handNotEmpty";
            else if(a!=null && b!=null && a.getPosition().distanceSquared(b.getPosition())<=25 && store.getComponent(player,DeathComponent.getComponentType())==null
                && InteractionValidation.canPlayerInteractWithEntity(player,store,held,soul)) {
                if(state.returning)result="alreadyReturning";
                else {
                    var token=state.token(owner.getUuid());
                    var drop=com.hypixel.hytale.server.core.modules.entity.item.ItemComponent.generateItemDrop(store,token,new Vector3d(b.getPosition()).add(0,.4,0),new Rotation3f(),0,0,0);
                    if(drop==null)result="dropUnavailable";
                    else {
                        var id=store.getComponent(soul,UUIDComponent.getComponentType()).getUuid();
                        result=returnDrop(store,soul,state,drop,()->logistics.clearWork(store,state))?"dropped":"alreadyReturning";
                        if("dropped".equals(result) && state.owner!=null)release(state.owner,state.type(),id);
                    }
                }
            }
            if("notOwner".equals(result))owner.sendMessage(Message.raw("This soul belongs to someone else."));
        } catch(RuntimeException failure){errors.report(owner,"Soul LMB return failed",failure);result="failed";}
        var hotbar=InventoryComponentAccess.hotbarComponent(store,player);
        trace(store,player,owner,InteractionType.Primary,"nativeMelee",item,hotbar==null?(short)-1:hotbar.getActiveSlot(),InventoryComponent.HOTBAR_SECTION_ID,result);
    }
    static boolean returnDrop(Store<EntityStore> store,Ref<EntityStore> soul,SoulState state,Holder<EntityStore> item,Runnable cleanup) {
        if(!soul.isValid() || state.returning)return false;
        state.returning=true;Ref<EntityStore> dropped=null;
        try {
            dropped=store.addEntity(item,AddReason.SPAWN);
            cleanup.run();store.removeEntity(soul,RemoveReason.REMOVE);return true;
        } catch(RuntimeException failure) {
            if(soul.isValid()) {
                if(dropped!=null && dropped.isValid())store.removeEntity(dropped,RemoveReason.REMOVE);
                state.returning=false;
            }
            throw failure;
        }
    }
    private void trace(Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,InteractionType type,String event,String item,short slot,int section,String decision) {
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getMana();var mana=stats==null?null:stats.get(index);
        debug.traceFileOnly(player,"Soul event="+(type==InteractionType.Primary?"nativeMeleeReturn":"nativeInteraction")+" interactionType="+type+" cancelled="+(type==InteractionType.Primary)+" itemSource=serverActiveSlot eventItem="+event+" itemId="+item+" slot="+slot+" section="+section
            +" mana.index="+index+" mana.current="+(mana==null?"n/a":mana.get())+" mana.max="+(mana==null?"n/a":mana.getMax())+" mana.min="+(mana==null?"n/a":mana.getMin())
            +" mana.gated=false allow="+Set.of("summoned","empowered","recalled","status","dropped").contains(decision)+" decision="+decision);
    }
    static void dirty(Store<EntityStore> store,Ref<EntityStore> ref) {
        var dirty=store.getComponent(ref,Dirty.getComponentType());if(dirty!=null)dirty.markDirty();
    }
    static void allowMelee(Store<EntityStore> store,Ref<EntityStore> ref){
        store.tryRemoveComponent(ref,Intangible.getComponentType());
        store.tryRemoveComponent(ref,Invulnerable.getComponentType());

        store.tryRemoveComponent(ref,com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision.getComponentType());
    }
    private int available(UUID owner,SoulKind kind){try{return roster.available(owner,kind);}catch(java.io.IOException e){throw new IllegalStateException("Soul slot storage unavailable",e);}}
    private int claim(Store<EntityStore> store,Ref<EntityStore> ref,UUID owner,SoulKind kind){
        try{return roster.claim(owner,kind,store.getComponent(ref,UUIDComponent.getComponentType()).getUuid());}
        catch(java.io.IOException e){throw new IllegalStateException("Soul slot claim failed",e);}
    }
    private void release(Store<EntityStore> store,Ref<EntityStore> ref,UUID owner,SoulKind kind){release(owner,kind,store.getComponent(ref,UUIDComponent.getComponentType()).getUuid());}
    private void release(UUID owner,SoulKind kind,UUID id){try{roster.release(owner,kind,id);}catch(java.io.IOException e){throw new IllegalStateException("Soul slot release failed",e);}}
    private SoulNeighbours<Ref<EntityStore>> neighbourhood(Store<EntityStore> store){
        var index=new SoulNeighbours<Ref<EntityStore>>();
        store.forEachChunk(Query.and(stateType,TransformComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++)index.put(chunk.getReferenceTo(i),chunk.getComponent(i,TransformComponent.getComponentType()).getPosition());
        });
        return index;
    }
    private void dropExcess(Store<EntityStore> store,Ref<EntityStore> ref,SoulState state){
        var at=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        var drop=com.hypixel.hytale.server.core.modules.entity.item.ItemComponent.generateItemDrop(store,state.token(state.owner),new Vector3d(at).add(0,.4,0),new Rotation3f(),0,0,0);
        if(drop!=null && returnDrop(store,ref,state,drop,()->logistics.clearWork(store,state)))debug.traceFileOnly(null,"Soul event=limitMigration owner="+state.owner+" kind="+state.kind+" allow=true decision=excessReturnedWithContents");
    }
}
