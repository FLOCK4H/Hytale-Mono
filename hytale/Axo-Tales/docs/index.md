<div class="hero-panel" data-reveal>
  <div class="hero-copy" markdown="1">
    <span class="hero-kicker">Axo Tales Guidebook</span>

    # Axo Tales has moved far beyond `0.1.147`

    The current line locks to Hytale `2026.03.26-89796e57b` and turns the old spellbook prototype into a full progression loop: deterministic Arcane Crystal worldgen, Kudu companions, two Arcanist tabs, movement blocks, reworked Sa'r armor, and a much sharper combat and animation pass.

    [See Everything That Changed](whats-new.md){ .button-link .button-link--primary }
    [Jump Into Crafting](crafting.md){ .button-link }
  </div>
  <div class="hero-art">
    <div class="art-stack tilt-card">
      <img src="assets/images/items/doom_book.png" alt="Book of Doom icon" class="art-stack__main">
      <img src="assets/images/blocks/cloud_block.png" alt="Cloud Block icon" class="art-stack__chip art-stack__chip--cloud">
      <img src="assets/images/items/ancient_sword.png" alt="Ancient Sword icon" class="art-stack__chip art-stack__chip--sword">
      <img src="assets/images/blocks/arcane_crystal.png" alt="Arcane Crystal icon" class="art-stack__chip art-stack__chip--crystal">
    </div>
  </div>
</div>

<div class="stat-strip" data-reveal>
  <div class="stat-card">
    <span class="stat-card__value">46</span>
    <span class="stat-card__label">iterations since 0.1.147</span>
  </div>
  <div class="stat-card">
    <span class="stat-card__value">10</span>
    <span class="stat-card__label">spellbooks with tuned cast identity</span>
  </div>
  <div class="stat-card">
    <span class="stat-card__value">2</span>
    <span class="stat-card__label">movement blocks: Cloud + Bounce</span>
  </div>
  <div class="stat-card">
    <span class="stat-card__value">2</span>
    <span class="stat-card__label">Kudu encounter loops to farm and bond</span>
  </div>
</div>

## The big post-`0.1.147` shifts

<div class="card-grid card-grid--feature">
  <div class="card tilt-card texture-panel texture-panel--crystal" data-reveal>
    <h3>Release and worldgen reset</h3>
    <p>The mod is now pinned to Hytale <code>2026.03.26-89796e57b</code>, publishes that exact server version in the jar manifest, and ships deterministic Arcane Crystal placement instead of noisy per-chunk scatter.</p>
    <span class="chip">0.1.148 to 0.1.154</span>
  </div>
  <div class="card tilt-card" data-reveal>
    <h3>Kudu content became a real progression lane</h3>
    <p>Rune Knights now drop guaranteed core resources, Adepts can bond off Arcane Crystal drops, and the whole companion loop gained particles, persistence, combat fixes, and cleaner spawn defaults.</p>
    <span class="chip">0.1.155 to 0.1.170</span>
  </div>
  <div class="card tilt-card texture-panel texture-panel--cloud" data-reveal>
    <h3>Movement tech entered the build meta</h3>
    <p>Cloud Blocks and Bounce Blocks add vertical routing, combo chains, and bonus loot drops, while the Arcanist's Workbench now exposes separate Axo Tales tabs for Spellblades and Armor.</p>
    <span class="chip">0.1.171 to 0.1.178</span>
  </div>
  <div class="card tilt-card" data-reveal>
    <h3>Combat polish finally landed</h3>
    <p>Sa'r armor now has unique identity, Taunt stacks into crater slams, Doom became a real 50-damage AoE explosion, and Teleport was retimed until the blink felt intentional.</p>
    <span class="chip">0.1.179 to 0.1.193</span>
  </div>
</div>

## Pick your lane

<div class="card-grid">
  <a class="card media-card tilt-card" href="items/spellbooks.md" data-reveal>
    <div class="media-card__visual">
      <img src="assets/images/items/heal_book.png" alt="Healing Book icon">
      <img src="assets/images/items/doom_book.png" alt="Doom Book icon">
      <img src="assets/images/items/taunt_book.png" alt="Taunt Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Spellbooks</h3>
      <p>Support, blink, AoE, mining, summons, elemental terrain control, and the full cast-timing pass.</p>
    </div>
  </a>
  <a class="card media-card tilt-card" href="items/gear.md" data-reveal>
    <div class="media-card__visual">
      <img src="assets/images/items/sar_diadem.png" alt="Sa'r Diadem icon">
      <img src="assets/images/items/sar_hands.png" alt="Sa'r Warfists icon">
      <img src="assets/images/items/ancient_sword.png" alt="Ancient Sword icon">
    </div>
    <div class="media-card__copy">
      <h3>Gear and spellblade loop</h3>
      <p>Sa'r set, Kudu Boots water-walk, invisibility tech, and the Ancient Sword's projectile stab.</p>
    </div>
  </a>
  <a class="card media-card tilt-card" href="blocks-ores.md" data-reveal>
    <div class="media-card__visual">
      <img src="assets/images/blocks/arcane_crystal.png" alt="Arcane Crystal icon">
      <img src="assets/images/blocks/cloud_block.png" alt="Cloud Block icon">
      <img src="assets/images/blocks/bounce_block.png" alt="Bounce Block icon">
    </div>
    <div class="media-card__copy">
      <h3>World and movement blocks</h3>
      <p>Arcane Matter, deterministic crystals, creative blocks, and the Cloud/Bounce movement pair.</p>
    </div>
  </a>
  <a class="card media-card tilt-card" href="npcs.md" data-reveal>
    <div class="media-card__visual">
      <img src="assets/images/blocks/arcane_crystal_shard.png" alt="Arcane Crystal Shard icon">
      <img src="assets/images/items/frost_book.png" alt="Frost Book icon">
      <img src="assets/images/items/kudu_boots.png" alt="Kudu Boots icon">
    </div>
    <div class="media-card__copy">
      <h3>NPCs and encounters</h3>
      <p>Rune Knight loot routes, Adept bonding, persistent companions, and the current spawn tuning.</p>
    </div>
  </a>
  <a class="card media-card tilt-card" href="config.md" data-reveal>
    <div class="media-card__visual">
      <img src="assets/images/items/teleport_book.png" alt="Teleport Book icon">
      <img src="assets/images/items/flame_book.png" alt="Flame Book icon">
      <img src="assets/images/potions/strength_potion.png" alt="Strength Potion icon">
    </div>
    <div class="media-card__copy">
      <h3>Server tuning</h3>
      <p>Shipped defaults for spell costs, worldgen, Kudu spawn density, Cloud/Bounce behavior, and weapon timing.</p>
    </div>
  </a>
</div>

## Quick install path

<div class="card-grid card-grid--steps">
  <div class="card" data-reveal>
    <span class="step-tag">1</span>
    <h3>Build or grab the jar</h3>
    <p>Use the latest Axo Tales jar that targets Hytale <code>2026.03.26-89796e57b</code>.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">2</span>
    <h3>Drop it in the real mods folder</h3>
    <p>Install to <code>C:\Users\&lt;you&gt;\AppData\Roaming\Hytale\UserData\Mods</code> and keep only one Axo Tales jar there.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">3</span>
    <h3>Read the delta page first if you are upgrading</h3>
    <p>The release path from <code>0.1.147</code> to <code>0.1.193</code> changes crafting tabs, worldgen, combat feel, and movement blocks.</p>
  </div>
</div>
