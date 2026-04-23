# Blocks and Ores

<div class="page-header" data-reveal>
  <div markdown="1">
    <span class="hero-kicker">World route</span>

    # Axo Tales now has both resource nodes and movement-tech blocks

    The world side of the mod is no longer just "mine Arcane Matter." You now have deterministic Arcane Crystals, Kudu-linked resource loops, and two bonus-drop movement blocks that can reshape traversal builds.
  </div>
</div>

## Core resource chain

<div class="card-grid">
  <div class="card media-card tilt-card texture-panel texture-panel--ore" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="assets/images/blocks/arcane_matter_ore.png" alt="Arcane Matter Ore icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Matter Ore</h3>
      <p>Ships in stone and volcanic variants. Both drop Arcane Matter plus the matching cobble type. The packaged defaults still generate both ore families aggressively enough to support regular crafting.</p>
      <p><strong>Shipped defaults</strong>: 50% chance per new chunk, target 12 placements, max 256 attempts.</p>
    </div>
  </div>
  <div class="card media-card tilt-card texture-panel texture-panel--crystal" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="assets/images/blocks/arcane_crystal.png" alt="Arcane Crystal icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Crystal</h3>
      <p>The big worldgen rewrite after 0.1.147. Crystals now act like routeable surface nodes instead of random clutter.</p>
      <p><strong>Shipped defaults</strong>: 64-block density radius, max 1 crystal in that radius, 33% chance, no existing-chunk seeding, legacy cluster pruning enabled.</p>
    </div>
  </div>
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="assets/images/blocks/arcane_crystal_shard.png" alt="Arcane Crystal Shard icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Crystal Shards</h3>
      <p>The universal premium craft ingredient. They also double as the bonding trigger for Kudu Adepts when dropped nearby.</p>
    </div>
  </div>
</div>

## Decorative and movement blocks

<div class="card-grid">
  <div class="card media-card tilt-card texture-panel texture-panel--grass" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="assets/images/blocks/arcane_grass.png" alt="Arcane Grass icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Grass</h3>
      <p>A builder-facing arcane grass variant with transition textures. It currently drops dirt, so treat it as creative or decorative content rather than a survival farm target.</p>
    </div>
  </div>
  <div class="card media-card tilt-card texture-panel texture-panel--cloud" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="assets/images/blocks/cloud_block.png" alt="Cloud Block icon">
    </div>
    <div class="media-card__copy">
      <h3>Cloud Block</h3>
      <p>Pass-through, translucent, placeable on floors, walls, or ceilings. It launches up or down based on entry direction and chains at 1.5x per same-direction follow-up cloud.</p>
      <p><strong>Shipped defaults</strong>: 6-block target height, 1.0s rearm, 4.0s chain reset.</p>
    </div>
  </div>
  <div class="card media-card tilt-card texture-panel texture-panel--bounce" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="assets/images/blocks/bounce_block.png" alt="Bounce Block icon">
    </div>
    <div class="media-card__copy">
      <h3>Bounce Block</h3>
      <p>Solid upward-only launch block. Streaked bounces scale from a 4-block target up to an 18-block cap, making it the more predictable vertical route compared with Cloud's directional chaos.</p>
      <p><strong>Shipped defaults</strong>: +2 target height per chained bounce, 0.2s rearm, 8.0s streak reset.</p>
    </div>
  </div>
</div>

## How the movement blocks enter progression

- Cloud and Bounce Blocks are not standard bench crafts right now.
- Arcane Matter ore can drop one as a bonus at a 25% total chance, split 50/50 between the two block types.
- Kudu Rune Knights and Kudu Adepts can also drop one as a bonus at a 33% total chance, again split 50/50 between Cloud and Bounce.

## Config keys that matter most here

- `worldgen.arcaneCrystalChancePerNewChunk`
- `worldgen.arcaneCrystalPlacementsPerChunk`
- `worldgen.arcaneCrystalDensityRadiusBlocks`
- `worldgen.arcaneCrystalMaxPlacementsPerRadius`
- `worldgen.arcaneMatterOres.stone.*`
- `worldgen.arcaneMatterOres.volcanic.*`
- `cloudBlock.*`
- `bounceBlock.*`
