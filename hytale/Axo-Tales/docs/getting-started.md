# Start Here

<div class="page-header" data-reveal>
  <div markdown="1">
    <span class="hero-kicker">Install and first boot</span>

    # The current Axo Tales line targets Hytale `2026.03.26-89796e57b`

    Keep that exact game release in mind when you update the mod. The build, the packaged manifest, and the docs are all aligned to that version.
  </div>
</div>

## Install / update path

<div class="card-grid card-grid--steps">
  <div class="card" data-reveal>
    <span class="step-tag">1</span>
    <h3>Build or download the jar</h3>
    <p>Use the latest <code>AxoTales-&lt;version&gt;.jar</code> from this repo line.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">2</span>
    <h3>Install to the shared mods folder</h3>
    <p>Copy it to <code>C:\Users\&lt;you&gt;\AppData\Roaming\Hytale\UserData\Mods</code>.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">3</span>
    <h3>Keep only one Axo Tales jar</h3>
    <p>Delete older <code>AxoTales-*.jar</code> builds so the client does not load the wrong version.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">4</span>
    <h3>Restart the game or server</h3>
    <p>If Windows says the old jar is in use, the world is still running. Close it first, then retry.</p>
  </div>
</div>

## What gets created on first run

<div class="card-grid">
  <div class="card" data-reveal>
    <h3><code>server-config.json</code></h3>
    <p>This is your live tuning surface for spell costs, worldgen density, Kudu spawn rates, loot, and movement-block behavior.</p>
  </div>
  <div class="card" data-reveal>
    <h3><code>spellbooks-debug.log</code></h3>
    <p>A persistent debug log under the plugin data directory. Use it when a cast, input path, stat gate, or movement block feels wrong.</p>
  </div>
  <div class="card" data-reveal>
    <h3>Your live config can differ from shipped defaults</h3>
    <p>The repo also contains a packaged default config in <code>src/main/resources/server-config.json</code>. Existing worlds keep their own edited runtime values.</p>
  </div>
</div>

## Crafting stations

<div class="card-grid">
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="assets/images/items/ancient_sword.png" alt="Ancient Sword icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcanist's Workbench</h3>
      <p>Axo Tales now appends two dedicated tabs: <strong>Spellblades</strong> for books and the sword, and <strong>Armor</strong> for the Sa'r set plus Kudu Boots.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="assets/images/potions/empty_bottle.png" alt="Empty potion bottle icon">
    </div>
    <div class="media-card__copy">
      <h3>Alchemybench</h3>
      <p>Every Axo Tales potion recipe starts from Axo's Empty Potion Bottle, then branches into movement, strength, invisibility, or throwable curse options.</p>
    </div>
  </div>
</div>

## Fast orientation for new players

- [What's New Since 0.1.147](whats-new.md) if you are returning to the mod after older releases.
- [Crafting Flow](crafting.md) if you want the actual resource loop from ore to spellbooks.
- [Blocks & Ores](blocks-ores.md) if you need to know where Arcane Matter, Crystals, Cloud Blocks, and Bounce Blocks enter progression.
- [Troubleshooting](troubleshooting.md) if installs, configs, or spell timing feel off.
