# NPCs and Encounters

<div class="page-header">
  <p class="eyebrow">Kudu route</p>
  <p class="lead-text">Axo Tales has two Kudu lanes that matter right now: Rune Knights as a hostile farm target, and Kudu Adepts as a bondable companion system tied to Arcane Crystal drops.</p>
</div>

## Kudu Rune Knight

<div class="accent-card media-card" data-reveal>
  <div class="media-card__visual media-card__visual--single">
    <img src="../assets/images/items/frost_book.png" alt="Frost Book icon">
  </div>
  <div class="media-card__copy">
    <h3>Hostile ranged encounter</h3>
    <p>Rune Knights are the reliable Kudu farm target. They spawn at night, fight at range, and now always pay out core materials instead of only flavor drops.</p>
  </div>
</div>

### Rune Knight loot

- Guaranteed: 1 to 2 Arcane Crystal Shards.
- Guaranteed: 1 Arcane Matter.
- Configurable chance: Kudu Boots, default 5%.
- Configurable chance: Frost Book, default 5%.
- Bonus drop pool: Cloud Block or Bounce Block.

### Rune Knight shipped defaults

- Spawn interval: 300 seconds.
- Spawn attempts per interval: 12.
- Active cap per world: 4.
- Spawn radius band: 24 to 96 blocks from players.
- Projectile cooldown: 1.25 seconds.
- Projectile range: 24 blocks.
- Despawn on day: enabled.

## Kudu Adept Magician

<div class="card media-card" data-reveal>
  <div class="media-card__visual media-card__visual--single">
    <img src="../assets/images/blocks/arcane_crystal_shard.png" alt="Arcane Crystal Shard icon">
  </div>
  <div class="media-card__copy">
    <h3>Friendly companion lane</h3>
    <p>Adepts spawn naturally, can bond to the player who drops a Shard or Arcane Crystal nearby, and then follow that owner into ranged support combat.</p>
  </div>
</div>

### How bonding works

- Drop an Arcane Crystal Shard or Arcane Crystal item near an unbonded Adept.
- The player who dropped it becomes that Adept's master.
- The mod shows the handoff with pickup behavior and bond particles.
- The Adept keeps that owner relationship even if the owner later logs out.

### Bonded Adept behavior

- Follows the owner instead of free-roaming socially.
- Can catch up with teleport logic if it falls too far behind.
- Uses ranged attacks instead of trying to fake a melee role.
- Focuses on the non-player target the owner last damaged.
- Drops 1 Arcane Crystal Shard and 1 Arcane Matter on death.
- Shares the Cloud and Bounce bonus-drop pool.

### Adept shipped defaults

- Spawn interval: 120 seconds.
- Spawn attempts per interval: 3.
- Density cell size: 256 blocks.
- Cell spawn chance: 33%.
- Spawn radius band: 8 to 280 blocks from players.
- Despawn on night: disabled.
- Timed despawn: disabled.

## Why the Kudu route matters

- Rune Knights are one of the cleanest ways to convert combat into Arcane materials.
- Adepts give the mod a companion loop instead of only more gear.
- Both encounter lanes can feed the movement-block bonus pool, so the Kudu route also bleeds into traversal builds.
