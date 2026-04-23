# Axo Tales (Hytale Mod)

Axo Tales is a magic-heavy Hytale mod built as a plugin plus asset pack. The current repo line is pinned to Hytale release `2026.03.26-89796e57b`.

## Current feature set

- 10 spellbooks with packet-driven server logic, custom cast timing, and distinct animation identity
- Sa'r armor, Kudu Boots, Invisibility Cloak, and Axo's Ancient Sword with passive stat and movement perks
- Kudu Rune Knights and bondable Kudu Adepts with progression drops
- Arcane Matter ore, Arcane Crystals, Arcane Grass, Cloud Blocks, and Bounce Blocks
- Configurable tuning in `server-config.json` for spell costs, worldgen, NPC spawning, loot, and movement blocks

## Install / update

Recommended:
- `powershell -ExecutionPolicy Bypass -File .\scripts\deploy-to-user-mods.ps1`

Manual:
1. Run `.\gradlew.bat build`
2. Copy `build/libs/AxoTales-<version>.jar` to `C:\Users\<you>\AppData\Roaming\Hytale\UserData\Mods`
3. Remove older `AxoTales-*.jar` files so only one build is present
4. Restart the game or server

## Guidebook

- MkDocs config: `mkdocs.yml`
- Guidebook pages and assets: `docs/`
- GitHub Pages workflow: `.github/workflows/pages.yml`
- Release delta page: `docs/whats-new.md`
