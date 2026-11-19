package org.example.plugin;


enum SoulKind {
    VILLA("Villa"), FOSSOR("Fossor"), CRUOR("Cruor");
    final String name, item, role;
    SoulKind(String name) { this.name=name; item="Axo_"+name+"_Soul"; role=name+"_Soul"; }
    static SoulKind item(String id) { for(var k:values())if(k.item.equals(id))return k;return null; }
    static SoulKind role(String id) { for(var k:values())if(k.role.equals(id))return k;return null; }
}
