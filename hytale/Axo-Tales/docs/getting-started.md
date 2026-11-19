# Start Here

Install Axo Tales for **Hytale 0.7.7**. This guide covers **AxoTales 0.1.3+**, which requires **Java 25**.

## Install or update

1. Obtain the current `AxoTales-<version>.jar`. To build from source, use the full development checkout; the public source mirror does not include Gradle build files.
2. Close the game or server. Keep a backup of your world and plugin data before updating.
3. Put the JAR in your server's mods folder or `%APPDATA%/Hytale/UserData/Mods` for local play. Keep exactly one AxoTales JAR. Avoid duplicate copies in individual save folders.
4. Restart the game or server. Preserve the existing configuration and storage files.

## Your first visit

A centered **Axo's Tale** page introduces the story and your Silver Book. Close it with **Close** or **Escape**. It appears once for each player in each save, across that save's dimensions. Existing Silver Book claims remain intact, so the tale never grants a duplicate. If your inventory is full, make one slot free; the book and tale wait for space.

## Files and backups {#first-files-worth-knowing}

| File or folder | Purpose |
| --- | --- |
| `server-config.json` | Spell costs, encounter rates, world resources and movement-block settings. Existing worlds keep their own runtime copy. |
| `spyglass-display.json` | The ordered fields shown by the Spyglass. |
| `AstralSatchels/` | Saved contents for physical satchels. Include this folder in backups. |
| `spellbooks-debug.log` and `plugin.log` | Cast, input and plugin diagnostics. Preserve both when reporting a problem. |

The [Configuration](config.md) page describes the packaged `server-config.json`. Your runtime file can have different values.

## Your starter book

On your first spawn, you receive **Axo's Silver Book**. Right-click to fire a gray bolt for **10 base damage** without spending mana. Each player gets one copy per save/server, shared across dimensions. A full inventory leaves the reward pending until a slot is free.

## Axo's Workbench {#where-crafting-starts}

At a **tier-two Workbench**, open **Crafting** and build **Axo's Workbench** with **8 Thorium Ingots, 12 Arcane Crystal Shards and 20 logs**. Place it and interact to use its five tabs:

| Tab | What you make |
| --- | --- |
| Items | Seeds, souls, bags, pickaxes, bars and utility equipment |
| Armory | Weapons, armor, Ancient Arrows and Arcane Oraculum |
| Library | Spellbooks, Arcane Wand and Spellbook Pouch |
| Blood Magic | Vitae Extractor, Blood Furnace and Frozen Chest |
| Alchemy | Axo's Empty Potion Bottle and potions |

The normal crafting screen supports search, previews, ingredient counts and queued crafts. Carried Astral Satchels can supply ingredients directly. See [Crafting Flow](crafting.md) for full costs.

## Getting started with magic {#good-first-session-route}

1. Try the free Silver Book and gather Arcane Crystal Shards and Arcane Matter.
2. Build Axo's Workbench, then defeat Cursed Knights for Evil Cores used by most crafted equipment and books.
3. Craft a spellbook or a piece of mana gear. Plant Crystal Seeds for renewable shards.
4. Craft the [Arcane Wand and Spellbook Pouch](items/arcane-equipment.md) to cast your carried spells from the wand.
5. Follow [Crafting Flow](crafting.md), [Helpful Souls](souls.md) or [Runes & Skill Tree](skill-tree.md) for more crafting recipes and abilities.

If an item does not respond, start with [Troubleshooting](troubleshooting.md).
