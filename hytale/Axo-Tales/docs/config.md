# Configuration

<div class="page-header">
  <p class="eyebrow">Server tuning</p>
  <p class="lead-text">The values below describe the <strong>packaged defaults</strong> from <code>src/main/resources/server-config.json</code>. A live world can diverge once it has already written its own runtime <code>server-config.json</code>.</p>
</div>

## High-impact defaults

<div class="card-grid">
  <div class="card" data-reveal>
    <h3>Global spell timing</h3>
    <p><code>spellbooks.inputDebounceSeconds = 0.6</code>, <code>spellbooks.castDebounceSeconds = 0.6</code>, and <code>spellbooks.secondaryUseDelaySeconds = 0.3</code>.</p>
  </div>
  <div class="card" data-reveal>
    <h3>Arcane Crystals</h3>
    <p><code>chance = 0.33</code>, <code>placementsPerChunk = 1</code>, <code>densityRadius = 64</code>, <code>maxPlacementsPerRadius = 1</code>.</p>
  </div>
  <div class="card" data-reveal>
    <h3>Elder Mages</h3>
    <p><code>interval = 50s</code>, <code>maxAttempts = 12</code>, <code>cellSize = 192</code>, <code>cellChance = 50%</code>, and no timed despawn.</p>
  </div>
  <div class="card" data-reveal>
    <h3>Movement blocks</h3>
    <p>Cloud defaults to a 6-block target height. Bounce starts at 4 blocks, gains 2 per chain, and caps at 18.</p>
  </div>
</div>

## Spell and weapon keys

| System | Defaults that matter most |
| --- | --- |
| Healing Book | `manaCost = 25`, `healAmount = "full"`, `projectileDelaySeconds = 0.15` |
| Teleport Book | `maxDistanceBlocks = 100`, `manaCost = 10`, `castDelaySeconds = 0.5` |
| Mining Book | `maxDistanceBlocks = 12`, `manaCost = 5`, `chargeTierSeconds = 1.0`, `maxTunnelBlocks = 10` |
| Immunity Book | `manaCost = 15`, `immunitySeconds = 3` |
| Taunt Book | `manaCost = 25`, `launchHeightBlocks = 10`, `fallImmunitySeconds = 6`, `slamDamage = 40`, `groundBreakDepthBlocks = 2` |
| Horde Book | `manaCost = 25`, `minionLifetimeSeconds = 30`, `ownerFriendlySeconds = 60` |
| Doom Book | `manaCost = 25`, `projectileDelaySeconds = 0.24` |
| Morph Book | `manaCost = 25` |
| Frost Book | `manaCost = 20` |
| Flame Book | `manaCost = 20`, `projectileDelaySeconds = 0.2` |
| Light Book | `manaCost = 15`, `projectileDelaySeconds = 0.16`, `dynamicLightRadius = 1` |
| Arcane Sword | `manaCost = 20`, `cooldownSeconds = 1.25`, `castDelaySeconds = 0.34` |

## World and NPC keys

### Whisperion

- `whisperion.enabled = true` toggles wild spawning; existing tamed horses remain usable
- `whisperion.spawnWeight = 2` sets the relative native creature weight; `0` disables new wild spawns.
- Wild spawns use soil in Zone 1 plains, forests, autumn woods and Azure areas throughout the day and night. Native population and terrain checks apply.
- Older interval, lifetime and world-cap fields are compatibility data and do not control native ecology.
- Native movement asset `AxoTales_Whisperion`: horizontal flight 10.5, vertical 5.5. Taming is fixed at one Azure Kelp with 100% success .

### Arcane Matter ore

- `worldgen.arcaneMatterOres.enabled`
- `worldgen.arcaneMatterOres.processExistingChunks`
- `worldgen.arcaneMatterOres.stone.*`
- `worldgen.arcaneMatterOres.volcanic.*`

### Arcane Crystals

- `worldgen.arcaneCrystalChancePerNewChunk`
- `worldgen.arcaneCrystalPlacementsPerChunk`
- `worldgen.arcaneCrystalDensityRadiusBlocks`
- `worldgen.arcaneCrystalMaxPlacementsPerRadius`
- `worldgen.arcaneCrystalProcessExistingChunks`
- `worldgen.arcaneCrystalPruneLegacyClusters`

### Cursed Knight

- `runeKnight.spawn.*`
- `runeKnight.despawn.*`
- `runeKnight.projectiles.*`
- `runeKnight.loot.*`

### Other encounters

- `encounters.tomTomEnabled = true` enables native Tom Tom encounters.
- `encounters.tomTomSpawnWeight = 2.5` sets Tom Tom's relative ecology weight; `0` disables new spawns.
- Tom Tom uses the same native surface habitats as Whisperion, during day and night.
- `encounters.grayCatIntervalSeconds = 600` controls the separate Gray Cat clock.
- `runeKnight.spawn.intervalSeconds = 62.5` controls Cursed Knight night opportunities.

Native ecology weights are relative choices within eligible local populations, not guaranteed spawn intervals. Whisperion and Tom Tom have no separate timer or plugin world cap. Their old population files do not limit new encounters. Taming, ownership and saved saddle data remain intact.

### Elder Mage

- Elder Mage spawn settings
- Elder Mage population settings

### Movement blocks

- `cloudBlock.*`
- `bounceBlock.*`

## Practical tuning advice

- Compare the live runtime config with the packaged defaults before assuming the guidebook is out of date.
- Tune `teleportBook.castDelaySeconds`, `doomBook.projectileDelaySeconds`, `flameBook.projectileDelaySeconds`, and `lightBook.projectileDelaySeconds` independently. They are separate on purpose.
- Keep `worldgen.arcaneCrystalMaxPlacementsPerRadius = 1` unless you intentionally want crystals to become much more common again.
- Pair config review with `spellbooks-debug.log` whenever a spell or movement report sounds vague. A lot of "balance" bugs are actually state or routing bugs.

### Arcane Grass

`worldgen.arcaneGrass` controls scattered surface patches in new chunks. Defaults: `enabled: true`, `chancePerNewChunk: 0.75`, `patchesPerChunk: 2`, `radius: 3`. Placement uses the world seed and replaces only eligible exposed natural grass or stone. It does not regenerate existing terrain.
