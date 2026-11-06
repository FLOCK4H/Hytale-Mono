# Mod compatibility

AxoTales uses Hytale **0.7.6**. The installed
asset packs listed below have **no conflicting asset IDs, Common asset paths
or translation keys** with AxoTales. Matching filenames in different folders were
checked too. No vanilla textures or models are replaced by the AxoTales JAR.

| Installed pack | Version |
| --- | --- |
| Arcane Power | 3.2.1 |
| BetterMap | 1.3.10 |
| Brightness Tweaks | 0.4.14 |
| CarryEverything | 0.1.10 |
| Extended Backpacks | 4.3.0 |
| EyeSpy | 2026.9.1 |
| LuckyMining | 1.0.8 |
| Overstacked | 2026.2.20 |
| Perfect Parries | 0.9.8 |
| Reforged Weapons | 4.5.1 |
| Wan's Wonder Weapon | 1.1.1 |
| YUNG's HyDungeons | installed ZIP |

This is an asset and offline behavior check. Mods can also change Java systems,
packets or interactions at runtime; a clean asset inventory does not prove every
combination will behave identically in live gameplay.

## Shared systems

Axo gear composes its owned modifiers with native Mana, health and stamina;
removing gear preserves foreign modifiers and the current resource amount.
Native inventory receipts preserve other mods' stack metadata in the Astral
Satchel, crafting and soul cargo paths. A separate private mana system needs its
own integration.

With `workarounds.disableWorldMap: false` (the default), AxoTales preserves the
world's map provider and compass settings. Setting this workaround to `true` explicitly
disables map/compass updates and may cause “Unknown location.” If you previously
used that workaround, restore the desired map settings through your world or
map mod's own controls.

## Intentional vanilla server integrations

These are server definitions, separate from artwork. They are checked against
the pinned native package and recorded in the development override policy.

| Integration | Definitions | Possible overlap |
| --- | ---: | --- |
| Melee reach selectors | 157 | A weapon mod replacing the same selectors may affect the skill-tree reach bonus. Native timing, damage and block selection are otherwise retained. |
| Cave placement lists | 28 | Another ore/worldgen pack editing the same native cave lists needs a merged list. Axo adds private ore placements and retains the original entries. |
| Zone 1 spawn tables | 3 | A creature pack replacing these tables needs the combined spawn entries and weights. |
| Azure Kelp | 1 | Axo changes its RMB route for Whisperion feeding and preserves every other current native field. |

Shared `server.lang` files merge by translation key. Their common filename alone
is not a conflict; different values under the same key are.

For a new mod pack, run the development compatibility check before deploying.
It checks the actual JAR, compares asset IDs across folders, rejects unreviewed
native overrides and reports collisions against the installed mod files. Keep
world backups when changing a live mod pack; load order cannot combine two
different complete replacements of the same definition automatically.
