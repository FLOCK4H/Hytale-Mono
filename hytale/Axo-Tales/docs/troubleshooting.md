# Troubleshooting

<div class="page-header" data-reveal>
  <div markdown="1">
    <span class="hero-kicker">Common failures and fast fixes</span>

    # Most Axo Tales issues boil down to one of four things

    Wrong jar in the wrong folder, stale runtime config, input-timing confusion, or a missing debug log when you actually need one.
  </div>
</div>

## Multiple versions are loading

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>The game behaves like an older build, or different testers report different behavior from the same repo line.</p>
  <h3>Fix</h3>
  <p>Remove old <code>AxoTales-*.jar</code> files from <code>C:\Users\&lt;you&gt;\AppData\Roaming\Hytale\UserData\Mods</code>. If Windows refuses to delete one, the world is still running and holding the file open.</p>
</div>

## The live config does not match the guidebook

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>Arcane Crystal density, Kudu spawn timing, or spell costs feel different from the docs.</p>
  <h3>Fix</h3>
  <p>The guidebook documents the packaged defaults from <code>src/main/resources/server-config.json</code>. Your live server may already have an older or hand-edited runtime <code>server-config.json</code>. Compare the two before changing code.</p>
</div>

## Spell behavior feels wrong or inconsistent

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>Teleport timing feels late, a spell refuses to fire, or mana gating looks broken.</p>
  <h3>Fix</h3>
  <p>Grab the persistent <code>spellbooks-debug.log</code> from the plugin data directory. Axo Tales logs event routing, item detection, stat snapshots, and final allow or deny decisions there for spellbook and movement-block debugging.</p>
</div>

## Bench or recipe confusion after upgrading

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>Players say recipes are missing because they are looking in the old generic Arcane bench path.</p>
  <h3>Fix</h3>
  <p>Axo Tales recipes now live in the runtime-injected <strong>Spellblades</strong> and <strong>Armor</strong> tabs on the Arcanist's Workbench. Point players there first.</p>
</div>

## Invalid config values

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>Manual edits leave weird negative numbers or malformed JSON in the config.</p>
  <h3>Fix</h3>
  <p><code>server-config.json</code> must stay valid JSON. Axo Tales sanitizes broken numeric values back toward safe defaults where it can, but malformed JSON still needs to be corrected manually.</p>
</div>
