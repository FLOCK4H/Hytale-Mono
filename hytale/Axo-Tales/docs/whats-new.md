# What's New Since `0.1.147`

<div class="page-header" data-reveal>
  <div markdown="1">
    <span class="hero-kicker">Upgrade map</span>

    # The `0.1.148` to `0.1.193` line is a real content expansion

    If your baseline memory of Axo Tales is "spellbooks, potions, and some early Sa'r gear," the mod now plays very differently. This page groups the full post-`0.1.147` change stream into the chunks that matter for players, server owners, and anyone syncing the mod into another repo.
  </div>
</div>

## The short version

<div class="card-grid">
  <div class="card" data-reveal>
    <h3>Platform and packaging</h3>
    <p>The build is pinned to Hytale <code>2026.03.26-89796e57b</code>, the jar manifest targets that exact release, and deployment is locked to the shared <code>UserData\Mods</code> folder.</p>
  </div>
  <div class="card" data-reveal>
    <h3>World and progression</h3>
    <p>Arcane Crystals moved to deterministic density cells, Kudu encounters now pay out guaranteed core resources, and the Arcanist's Workbench exposes dedicated Axo Tales tabs.</p>
  </div>
  <div class="card" data-reveal>
    <h3>Combat feel</h3>
    <p>The whole spellbook family got a cast polish pass. Doom is now a 50-damage, 5-block-radius fire burst. Taunt stacks. Teleport finally has a deliberate blink window.</p>
  </div>
</div>

## Release bands

<div class="timeline-grid">
  <section class="timeline-card tilt-card" data-reveal>
    <span class="chip">0.1.148 to 0.1.154</span>
    <h3>Release sync and Arcane Crystal reset</h3>
    <ul>
      <li><strong>0.1.148</strong>: repo pinned to Hytale <code>2026.03.26-89796e57b</code> and upgraded for the latest inventory API.</li>
      <li><strong>0.1.149</strong>: packaged <code>manifest.json</code> now writes the exact target server version instead of <code>*</code>.</li>
      <li><strong>0.1.150</strong>: Frost Book and Kudu Boots got proper Arcanist recipes, and Axo Tales stopped overriding the whole vanilla Arcane bench.</li>
      <li><strong>0.1.151</strong> to <strong>0.1.154</strong>: Arcane Crystal worldgen became persistent, density-limited, deterministic, and safer for already-generated chunks.</li>
    </ul>
  </section>

  <section class="timeline-card tilt-card" data-reveal>
    <span class="chip">0.1.155 to 0.1.170</span>
    <h3>Kudu companions became a proper system</h3>
    <ul>
      <li>Rune Knights now guarantee Arcane Crystal Shards and Arcane Matter, keeping Kudu encounters relevant for progression.</li>
      <li>Kudu Adepts can bond when players drop Arcane Crystals or Shards nearby, with particles and pickup feedback clarifying the handoff.</li>
      <li>Adept AI got repeated fixes for spawn validation, ranged attack behavior, owner following, combat targeting, and persistence across owner logouts.</li>
      <li>The shipped defaults settled on a slower, wider spawn cadence: 120s intervals, 256-block density cells, 33% cell chance, and no automatic night despawn.</li>
      <li><strong>0.1.163</strong> also hard-locked deployment back to <code>UserData\Mods</code> only to stop duplicate-load confusion.</li>
    </ul>
  </section>

  <section class="timeline-card tilt-card" data-reveal>
    <span class="chip">0.1.171 to 0.1.178</span>
    <h3>Movement blocks and bench tabs landed</h3>
    <ul>
      <li><strong>0.1.171</strong> to <strong>0.1.176</strong>: Cloud Block launched as a pass-through vertical routing block, then gained placement fixes, better contact handling, a 6-block target height, and same-direction chaining.</li>
      <li><strong>0.1.175</strong>: Bounce Block joined as the solid upward-only partner, with chained jump scaling up to an 18-block target height.</li>
      <li><strong>0.1.177</strong> and <strong>0.1.178</strong>: Arcanist's Workbench gained Axo Tales Spellblades and Armor tabs via runtime category injection instead of a fragile full asset override.</li>
      <li>Cloud and Bounce Blocks also entered the loot pool through Arcane Matter ore and Kudu encounter bonus drops.</li>
    </ul>
  </section>

  <section class="timeline-card tilt-card" data-reveal>
    <span class="chip">0.1.179 to 0.1.180</span>
    <h3>Armor identity and slam scaling</h3>
    <ul>
      <li>Every Sa'r armor piece now has shared stamina, regen, and defense value instead of feeling like a mana-only shell.</li>
      <li>Diadem got aquatic utility. Chest gained +75 health and +25% physical/projectile damage. Warfists got an unarmed stun projectile.</li>
      <li>All current armor durability jumped to 5x the old lifespan with <code>MaxDurability=500</code> and <code>DurabilityLossOnHit=0.5</code>.</li>
      <li>Taunt Book mid-air recasts now stack by 1.5x up to 2000 damage while widening the crater footprint.</li>
      <li>Ancient Sword melee damage switched to <code>Physical</code> and picked up a 10% damage bump.</li>
    </ul>
  </section>

  <section class="timeline-card tilt-card" data-reveal>
    <span class="chip">0.1.181 to 0.1.193</span>
    <h3>Spell identity, Doom blast, and Teleport timing pass</h3>
    <ul>
      <li>All spellbooks now point at distinct cast ids inside <code>AxoTales_Spellbook.json</code>, so each book reads differently instead of sharing one generic motion.</li>
      <li>Taunt craters now dig downward with deterministic sparing instead of scraping a flat square.</li>
      <li>Healing and Morph were sped up to <code>2x</code>, with Healing's projectile leaving slightly later for a cleaner visual beat.</li>
      <li>Book of Doom now explodes for <strong>50 AoE damage in a 5-block radius</strong> and uses a custom yellow/orange fire burst VFX.</li>
      <li>Flame got its own faster cast timing and projectile delay.</li>
      <li>Teleport went through a long fit pass, ending at <strong>0.1.193</strong> with the exact Taunt-style cast behavior plus a delayed <code>0.5s</code> blink window.</li>
    </ul>
  </section>
</div>

## If you are updating a live server from `0.1.147`

<div class="card-grid card-grid--steps">
  <div class="card" data-reveal>
    <span class="step-tag">1</span>
    <h3>Expect the Arcane bench to look different</h3>
    <p>Axo Tales recipes are now split into dedicated Spellblades and Armor tabs instead of hiding in a single generic lane.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">2</span>
    <h3>World resource routes changed</h3>
    <p>Arcane Crystals are no longer a random clutter pass. They use deterministic placement limits, so hunt them like routeable resource nodes.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">3</span>
    <h3>Kudu content matters now</h3>
    <p>Rune Knights are worth farming, and Adepts are now part companion system, part moving loot ecosystem once bonded.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">4</span>
    <h3>Spell timing is intentionally different</h3>
    <p>Doom, Flame, Teleport, Healing, Morph, and Taunt all have their own tuned release windows now. Old muscle memory will be close, not exact.</p>
  </div>
</div>

## Pages to read next

- [Start Here](getting-started.md) for install, config, and log paths.
- [Spellbooks](items/spellbooks.md) for the post-polish cast behavior.
- [NPCs and Encounters](npcs.md) for Rune Knight and Adept loops.
- [Configuration](config.md) for the shipped defaults that matter most.
