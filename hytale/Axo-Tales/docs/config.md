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
    <h3>Kudu Adepts</h3>
    <p><code>interval = 120s</code>, <code>maxAttempts = 3</code>, <code>cellSize = 256</code>, <code>cellChance = 33%</code>, and no timed despawn.</p>
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
| Ancient Sword | `manaCost = 20`, `cooldownSeconds = 1.25`, `castDelaySeconds = 0.34` |

## World and NPC keys

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

### Rune Knight

- `runeKnight.spawn.*`
- `runeKnight.despawn.*`
- `runeKnight.projectiles.*`
- `runeKnight.loot.*`

### Kudu Adept

- `kuduAdept.spawn.*`
- `kuduAdept.despawn.*`

### Movement blocks

- `cloudBlock.*`
- `bounceBlock.*`

## Practical tuning advice

- Compare the live runtime config with the packaged defaults before assuming the guidebook is out of date.
- Tune `teleportBook.castDelaySeconds`, `doomBook.projectileDelaySeconds`, `flameBook.projectileDelaySeconds`, and `lightBook.projectileDelaySeconds` independently. They are separate on purpose.
- Keep `worldgen.arcaneCrystalMaxPlacementsPerRadius = 1` unless you intentionally want crystals to become much more common again.
- Pair config review with `spellbooks-debug.log` whenever a spell or movement report sounds vague. A lot of "balance" bugs are actually state or routing bugs.
