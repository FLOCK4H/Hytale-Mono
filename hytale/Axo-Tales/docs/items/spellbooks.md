# Spellbooks

<div class="page-header">
  <p class="eyebrow">Book lineup</p>
  <p class="lead-text">Most Axo Tales books still cast on <strong>RMB</strong>, but the current lineup is much broader than the old early-book phase. There are now dedicated support, utility, mining, summon, burst, terrain-control, and lantern roles.</p>
</div>

<div class="accent-card" data-reveal>
  <h3>Global rules</h3>
  <p>Spellbooks are crafted in the Arcanist's Workbench <strong>Spellblades</strong> tab. Most of the real gameplay logic is packet-driven on the server, while the item assets mainly carry visuals, sounds, and cast animation feel. Mining also uses a real hold-to-charge flow with shape cycling on LMB.</p>
</div>

## Support and utility books

<div class="card-grid">
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/heal_book.png" alt="Healing Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Healing Book</h3>
      <p><strong>LMB</strong> fires a Healing Bolt that fully heals what it hits. <strong>RMB</strong> fully heals you. Default mana cost is 25.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 4 Azure Fruit.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/teleport_book.png" alt="Teleport Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Teleport Book</h3>
      <p>Blinks you to the solid block you are targeting. Current defaults are 100-block range, 10 mana, and a 0.5-second cast delay.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 2 Blue Crystal Shards.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/mining_book.png" alt="Mining Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Mining Book</h3>
      <p><strong>LMB</strong> cycles shapes between <code>1x1</code>, <code>3x3</code>, and <code>Cross</code>. <strong>RMB</strong> charges a tunnel spell that scales from 1 block up to 10 blocks by hold time.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 10 Volcanic Cobble.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/immunity_book.png" alt="Immunity Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Immunity Book</h3>
      <p>Applies a short 3-second damage-immunity window and the vanilla Immune status effect. Default mana cost is 15.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 1 Cobalt Shield.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/light_book.png" alt="Light Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Light Book</h3>
      <p>Launches a harmless moving lantern that slows into a warm cruise, lasts up to 2 minutes, and stops after 100 blocks or on contact. Default mana cost is 15.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 6 Cyan Crystal Shards.</p>
    </div>
  </div>
</div>

## Summon and control books

<div class="card-grid">
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/horde_book.png" alt="Horde Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Horde Book</h3>
      <p>Summons three short-lived Outlander Brutes that fight for you, retarget attackers, and persist for 30 seconds by default. Mana cost is 25.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 3 Feran Candles.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/morph_book.png" alt="Morph Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Morph Book</h3>
      <p><strong>RMB</strong> steals the struck target's model. <strong>LMB</strong> restores your baseline form. Default mana cost is 25.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 2 Dark Feathers.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/frost_book.png" alt="Frost Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Frost Book</h3>
      <p>Launches a frost shard that slows enemies and converts hit terrain to Snow Brick. Default mana cost is 20.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 20 Ice Essence.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/flame_book.png" alt="Flame Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Flame Book</h3>
      <p>Throws a faster fire bolt that burns enemies and converts terrain to Volcanic Rock. Current defaults are 20 mana and a 0.2-second projectile delay.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 20 Fire Essence.</p>
    </div>
  </div>
</div>

## Impact and burst books

<div class="card-grid">
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/taunt_book.png" alt="Taunt Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Taunt Book</h3>
      <p>Launches you upward, grants a short fall-immunity window, then slams on landing for area damage. Mid-air recasts stack by 1.5x up to 2000 damage, deepen the crater, and now clear breakable surface clutter as well as the ground itself.</p>
      <p><strong>Shipped defaults</strong>: 25 mana, 10-block launch, 6 seconds of fall immunity, 40 base slam damage, 7-block damage radius.</p>
    </div>
  </div>
  <div class="card media-card" data-reveal>
    <div class="media-card__visual media-card__visual--single">
      <img src="../../assets/images/items/doom_book.png" alt="Doom Book icon">
    </div>
    <div class="media-card__copy">
      <h3>Book of Doom</h3>
      <p>Fires a Doom Ball that explodes for 50 damage in a 5-block radius and reads as the heaviest burst spell in the current lineup. Default mana cost is 25.</p>
      <p><strong>Craft</strong>: 2 Arcane Crystal Shards, 2 Arcane Matter, 4 Voidhearts.</p>
    </div>
  </div>
</div>

## Quick feel notes

- Healing, Morph, and Mining all use LMB for an extra function, so they play differently from the standard pure-RMB books.
- Light Book is utility-only. It is there to light space and travel with you, not to deal damage.
- Teleport and Taunt both care a lot about cast timing, so if they feel "off," grab `spellbooks-debug.log` before retuning numbers.
