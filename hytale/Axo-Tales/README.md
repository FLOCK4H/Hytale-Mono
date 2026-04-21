**Axo Tales** is a magic-focused mod. It adds boots that let you jump high while negating fall damage and increasing your movement speed, each armor piece gives you additional max mana, max health, max stamina, and special abilities like underwater breathing, faster swimming, damage and defense buffs, or lets you shoot projectiles with a fancy gloves. **Kudu Boots allow you to walk on water**, **Clouds & Bouncy** blocks that will become mandatory inside your base, **10 new powerful spells** to craft, many useful potions, and two **Arcane NPCs with one that can fight for you if you give it Arcane Crystal**. In other words, it adds custom items, recipes, world content, and server-side gameplay logic.

### Spellbooks

*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_34ea8ecb-dc2a-4e4c-a36e-083668fed061.png) **Healing Book**: LMB fires a healing bolt; RMB fully heals you.
*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_80b1f897-b6a4-4d25-8475-d7e66943fe1a.png) **Teleport Book**: RMB teleports you to the block you’re looking at.
*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_75a6aeaf-5e62-4f9b-93a1-6fb5aeb68c69.png) **Mining Book**: RMB mines a shaped patch around the targeted block.
*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_4e1abf54-25d9-4a01-a0a1-77a90a91c224.png) **Immunity Book**: RMB grants brief damage immunity.
*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_62345ade-1f4d-415d-84ef-94392163e0e4.png) **Taunt Book**: RMB launches you upward and slams on landing for area damage.
*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_1b94dc45-6079-4653-a813-dd85bb77301c.png) **Horde Book**: RMB summons a short-lived brute horde that fights for you.
*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_3a08a0df-a48a-4a86-ab34-bfe60abaa8f3.png)**Book of Doom**: RMB hurls a Doom Ball projectile.
*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_13ca21db-d267-406c-b8db-1fd70f387f46.png) **Morph Book**: RMB fires a morph vortex to steal a target’s form; LMB restores your own.
*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_f4441c5a-6160-4bbe-94ec-b98994ab6907.png) **Frost Book**: RMB launches a frost shard that slows foes and turns terrain to snow brick.
*   ![image](https://media.forgecdn.net/attachments/description/1454208/description_5a3c8a24-27b2-418f-b9ab-fadf42ad126c.png) **Flame Book**: RMB launches a flame bolt that burns foes and turns terrain to volcanic rock.

### Potions (Alchemybench)

*   **Axo’s Empty Potion Bottle** is required for brewing every Axo Tales potion.
*   **Swift Potion**: movement speed buff.
*   **Strength Potion**: doubles outgoing damage for a short time.
*   **Rabbit Potion**: jump height buff.
*   **Invisibility Potion**: hides you from other players for a short time.
*   **Curse Potion**: thrown splash vial that explodes in cursed damage.

### Gear

*   **Sa’r set** (Boots / Diadem / Armor / Warfists): mana bonuses + regen while worn; boots add movement/jump/stamina/fall immunity.
*   **Invisibility Cloak**: keeps you invisible while worn and dulls nearby NPC aggression; increases max mana.

![image](https://media.forgecdn.net/attachments/description/1454208/description_49af15f5-c5c3-4543-bd43-fec242635a25.png)

*   **Kudu Boots**: walk on water by turning water beneath you into snow brick; increases max mana.

![image](https://media.forgecdn.net/attachments/description/1454208/description_3b8f9605-3af4-43e7-b4bf-7897be1c1184.png)

*   **Axo’s Ancient Sword**: sky-blue trail; RMB casts an Ancient Slash at a mana cost; carrying it increases max mana.

![image](https://media.forgecdn.net/attachments/description/1454208/description_438bff1d-aad2-4799-a55d-9a1166be5142.png)

### Blocks / Ores

*   **Arcane Crystal**: Spawns around the world
*   **Arcane Crystal Shards**: a craft ingredient.
*   **Arcane Matter**: a craft ingredient.
*   **Arcane Matter Ore**: generates in stone and volcanic rock (worldgen is configurable).
*   **Arcane Grass**: a custom grass block.
*   **Cloud Block**: Applies force at the direction you are going if you go through it
*   **Bouncy Block**: Almost doubles the height of each jump on it

### NPC encounters

*   **Kudu Rune Knight**: configurable spawns and drops (Kudu Boots / Frost Book drop chances, spawn interval, etc.).
*   **Kudu Adept Magician**: **Drop an arcane crystal near Kudu Adept Magician to befriend the NPC and make it fight for you.**

### Contributions

Although the mod is open-source, the codebase contributions aren't needed. **Textures & models** are, so if you've got something you would like to share and see in the next version release, please either fork the github repo and upload directly, or send via Discord/ Telegram/ e-mail.

### Suggestions

Please use the comments page, github issues, or contact me directly if you want something to be added.

### Configuration

After first run, edit `server-config.json` in the mod’s data folder to tune:

*   spell mana costs + cooldowns
*   worldgen (Arcane Matter ore)
*   Kudu Rune Knight spawns/drops
*   Ancient Sword ability settings

### Installation

1.  Download the `AxoTales-<version>.jar`.
2.  Copy it to your Hytale Mods folder: `...\Hytale\UserData\Mods`
3.  Start/restart the game/server.

## What’s included

- Spellbooks with server-side abilities (healing, teleport, mining, immunity, taunt slam, horde summon, doom projectile, morph, frost, flame)
- Brewable potions (swift/strength/rabbit/invisibility/curse) using **Axo’s Empty Potion Bottle** and Alchemist Workbench
- Gear with passive effects (Sa’r set, Kudu Boots water-walk, Invisibility Cloak, Axo’s Ancient Sword)
- World content (Arcane Matter ores + Arcane Grass)
- Configurable tuning via `server-config.json` (mana costs, spawns, loot, worldgen, etc.)

## Install (Server / Local)

Recommended (builds + deploys):
- `powershell -ExecutionPolicy Bypass -File .\scripts\deploy-to-user-mods.ps1`

Manual:
1. Build: `.\gradlew.bat build`
2. Copy `build/libs/AxoTales-<version>.jar` to `...\Hytale\UserData\Mods`
3. Remove older `AxoTales-*.jar` files (keep only one version)
4. Start/restart the game/server

## Guidebook (GitHub Pages)

- MkDocs site config: `mkdocs.yml`
- Pages: `docs/`
- Deployment workflow: `.github/workflows/pages.yml`
