# Troubleshooting

<div class="page-header">
  <p class="eyebrow">Fast fixes</p>
  <p class="lead-text">Most Axo Tales problems still collapse to a small set of root causes: the wrong jar, the wrong runtime config, a misunderstood workbench lane, or missing debug logs when someone says a spell "just feels off."</p>
</div>

## Multiple versions are loading

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>The game behaves like an older build, or different testers swear they are on the same mod version but see different behavior.</p>
  <h3>Fix</h3>
  <p>Remove old <code>AxoTales-*.jar</code> files from <code>C:\Users\&lt;you&gt;\AppData\Roaming\Hytale\UserData\Mods</code>. If Windows refuses to delete one, the world is still running and holding the file open.</p>
</div>

## The live config does not match the guidebook

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>Arcane Crystal density, Kudu spawn timing, or spell costs feel different from the docs.</p>
  <h3>Fix</h3>
  <p>The guidebook documents <code>src/main/resources/server-config.json</code>. Your live world may already own an older or hand-edited runtime <code>server-config.json</code>. Compare the two before changing code.</p>
</div>

## Spell behavior feels wrong or inconsistent

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>Teleport timing feels late, a spell refuses to cast, Light Book never settles, or mana gating looks wrong.</p>
  <h3>Fix</h3>
  <p>Grab the persistent <code>spellbooks-debug.log</code> from the plugin data directory. Axo Tales logs item detection, stat snapshots, event routing, and final allow-or-deny decisions there.</p>
</div>

## Bench or recipe confusion after updating

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>Players think recipes vanished because they are looking in the old generic Arcane bench path.</p>
  <h3>Fix</h3>
  <p>Axo Tales recipes now live in the runtime-injected <strong>Spellblades</strong> and <strong>Armor</strong> tabs on the Arcanist's Workbench. Point players there first.</p>
</div>

## Invalid config edits

<div class="card" data-reveal>
  <h3>Symptom</h3>
  <p>Manual edits leave weird negative numbers, impossible values, or malformed JSON.</p>
  <h3>Fix</h3>
  <p><code>server-config.json</code> must stay valid JSON. Axo Tales sanitizes some bad numeric values toward safe defaults, but malformed JSON still has to be corrected manually.</p>
</div>
