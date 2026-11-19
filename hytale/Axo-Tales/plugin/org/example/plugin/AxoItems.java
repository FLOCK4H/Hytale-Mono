package org.example.plugin;

import java.util.*;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;


final class AxoItems {
    static final String ANCIENT_BOOTS="Axo_Ancient_Boots";
    static final String ANCIENT_CHESTPLATE="Axo_Ancient_Chestplate";
    static final String ANCIENT_GAUNTLETS="Axo_Ancient_Gauntlets";
    static final String ANCIENT_HELM="Axo_Ancient_Helm";
    static final String ARCANE_VEIL_HOOD="Axo_Arcane_Veil_Hood";
    static final String ARCANE_VEIL_LEGGINGS="Axo_Arcane_Veil_Leggings";
    static final String ARCANE_VEIL_ROBE="Axo_Arcane_Veil_Robe";
    static final String INVISIBILITY_CLOAK="Axo_Invisibility_Cloak";
    static final String SAR_ARMOR="Axo_Sar_Armor";
    static final String SAR_DIADEM="Axo_Sar_Diadem";
    static final String SAR_WARFISTS="Axo_Sar_Warfists";
    static final String SAR_BOOTS="Axo_Sar_Boots";
    static final String AEQUALIS_ORE_DEPOSIT="Axo_Aequalis_Ore_Deposit";
    static final String AEQUALIS_ORE_DEPOSIT_CRACKED="Axo_Aequalis_Ore_Deposit_Cracked";
    static final String AGRETIS_ORE_DEPOSIT="Axo_Agretis_Ore_Deposit";
    static final String AGRETIS_ORE_DEPOSIT_CRACKED="Axo_Agretis_Ore_Deposit_Cracked";
    static final String ANCIENT_ARROW="Axo_Ancient_Arrow";
    static final String ARCANE_CRYSTAL_CROP="Axo_Arcane_Crystal_Crop";
    static final String ARCANE_CRYSTAL_SEED="Axo_Arcane_Crystal_Seed";
    static final String ARCANE_ORACULUM="Axo_Arcane_Oraculum";
    static final String ARCANE_PICKAXE="Axo_Arcane_Pickaxe";
    static final String ARCANE_WAND="Axo_Arcane_Wand";
    static final String ARCANE_WAND_BLOODY="Axo_Arcane_Wand_Bloody";
    static final String ARCANE_WAND_DOOM="Axo_Arcane_Wand_Doom";
    static final String ARCANE_WAND_FLAME="Axo_Arcane_Wand_Flame";
    static final String ARCANE_WAND_FROST="Axo_Arcane_Wand_Frost";
    static final String ARCANE_WAND_HEAL="Axo_Arcane_Wand_Heal";
    static final String ARCANE_WAND_HORDE="Axo_Arcane_Wand_Horde";
    static final String ARCANE_WAND_IMMUNITY="Axo_Arcane_Wand_Immunity";
    static final String ARCANE_WAND_LEECH="Axo_Arcane_Wand_Leech";
    static final String ARCANE_WAND_LIFE="Axo_Arcane_Wand_Life";
    static final String ARCANE_WAND_LIGHT="Axo_Arcane_Wand_Light";
    static final String ARCANE_WAND_LIGHTNING="Axo_Arcane_Wand_Lightning";
    static final String ARCANE_WAND_METEORAIN="Axo_Arcane_Wand_Meteorain";
    static final String ARCANE_WAND_MINING="Axo_Arcane_Wand_Mining";
    static final String ARCANE_WAND_MORPH="Axo_Arcane_Wand_Morph";
    static final String ARCANE_WAND_TAUNT="Axo_Arcane_Wand_Taunt";
    static final String ARCANE_WAND_TELEPORT="Axo_Arcane_Wand_Teleport";
    static final String ARCANE_WAND_TEMPLATE="Axo_Arcane_Wand_Template";
    static final String ASTRAL_SATCHEL="Axo_Astral_Satchel";
    static final String AEQUALIS_BAR="Axo_Aequalis_Bar";
    static final String AGRETIS_BAR="Axo_Agretis_Bar";
    static final String REVEL_BAR="Axo_Revel_Bar";
    static final String DREADMAW_BLOOD_SAMPLE="Axo_Dreadmaw_Blood_Sample";
    static final String BLOOD_DROPLET="Axo_Blood_Droplet";
    static final String EMBERWULF_BLOOD_SAMPLE="Axo_Emberwulf_Blood_Sample";
    static final String BLOOD_FURNACE="Axo_Blood_Furnace";
    static final String REX_BLOOD_SAMPLE="Axo_Rex_Blood_Sample";
    static final String YETI_BLOOD_SAMPLE="Axo_Yeti_Blood_Sample";
    static final String CRUOR_SOUL="Axo_Cruor_Soul";
    static final String DNA_SAMPLE="Axo_DNA_Sample";
    static final String EVIL_CORE="Axo_Evil_Core";
    static final String FOSSOR_SOUL="Axo_Fossor_Soul";
    static final String AEQUALIS_ORE="Axo_Aequalis_Ore";
    static final String AGRETIS_ORE="Axo_Agretis_Ore";
    static final String REVEL_ORE="Axo_Revel_Ore";
    static final String REVEL_ORE_DEPOSIT="Axo_Revel_Ore_Deposit";
    static final String REVEL_ORE_DEPOSIT_CRACKED="Axo_Revel_Ore_Deposit_Cracked";
    static final String RIMEFANG="Axo_Rimefang";
    static final String ALFA_RUNE="Axo_Alfa_Rune";
    static final String BETA_RUNE="Axo_Beta_Rune";
    static final String CHI_RUNE="Axo_Chi_Rune";
    static final String DELTA_RUNE="Axo_Delta_Rune";
    static final String EPSILON_RUNE="Axo_Epsilon_Rune";
    static final String ETA_RUNE="Axo_Eta_Rune";
    static final String GAMMA_RUNE="Axo_Gamma_Rune";
    static final String IOTA_RUNE="Axo_Iota_Rune";
    static final String KAPPA_RUNE="Axo_Kappa_Rune";
    static final String LAMBDA_RUNE="Axo_Lambda_Rune";
    static final String MU_RUNE="Axo_Mu_Rune";
    static final String NU_RUNE="Axo_Nu_Rune";
    static final String OMEGA_RUNE="Axo_Omega_Rune";
    static final String OMICRON_RUNE="Axo_Omicron_Rune";
    static final String PHI_RUNE="Axo_Phi_Rune";
    static final String PI_RUNE="Axo_Pi_Rune";
    static final String PSI_RUNE="Axo_Psi_Rune";
    static final String RHO_RUNE="Axo_Rho_Rune";
    static final String SIGMA_RUNE="Axo_Sigma_Rune";
    static final String TAU_RUNE="Axo_Tau_Rune";
    static final String THETA_RUNE="Axo_Theta_Rune";
    static final String UPSILON_RUNE="Axo_Upsilon_Rune";
    static final String XI_RUNE="Axo_Xi_Rune";
    static final String ZETA_RUNE="Axo_Zeta_Rune";
    static final String SKILL_TREE="Axo_Skill_Tree";
    static final String SKILL_TREE_SEED="Axo_Skill_Tree_Seed";
    static final String SPELLBOOK_POUCH="Axo_Spellbook_Pouch";
    static final String SPOILED_HEART="Axo_Spoiled_Heart";
    static final String SPYGLASS="Axo_Spyglass";
    static final String BLOODFANG_DAGGERS="Axo_Bloodfang_Daggers";
    static final String VILLA_SOUL="Axo_Villa_Soul";
    static final String VITAE_EXTRACTOR="Axo_Vitae_Extractor";
    static final String WHISPERION_SADDLE="Axo_Whisperion_Saddle";
    static final String BLOODY_BOOK="Axo_Bloody_Book";
    static final String BOOK_OF_DOOM="Axo_Book_Of_Doom";
    static final String FLAME_BOOK="Axo_Flame_Book";
    static final String FROST_BOOK="Axo_Frost_Book";
    static final String HEALING_BOOK="Axo_Healing_Book";
    static final String HORDE_BOOK="Axo_Horde_Book";
    static final String IMMUNITY_BOOK="Axo_Immunity_Book";
    static final String LEECH_BOOK="Axo_Leech_Book";
    static final String BOOK_OF_LIFE="Axo_Book_Of_Life";
    static final String LIGHT_BOOK="Axo_Light_Book";
    static final String LIGHTNING_BOOK="Axo_Lightning_Book";
    static final String METEORAIN_BOOK="Axo_Meteorain_Book";
    static final String MINING_BOOK="Axo_Mining_Book";
    static final String MORPH_BOOK="Axo_Morph_Book";
    static final String SILVER_BOOK="Axo_Silver_Book";
    static final String TAUNT_BOOK="Axo_Taunt_Book";
    static final String TELEPORT_BOOK="Axo_Teleport_Book";
    static final String CURSE_POTION="Axo_Curse_Potion";
    static final String EMPTY_POTION_BOTTLE="Axo_Empty_Potion_Bottle";
    static final String INVISIBILITY_POTION="Axo_Invisibility_Potion";
    static final String RABBIT_POTION="Axo_Rabbit_Potion";
    static final String STRENGTH_POTION="Axo_Strength_Potion";
    static final String SWIFT_POTION="Axo_Swift_Potion";
    static final String EVERFLAME_SHORTBOW="Axo_Everflame_Shortbow";
    static final String MIRETHORN_SHORTBOW="Axo_Mirethorn_Shortbow";
    static final String BOUNCE_BLOCK="Axo_Bounce_Block";
    static final String CLOUD_BLOCK="Axo_Cloud_Block";
    static final String CUSTOM_PLACEHOLDER_BLOCK="Axo_Custom_Placeholder_Block";
    static final String ARCANE_GRASS="Axo_Arcane_Grass";
    static final String ARCANE_MATTER="Axo_Arcane_Matter";
    static final String ARCANE_CRYSTAL_SHARDS="Axo_Arcane_Crystal_Shards";
    static final String ARCANE_MATTER_ORE_VOLCANIC="Axo_Arcane_Matter_Ore_Volcanic";
    static final String ARCANE_MATTER_ORE_STONE="Axo_Arcane_Matter_Ore_Stone";
    static final String ARCANE_CRYSTAL="Axo_Arcane_Crystal";
    static final String ARCANE_SWORD="Axo_Arcane_Sword";
    static final String ANCIENT_SWORD="Axo_Ancient_Sword";
    static final String ARCANE_WORKBENCH="Axo_Arcane_Workbench";
    static final String POISONOUS_VINE_SEED="Axo_Poisonous_Vine_Seed";
    static final String POISONOUS_VINE="Axo_Poisonous_Vine";
    static final Map<String,String> LEGACY=Map.ofEntries(
        Map.entry("Axo_Arcane_Workbench","Axo_Arcane_Workbench"),
        Map.entry("Axo_Poisonous_Vine_Seed","Axo_Poisonous_Vine_Seed"),
        Map.entry("Axo_Poisonous_Vine","Axo_Poisonous_Vine"),
        Map.entry("AxoTales_Ancient_Boots","Axo_Ancient_Boots"),
        Map.entry("AxoTales_Ancient_Chestplate","Axo_Ancient_Chestplate"),
        Map.entry("AxoTales_Ancient_Gauntlets","Axo_Ancient_Gauntlets"),
        Map.entry("AxoTales_Ancient_Helm","Axo_Ancient_Helm"),
        Map.entry("AxoTales_Arcane_Hood","Axo_Arcane_Veil_Hood"),
        Map.entry("AxoTales_Arcane_Leggings","Axo_Arcane_Veil_Leggings"),
        Map.entry("AxoTales_Arcane_Robe","Axo_Arcane_Veil_Robe"),
        Map.entry("Invisibility_Cloak","Axo_Invisibility_Cloak"),
        Map.entry("Sar_Chest","Axo_Sar_Armor"),
        Map.entry("Sar_Diadem","Axo_Sar_Diadem"),
        Map.entry("Sar_Warfists","Axo_Sar_Warfists"),
        Map.entry("Sars_Legs","Axo_Sar_Boots"),
        Map.entry("AxoTales_Aequalis_Ore","Axo_Aequalis_Ore_Deposit"),
        Map.entry("AxoTales_Aequalis_Ore_Cracked","Axo_Aequalis_Ore_Deposit_Cracked"),
        Map.entry("AxoTales_Agretis_Ore","Axo_Agretis_Ore_Deposit"),
        Map.entry("AxoTales_Agretis_Ore_Cracked","Axo_Agretis_Ore_Deposit_Cracked"),
        Map.entry("AxoTales_Ancient_Arrow","Axo_Ancient_Arrow"),
        Map.entry("AxoTales_Arcane_Crystal_Crop","Axo_Arcane_Crystal_Crop"),
        Map.entry("AxoTales_Arcane_Crystal_Seed","Axo_Arcane_Crystal_Seed"),
        Map.entry("AxoTales_Arcane_Oraculum","Axo_Arcane_Oraculum"),
        Map.entry("AxoTales_Arcane_Pickaxe","Axo_Arcane_Pickaxe"),
        Map.entry("AxoTales_Arcane_Wand","Axo_Arcane_Wand"),
        Map.entry("AxoTales_Arcane_Wand_Bloody","Axo_Arcane_Wand_Bloody"),
        Map.entry("AxoTales_Arcane_Wand_Doom","Axo_Arcane_Wand_Doom"),
        Map.entry("AxoTales_Arcane_Wand_Flame","Axo_Arcane_Wand_Flame"),
        Map.entry("AxoTales_Arcane_Wand_Frost","Axo_Arcane_Wand_Frost"),
        Map.entry("AxoTales_Arcane_Wand_Heal","Axo_Arcane_Wand_Heal"),
        Map.entry("AxoTales_Arcane_Wand_Horde","Axo_Arcane_Wand_Horde"),
        Map.entry("AxoTales_Arcane_Wand_Immunity","Axo_Arcane_Wand_Immunity"),
        Map.entry("AxoTales_Arcane_Wand_Leech","Axo_Arcane_Wand_Leech"),
        Map.entry("AxoTales_Arcane_Wand_Life","Axo_Arcane_Wand_Life"),
        Map.entry("AxoTales_Arcane_Wand_Light","Axo_Arcane_Wand_Light"),
        Map.entry("AxoTales_Arcane_Wand_Lightning","Axo_Arcane_Wand_Lightning"),
        Map.entry("AxoTales_Arcane_Wand_Meteorain","Axo_Arcane_Wand_Meteorain"),
        Map.entry("AxoTales_Arcane_Wand_Mining","Axo_Arcane_Wand_Mining"),
        Map.entry("AxoTales_Arcane_Wand_Morph","Axo_Arcane_Wand_Morph"),
        Map.entry("AxoTales_Arcane_Wand_Taunt","Axo_Arcane_Wand_Taunt"),
        Map.entry("AxoTales_Arcane_Wand_Teleport","Axo_Arcane_Wand_Teleport"),
        Map.entry("AxoTales_Arcane_Wand_Template","Axo_Arcane_Wand_Template"),
        Map.entry("AxoTales_Astral_Satchel","Axo_Astral_Satchel"),
        Map.entry("AxoTales_Bar_Aequalis","Axo_Aequalis_Bar"),
        Map.entry("AxoTales_Bar_Agretis","Axo_Agretis_Bar"),
        Map.entry("AxoTales_Bar_Revel","Axo_Revel_Bar"),
        Map.entry("AxoTales_Blood_Dreadmaw","Axo_Dreadmaw_Blood_Sample"),
        Map.entry("AxoTales_Blood_Droplet","Axo_Blood_Droplet"),
        Map.entry("AxoTales_Blood_Emberwulf","Axo_Emberwulf_Blood_Sample"),
        Map.entry("AxoTales_Blood_Furnace","Axo_Blood_Furnace"),
        Map.entry("AxoTales_Blood_Rex","Axo_Rex_Blood_Sample"),
        Map.entry("AxoTales_Blood_Yeti","Axo_Yeti_Blood_Sample"),
        Map.entry("AxoTales_Cruor_Soul","Axo_Cruor_Soul"),
        Map.entry("AxoTales_DNA_Sample","Axo_DNA_Sample"),
        Map.entry("AxoTales_Evil_Core","Axo_Evil_Core"),
        Map.entry("AxoTales_Fossor_Soul","Axo_Fossor_Soul"),
        Map.entry("AxoTales_Ore_Aequalis","Axo_Aequalis_Ore"),
        Map.entry("AxoTales_Ore_Agretis","Axo_Agretis_Ore"),
        Map.entry("AxoTales_Ore_Revel","Axo_Revel_Ore"),
        Map.entry("AxoTales_Revel_Ore","Axo_Revel_Ore_Deposit"),
        Map.entry("AxoTales_Revel_Ore_Cracked","Axo_Revel_Ore_Deposit_Cracked"),
        Map.entry("AxoTales_Rimefang","Axo_Rimefang"),
        Map.entry("AxoTales_Rune_Alfa","Axo_Alfa_Rune"),
        Map.entry("AxoTales_Rune_Beta","Axo_Beta_Rune"),
        Map.entry("AxoTales_Rune_Chi","Axo_Chi_Rune"),
        Map.entry("AxoTales_Rune_Delta","Axo_Delta_Rune"),
        Map.entry("AxoTales_Rune_Epsilon","Axo_Epsilon_Rune"),
        Map.entry("AxoTales_Rune_Eta","Axo_Eta_Rune"),
        Map.entry("AxoTales_Rune_Gamma","Axo_Gamma_Rune"),
        Map.entry("AxoTales_Rune_Iota","Axo_Iota_Rune"),
        Map.entry("AxoTales_Rune_Kappa","Axo_Kappa_Rune"),
        Map.entry("AxoTales_Rune_Lambda","Axo_Lambda_Rune"),
        Map.entry("AxoTales_Rune_Mu","Axo_Mu_Rune"),
        Map.entry("AxoTales_Rune_Nu","Axo_Nu_Rune"),
        Map.entry("AxoTales_Rune_Omega","Axo_Omega_Rune"),
        Map.entry("AxoTales_Rune_Omicron","Axo_Omicron_Rune"),
        Map.entry("AxoTales_Rune_Phi","Axo_Phi_Rune"),
        Map.entry("AxoTales_Rune_Pi","Axo_Pi_Rune"),
        Map.entry("AxoTales_Rune_Psi","Axo_Psi_Rune"),
        Map.entry("AxoTales_Rune_Rho","Axo_Rho_Rune"),
        Map.entry("AxoTales_Rune_Sigma","Axo_Sigma_Rune"),
        Map.entry("AxoTales_Rune_Tau","Axo_Tau_Rune"),
        Map.entry("AxoTales_Rune_Theta","Axo_Theta_Rune"),
        Map.entry("AxoTales_Rune_Upsilon","Axo_Upsilon_Rune"),
        Map.entry("AxoTales_Rune_Xi","Axo_Xi_Rune"),
        Map.entry("AxoTales_Rune_Zeta","Axo_Zeta_Rune"),
        Map.entry("AxoTales_Skill_Tree","Axo_Skill_Tree"),
        Map.entry("AxoTales_Skill_Tree_Seed","Axo_Skill_Tree_Seed"),
        Map.entry("AxoTales_Spellbook_Pouch","Axo_Spellbook_Pouch"),
        Map.entry("AxoTales_Spoiled_Heart","Axo_Spoiled_Heart"),
        Map.entry("AxoTales_Spyglass","Axo_Spyglass"),
        Map.entry("AxoTales_Starfang_Daggers","Axo_Bloodfang_Daggers"),
        Map.entry("AxoTales_Villa_Soul","Axo_Villa_Soul"),
        Map.entry("AxoTales_Vitae_Extractor","Axo_Vitae_Extractor"),
        Map.entry("AxoTales_Whisperion_Saddle","Axo_Whisperion_Saddle"),
        Map.entry("Book_Bloody_Texture","Axo_Bloody_Book"),
        Map.entry("Book_Doom_Texture","Axo_Book_Of_Doom"),
        Map.entry("Book_Flame_Texture","Axo_Flame_Book"),
        Map.entry("Book_Frost_Texture","Axo_Frost_Book"),
        Map.entry("Book_Heal_Texture","Axo_Healing_Book"),
        Map.entry("Book_Horde_Texture","Axo_Horde_Book"),
        Map.entry("Book_Immunity_Texture","Axo_Immunity_Book"),
        Map.entry("Book_Leech_Texture","Axo_Leech_Book"),
        Map.entry("Book_Life_Texture","Axo_Book_Of_Life"),
        Map.entry("Book_Light_Texture","Axo_Light_Book"),
        Map.entry("Book_Lightning_Texture","Axo_Lightning_Book"),
        Map.entry("Book_Meteorain_Texture","Axo_Meteorain_Book"),
        Map.entry("Book_Mining_Texture","Axo_Mining_Book"),
        Map.entry("Book_Morph_Texture","Axo_Morph_Book"),
        Map.entry("Book_Silver_Texture","Axo_Silver_Book"),
        Map.entry("Book_Taunt_Texture","Axo_Taunt_Book"),
        Map.entry("Book_Teleport_Texture","Axo_Teleport_Book"),
        Map.entry("Curse_Potion","Axo_Curse_Potion"),
        Map.entry("Empty_Potion_Bottle","Axo_Empty_Potion_Bottle"),
        Map.entry("Invisibility_Potion","Axo_Invisibility_Potion"),
        Map.entry("Rabbit_Potion","Axo_Rabbit_Potion"),
        Map.entry("Strength_Potion","Axo_Strength_Potion"),
        Map.entry("Swift_Potion","Axo_Swift_Potion"),
        Map.entry("Weapon_Shortbow_AxoTales_Everflame","Axo_Everflame_Shortbow"),
        Map.entry("Weapon_Shortbow_AxoTales_Mirethorn","Axo_Mirethorn_Shortbow"),
        Map.entry("AxoTales_Bounce_Block","Axo_Bounce_Block"),
        Map.entry("AxoTales_Cloud_Block","Axo_Cloud_Block"),
        Map.entry("Block_CustomPlaceholder","Axo_Custom_Placeholder_Block"),
        Map.entry("Soil_Grass_Arcane","Axo_Arcane_Grass"),
        Map.entry("Arcane_Matter","Axo_Arcane_Matter"),
        Map.entry("Ingredient_Crystal_Arcane","Axo_Arcane_Crystal_Shards"),
        Map.entry("Arcane_Matter_Volcanic","Axo_Arcane_Matter_Ore_Volcanic"),
        Map.entry("Ore_Stone_Parent","Axo_Arcane_Matter_Ore_Stone"),
        Map.entry("Rock_Crystal_Arcane_Large","Axo_Arcane_Crystal"),
        Map.entry("Axo_Ancient_Sword","Axo_Arcane_Sword"),
        Map.entry("AxoTales_Ancient_Sword","Axo_Ancient_Sword"));
    private static final Map<String,String> MODIFIER_IDS;
    static {var reverse=new HashMap<String,String>();LEGACY.forEach((old,id)->reverse.put(id,old));MODIFIER_IDS=Map.copyOf(reverse);}
    private AxoItems(){}
    static String canonical(String id){return id==null?null:ANCIENT_SWORD.equals(id)?id:LEGACY.getOrDefault(id,id);}
    static String modifierId(String canonical){return MODIFIER_IDS.getOrDefault(canonical,canonical);}
    static String blockId(BlockType type){return type==null?null:canonical(type.getDefaultStateKey()==null?type.getId():type.getDefaultStateKey());}
    static boolean block(BlockType type,String id){return type!=null&&id.equals(blockId(type));}
    static String rune(String name){return "Axo_"+name+"_Rune";}
    static boolean rune(BlockType type){if(type==null)return false;String id=blockId(type);return LEGACY.entrySet().stream().anyMatch(e->e.getKey().startsWith("AxoTales_Rune_")&&e.getValue().equals(id));}
}
