# Start Here

<div class="page-header">
  <p class="eyebrow">Install and first boot</p>
  <p class="lead-text">Use this page if you are setting up Axo Tales from scratch or moving a server onto the current build. The packaged jar, manifest, and guidebook all target Hytale <code>0.7.6</code>.</p>
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

## Your first visit

A centered **Axo's Tale** page introduces the story and your Silver Book. Close it with **Close** or **Escape**. It appears once for each player in each save, across that save's dimensions. Existing Silver Book claims remain intact, so the tale never grants a duplicate. If your inventory is full, make one slot free; the book and tale wait for space.

## First files worth knowing

<div class="card-grid">
  <div class="card" data-reveal>
    <h3><code>server-config.json</code></h3>
    <p>Your live tuning surface for spell costs, worldgen density, creature spawn rates, loot, and movement-block behavior.</p>
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

## Your starter book

On your first spawn with this version, you receive **Axo's Silver Book**. Right-click to fire a gray bolt for 10 base damage without spending mana. Each player gets one copy per save/server, shared across dimensions. A full inventory leaves the reward pending until a slot is free.

## Where crafting starts

<div class="card-grid">
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/ancient_sword.png" alt="Ancient Sword icon">
    </div>
    <div class="media-card__copy">
      <h3>Arcanist's Workbench</h3>
      <p>Axo Tales uses two dedicated tabs here: <strong>Spellblades</strong> for books, Arcane Sword and the Ancient Sword, and <strong>Armor</strong> for Sa'r and Ancient armor.</p>
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

- Try the Silver Book you received on spawn, then collect mana gear for advanced books.
- Mine or spawn enough Arcane Matter and Arcane Crystal Shards to unlock your first spellbook or armor piece.
- Craft the [Arcane Wand and Spellbook Pouch](items/arcane-equipment.md) when you want one focus for your collection.
- Open the Arcanist's Workbench and choose a lane: mana gear first, or one utility spellbook first.
- Read [Crafting Flow](crafting.md) if you want the cleanest progression order.
- Read [Troubleshooting](troubleshooting.md) early if testers keep seeing the wrong jar or the wrong live config.
