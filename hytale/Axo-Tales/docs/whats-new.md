# Returning Players

<div class="page-header">
  <p class="eyebrow">Returning players</p>
  <p class="lead-text">If you are coming back after a long gap, start here once. Everyone else can treat this as optional background.</p>
</div>

## The short version

<div class="card-grid">
  <div class="card" data-reveal>
    <h3>The build is pinned to one verified Hytale release</h3>
    <p>The repo, jar manifest, and guidebook are all aligned to <code>2026.03.26-89796e57b</code>. Old "works on anything" assumptions are gone.</p>
  </div>
  <div class="card" data-reveal>
    <h3>The progression loop is broader</h3>
    <p>Arcane Crystals became deterministic resource nodes, Kudu content became worth farming, and the Arcanist's Workbench now exposes Axo Tales lanes instead of burying everything in one generic category.</p>
  </div>
  <div class="card" data-reveal>
    <h3>Spell feel changed for real</h3>
    <p>Healing, Morph, Flame, Doom, Teleport, Mining, Taunt, and Light all have more deliberate behavior now. Old muscle memory is still useful, but it is no longer exact.</p>
  </div>
  <div class="card" data-reveal>
    <h3>The mod now includes traversal tech</h3>
    <p>Cloud Block and Bounce Block turned extra drops into movement tools, not just collectibles or builder props.</p>
  </div>
</div>

## Release bands that matter

<div class="timeline-grid">
  <section class="timeline-card" data-reveal>
    <span class="chip">0.1.148 to 0.1.154</span>
    <h3>Release sync and Arcane Crystal reset</h3>
    <ul>
      <li>The project was pinned to Hytale <code>2026.03.26-89796e57b</code>, including the packaged <code>ServerVersion</code>.</li>
      <li>Arcane Crystal placement stopped being noisy chunk clutter and became deterministic, density-limited world routing.</li>
      <li>The repo also moved off the deprecated inventory wrapper APIs needed by older builds.</li>
    </ul>
  </section>

  <section class="timeline-card" data-reveal>
    <span class="chip">0.1.155 to 0.1.170</span>
    <h3>Kudu content became a real lane</h3>
    <ul>
      <li>Rune Knights now pay out guaranteed core materials, making them worth fighting.</li>
      <li>Kudu Adepts became bondable companions tied to dropped Arcane Crystals or Shards.</li>
      <li>Companion follow, retargeting, persistence, and loot were repeatedly tightened until the loop became dependable.</li>
    </ul>
  </section>

  <section class="timeline-card" data-reveal>
    <span class="chip">0.1.171 to 0.1.178</span>
    <h3>Movement blocks and workbench lanes landed</h3>
    <ul>
      <li>Cloud Block launched as a directional pass-through movement block, then gained better placement and chaining.</li>
      <li>Bounce Block joined as the cleaner upward partner.</li>
      <li>The Arcanist's Workbench now exposes Axo Tales <strong>Spellblades</strong> and <strong>Armor</strong> tabs instead of one muddy bucket.</li>
    </ul>
  </section>

  <section class="timeline-card" data-reveal>
    <span class="chip">0.1.179 to 0.1.193</span>
    <h3>Gear identity and spell polish</h3>
    <ul>
      <li>Sa'r gear stopped being "mana armor" and gained real specialties.</li>
      <li>Taunt started stacking into a wider, deeper crater slam.</li>
      <li>Doom became a real AoE blast, and Teleport went through a long alignment pass until the blink timing finally settled.</li>
    </ul>
  </section>

  <section class="timeline-card" data-reveal>
    <span class="chip">0.1.194 to 0.1.202</span>
    <h3>Mining, Light Book, and late fit fixes</h3>
    <ul>
      <li>Mining switched to a charged tunnel spell with per-player shape cycling.</li>
      <li>Light Book arrived as a moving lantern spell with a soft dynamic-light trail and no real damage role.</li>
      <li>Late fixes cleaned up Sa'r Boots stacking and a few risky rendering and water-related asset problems.</li>
    </ul>
  </section>
</div>

## What to verify on a live server after updating

<div class="card-grid card-grid--steps">
  <div class="card" data-reveal>
    <span class="step-tag">1</span>
    <h3>Check the real jar path first</h3>
    <p>Only load Axo Tales from <code>UserData\Mods</code>. Old copies are the easiest way to "update" into the wrong behavior.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">2</span>
    <h3>Compare the live runtime config with packaged defaults</h3>
    <p>Worldgen and spell timing might feel wrong because the world already owns an older <code>server-config.json</code>.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">3</span>
    <h3>Point players at the right workbench tabs</h3>
    <p>Recipes are now split across <strong>Spellblades</strong> and <strong>Armor</strong>. A lot of "missing recipe" reports are really navigation mistakes.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">4</span>
    <h3>Expect spell timing to feel different</h3>
    <p>Teleport, Mining, Taunt, Doom, Flame, Morph, Healing, and Light all behave more deliberately than the old early-book loop.</p>
  </div>
</div>
