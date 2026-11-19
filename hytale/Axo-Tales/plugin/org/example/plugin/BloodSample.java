package org.example.plugin;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.UUID;


public final class BloodSample {
    static final String META="AxoTalesBloodline", BLOOD="Axo_Blood_Droplet", DNA="Axo_DNA_Sample";
    public static final BuilderCodec<BloodSample> CODEC=BuilderCodec.builder(BloodSample.class,BloodSample::new)
        .append(new KeyedCodec<>("Version",Codec.INTEGER),(v,x)->v.version=x,v->v.version).add()
        .append(new KeyedCodec<>("Source",Codec.UUID_STRING),(v,x)->v.source=x,v->v.source).add()
        .append(new KeyedCodec<>("Species",Codec.STRING),(v,x)->v.species=x,v->v.species).add()
        .append(new KeyedCodec<>("Name",Codec.STRING),(v,x)->v.name=x,v->v.name).add()
        .append(new KeyedCodec<>("Model",Model.ModelReference.CODEC),(v,x)->v.model=x,v->v.model).add()
        .append(new KeyedCodec<>("Motion",Codec.STRING),(v,x)->v.motion=x,v->v.motion).add()
        .append(new KeyedCodec<>("Health",Codec.FLOAT),(v,x)->v.health=x,v->v.health).add().build();
    int version=1;
    UUID source;
    String species,name,motion="Walk";
    Model.ModelReference model;
    float health=100;
    boolean valid(){return version==1 && source!=null && species!=null && !species.isBlank() && model!=null
        && model.getModelAssetId()!=null && Float.isFinite(model.getScale()) && model.getScale()>0
        && Float.isFinite(health) && health>0 && java.util.Set.of("Walk","Fly","Dive").contains(motion);}
    static BloodSample read(ItemStack item,String id) {
        if(ItemStack.isEmpty(item) || !(BLOOD.equals(id)?LeechBlood.blood(item.getItemId()):id.equals(item.getItemId())))return null;
        try{var v=item.getFromMetadataOrNull(META,CODEC);return v!=null && v.valid() && (!BLOOD.equals(id)||LeechBlood.matches(item.getItemId(),v.species))?v:null;}
        catch(RuntimeException invalid){return null;}
    }
    ItemStack item(String id) {
        if(!valid())throw new IllegalStateException("Incomplete bloodline");
        String label=name==null?species.replace('_',' '):name;
        String title=(BLOOD.equals(id)?"Blood Droplet":"DNA Sample")+" — "+label;
        var description=Message.raw("Species: "+label+"\n"+(BLOOD.equals(id)
            ?"Place in a Vitae Extractor with one Essence of Life."
            :"Consumed by the Book of Life to summon one loyal bloodbound companion."));
        String actual=BLOOD.equals(id)?LeechBlood.itemFor(species):id;
        return new ItemStack(actual,1).withMetadata(META,CODEC,this)
            .withMetadata(ItemDisplayMetadata.KEYED_CODEC,new ItemDisplayMetadata(Message.raw(title),description));
    }
}
