# Blocks and Ores

<div class="page-header">
  <p class="lead-text">Find Arcane Matter, grow crystals and use Cloud and Bounce Blocks for movement. Arcane Grass decorates new terrain; Poisonous Vines form regenerating screens.</p>
</div>

## Grow Arcane Crystals

For Agretis, winter Revel and fire-region Aequalis deposits, Furnace smelting and their equipment costs, see [New Metals](materials.md).

![Arcane Crystal Seed](assets/images/catalog/Axo_Arcane_Crystal_Seed.png)

Mine a wild Arcane Crystal for **one guaranteed seed (100%) plus 2-3 shards**. You can also craft **Arcane Crystal Seed** at Axo's Workbench → Items: 2 Arcane Crystal Shards, 1 Arcane Matter, 2 Life Essence; 3 seconds. Plant on prepared soil. A pointed seed crystal grows to a medium cluster after 5 minutes and matures 10 minutes later.

RMB-harvest mature growth for **2-3 Arcane Crystal Shards**; it restarts the growth cycle. Breaking any stage returns its seed, and mature growth also drops shards. Native farming data saves the stages/timers across world reloads. Growth does not require water or light modifiers.

## Core resource chain

<div class="card-grid">
  <div class="card media-card texture-panel texture-panel--ore">
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/catalog/Axo_Arcane_Matter_Ore_Stone.png" alt="Arcane Matter Ore icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Matter Ore</h3>
      <p>Stone and volcanic deposits both yield Arcane Matter for crafting.</p>
      <p><strong>Shipped defaults</strong>: 50% chance per new chunk, target 12 placements, max 256 attempts per chunk.</p>
    </div>
  </div>
  <div class="card media-card texture-panel texture-panel--crystal">
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/catalog/Axo_Arcane_Crystal.png" alt="Arcane Crystal icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Crystal</h3>
      <p>Grow crystals for repeatable shard harvests, bond Elder Mages and supply crafting recipes.</p>
      <p><strong>Shipped defaults</strong>: 64-block density radius, max 1 crystal in that radius, 33% chance per placement attempt.</p>
    </div>
  </div>
  <div class="card media-card">
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/catalog/Axo_Arcane_Crystal_Shards.png" alt="Arcane Crystal Shard icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Crystal Shards</h3>
      <p>Used in gear, spellbook and potion recipes. Drop a shard near an unbonded Elder Mage to bond it to you.</p>
    </div>
  </div>
</div>

## Decorative and movement blocks

<div class="card-grid">
  <div class="card media-card texture-panel texture-panel--grass">
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/catalog/Axo_Arcane_Grass.png" alt="Arcane Grass icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcane Grass</h3>
      <p>Decorative grass with textures that blend into the surrounding ground. It is not a crafting material.</p>
    </div>
  </div>
  <div class="card media-card texture-panel texture-panel--cloud">
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/catalog/Axo_Cloud_Block.png" alt="Cloud Block icon">
    </div>
    <div class="media-card__copy">
      <h3>Cloud Block</h3>
      <p>A white cloud with pale lavender shading.</p>
      <p>You can pass through it and place it on floors, walls or ceilings. It launches you in the direction you enter.</p>
      <p><strong>Shipped defaults</strong>: 6-block target height, 1.0s rearm, 4.0s chain reset.</p>
    </div>
  </div>
  <div class="card media-card texture-panel texture-panel--bounce">
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/catalog/Axo_Bounce_Block.png" alt="Bounce Block icon">
    </div>
    <div class="media-card__copy">
      <h3>Bounce Block</h3>
      <p>A spring block with indigo plates, gold supports and an amethyst.</p>
      <p>Launches you straight up. Each consecutive bounce sends you higher.</p>
      <p><strong>Shipped defaults</strong>: 4-block base target height, +2 blocks per chain, 18-block cap.</p>
    </div>
  </div>
</div>

## How these blocks enter play

- Mine Arcane Matter ore for crafting materials.
- Use Arcane Crystals and Shards in recipes or to bond Elder Mages.
- Cloud and Bounce Blocks drop as loot; they have no workbench recipe.
- Cursed Knights, Elder Mages, and Arcane Matter ore can all drop movement blocks.

## Arcane Matter appearance

Look for dark rock clusters with purple cracks and particles. They appear in stone and volcanic rock, and both variants yield Arcane Matter.

![Arcane Matter](assets/images/catalog/Axo_Arcane_Matter.png)

## Arcane Grass

Small purple patches appear among exposed natural grass and stone as you explore new terrain. They are spread across eligible regions without covering the landscape. Water, ores, worked stone and existing chunks are left alone. Arcane Grass is decorative soil; breaking it gives ordinary Dirt.


## Poisonous Vine

Craft a **Poisonous Vine Seed** in **Axo's Workbench → Items** with **4 Venom
Sacs, 2 Life Essence and 1 Purple Orchid**. It takes 3 seconds.

Aim in the direction you want the screen to face, then plant it. New seeds grow
into a screen up to **40 blocks wide and 40 blocks high**, with the root at the
bottom centre. The plane stays fixed after planting. Growth can go around a tree
where there is space, then come back down to fill reachable gaps. Grass plants
and ordinary bushes are replaced; solid terrain, trees, crops and containers stay.
Unloaded terrain pauses growth. Existing larger vines keep their size.

The foliage can hide a base from view. Players can walk straight through it,
but contact applies **10 seconds of poison**. Crossing more leaves refreshes that
time without stacking separate ten-second periods. Break a leaf block to clear just that block. A normal harvest has a **5% chance**
to drop one seed. The missing section grows back quickly while the **original root**
is intact. Destroy the first block placed by the seed to stop further growth and
regeneration. Its leaves gradually wither and disappear, spreading outward from
the removed root. Withering leaves do not drop seeds.
