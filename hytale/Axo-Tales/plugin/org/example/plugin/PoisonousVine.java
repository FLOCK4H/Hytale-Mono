package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.*;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.*;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;


final class PoisonousVine implements Component<ChunkStore> {
    static final int SCHEMA=302,WIDTH=40,HEIGHT=40,LEFT=-20,RIGHT=19;
    static final int OLD_WIDTH=233,OLD_HEIGHT=117;
    static final BuilderCodec<PoisonousVine> CODEC=BuilderCodec.builder(PoisonousVine.class,PoisonousVine::new)
        .append(new KeyedCodec<>("Branches",Codec.INTEGER),(s,v)->s.branches=v,s->s.branches).add()
        .append(new KeyedCodec<>("Schema",Codec.INTEGER),(s,v)->s.schema=v,s->s.schema).add()
        .append(new KeyedCodec<>("HasOrigin",Codec.BOOLEAN),(s,v)->s.hasOrigin=v,s->s.hasOrigin).add()
        .append(new KeyedCodec<>("OriginX",Codec.INTEGER),(s,v)->s.originX=v,s->s.originX).add()
        .append(new KeyedCodec<>("OriginY",Codec.INTEGER),(s,v)->s.originY=v,s->s.originY).add()
        .append(new KeyedCodec<>("OriginZ",Codec.INTEGER),(s,v)->s.originZ=v,s->s.originZ).add()
        .append(new KeyedCodec<>("Root",Codec.BOOLEAN),(s,v)->s.root=v,s->s.root).add()
        .append(new KeyedCodec<>("RootId",Codec.UUID_STRING),(s,v)->s.rootId=v,s->s.rootId).add()
        .append(new KeyedCodec<>("LegacyClaim",Codec.BOOLEAN),(s,v)->s.legacyClaim=v,s->s.legacyClaim).add()
        .append(new KeyedCodec<>("Width",Codec.INTEGER),(s,v)->s.width=v==OLD_WIDTH?OLD_WIDTH:WIDTH,s->s.width).add()
        .append(new KeyedCodec<>("Height",Codec.INTEGER),(s,v)->s.height=v==OLD_HEIGHT?OLD_HEIGHT:HEIGHT,s->s.height).add()
        .append(new KeyedCodec<>("WitherAt",Codec.LONG),(s,v)->s.witherAt=Math.max(0,v),s->s.witherAt).add().build();
    int branches=7,schema,originX,originY,originZ,width=WIDTH,height=HEIGHT;boolean hasOrigin,root,legacyClaim;UUID rootId;
    long witherAt;
    static final class DeadRoots implements Resource<ChunkStore> {
        static ResourceType<ChunkStore,DeadRoots> TYPE;
        static final BuilderCodec<DeadRoots> CODEC=BuilderCodec.builder(DeadRoots.class,DeadRoots::new)
            .append(new KeyedCodec<>("Roots",Codec.STRING_ARRAY),(s,v)->s.roots.addAll(Arrays.asList(v)),s->s.roots.toArray(String[]::new)).add().build();
        final Set<String> roots=new HashSet<>();
        private static String legacy(PoisonousVine state){return "legacy@"+state.originX+","+state.originY+","+state.originZ;}
        void remember(PoisonousVine state){if(state.rootId!=null)roots.add(state.rootId.toString());if(state.schema==301||state.legacyClaim)roots.add(legacy(state));}
        boolean lost(PoisonousVine state){return state.rootId!=null&&roots.contains(state.rootId.toString())||state.schema==301&&state.hasOrigin&&roots.contains(legacy(state));}
        @Override public DeadRoots clone(){var copy=new DeadRoots();copy.roots.addAll(roots);return copy;}
    }
    Vector3i origin(){return new Vector3i(originX,originY,originZ);}
    enum RootPresence { LIVE,LOST,UNAVAILABLE }
    static RootPresence presence(boolean vine,PoisonousVine root,PoisonousVine leaf){
        if(!vine)return RootPresence.LOST;
        if(root==null||!root.hasOrigin||root.schema==SCHEMA&&(!root.root||root.rootId==null))return RootPresence.UNAVAILABLE;
        return root.root&&root.owns(leaf)||root.schema==301&&leaf.schema==301&&root.origin().equals(leaf.origin())?RootPresence.LIVE:RootPresence.LOST;
    }
    boolean wither(RootPresence presence,Vector3i at,long now){
        if(root||!hasOrigin)return false;
        if(witherAt==0&&presence==RootPresence.LOST)witherAt=now+500+60L*(Math.abs((long)at.x-originX)+Math.abs((long)at.y-originY)+Math.abs((long)at.z-originZ));
        return witherAt>0&&now>=witherAt;
    }
    boolean initialize(Vector3i at){
        if(schema==SCHEMA&&!hasOrigin){originX=at.x;originY=at.y;originZ=at.z;hasOrigin=true;root=true;rootId=UUID.randomUUID();return true;}
        if(schema==301&&hasOrigin&&at.equals(origin())){schema=SCHEMA;root=true;rootId=UUID.randomUUID();legacyClaim=true;width=OLD_WIDTH;height=OLD_HEIGHT;return true;}
        return root&&schema==SCHEMA&&rootId!=null&&hasOrigin&&at.equals(origin());
    }
    PoisonousVine child(){var copy=clone();copy.root=false;copy.branches=0;copy.legacyClaim=false;return copy;}
    boolean owns(PoisonousVine leaf){return leaf!=null&&leaf.hasOrigin&&origin().equals(leaf.origin())&&(Objects.equals(rootId,leaf.rootId)&&rootId!=null||legacyClaim&&leaf.schema==301);}
    @Override public PoisonousVine clone(){var c=new PoisonousVine();c.branches=branches;c.schema=schema;c.hasOrigin=hasOrigin;c.originX=originX;c.originY=originY;c.originZ=originZ;c.root=root;c.rootId=rootId;c.legacyClaim=legacyClaim;c.width=width;c.height=height;c.witherAt=witherAt;return c;}

