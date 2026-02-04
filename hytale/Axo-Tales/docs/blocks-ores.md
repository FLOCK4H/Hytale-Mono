# Blocks & Ores

## Arcane Matter

**What it is**
- A craft ingredient used in most Axo Tales recipes.

**How to get**
- Mine Arcane Matter Ore (stone or volcanic).

## Arcane Matter Ore (Stone / Volcanic)

**What it is**
- Two ore blocks that generate in the world:
  - Stone-hosted Arcane Matter Ore
  - Volcanic-hosted Arcane Matter Ore

**Drops**
- Arcane Matter
- Matching cobble type (stone or volcanic)

**World generation**
- Controlled by `worldgen.arcaneMatterOres.*` in `server-config.json`.
- Defaults:
  - Stone ore: underground on exposed rock (caves/dungeons are the easiest way to spot it).
  - Volcanic ore: inside volcanic rock (volcanic areas are the best place to mine for it).

## Arcane Crystal

**What it is**
- A surface crystal formation.

**Drops**
- Arcane Crystal Shards (2-3)

**World generation (defaults)**
- Spawns on the surface in newly-generated chunks (about 1 per ~12 chunks by default; configurable).
- Controlled by `worldgen.arcaneCrystalChancePerNewChunk` in `server-config.json`.

## Arcane Grass

**What it is**
- A custom grass block for building.

**Notes**
- Breaking it drops dirt by default (so survival collection isn't intended).

## Custom Placeholder Block

**What it is**
- A debug marker block used for worldgen/testing.
