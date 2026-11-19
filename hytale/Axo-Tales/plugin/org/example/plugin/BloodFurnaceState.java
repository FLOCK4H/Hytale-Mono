package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.*;


final class BloodFurnaceState implements Component<ChunkStore> {
    static final String ITEM="Axo_Blood_Furnace";
    static final double BLOOD_SECONDS=300;
    static final BuilderCodec<BloodFurnaceState> CODEC=BuilderCodec.builder(BloodFurnaceState.class,BloodFurnaceState::new)
        .append(new KeyedCodec<>("Fuel",Codec.DOUBLE),(s,v)->s.fuel=finite(v),s->s.fuel).add()
        .append(new KeyedCodec<>("Progress",Codec.DOUBLE),(s,v)->s.progress=finite(v),s->s.progress).add()
        .append(new KeyedCodec<>("Recipe",Codec.STRING),(s,v)->s.recipe=v,s->s.recipe).add()
        .append(new KeyedCodec<>("Enabled",Codec.BOOLEAN),(s,v)->s.enabled=v,s->s.enabled).add().build();
    double fuel,progress;
    String recipe="";
    boolean enabled=true;
    transient boolean burning;
    transient String visualKey;
    transient String status="Add blood and ingredients";
    static int fuelLevel(double value){return !Double.isFinite(value)||value<=0?0:(int)Math.ceil(Math.min(1,value/BLOOD_SECONDS)*32);}
    String visualState(){return (burning?"Processing":"Fuel")+fuelLevel(fuel);}
    private static double finite(double value){return Double.isFinite(value)?Math.max(0,value):0;}
    @Override public Component<ChunkStore> clone(){var copy=new BloodFurnaceState();copy.fuel=fuel;copy.progress=progress;copy.recipe=recipe;copy.enabled=enabled;return copy;}
    static SimpleItemContainer slice(ItemContainer items,int start){var copy=new SimpleItemContainer((short)4);for(short i=0;i<4;i++)copy.setItemStackForSlot(i,items.getItemStack((short)(start+i)),false);return copy;}

    boolean advance(ItemContainer items,List<CraftingRecipe> recipes,double delta){
        burning=false;if(!Double.isFinite(delta)||delta<=0)return false;
        if(!enabled){status="Paused";return false;}
        var inputs=slice(items,1);CraftingRecipe selected=null;
        for(var candidate:recipes)if(inputs.canRemoveMaterials(CraftingManager.getInputMaterials(candidate))){selected=candidate;break;}
        if(selected==null){boolean changed=progress!=0||!recipe.isEmpty();progress=0;recipe="";status="Add smelting or cooking ingredients";return changed;}
        if(!Objects.equals(recipe,selected.getId())){recipe=selected.getId();progress=0;}
        var outputs=slice(items,5);var result=CraftingManager.getOutputItemStacks(selected);
        if(!outputs.canAddItemStacks(result,false,false)){status="Output full · collect finished items";return false;}
        double dt=Math.min(.25,delta),nextFuel=fuel,nextProgress=progress;
        var before=new ArrayList<ItemStack>();for(short i=0;i<9;i++)before.add(items.getItemStack(i));
        ItemStack blood=before.getFirst();boolean consumeBlood=false;
        if(nextFuel<=.000001){
            if(ItemStack.isEmpty(blood)||!LeechBlood.blood(blood.getItemId())){status="Add a blood droplet";return false;}
            consumeBlood=true;nextFuel=BLOOD_SECONDS;
        }
        double elapsed=Math.min(dt,nextFuel);nextFuel-=elapsed;nextProgress+=elapsed*2;
        double duration=Math.max(.001,selected.getTimeSeconds());boolean completes=false;
        while(nextProgress+.000001>=duration&&inputs.canRemoveMaterials(CraftingManager.getInputMaterials(selected))&&outputs.canAddItemStacks(result,false,false)){
            if(!inputs.removeMaterials(CraftingManager.getInputMaterials(selected),true,true,true).succeeded())return false;
            if(!outputs.addItemStacks(result,true,false,true).succeeded())return false;
            nextProgress=Math.max(0,nextProgress-duration);completes=true;
        }
        var after=new ArrayList<>(before);
        if(consumeBlood)after.set(0,blood.getQuantity()==1?null:blood.withQuantity(blood.getQuantity()-1));
        if(completes)for(short i=0;i<4;i++){after.set(i+1,inputs.getItemStack(i));after.set(i+5,outputs.getItemStack(i));}
        if(!commit(items,before,after)){status="Output blocked · items retained";return false;}
        fuel=nextFuel;progress=nextProgress;burning=true;status="";return true;
    }
    static boolean commit(ItemContainer items,List<ItemStack> before,List<ItemStack> after){
        for(short i=0;i<9;i++)if(!Objects.equals(before.get(i),items.getItemStack(i)))return false;
        try{
            for(short i=0;i<9;i++){
                if(Objects.equals(before.get(i),after.get(i)))continue;
                if(!items.setItemStackForSlot(i,after.get(i)).succeeded()){restore(items,before);return false;}
            }
            return true;
        }catch(RuntimeException failure){restore(items,before);return false;}
    }
    private static void restore(ItemContainer items,List<ItemStack> before){
        RuntimeException failure=null;
        for(short i=0;i<9;i++)try{
            if(!Objects.equals(before.get(i),items.getItemStack(i))&&!items.setItemStackForSlot(i,before.get(i),false).succeeded())throw new IllegalStateException("Blood Furnace native rollback rejected");
        }catch(RuntimeException rejected){if(failure==null)failure=rejected;else failure.addSuppressed(rejected);}
        if(failure!=null)throw failure;
    }
}