    static Vector3i lateral(int rotation){var a=RotationTuple.get(rotation).rotatedVector(new Vector3d(1,0,0));return Math.abs(a.x)>=Math.abs(a.z)?new Vector3i(a.x>=0?1:-1,0,0):new Vector3i(0,0,a.z>=0?1:-1);}
    static boolean within(Vector3i at,Vector3i origin,Vector3i across){return within(at,origin,across,WIDTH,HEIGHT);}
    static boolean within(Vector3i at,Vector3i origin,Vector3i across,int width,int height){long dx=(long)at.x-origin.x,dz=(long)at.z-origin.z,y=(long)at.y-origin.y,x=dx*across.x+dz*across.z;return y>=0&&y<height&&x>=-width/2&&x<width-width/2&&dx*across.z-dz*across.x==0;}
    static boolean replaceable(BlockType block){
        if(block==null||block.getMaterial()!=com.hypixel.hytale.protocol.BlockMaterial.Empty||block.getBlockEntity()!=null)return false;
        String id=block.getDefaultStateKey()==null?block.getId():block.getDefaultStateKey();
        return id!=null&&(id.startsWith("Plant_Grass_")||id.startsWith("Plant_Bush_")||id.startsWith("Plant_Bramble_"));
    }
    enum Space { OPEN,OWN,BLOCKED,UNAVAILABLE }
    interface Growth {Space space(Vector3i at);boolean place(Vector3i at);boolean alive();}
    record Result(int checked,int placed,boolean finished){}
    static final class Sweep {
        private final Vector3i origin,across;private final int width,height;private final ArrayDeque<Integer> queue=new ArrayDeque<>();private final BitSet seen=new BitSet();
        Sweep(Vector3i origin,Vector3i across){this(origin,across,WIDTH,HEIGHT);}
        Sweep(Vector3i origin,Vector3i across,int width,int height){this.origin=new Vector3i(origin);this.across=new Vector3i(across);this.width=width;this.height=height;add(width/2);}
        private void add(int index){if(index<0||index>=width*height||seen.get(index))return;seen.set(index);queue.addLast(index);}
        Result step(Growth grid,int checks,int placements){
            if(!grid.alive()){queue.clear();return new Result(0,0,true);}
            int checked=0,placed=0;
            while(!queue.isEmpty()&&checked<checks&&placed<placements){
                int index=queue.removeFirst(),x=index%width,y=index/width;checked++;
                var at=new Vector3i(origin).add(new Vector3i(across).mul(x-width/2)).add(0,y,0);
                if(at.y<0||at.y>=ChunkUtil.HEIGHT)continue;
                Space space=grid.space(at);
                if(space==Space.OPEN){if(!grid.alive()||!grid.place(at))continue;placed++;}
                else if(space!=Space.OWN)continue;
                if(y+1<height)add(index+width);if(x>0)add(index-1);if(x+1<width)add(index+1);if(y>0)add(index-width);
            }
            return new Result(checked,placed,queue.isEmpty());
        }
    }
    static final class GrowthSystem extends TickingSystem<ChunkStore> {
        private static final class Work {Sweep sweep;float pause;final Set<Vector3i> repairs=new LinkedHashSet<>();}
        private static final class Scene {final Set<Ref<ChunkStore>> roots=new LinkedHashSet<>();final Map<Ref<ChunkStore>,Work> work=new HashMap<>();float elapsed;}
        private final Map<Store<ChunkStore>,Scene> scenes=Collections.synchronizedMap(new WeakHashMap<>());
        private final ComponentType<ChunkStore,PoisonousVine> type;private final PluginErrorReporter errors;
        GrowthSystem(ComponentType<ChunkStore,PoisonousVine> type,PluginErrorReporter errors){this.type=type;this.errors=errors;}
        RefSystem<ChunkStore> lifecycle(){return new RefSystem<>(){
            @Override public Query<ChunkStore> getQuery(){return Query.and(type,BlockModule.BlockStateInfo.getComponentType());}
            @Override public void onEntityAdded(Ref<ChunkStore> ref,AddReason reason,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){scenes.computeIfAbsent(store,k->new Scene()).roots.add(ref);}
            @Override public void onEntityRemove(Ref<ChunkStore> ref,RemoveReason reason,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){
                var scene=scenes.get(store);if(scene==null)return;scene.roots.remove(ref);scene.work.remove(ref);
                if(reason==RemoveReason.UNLOAD)return;
                var leaf=store.getComponent(ref,type);var info=store.getComponent(ref,BlockModule.BlockStateInfo.getComponentType());var at=new Vector3i();
                if(leaf==null||!leaf.hasOrigin||info==null||!info.fillWorldPos(store,at))return;
                if(leaf.root||at.equals(leaf.origin())){
                    if(DeadRoots.TYPE!=null){var dead=store.getResource(DeadRoots.TYPE).clone();dead.remember(leaf);store.replaceResource(DeadRoots.TYPE,dead);}
                    return;
                }
                var world=store.getExternalData().getWorld();var origin=leaf.origin();var owner=BlockModule.getBlockEntity(world,origin.x,origin.y,origin.z);
                if(owner==null||!owner.isValid())return;var root=store.getComponent(owner,type);
                if(root==null||!root.root||!root.owns(leaf))return;
                scene.work.computeIfAbsent(owner,k->new Work()).repairs.add(at);
            }
        };}
        @Override public void tick(float delta,int tick,@Nonnull Store<ChunkStore> store){
            if(!Float.isFinite(delta)||delta<=0)return;var scene=scenes.get(store);if(scene==null||scene.roots.isEmpty())return;
            scene.elapsed+=delta;if(scene.elapsed<.25f)return;float beat=scene.elapsed;scene.elapsed=0;int budget=256,placements=16,count=scene.roots.size();
            for(int i=0;i<count&&budget>0&&placements>0;i++){
                var it=scene.roots.iterator();if(!it.hasNext())break;var ref=it.next();it.remove();if(!ref.isValid()){scene.work.remove(ref);continue;}
                var work=scene.work.computeIfAbsent(ref,k->new Work());work.pause=Math.max(0,work.pause-beat);
                try{var result=advance(store,ref,work,budget,placements);budget-=Math.max(1,result.checked());placements-=result.placed();if(result.checked()<0){scene.work.remove(ref);continue;}scene.roots.add(ref);}
                catch(RuntimeException e){scene.roots.add(ref);errors.report((com.hypixel.hytale.server.core.universe.PlayerRef)null,"Poisonous Vine root refresh deferred",e);}
            }
        }
        private Result advance(Store<ChunkStore> store,Ref<ChunkStore> ref,Work work,int checks,int placements){
            var state=store.getComponent(ref,type);var info=store.getComponent(ref,BlockModule.BlockStateInfo.getComponentType());var at=new Vector3i();
            if(state==null||info==null||!info.fillWorldPos(store,at))return new Result(-1,0,true);
            if(state.hasOrigin&&!at.equals(state.origin()))return decay(store,ref,state,info,at);
            if(!state.root||state.schema!=SCHEMA||!state.hasOrigin){state=state.clone();if(!state.initialize(at))return new Result(-1,0,true);store.putComponent(ref,type,state);info.markNeedsSaving(store);}
            World world=store.getExternalData().getWorld();var col=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(at.x,at.z));
            if(col==null||!AxoItems.block(col.getBlockType(at.x,at.y,at.z),AxoItems.POISONOUS_VINE))return new Result(-1,0,true);
            var section=world.getChunkStore().getChunkSectionReference(ChunkUtil.chunkCoordinate(at.x),ChunkUtil.indexSection(at.y),ChunkUtil.chunkCoordinate(at.z));
            if(section==null||!section.isValid())return new Result(0,0,false);var blocks=store.getComponent(section,BlockSection.getComponentType());if(blocks==null)return new Result(0,0,false);
            int rotation=blocks.getRotationIndex(at.x,at.y,at.z),index=BlockType.getAssetMap().getIndex(AxoItems.POISONOUS_VINE);var asset=BlockType.getAssetMap().getAsset(index);if(index<0||asset==null)return new Result(0,0,false);
            var root=state;var origin=root.origin();var across=lateral(rotation);if(work.sweep==null)work.sweep=new Sweep(origin,across,root.width,root.height);
            var grid=new Growth(){
                @Override public boolean alive(){if(!ref.isValid())return false;var current=store.getComponent(ref,type);return current!=null&&current.root&&Objects.equals(current.rootId,root.rootId);}
                @Override public Space space(Vector3i target){
                    var loaded=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(target.x,target.z));if(loaded==null)return Space.UNAVAILABLE;
                    var sr=world.getChunkStore().getChunkSectionReference(ChunkUtil.chunkCoordinate(target.x),ChunkUtil.indexSection(target.y),ChunkUtil.chunkCoordinate(target.z));if(sr==null||!sr.isValid()||store.getComponent(sr,BlockSection.getComponentType())==null)return Space.UNAVAILABLE;
                    if(loaded.getFluidId(target.x,target.y,target.z)!=0)return Space.BLOCKED;
                    var block=loaded.getBlockType(target.x,target.y,target.z);if(block==null)return Space.UNAVAILABLE;
                    if(AxoItems.block(block,AxoItems.POISONOUS_VINE)){
                        var leaf=BlockModule.getBlockEntity(world,target.x,target.y,target.z);var data=leaf==null?null:store.getComponent(leaf,type);
                        if(root.owns(data))return Space.OWN;
                        return Space.BLOCKED;
                    }
                    return loaded.getBlock(target.x,target.y,target.z)==BlockType.EMPTY_ID||replaceable(block)?Space.OPEN:Space.BLOCKED;
                }
                @Override public boolean place(Vector3i target){
                    if(!alive()||!within(target,origin,across,root.width,root.height)||space(target)!=Space.OPEN)return false;
                    var sr=world.getChunkStore().getChunkSectionReference(ChunkUtil.chunkCoordinate(target.x),ChunkUtil.indexSection(target.y),ChunkUtil.chunkCoordinate(target.z));
                    if(!BlockOperations.setBlock(world.getChunkStore(),sr,target.x,target.y,target.z,index,asset,rotation,0,0))return false;
                    var leaf=BlockModule.getBlockEntity(world,target.x,target.y,target.z);if(leaf==null||!leaf.isValid())throw new IllegalStateException("Vine has no native block entity");
                    store.putComponent(leaf,type,root.child());var saved=store.getComponent(leaf,BlockModule.BlockStateInfo.getComponentType());if(saved!=null)saved.markNeedsSaving(store);return true;
                }
            };
            int repaired=0,examined=0;
            while(!work.repairs.isEmpty()&&examined<checks&&repaired<placements){
                var it=work.repairs.iterator();var hole=it.next();it.remove();examined++;
                Space space=grid.space(hole);if(space==Space.UNAVAILABLE){work.repairs.add(hole);break;}
                if(space==Space.OPEN&&grid.place(hole))repaired++;
            }
            if(work.pause>0||repaired==placements||examined==checks)return new Result(examined,repaired,false);
            var step=work.sweep.step(grid,checks-examined,placements-repaired);var result=new Result(step.checked()+examined,step.placed()+repaired,step.finished());
            if(result.finished()){work.sweep=null;work.pause=.5f;}
            return result;
        }
        private Result decay(Store<ChunkStore> store,Ref<ChunkStore> ref,PoisonousVine state,BlockModule.BlockStateInfo info,Vector3i at){
            var world=store.getExternalData().getWorld();var origin=state.origin();var column=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(origin.x,origin.z));
            RootPresence presence=DeadRoots.TYPE!=null&&store.getResource(DeadRoots.TYPE).lost(state)?RootPresence.LOST:RootPresence.UNAVAILABLE;
            var section=world.getChunkStore().getChunkSectionReference(ChunkUtil.chunkCoordinate(origin.x),ChunkUtil.indexSection(origin.y),ChunkUtil.chunkCoordinate(origin.z));
            if(presence!=RootPresence.LOST&&column!=null&&section!=null&&section.isValid()&&store.getComponent(section,BlockSection.getComponentType())!=null){
                var owner=BlockModule.getBlockEntity(world,origin.x,origin.y,origin.z);var root=owner!=null&&owner.isValid()?store.getComponent(owner,type):null;
                presence=presence(AxoItems.block(column.getBlockType(origin.x,origin.y,origin.z),AxoItems.POISONOUS_VINE),root,state);


                if(presence==RootPresence.LOST&&DeadRoots.TYPE!=null){var dead=store.getResource(DeadRoots.TYPE).clone();dead.remember(state);store.replaceResource(DeadRoots.TYPE,dead);}
            }
            var next=state.clone();boolean remove=next.wither(presence,at,System.currentTimeMillis());
            if(next.witherAt!=state.witherAt){store.putComponent(ref,type,next);info.markNeedsSaving(store);}
            if(!remove)return new Result(1,0,false);
            var loaded=WorldBlocks.loaded(world,ChunkUtil.indexChunkFromBlock(at.x,at.z));
            var sr=world.getChunkStore().getChunkSectionReference(ChunkUtil.chunkCoordinate(at.x),ChunkUtil.indexSection(at.y),ChunkUtil.chunkCoordinate(at.z));
            if(loaded==null||sr==null||!sr.isValid())return new Result(1,0,false);
            if(!AxoItems.block(loaded.getBlockType(at.x,at.y,at.z),AxoItems.POISONOUS_VINE))return new Result(-1,0,true);

            return BlockOperations.setBlock(world.getChunkStore(),sr,at.x,at.y,at.z,BlockType.EMPTY_ID,BlockType.EMPTY,0,0,0)?new Result(-1,1,true):new Result(1,0,false);
        }
    }
}
