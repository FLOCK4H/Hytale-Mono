# Axo's Astral Satchel

A small Epic bag with expandable storage and **no fixed slot limit**.

![Axo's Astral Satchel](../assets/images/items/astral_satchel.png)

Hold it and press **RMB**. The window shows **three rows** of stored items. Type an item name to search, choose a category to narrow the view, or drag the page control to browse. Previous/Next buttons work too. **Filters change the view only**; any eligible item can still be deposited.

- **LMB** an item to transfer one stack.
- **RMB** an item to transfer one unit.
- Use **Storage**, **Backpack** and **Hotbar** tabs to choose your carried inventory. Additional pages expose larger inventories.
- A full carried inventory leaves remaining items safely in the satchel.
- Close with **Close**, **Space** outside the search field, **Tab**, or **Escape**. Spaces work normally while typing a search. Moving/dropping/swapping the physical bag closes its stale view.

The three icons beside the stored-item count work from left to right:

| Control | Action |
| --- | --- |
| **Take Visible** — down arrow into a tray | Take all quantities shown on the current bag page, up to 27 entries. Items fill available Storage, Backpack and Hotbar space in normal stacks; overflow stays in the satchel. |
| **Store Visible** — up arrow above a tray | Store eligible items from the current inventory page, up to 36 slots, in the selected Storage/Backpack/Hotbar tab. |
| **Quickstack** — stacked layers | Store items from that same visible inventory page only when their item type is already stored somewhere in the satchel. Different durability, DNA or other metadata stays separate. |

Hover an icon to read its action. These controls affect the current page only. Search and category determine which bag entries **Take Visible** can take; they do not restrict **Store Visible** or **Quickstack**. Quickstack can match a stored type even when the bag filter hides it. Browse other pages or switch inventory tabs to act on those items. Bag entries remain sorted by name.

If a transfer stops with an error, completed transfers remain saved and the remaining items stay at their source. The status line reports what happened.

Different satchels have separate contents. Dropping, trading or dying with the bag retains contents through its identity, including on ordinary server restarts. It cannot contain another Astral Satchel, including inside a nested container. Stored DNA, durability and other native item metadata are preserved. Only one player can open the same satchel identity at a time.

## Using stored ingredients

Keep the satchel in your carried inventory and use a workbench normally, including the Arcanist's, Furniture and Goblin workbenches. Recipes can use ingredients directly from the bag, together with your loose inventory and any nearby chests supported by that workbench. You do not need to open the satchel or take its ingredients out first. All stored pages count, regardless of the bag's search or category.

Several carried satchels can supply one recipe. Duplicate copies of the same satchel identity count once. Crafting spends the required quantities and preserves the metadata of the remaining items. Keep the bag with you while queued crafts run: a bag you drop or stop carrying cannot supply a later craft. A satchel inside another container does not supply ingredients.

## Crafting

**Arcanist's Workbench → Spellblades**, 8 seconds:

| Material | Quantity |
| --- | ---: |
| Medium Leather | 12 |
| Spider Silk | 8 |
| Arcane Matter | 4 |
| Aequalis Bars | 2 |
| Evil Core | 1 |

## Server storage and backups

Contents save in **AstralSatchels/** inside the AxoTales plugin data directory. Include this folder in server/world backups. The carried item contains only its ID, so inventory packets stay small as storage grows. Moving a bag to an independent server also requires its saved file. Missing/damaged data refuses opening rather than replacing contents with an empty bag; the previous generation remains for an administrator to inspect.

There is no designed slot cap, but the server's actual disk and memory remain finite.

## Carrying the satchel

The satchel hangs from its handle in your right hand. The other hand stays free.
