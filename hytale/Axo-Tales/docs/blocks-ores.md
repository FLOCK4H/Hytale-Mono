# Blocks and Ores

<div class="page-header">
  <p class="eyebrow">World route</p>
  <p class="lead-text">The world side of Axo Tales is more than one ore drop. Arcane Matter, Arcane Crystals, and movement-block bonuses all feed progression now, while Arcane Grass remains a decorative builder-facing block.</p>
</div>

## Core resource chain

<div class="card-grid">
  <div class="card media-card texture-panel texture-panel--ore" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/blocks/arcane_matter_ore.png" alt="Arcane Matter Ore icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Matter Ore</h3>
      <p>Ships in stone and volcanic variants. Both feed the same main crafting material, which keeps the ore route relevant whether you are in calmer terrain or volcanic regions.</p>
      <p><strong>Shipped defaults</strong>: 50% chance per new chunk, target 12 placements, max 256 attempts per chunk.</p>
    </div>
  </div>
  <div class="card media-card texture-panel texture-panel--crystal" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/blocks/arcane_crystal.png" alt="Arcane Crystal icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Crystal</h3>
      <p>Acts like a routeable resource node now, not random world clutter. Crystals also feed Kudu Adept bonding and several core recipes.</p>
      <p><strong>Shipped defaults</strong>: 64-block density radius, max 1 crystal in that radius, 33% chance per placement attempt.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/blocks/arcane_crystal_shard.png" alt="Arcane Crystal Shard icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Crystal Shards</h3>
      <p>The shared premium ingredient across gear, spellbooks, and potions. Shards also trigger Adept bonding when dropped near one.</p>
    </div>
  </div>
</div>

## Decorative and movement blocks

<div class="card-grid">
  <div class="card media-card texture-panel texture-panel--grass" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/blocks/arcane_grass.png" alt="Arcane Grass icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Grass</h3>
      <p>A decorative arcane grass variant with transition textures. Treat it as builder content rather than a progression material.</p>
    </div>
  </div>
  <div class="card media-card texture-panel texture-panel--cloud" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/blocks/cloud_block.png" alt="Cloud Block icon">
    </div>
    <div class="media-card__copy">
      <h3>Cloud Block</h3>
      <p>Directional movement tech. It is pass-through, can be placed on floors, walls, or ceilings, and launches based on the direction you enter it.</p>
      <p><strong>Shipped defaults</strong>: 6-block target height, 1.0s rearm, 4.0s chain reset.</p>
    </div>
  </div>
  <div class="card media-card texture-panel texture-panel--bounce" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/blocks/bounce_block.png" alt="Bounce Block icon">
    </div>
    <div class="media-card__copy">
      <h3>Bounce Block</h3>
      <p>The more predictable vertical movement block. It launches upward only and grows stronger through chained bounces.</p>
      <p><strong>Shipped defaults</strong>: 4-block base target height, +2 blocks per chain, 18-block cap.</p>
    </div>
  </div>
</div>

## How these blocks enter play

- Arcane Matter ore is the main crafted-resource route.
- Arcane Crystals and Shards feed both recipes and the Kudu companion lane.
- Cloud and Bounce Blocks are bonus drops rather than normal bench crafts.
- Kudu Rune Knights, Kudu Adepts, and Arcane Matter ore can all feed the movement-block pool.
