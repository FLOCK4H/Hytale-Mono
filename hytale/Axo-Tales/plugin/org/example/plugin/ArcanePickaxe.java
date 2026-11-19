package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsConstants;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.*;
import org.joml.Vector3i;
import org.joml.Vector3d;


final class ArcanePickaxe extends TickingSystem<EntityStore> {
    static final String ITEM="Axo_Arcane_Pickaxe";
    static final String ANCIENT="Axo_Ancient_Pickaxe",ANIMATIONS="AxoTales_Pickaxe303";
    static final float MANA=10,IMPACT_TIME=1.5f,ANIMATION_TIME=2.2f;
    static final double LAUNCH_HEIGHT=25,RECOIL_AIRTIME=.25;
    enum SlamStep { WAIT,CANCEL }
    static final class SlamClock {
        float elapsed;boolean committed;
        SlamStep advance(float delta,boolean jump,boolean valid){
            if(jump||!valid)return SlamStep.CANCEL;
            if(!Float.isFinite(delta)||delta<=0)return SlamStep.WAIT;
            elapsed+=Math.min(delta,.25f);
            return SlamStep.WAIT;
        }
        boolean contact(){if(committed)return false;committed=true;return true;}
    }
    private record Held(String item,short slot,InteractionChain chain,float[] elapsed){}
    private record Slam(String item,short slot,InteractionChain chain,SlamClock clock){}
    private final Map<Ref<EntityStore>,Held> holds=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Ref<EntityStore>,Slam> slams=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Ref<EntityStore>,Long> slamReady=Collections.synchronizedMap(new WeakHashMap<>());
    record MiningDrops(int quantity,String itemId,String dropListId){}
    static MiningDrops miningDrops(BlockType block){
        var gathering=block.getGathering();
        if(gathering!=null&&gathering.getBreaking()!=null){var drop=gathering.getBreaking();return new MiningDrops(drop.getQuantity(),drop.getItemId(),drop.getDropListId());}
        if(gathering!=null&&gathering.getSoft()!=null){var drop=gathering.getSoft();return new MiningDrops(1,drop.getItemId(),drop.getDropListId());}
        return new MiningDrops(1,null,null);
    }
    private final Map<Ref<EntityStore>,Long> ready=Collections.synchronizedMap(new WeakHashMap<>());
    private final PluginDebugReporter debug;
    private final PluginErrorReporter errors;
    ArcanePickaxe(PluginDebugReporter debug,PluginErrorReporter errors){this.debug=debug;this.errors=errors;}
    static boolean tool(String id){return ITEM.equals(id)||ANCIENT.equals(id);}
    boolean use(InteractionContext context){return use("pickaxe",context);}
    boolean use(String mode,InteractionContext context){
        var ref=context.getEntity();var buffer=context.getCommandBuffer();if(ref==null||buffer==null)return false;
        var held=InventoryComponentAccess.itemInHand(buffer,ref);if(ItemStack.isEmpty(held)||!tool(held.getItemId())||held.isBroken())return false;
        if("pickaxeStop".equals(mode)){holds.remove(ref);return true;}
        if("pickaxeSlam".equals(mode))return startSlam(context,held);
        if("pickaxeImpact".equals(mode))return contact(context);
        if(slams.containsKey(ref))return false;
        long now=System.nanoTime();if(ready.getOrDefault(ref,0L)>now)return false;ready.put(ref,now+450_000_000L);
        boolean single="pickaxePrimary".equals(mode)&&ANCIENT.equals(held.getItemId());
        buffer.run(store->{if(ref.isValid())try{mine(store,ref,held,single,false);}catch(Throwable failure){errors.report(store.getComponent(ref,PlayerRef.getComponentType()),"Pickaxe mining failed",failure);}});return true;
    }
    boolean hold(InteractionContext context){
        var ref=context.getEntity();var buffer=context.getCommandBuffer();if(ref==null||buffer==null||context.getChain()==null)return false;
        var item=InventoryComponentAccess.itemInHand(buffer,ref);if(ItemStack.isEmpty(item)||!tool(item.getItemId())||item.isBroken()||slams.containsKey(ref))return false;
        var active=new Held(item.getItemId(),InventoryComponentAccess.activeHotbarSlot(buffer,ref),context.getChain(),new float[]{.45f});
        holds.put(ref,active);trace(buffer,ref,true,"heldBegin",0,item.getItemId());return true;
    }
    private boolean startSlam(InteractionContext context,ItemStack item){
        var ref=context.getEntity();var buffer=context.getCommandBuffer();var movement=buffer.getComponent(ref,MovementStatesComponent.getComponentType());
        var player=buffer.getComponent(ref,Player.getComponentType());long now=System.nanoTime();
        var model=buffer.getComponent(ref,ModelComponent.getComponentType());
        if(useTarget(buffer,ref)){trace(buffer,ref,false,"aimedUseTargetHasPriority",0,item.getItemId());return false;}
        if(context.getChain()==null||movement==null||!movement.getMovementStates().onGround||movement.getMovementStates().jumping||movement.getMovementStates().flying||player==null||player.getMountEntityId()!=0||model!=null&&WhisperionFlight.MODEL.equals(model.getModel().getModelAssetId())||!living(buffer,ref)||slams.containsKey(ref)||slamReady.getOrDefault(ref,0L)>now){trace(buffer,ref,false,"slamGroundedGate",0,item.getItemId());return false;}
        holds.remove(ref);slams.put(ref,new Slam(item.getItemId(),InventoryComponentAccess.activeHotbarSlot(buffer,ref),context.getChain(),new SlamClock()));
        slamReady.put(ref,now+3_000_000_000L);trace(buffer,ref,true,"slamBegin",0,item.getItemId());return true;
    }
    static boolean interactive(com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType block){return block!=null&&(block.getBench()!=null||block.getInteractions()!=null&&block.getInteractions().containsKey(InteractionType.Use));}
    static boolean useTarget(ComponentAccessor<EntityStore> access,Ref<EntityStore> ref){
        var at=TargetUtil.getTargetBlock(ref,6,access);
        if(at!=null){var world=access.getExternalData().getWorld();var base=com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations.resolveBaseBlockPosition(world.getChunkStore(),new BlockPosition(at.x,at.y,at.z));var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(base.x,base.z));if(column!=null&&interactive(column.getBlockType(base.x,base.y,base.z)))return true;}
        var entity=ScoutTargets.visible(ref,6,false,access);if(entity==null||!entity.isValid())return false;
        var interactions=access.getComponent(entity,com.hypixel.hytale.server.core.modules.interaction.Interactions.getComponentType());
        return interactions!=null&&interactions.getInteractionId(InteractionType.Use)!=null;
    }
    private boolean contact(InteractionContext context){
        var ref=context.getEntity();var buffer=context.getCommandBuffer();var slam=slams.get(ref);
        if(slam==null||slam.chain!=context.getChain()||!same(buffer,ref,slam.item,slam.slot)||!live(slam.chain))return false;
        var movement=buffer.getComponent(ref,MovementStatesComponent.getComponentType());
        if(movement!=null&&movement.getMovementStates().jumping){slams.remove(ref);buffer.run(store->{if(ref.isValid())stop(store,ref,slam);});return false;}
        if(!slam.clock.contact())return false;
        buffer.run(store->{if(ref.isValid()&&same(store,ref,slam.item,slam.slot))try{impact(store,ref,slam);}catch(RuntimeException failure){errors.report(store.getComponent(ref,PlayerRef.getComponentType()),"Pickaxe contact failed",failure);}});
        trace(buffer,ref,true,"animationContact frame=45 speed=.5",0,slam.item);return true;
    }
    static boolean live(InteractionChain chain){return chain!=null&&chain.getServerState()==InteractionState.NotFinished&&(!chain.requiresClient()||chain.getClientState()==InteractionState.NotFinished);}
    static boolean completed(InteractionChain chain){return chain!=null&&chain.getServerState()==InteractionState.Finished&&(!chain.requiresClient()||chain.getClientState()==InteractionState.Finished);}
    static boolean living(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref){return store.getComponent(ref,DeathComponent.getComponentType())==null&&store.getComponent(ref,Spectating.getComponentType())==null;}
    private static boolean same(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref,String id,short slot){var item=InventoryComponentAccess.itemInHand(store,ref);return living(store,ref)&&!ItemStack.isEmpty(item)&&id.equals(item.getItemId())&&!item.isBroken()&&slot==InventoryComponentAccess.activeHotbarSlot(store,ref);}
    @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
        if(!Float.isFinite(delta)||delta<=0)return;
        for(var entry:new ArrayList<>(holds.entrySet())){
            var ref=entry.getKey();var hold=entry.getValue();if(!ref.isValid()){holds.remove(ref);continue;}if(ref.getStore()!=store)continue;
            if(!same(store,ref,hold.item,hold.slot)||!live(hold.chain)||slams.containsKey(ref)){holds.remove(ref);continue;}
            hold.elapsed[0]+=Math.min(delta,.25f);if(hold.elapsed[0]<.45f)continue;hold.elapsed[0]=0;
            try{mine(store,ref,InventoryComponentAccess.itemInHand(store,ref),false,false);}catch(RuntimeException failure){holds.remove(ref);errors.report(store.getComponent(ref,PlayerRef.getComponentType()),"Held pickaxe mining failed",failure);}
        }
        for(var entry:new ArrayList<>(slams.entrySet())){
            var ref=entry.getKey();var slam=entry.getValue();if(!ref.isValid()){slams.remove(ref);continue;}if(ref.getStore()!=store)continue;
            if(completed(slam.chain)){slams.remove(ref);continue;}
            var movement=store.getComponent(ref,MovementStatesComponent.getComponentType());
            var step=slam.clock.advance(delta,movement!=null&&movement.getMovementStates().jumping,same(store,ref,slam.item,slam.slot)&&live(slam.chain));
            if(step==SlamStep.CANCEL){slams.remove(ref);stop(store,ref,slam);continue;}
        }
    }
    private void stop(Store<EntityStore> store,Ref<EntityStore> ref,Slam slam){
        var manager=store.getComponent(ref,InteractionModule.get().getInteractionManagerComponent());if(manager!=null&&live(slam.chain))manager.cancelChains(slam.chain);
        AnimationUtils.stopAnimation(ref,AnimationSlot.Action,true,store);
        if(slam.clock.committed)store.ensureAndGetComponent(ref,Velocity.getComponentType()).addInstruction(new Vector3d(),forceConfig(),ChangeVelocityType.Set);
        trace(store,ref,false,"slamCancelled",0,slam.item);
    }
    static List<Vector3i> crater(Vector3i surface){var result=new ArrayList<Vector3i>(18);for(int depth=0;depth<2;depth++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)result.add(new Vector3i(surface).add(x,-depth,z));return result;}
    static double launchVelocity(){return Math.sqrt(2*PhysicsConstants.GRAVITY_ACCELERATION*LAUNCH_HEIGHT);}
    static double recoilDistance(java.util.random.RandomGenerator rng){return rng.nextDouble(1.1,2.0);}
    static Vector3d recoil(Vector3d look,double distance){var flat=new Vector3d(look.x,0,look.z);if(!flat.isFinite()||flat.lengthSquared()<1e-8||!Double.isFinite(distance))return new Vector3d();return flat.normalize().mul(-distance/RECOIL_AIRTIME).setComponent(1,PhysicsConstants.GRAVITY_ACCELERATION*RECOIL_AIRTIME/2);}
    static boolean queueRecoil(Velocity velocity,MovementStates movement,Vector3d look,double distance){
        if(movement==null||!movement.onGround||movement.jumping||movement.flying||movement.falling||movement.fallingFar||movement.climbing||movement.swimming||movement.inFluid)return false;
        var impulse=recoil(look,distance);if(impulse.lengthSquared()==0)return false;



        velocity.addInstruction(impulse,recoilConfig(),ChangeVelocityType.Set);return true;
    }
    static VelocityConfig recoilConfig(){var config=new VelocityConfig();config.setAirResistance(.97f);config.setAirResistanceMax(.97f);config.setGroundResistance(.8f);config.setGroundResistanceMax(.8f);config.setThreshold(0);return config;}
    static VelocityConfig forceConfig(){var config=new VelocityConfig();config.setAirResistance(1);config.setAirResistanceMax(1);config.setGroundResistance(1);config.setGroundResistanceMax(1);config.setThreshold(0);return config;}
    private void kick(Store<EntityStore> store,Ref<EntityStore> ref){
        var head=store.getComponent(ref,HeadRotation.getComponentType());var movement=store.getComponent(ref,MovementStatesComponent.getComponentType());var player=store.getComponent(ref,Player.getComponentType());
        var model=store.getComponent(ref,ModelComponent.getComponentType());
        if(head==null||movement==null||player!=null&&player.getMountEntityId()!=0||model!=null&&WhisperionFlight.MODEL.equals(model.getModel().getModelAssetId()))return;

        var flat=new HeadRotation(head.getRotation());flat.getRotation().setPitch(0);
        boolean allowed=queueRecoil(store.ensureAndGetComponent(ref,Velocity.getComponentType()),movement.getMovementStates(),flat.getDirection(),recoilDistance(ThreadLocalRandom.current()));
        trace(store,ref,allowed,allowed?"recoilOneShotGrounded":"recoilSkippedAirborne",0,ANCIENT);
    }
    private void impact(Store<EntityStore> store,Ref<EntityStore> ref,Slam slam){
        var position=store.getComponent(ref,TransformComponent.getComponentType());var head=store.getComponent(ref,HeadRotation.getComponentType());if(position==null||head==null)return;
        var forward=head.getDirection();forward.y=0;if(forward.lengthSquared()>1e-6)forward.normalize();
        var point=new Vector3d(position.getPosition()).fma(1.5,forward);var surface=new Vector3i((int)Math.floor(point.x),(int)Math.floor(position.getPosition().y)-1,(int)Math.floor(point.z));
        var item=InventoryComponentAccess.itemInHand(store,ref);int broken=breakBlocks(store,ref,item,crater(surface));
        if(broken==0){trace(store,ref,false,"slamProtectedOrNoGround",0,slam.item);return;}
        store.ensureAndGetComponent(ref,Velocity.getComponentType()).addInstruction(new Vector3d(0,launchVelocity(),0),forceConfig(),ChangeVelocityType.Set);
        var player=store.getComponent(ref,Player.getComponentType());if(player!=null)player.setCurrentFallDistance(0);
        trace(store,ref,true,"slamImpact apex=25 gravity="+PhysicsConstants.GRAVITY_ACCELERATION,broken,slam.item);
    }
    static List<Vector3i> cube(Vector3i target,Vector3d direction){
        int axis=Math.abs(direction.y)>Math.abs(direction.x)&&Math.abs(direction.y)>Math.abs(direction.z)?1:Math.abs(direction.x)>Math.abs(direction.z)?0:2;
        int sign=(axis==0?direction.x:axis==1?direction.y:direction.z)>=0?1:-1;
        var blocks=new ArrayList<Vector3i>(27);
        for(int depth=0;depth<3;depth++)for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++)blocks.add(axis==0?new Vector3i(target.x+depth*sign,target.y+a,target.z+b):axis==1?new Vector3i(target.x+a,target.y+depth*sign,target.z+b):new Vector3i(target.x+a,target.y+b,target.z+depth*sign));
        return blocks;
    }

    static Vector3d faceDirection(Vector3i target,Vector3d eye,Vector3d direction){
        if(!eye.isFinite()||!direction.isFinite()||direction.lengthSquared()==0)return null;
        double entry=Double.NEGATIVE_INFINITY;int axis=-1;
        for(int i=0;i<3;i++){
            double component=direction.get(i);if(Math.abs(component)<1e-9)continue;
            double boundary=target.get(i)+(component<0?1:0);
            double distance=(boundary-eye.get(i))/component;
            if(distance>entry){entry=distance;axis=i;}
        }
        return axis<0?null:new Vector3d().setComponent(axis,Math.signum(direction.get(axis)));
    }
    private void mine(Store<EntityStore> store,Ref<EntityStore> player,ItemStack expected,boolean single,boolean slam){
        int index=DefaultEntityStatTypes.getMana();var stats=store.getComponent(player,EntityStatMap.getComponentType());var mana=stats==null||index<0?null:stats.get(index);boolean ancient=ANCIENT.equals(expected.getItemId());
        boolean allowed=Objects.equals(expected,InventoryComponentAccess.itemInHand(store,player))&&living(store,player)&&(ancient||mana!=null&&mana.get()-mana.getMin()>=MANA);
        if(!allowed){trace(store,player,false,"heldItemOrManaGate",0,expected.getItemId());return;}
        if(ancient)kick(store,player);
        var target=TargetUtil.getTargetBlock(player,6,store);var look=TargetUtil.getLook(player,store);
        var direction=target==null||look==null?null:faceDirection(target,look.getPosition(),look.getDirection());
        if(direction==null){trace(store,player,false,"noSolidTarget",0,expected.getItemId());return;}
        int broken=breakBlocks(store,player,expected,single?List.of(target):cube(target,direction));
        if(broken>0&&!ancient){stats.setStatValue(index,Math.max(mana.getMin(),mana.get()-MANA));stats.update();
            var parent=InventoryComponentAccess.hotbar(store,player);short slot=InventoryComponentAccess.activeHotbarSlot(store,player);
            if(parent!=null&&slot>=0&&slot<parent.getCapacity()&&Objects.equals(expected,parent.getItemStack(slot)))parent.setItemStackForSlot(slot,expected.withDurability(Math.max(0,expected.getDurability()-1)),false);
        }
        trace(store,player,broken>0,"nativeBreakEvents",broken,expected.getItemId());
    }
    static boolean ore(BlockType block){
        var gather=block.getGathering();var breaking=gather==null?null:gather.getBreaking();if(breaking!=null&&breaking.getGatherType()!=null&&breaking.getGatherType().startsWith("Ore"))return true;
        var item=block.getItem();if(item==null)return false;
        if(item.getCategories()!=null&&Arrays.asList(item.getCategories()).contains("Blocks.Ores"))return true;
        var data=item.getData();var tags=data==null?null:data.getRawTags();return tags!=null&&tags.get("Type")!=null&&Arrays.asList(tags.get("Type")).contains("Ore");
    }
    static MiningDrops miningDrops(BlockType block,boolean ancient){var receipt=miningDrops(block);return ancient&&ore(block)?new MiningDrops(Math.multiplyExact(receipt.quantity,3),receipt.itemId,receipt.dropListId):receipt;}
    private int breakBlocks(Store<EntityStore> store,Ref<EntityStore> player,ItemStack expected,List<Vector3i> targets){
        var world=store.getExternalData().getWorld();var chunks=world.getChunkStore();int broken=0;
        for(var at:targets){
            if(at.y<0||at.y>=ChunkUtil.HEIGHT)continue;
            var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(at.x,at.z));if(column==null)continue;
            var type=column.getBlockType(at.x,at.y,at.z);
            if(type==null||type.isUnknown()||type.getDrawType()==DrawType.Empty||type.getId().endsWith("Bedrock")||type.getGathering()==null||(type.getGathering().getBreaking()==null&&type.getGathering().getSoft()==null)||SpellTerrainProtection.protectedAt(world,at.x,at.y,at.z,type))continue;
            var section=chunks.getChunkSectionReferenceAtBlock(at.x,at.y,at.z);if(section==null||!section.isValid())continue;
            var drops=miningDrops(type,ANCIENT.equals(expected.getItemId()));
            BlockHarvestUtils.performBlockBreak(at,type,expected,drops.quantity,drops.itemId,drops.dropListId,0,player,section,store,chunks.getStore());
            var after=column.getBlockType(at.x,at.y,at.z);if(after==null||!type.getId().equals(after.getId()))broken++;
        }
        return broken;
    }
    private void trace(ComponentAccessor<EntityStore> store,Ref<EntityStore> player,boolean allow,String reason,int broken,String item){
        int index=DefaultEntityStatTypes.getMana();var stats=store.getComponent(player,EntityStatMap.getComponentType());var mana=stats==null||index<0?null:stats.get(index);
        debug.traceFileOnly(store.getComponent(player,PlayerRef.getComponentType()),"Pickaxe event=nativeInput cancelled="+reason.contains("Cancelled")+" itemSource=serverHotbar item="+item+" mana.index="+index+" mana.current="+(mana==null?"missing":mana.get())+" mana.min="+(mana==null?"missing":mana.getMin())+" mana.max="+(mana==null?"missing":mana.getMax())+" broken="+broken+" reason="+reason+" allow="+allow);
    }
}
