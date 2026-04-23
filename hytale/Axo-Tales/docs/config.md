# Configuration

<div class="page-header" data-reveal>
  <div markdown="1">
    <span class="hero-kicker">Server tuning</span>

    # Read the packaged defaults before you tune a live world

    The values below describe the <strong>shipped default</strong> config from <code>src/main/resources/server-config.json</code>. Once a world or server has already generated its own runtime <code>server-config.json</code>, that live file wins.
  </div>
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
| Mining Book | `maxDistanceBlocks = 12`, `manaCost = 5`, `gridSize = 3`, `maxBlocks = 9` |
| Immunity Book | `manaCost = 15`, `immunitySeconds = 3` |
| Taunt Book | `manaCost = 25`, `launchHeightBlocks = 10`, `fallImmunitySeconds = 6`, `slamDamage = 40`, `slamRadiusBlocks = 7` |
| Horde Book | `manaCost = 25`, `minionLifetimeSeconds = 30`, `ownerFriendlySeconds = 60` |
| Doom Book | `manaCost = 25`, `projectileDelaySeconds = 0.24` |
| Morph Book | `manaCost = 25` |
| Frost Book | `manaCost = 20` |
| Flame Book | `manaCost = 20`, `projectileDelaySeconds = 0.2` |
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

- If a live server already has a runtime config, compare it against the packaged defaults before assuming the docs are wrong.
- Tune `teleportBook.castDelaySeconds`, `doomBook.projectileDelaySeconds`, and `flameBook.projectileDelaySeconds` independently; those three were separated on purpose during the spell feel pass.
- Keep `worldgen.arcaneCrystalMaxPlacementsPerRadius = 1` unless you intentionally want crystals to become common visual clutter again.
- When debugging player reports, pair config review with `spellbooks-debug.log`; a surprising amount of "bad balance" bugs are actually bad input or stat-gate state.
