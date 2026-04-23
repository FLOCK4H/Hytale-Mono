# NPCs and Encounters

<div class="page-header" data-reveal>
  <div markdown="1">
    <span class="hero-kicker">Kudu route</span>

    # The Kudu lane now does real work in progression

    Since `0.1.155`, the Kudu content stopped being a side experiment. Rune Knights became reliable resource fights, and Adepts grew into a bondable companion loop with particles, owner persistence, and targeted combat behavior.
  </div>
</div>

## Kudu Rune Knight

<div class="card media-card accent-card" data-reveal>
  <div class="media-card__visual media-card__visual--single">
    <img src="assets/images/items/frost_book.png" alt="Frost Book icon">
  </div>
  <div class="media-card__copy">
    <h3>Hostile ranged encounter</h3>
    <p>Rune Knights are the aggressive Kudu fight. They spawn at night, use projectile combat, and now always pay out the baseline crafting materials that keep them worth hunting.</p>
  </div>
</div>

### Rune Knight loot

- Guaranteed: 1 to 2 Arcane Crystal Shards.
- Guaranteed: 1 Arcane Matter.
- Configurable chance: Kudu Boots, default 5%.
- Configurable chance: Frost Book, default 5%.
- Bonus drop pool: Cloud Block or Bounce Block at the Axo Tales movement-block bonus rate.

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
    <img src="assets/images/blocks/arcane_crystal_shard.png" alt="Arcane Crystal Shard icon">
  </div>
  <div class="media-card__copy">
    <h3>Friendly companion lane</h3>
    <p>Adepts spawn naturally, can bond to the player who drops a shard or Arcane Crystal nearby, and then fight only the owner's current non-player target using ranged fire-like attacks.</p>
  </div>
</div>

### How bonding works now

- Drop an Arcane Crystal Shard or Arcane Crystal block item near an unbonded Adept.
- The player who dropped the crystal becomes that Adept's master.
- The bond now has visual feedback: pickup behavior, bond particles, and clearer ownership transfer.
- Bonded Adepts persist their owner data and keep the relationship even when that player is offline.

### Bonded Adept behavior

- Follows the owner rather than free-roaming socially.
- Can catch up with teleport logic if it falls badly behind.
- Uses projectile-only combat and does not chase every random mob in range.
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

## Why this matters if you last played older builds

- Rune Knights are now reliable farm targets instead of high-effort, low-return flavor mobs.
- Adepts are not just ambient NPCs anymore; they are companion content tied directly to Arcane Crystal routing.
- The Kudu loop is now one of the cleanest bridges between world resources, combat, and traversal block drops.
