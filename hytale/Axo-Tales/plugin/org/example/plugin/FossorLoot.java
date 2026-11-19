package org.example.plugin;

import java.util.List;


final class FossorLoot {
    record Entry(String item,double base,boolean boosted) {}
    static final List<Entry> TABLE=List.of(
        new Entry("Ore_Adamantite",.01,true),new Entry("Ore_Mithril",.01,true),
        new Entry("Ore_Cobalt",.04,true),new Entry("Ore_Thorium",.04,true),
        new Entry("Ore_Gold",.05,false),new Entry("Ore_Silver",.06,false),
        new Entry("Ore_Iron",.14,false),new Entry("Ore_Copper",.18,false),
        new Entry("Rock_Stone",.22,false),new Entry("Soil_Dirt",.15,false),
        new Entry("Axo_Arcane_Matter",.10,false));
    static double chance(Entry e,int feeds) {
        int power=Math.clamp(feeds,0,200);
        if("Axo_Arcane_Matter".equals(e.item))return .10;
        return e.boosted?e.base+power*.001:e.base*Math.max(0,.8-power*.004)/.8;
    }
    static String roll(int feeds,double random) {
        double cumulative=0;
        for(var e:TABLE){cumulative+=chance(e,feeds);if(random<cumulative)return e.item;}
        return "Axo_Arcane_Matter";
    }
    static String model(String item){return "AxoTales_Fossor_"+AxoItems.modifierId(item);}
    static boolean valid(String item){return TABLE.stream().anyMatch(e->e.item.equals(item));}
}
