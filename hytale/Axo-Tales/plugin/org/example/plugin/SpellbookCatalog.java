package org.example.plugin;

import java.util.Arrays;


public enum SpellbookCatalog {
    SILVER("Silver", "Silver Book", "Gray bolt · no mana", "No alternate action"),
    HEAL("Heal", "Healing Book", "Heal yourself", "LMB: healing bolt"),
    TELEPORT("Teleport", "Teleport Book", "Blink to the targeted block", "No alternate action"),
    MINING("Mining", "Mining Book", "Hold RMB: charge a tunnel", "LMB: cycle tunnel shape"),
    IMMUNITY("Immunity", "Immunity Book", "Brief damage immunity", "No alternate action"),
    LIGHT("Light", "Light Book", "Launch a moving lantern", "No alternate action"),
    FROST("Frost", "Frost Book", "Frost shard and terrain control", "No alternate action"),
    FLAME("Flame", "Flame Book", "Fire bolt and burning", "No alternate action"),
    HORDE("Horde", "Horde Book", "Summon allies", "No alternate action"),
    MORPH("Morph", "Morph Book", "Copy a target's form", "LMB: restore your form"),
    TAUNT("Taunt", "Taunt Book", "Leap and slam", "No alternate action"),
    DOOM("Doom", "Book of Doom", "Explosive doom projectile", "No alternate action"),
    BLOODY("Bloody", "Bloody Book", "Extract blood: aim within 8 blocks for 8 seconds", "No alternate action"),
    LIFE("Life", "Book of Life", "Consume all carried DNA to awaken loyal companions", "Uses all current mana"),
    LIGHTNING("Lightning", "Axo's Lightning Book", "40-block lightning storm: 50 damage each second for 8 seconds", "80 mana; 24-second cooldown"),
    LEECH("Leech", "Leech Book", "Hold RMB: drain 30 damage and heal 20 HP each second", "10 mana per second; 16-block range"),
    METEORAIN("Meteorain", "Axo's Meteorain Book", "40-block meteor rain: 50 damage each second for 8 seconds", "80 mana; 24-second cooldown");

    public static final String WAND_ID = "Axo_Arcane_Wand";
    public static final String LEGACY_WAND_TEMPLATE_ID = WAND_ID + "_Template";
    public static final String POUCH_ID = "Axo_Spellbook_Pouch";
    public static final String BOOK_TAG = "Family=AxoTalesSpellbook";
    public final String key;
    public final String title;
    public final String secondaryHint;
    public final String primaryHint;

    SpellbookCatalog(String key, String title, String secondaryHint, String primaryHint) {
        this.key = key;
        this.title = title;
        this.secondaryHint = secondaryHint;
        this.primaryHint = primaryHint;
    }

    public String itemId() { return AxoItems.LEGACY.get("Book_" + key + "_Texture"); }
    public String wandId() { return this == SILVER ? WAND_ID : WAND_ID + "_" + key; }

    public static SpellbookCatalog fromBook(String itemId) {
        String id=AxoItems.canonical(itemId);return Arrays.stream(values()).filter(spell -> spell.itemId().equals(id)).findFirst().orElse(null);
    }

    public static SpellbookCatalog fromWand(String itemId) {
        itemId=AxoItems.canonical(itemId);


        if (LEGACY_WAND_TEMPLATE_ID.equals(itemId)) return SILVER;
        String id=itemId;return Arrays.stream(values()).filter(spell -> spell.wandId().equals(id)).findFirst().orElse(null);
    }
}
