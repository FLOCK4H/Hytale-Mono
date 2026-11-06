# Helpful Souls

Villa, Fossor and Cruor are spectral workers with distinct tools and actions. Fossor carries an upright pick and uses a forward mining swing. Villa braces its satchel while delivering cargo.

Villa, Fossor and Cruor are immortal ghost companions. Their ownership, carried items, mining progress and upgrades are saved with the world.

You may have **three active souls of each type: nine total per player**, including unloaded souls and other worlds on the same server. Returning one frees that type's slot. A fourth summon consumes nothing. Existing excess souls return as saved items when loaded, keeping their cargo, progress and upgrades.

Cruors wander nearby and follow your movement within **20 blocks** out of combat. Turning the camera does not make them circle around you. Out of combat, **crouch to make them wait**, then approach to inspect or recall them. They have no physical pushing. If another soul crosses a firing lane, Cruor repositions before casting.

Souls survive logout. Their existing work areas stay loaded and active while the world/server is running, so Villa can deliver items and Fossor can keep mining without their owner online. Closing a local world or stopping the server stops simulation; there is no production catch-up for shutdown time. Souls pass through terrain, and Fossor's private work block can overlap real blocks without mining or replacing them.


## Crafting

Craft at **Workbench → Tools**, five seconds per soul. Every recipe uses **one Evil Core and three Azure Kelps**, plus its special ingredient:

| Soul | Special ingredient | Purpose |
| --- | --- | --- |
| Villa Soul | 1 Gold Bar | Collects dropped stacks and delivers them to chests |
| Fossor Soul | 1 Iron Pickaxe | Conjures and slowly mines renewable resources |
| Cruor Soul | 1 Ruby | Follows and protects its owner |

Hold the crafted soul and press **RMB** to summon it into clear space. Villa and Fossor appear in front; Cruor takes a rear guard position. **Interact** with it for status. Hit your soul with **LMB using an empty hand or melee weapon** to return it as a dropped item, even when your inventory is full. It keeps its cargo, upgrades and unfinished mining cycle when summoned again. **Empty hand + Crouch + Interact** also recalls it directly into your inventory if there is room. Another player cannot feed, return or claim your soul. A soul spawned directly with Creative Tools can be claimed by interacting with it or returning it with a melee hit.


## Villa Soul

Villa works within **50 blocks of the position where you summon it**. It floats to eligible dropped items, collects whole stacks into a nine-slot satchel, and flies to the nearest loaded chest that accepts the carried item. It can merge into an existing stack or use an empty slot. Native item metadata and chest filters are preserved.

If a chest fills during delivery, Villa keeps the remainder and looks for another chest. If none can accept its cargo, it waits with everything saved. When no chest can accept a ground item, that item stays available on the ground. Place a chest near its station to begin. Recall and resummon Villa to move its work area.

The 50-block search is a sphere around its station and includes loaded chests in that area. Villa does not force terrain to load, take items out of containers, or bypass custom pickup interactions.

## Fossor Soul

Fossor stays at its station, casts a conjuring spell, then swings its spectral pick at a private floating block. It drops **one resource every 60 seconds of active mining**. The block is a protected conjured object: players cannot mine it early, and it does not replace terrain. Its work prop occupies the ghost realm and can share space with terrain. Unloaded areas pause production until loaded again.

One mined block produces one raw ore, Stone, Dirt or Arcane Matter item. Villa can collect these drops. Unloaded souls do not accumulate offline production.

| Resource | Initial chance |
| --- | --- |
| Adamantite Ore | 1% |
| Mithril Ore | 1% |
| Cobalt Ore | 4% |
| Thorium Ore | 4% |
| Gold Ore | 5% |
| Silver Ore | 6% |
| Iron Ore | 14% |
| Copper Ore | 18% |
| Stone | 22% |
| Dirt | 15% |
| Arcane Matter | **10% at every upgrade level** |

Hold an **Arcane Crystal or Arcane Crystal Shard** and press **RMB** on your Fossor. Use **Crouch + RMB** to quickly feed the held stack, stopping at the 200-feed cap and leaving unused crystals in your hand. Each crystal consumed permanently adds **0.1 percentage points to each of Adamantite, Mithril, Cobalt and Thorium**. For example, after ten feeds, their chances become 2%, 2%, 5% and 5%. Arcane Matter keeps a fixed **10% chance per completed cycle**, so it remains a renewable resource at every upgrade level. Other non-premium resources decrease proportionally so the table always totals 100%.

The limit is **200 feeds**: 21% each for Adamantite and Mithril, 24% each for Cobalt and Thorium, with the remaining **10% producing Arcane Matter**. At this maximum, other non-premium resources no longer roll. Further feeding consumes nothing. Existing blocks keep their selected resource; upgrades apply when the next block is conjured.

## Cruor Soul

Cruor floats beside its owner and uses a crimson projectile against entities that damage the owner or take accepted damage from the owner, including player-shot spells. Its bolts deal **10 base damage every 1.5 seconds**, subject to the target's normal defenses and native damage rules.

It needs line of sight and can shoot within 30 blocks. It stops pursuing targets more than 40 blocks from the owner, invalid or dead targets, and fights that have been inactive for 30 seconds. It catches up when the owner moves far away in the same world. Its bolts cannot damage its owner or any helpful soul. Recall it before moving it between worlds.

During combat, Cruor faces its enemy and approaches a firing position about 5 blocks away. It holds that position until the enemy moves too close or too far away, instead of switching back toward its owner between shots.
