# Configuration

Axo Tales creates `server-config.json` in the mod’s data folder after first run.

## Common keys

### Spellbooks

- `spellbooks.inputDebounceSeconds`: animation/input spam guard
- `spellbooks.castDebounceSeconds`: cast spam guard
- `spellbooks.secondaryUseDelaySeconds`: delays RMB/Use effects to line up with the cast animation

### Worldgen (Arcane Matter)

- `worldgen.arcaneMatterOres.enabled`
- `worldgen.arcaneMatterOres.processExistingChunks`
- `worldgen.arcaneMatterOres.stone.*`
- `worldgen.arcaneMatterOres.volcanic.*`

### Kudu Rune Knight

- `runeKnight.enabled`
- `runeKnight.spawn.*`
- `runeKnight.despawn.*`
- `runeKnight.projectiles.*`
- `runeKnight.loot.*` (drop chances)

### Kudu Adept Magician

- `kuduAdept.enabled` (disabled by default)
- `kuduAdept.spawn.*`
- `kuduAdept.despawn.*`

### Book mana costs and tuning

- `healingBook.*`
- `teleportBook.*`
- `miningBook.*`
- `immunityBook.*`
- `tauntBook.*`
- `hordeBook.*`
- `doomBook.*`
- `morphBook.*`
- `frostBook.*`
- `flameBook.*`

### Ancient Sword

- `ancientSword.enabled`
- `ancientSword.projectileId`
- `ancientSword.manaCost`
- `ancientSword.cooldownSeconds`

