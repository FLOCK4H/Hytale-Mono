# Axo Tales (Hytale Mod)

Axo Tales is a magic-focused mod (plugin + asset pack) for Hytale servers and local worlds.

## What’s included

- Spellbooks with server-side abilities (healing, teleport, mining, immunity, taunt slam, horde summon, doom projectile, morph, frost, flame)
- Brewable potions (swift/strength/rabbit/invisibility/curse) using **Axo’s Empty Potion Bottle**
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
