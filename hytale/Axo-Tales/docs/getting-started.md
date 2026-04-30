# Start Here

<div class="page-header">
  <p class="eyebrow">Install and first boot</p>
  <p class="lead-text">Use this page if you are setting up Axo Tales from scratch or moving a server onto the current build. The packaged jar, manifest, and guidebook all target Hytale <code>2026.03.26-89796e57b</code>.</p>
</div>

## Install or update

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
    <h3>Keep only one Axo Tales jar there</h3>
    <p>Delete older <code>AxoTales-*.jar</code> files so the game does not load the wrong version.</p>
  </div>
  <div class="card" data-reveal>
    <span class="step-tag">4</span>
    <h3>Restart the world if the file is locked</h3>
    <p>If Windows says the jar is in use, the game or server still has the old build open.</p>
  </div>
</div>

## First files worth knowing

<div class="card-grid">
  <div class="card" data-reveal>
    <h3><code>server-config.json</code></h3>
    <p>Your live tuning surface for spell costs, worldgen density, Kudu spawn rates, loot, and movement-block behavior.</p>
  </div>
  <div class="card" data-reveal>
    <h3><code>spellbooks-debug.log</code></h3>
    <p>A persistent debug log under the plugin data directory. Use it for casts, stat gates, input routing, and movement-block problems.</p>
  </div>
  <div class="card" data-reveal>
    <h3>Packaged defaults can differ from your live runtime file</h3>
    <p>The guidebook documents <code>src/main/resources/server-config.json</code>. Existing worlds keep their own runtime copy once the mod has already been launched there.</p>
  </div>
</div>

## Where crafting starts

<div class="card-grid">
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/ancient_sword.png" alt="Ancient Sword icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcanist's Workbench</h3>
      <p>Axo Tales uses two dedicated tabs here: <strong>Spellblades</strong> for books and the Ancient Sword, and <strong>Armor</strong> for the Sa'r set plus Kudu Boots.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/potions/empty_bottle.png" alt="Empty potion bottle icon">
    </div>
    <div class="media-card__copy">
      <h3>Alchemybench</h3>
      <p>Every Axo Tales potion starts from Axo's Empty Potion Bottle, then branches into mobility, stealth, strength, or curse routes.</p>
    </div>
  </div>
</div>

## Good first-session route

- Mine or spawn enough Arcane Matter and Arcane Crystal Shards to unlock your first spellbook or armor piece.
- Open the Arcanist's Workbench and choose a lane: mana gear first, or one utility spellbook first.
- Read [Crafting Flow](crafting.md) if you want the cleanest progression order.
- Read [Troubleshooting](troubleshooting.md) early if testers keep seeing the wrong jar or the wrong live config.
