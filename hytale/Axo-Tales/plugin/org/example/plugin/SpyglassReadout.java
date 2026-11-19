package org.example.plugin;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;


final class SpyglassReadout {
    record Row(String label,String value){}
    record Snapshot(String name,List<Row> rows){}
    static EntityStatValue value(EntityStatMap stats,String id){
        if(stats==null)return null;int index=com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType.getAssetMap().getIndex(id);
        return index<0||index>=stats.size()?null:stats.get(index);
    }
    static String number(double value){return String.format(Locale.ROOT,Math.abs(value-Math.rint(value))<.05?"%.0f":"%.1f",value);}
    static String stat(EntityStatMap stats,String id,boolean percent){
        var value=value(stats,id);if(value==null||value.getMax()<=value.getMin())return null;
        return percent?number(Math.max(0,Math.min(100,100*(value.get()-value.getMin())/(value.getMax()-value.getMin()))))+"%":number(value.get())+" / "+number(value.getMax());
    }
    static Snapshot capture(ComponentAccessor<EntityStore> store,Ref<EntityStore> viewer,Ref<EntityStore> target,List<String> fields){
        if(target==null||!target.isValid())return new Snapshot("No target",List.of(new Row("FOCUS","Aim at an entity")));
        var npc=store.getComponent(target,NPCEntity.getComponentType());var player=store.getComponent(target,PlayerRef.getComponentType());
        String role=npc==null?null:npc.getRoleName();String name=player==null?(role==null?"Entity":role.replace('_',' ')):player.getUsername();
        var observer=store.getComponent(viewer,PlayerRef.getComponentType());String locale=observer==null?"en-US":observer.getLanguage();var i18n=I18nModule.get();
        if(role!=null){var localized=i18n==null?null:i18n.getMessage(locale,"server.npcRoles."+role+".name");if(localized!=null&&!localized.isBlank())name=localized;}
        var plate=store.getComponent(target,Nameplate.getComponentType());if(plate!=null&&plate.getText()!=null&&!plate.getText().isBlank())name=plate.getText();
        var display=store.getComponent(target,DisplayNameComponent.getComponentType());
        if(display!=null&&display.getDisplayName()!=null){var message=display.getDisplayName();String text=message.getRawText();if((text==null||text.isBlank())&&message.getMessageId()!=null&&i18n!=null)text=i18n.getMessage(locale,message.getMessageId());if(text!=null&&!text.isBlank())name=text;}
        var stats=store.getComponent(target,EntityStatMap.getComponentType());var transform=store.getComponent(target,TransformComponent.getComponentType());var origin=store.getComponent(viewer,TransformComponent.getComponentType());
        var rows=new ArrayList<Row>();
        for(String field:fields){String value=null,label=field.toUpperCase(Locale.ROOT);
            switch(field){
                case "health"->{label="HEALTH";value=stat(stats,"Health",false);}
                case "health_percent"->{label="HEALTH";value=stat(stats,"Health",true);}
                case "mana"->{label="MANA";value=stat(stats,"Mana",false);}
                case "mana_percent"->{label="MANA";value=stat(stats,"Mana",true);}
                case "stamina"->value=stat(stats,"Stamina",false);
                case "oxygen"->value=stat(stats,"Oxygen",false);
                case "signature_energy"->{label="SIGNATURE";value=stat(stats,"SignatureEnergy",false);}
                case "ammo"->value=stat(stats,"Ammo",false);
                case "role"->{label="TYPE";value=role==null?(player==null?"Entity":"Player"):role;}
                case "distance"->{label="RANGE";if(transform!=null&&origin!=null)value=number(transform.getPosition().distance(origin.getPosition()))+" blocks";}
                case "position"->{label="POSITION";if(transform!=null){var p=transform.getPosition();value=number(p.x)+", "+number(p.y)+", "+number(p.z);}}
                case "world"->value=store.getExternalData().getWorld().getName();
                case "uuid"->{var id=store.getComponent(target,UUIDComponent.getComponentType());value=id==null?null:id.getUuid().toString();}
                default->{if(field.startsWith("stat:")){label=field.substring(5);value=stat(stats,label,false);}}
            }
            if(value!=null)rows.add(new Row(label,value));
        }
        return new Snapshot(name,List.copyOf(rows));
    }
}
