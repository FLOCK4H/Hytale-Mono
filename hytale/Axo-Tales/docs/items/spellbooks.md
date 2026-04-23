# Spellbooks

<div class="page-header" data-reveal>
  <div markdown="1">
    <span class="hero-kicker">Spellbook identity pass</span>

    # The book lineup now has tuned release windows, not one generic cast

    Most Axo Tales books still cast on <strong>RMB</strong>, but the current line adds more personality to every spell: Doom detonates, Taunt stacks, Flame throws faster, Healing and Morph snap harder, and Teleport finally blinks on a clean delayed beat.
  </div>
</div>

<div class="card accent-card" data-reveal>
  <h3>Global rules</h3>
  <p>Spellbooks are crafted in the Arcanist's Workbench <strong>Spellblades</strong> tab. Most casts are packet-driven server logic with cosmetic item interactions on the asset side, which is why the timing and mana gates feel much tighter than the old early builds.</p>
</div>

## Support and utility

<div class="card-grid">
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/heal_book.png" alt="Healing Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Healing Book</h3>
      <p><strong>LMB</strong>: fires a Healing Bolt that fully heals the target hit. <strong>RMB</strong>: full self-heal. Default mana cost is 25, with a 0.15s projectile delay on the primary shot.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 4 Azure Fruit.</p>
    </div>
  </div>
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/teleport_book.png" alt="Teleport Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Teleport Book</h3>
      <p>Blinks you to the solid block you are targeting. Current defaults: 100-block range, 10 mana, and a 0.5s cast delay so the blink lands after the Taunt-mirrored charge animation settles.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 2 Blue Crystal Shards.</p>
    </div>
  </div>
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/mining_book.png" alt="Mining Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Mining Book</h3>
      <p>Breaks a face-oriented 3x3 patch around the targeted block by default, spawns block drops, reaches 12 blocks, and costs 5 mana.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 10 Volcanic Cobble.</p>
    </div>
  </div>
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/immunity_book.png" alt="Immunity Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Immunity Book</h3>
      <p>Applies a short 3-second damage immunity window and the vanilla Immune effect. Default mana cost is 15.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 1 Cobalt Shield.</p>
    </div>
  </div>
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/horde_book.png" alt="Horde Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Horde Book</h3>
      <p>Summons three short-lived Outlander Brutes that fight for you, retarget attackers, and persist for 30 seconds by default. Mana cost is 25.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 3 Feran Candles.</p>
    </div>
  </div>
</div>

## Control and terrain play

<div class="card-grid">
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/morph_book.png" alt="Morph Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Morph Book</h3>
      <p><strong>RMB</strong>: morphs you into the struck target's model. <strong>LMB</strong>: reset back to baseline form. The cast runs at 2x speed in the current fit pass. Default mana cost is 25.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 2 Dark Feathers.</p>
    </div>
  </div>
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/frost_book.png" alt="Frost Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Frost Book</h3>
      <p>Launches a frost shard that slows enemies and converts hit terrain to Snow Brick. Default mana cost is 20, and Rune Knights can drop it.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 20 Ice Essence.</p>
    </div>
  </div>
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/flame_book.png" alt="Flame Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Flame Book</h3>
      <p>Throws a faster fire bolt that burns enemies and converts terrain to Volcanic Rock. Current defaults: 20 mana and a 0.2s dedicated projectile delay.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 20 Fire Essence.</p>
    </div>
  </div>
</div>

## Burst and impact books

<div class="card-grid">
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/taunt_book.png" alt="Taunt Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Taunt Book</h3>
      <p>Launches you upward, grants a short fall-immunity window, then slams for area damage on landing. Mid-air recasts stack by 1.5x up to 2000 damage and deepen the crater.</p>
      <p><strong>Shipped defaults</strong>: 25 mana, 10-block launch, 6 seconds of fall immunity, 40 base slam damage, 7-block damage radius.</p>
    </div>
  </div>
  <div class="card media-card tilt-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../assets/images/items/doom_book.png" alt="Doom Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Book of Doom</h3>
      <p>The big post-0.1.183 upgrade. The Doom Ball now explodes for 50 damage in a 5-block radius and uses a custom yellow/orange fire burst VFX. Default mana cost is 25.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 4 Voidhearts.</p>
    </div>
  </div>
</div>

## Spell feel notes worth knowing

- Healing and Morph now animate at 2x speed, which makes both books feel much snappier than the older cast loop.
- Teleport deliberately waits longer now. The client cast and server blink no longer race each other.
- Doom and Flame both have their own projectile delay keys, so server owners can tune those two without disturbing every other book.
- Every Axo Tales spellbook now points at its own cast id inside `AxoTales_Spellbook.json`, even if the gameplay logic lives in plugin code.
