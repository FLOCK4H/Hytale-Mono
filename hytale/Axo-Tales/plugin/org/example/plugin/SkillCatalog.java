package org.example.plugin;

import java.util.List;


final class SkillCatalog {
    record Node(int id, String name, int cost, int parent, String description, int x, int y) {}
    static final List<Node> NODES = List.of(
        new Node(0,"First steps",0,-1,"+2 mana every 5 seconds",407,510),
        new Node(1,"Vis",5,0,"+2.5% attack damage",87,420),
        new Node(2,"Cutis",5,0,"+2.5% defense",267,420),
        new Node(3,"Celer",5,0,"+2.5% movement speed",447,420),
        new Node(4,"Vita",5,0,"+2.5% maximum health",627,420),
        new Node(5,"Vigor",5,0,"+5% maximum stamina\n+2 stamina every 5 seconds",757,420),
        new Node(6,"Acies",10,1,"+5% attack damage\n+1 block melee reach",87,310),
        new Node(7,"Aegis",10,2,"+5% defense\n+5% projectile resistance",267,310),
        new Node(8,"Saltus",10,3,"+5% movement speed\n+1 block jump height\n+25% fall damage resistance",447,310),
        new Node(9,"Salus",10,4,"+5% maximum health\n+1 health every 2 seconds",627,310),
        new Node(10,"Impetus",20,6,"+10% attack damage\n+1 block melee reach\nSlight melee knockback",87,200),
        new Node(11,"Antidotum",20,7,"+10% defense\n+10% projectile resistance\n+25% poison resistance",267,200),
        new Node(12,"Alatus",20,8,"+10% movement speed\n+1 extra jump",447,200),
        new Node(13,"Robur",20,9,"+10% maximum health\n+25 maximum health",627,200),
        new Node(14,"Ventus",25,10,"+20% attack damage\n+1 block melee reach\nAncient or Arcane Sword: primary attacks\nrelease a piercing air wave for 31 damage",87,80),
        new Node(15,"Umbra",25,11,"+20% defense\n+20% projectile resistance\nEmpty hotbar slot: enemies detect you\nonly within 2 blocks",267,80),
        new Node(16,"Lazarus",25,13,"+20% maximum health\n+3 health every 2 seconds\nSurvive a fatal hit; immune for 3 seconds\n5 minute cooldown",627,80)
    );
    static final int FEROX=17,EVANESCO=18;
    static final List<Node> SUPERSKILLS=List.of(
        new Node(FEROX,"Ferox",100,0,"Twice the natural creature population\nin every world you enter.\n+50% damage dealt\n+25% maximum health\nThe population bonus does not stack\nwith other players.",0,0),
        new Node(EVANESCO,"Evanesco",100,0,"Become fully invisible while\nboth hands are empty.\nCreatures cannot target you.\nHolding an item reveals you.",0,0),
        new Node(19,"Superskill 3",100,-1,"This superskill is not available yet.",0,0));
    static final List<Node> ALL=java.util.stream.Stream.concat(NODES.stream(),SUPERSKILLS.stream()).toList();
    static Node node(int id) { return id >= 0 && id < ALL.size() ? ALL.get(id) : null; }
    static boolean implemented(int id){return id>=0&&id<=EVANESCO;}
    static float superDamage(int mask){return has(mask,FEROX)?1.5f:1;}
    static float superHealth(int mask){return has(mask,FEROX)?1.25f:1;}
    static float attack(int mask) { return sum(mask,1,.025f,6,.05f,10,.10f,14,.20f); }
    static float defense(int mask) { return sum(mask,2,.025f,7,.05f,11,.10f,15,.20f); }
    static float speed(int mask) { return sum(mask,3,.025f,8,.05f,12,.10f,-1,0); }
    static float health(int mask) { return sum(mask,4,.025f,9,.05f,13,.10f,16,.20f); }
    static float projectiles(int mask) { return sum(mask,7,.05f,11,.10f,15,.20f,-1,0); }
    static int reach(int mask) { return (has(mask,6)?1:0)+(has(mask,10)?1:0)+(has(mask,14)?1:0); }
    static boolean has(int mask,int id) { return id>=0 && (mask & (1<<id)) != 0; }
    private static float sum(int mask,int a,float av,int b,float bv,int c,float cv,int d,float dv) {
        return (has(mask,a)?av:0)+(has(mask,b)?bv:0)+(has(mask,c)?cv:0)+(has(mask,d)?dv:0);
    }
}
