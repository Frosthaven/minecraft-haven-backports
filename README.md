# Haven Backports

Content from newer Minecraft versions, backported to 1.21.1 (NeoForge) for the Haven pack. Required on the server and
on every client.

Everything comes from Mojang's own files: textures, models, blockstates, recipes, loot tables, tags and block
properties are read from the newest snapshot's client jar (`tools/backport.py`), and block behaviour follows Mojang's
code in that jar (it ships unobfuscated). Nothing is redrawn or invented. Vanilla textures the new blocks reuse
(wool, concrete, copper, calcite...) are the same pixels in 1.21.1 and 26.4, so they come from the game itself. Where another mod in the pack already adds the
same block in its own art, it is merged into the backported one (Haven Consolidated carries out the merge), so there is
one block per concept and it looks the way Mojang made it.

## Content (26.4 snapshot 7)

| Set | Blocks and items |
|---|---|
| Calcite | Calcite Pillar, Stairs, Slab, Wall; Polished Calcite with Stairs, Slab, Wall; Polished Calcite Bricks with Stairs, Slab, Wall |
| Snow and ice | Snow Bricks with Stairs, Slab, Wall; Packed Ice Bricks with Stairs, Slab, Wall; Chiseled Packed Ice (new packed ice sounds) |
| Wool and concrete | Stairs and Slab for all 16 colors of each; wool ones re-dye with any dye and burn like wool |
| Copper | Copper Bars, Chain and Lantern in every oxidation stage, waxed or not (honeycomb, axe, rain); Copper Torch; Copper Nugget |
| Shelves | Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry, Bamboo, Crimson and Warped Shelf; plus one for each of the pack's 21 modded woods (see below). Pale Oak and Poplar come with their woods |
| Copper chest | Copper Chest in every oxidation stage, waxed or not; keeps its items when it ages, is waxed or scraped |
| Copper gear | Copper Sword, Shovel, Pickaxe, Axe, Hoe; Copper Helmet, Chestplate, Leggings, Boots (trims; copper on copper uses Mojang's darker copper) |
| Horse armor | Copper Horse Armor (chest loot, as 26.x), Netherite Horse Armor (smithing) |
| Spears | Wooden, Stone, Copper, Iron, Golden, Diamond and Netherite Spear; the Lunge enchantment (see below) |
| Recipes | Name Tag (paper + metal nugget), Saddle; stonecutter: cobblestone set from stone, every deepslate variant from deepslate |

Merged into the backported ones (Haven Consolidated): New Slab Variants' calcite, wool and concrete slabs; Chipped's calcite bricks, calcite pillar and packed ice bricks; Create's calcite pillar;
Create's and Create Deco's copper bars and Macaw's Lights' copper chain (into the waxed ones: they never oxidized);
Create's copper nugget (its nugget and ingot recipes stay, making ours); Create Stuff 'N Additions' copper tools and
armor (CSA: 131 uses, +0 damage, iron mining level, armor 2/6/5/1; Mojang: 190 uses, +1 damage, stone mining level,
armor 2/4/3/1).

Mobs pick armor as in 26.x (MobMixin): the tiers are leather, copper, gold, chainmail, iron, diamond, and the roll is
26.x's. 26.x's chest loot that names our items is taken whole (copper horse armor, spears; leather in place of saddles, which
26.x makes craftable). Tables that also name items not backported stay at 1.21.1: buried treasure and underwater ruins
(nautilus armor, buried treasure map) and the village weaponsmith (26.x adds a bundle, which 1.21.1 keeps behind an
experimental feature flag).

Kept apart: TFMG's colored concrete slabs and stairs (TFMG's own concrete, its own textures); Handcrafted's shelves
(a different block: decoration, no slots).

## Shelves

Mojang's shelf, method for method: right-click a slot to swap the held item with it; powered by redstone, up to 3 shelves
side by side join, and right-clicking swaps the whole row with the hotbar. Comparators read one bit per filled slot from
the back only. Items stand in each slot at a quarter size, centred by their model's box.

1.21.1 has no "on shelf" item display, so the mod adds one (`haven_backports:on_shelf`, a NeoForge enum extension; it
is checked on join, so server and client need the same build). Mojang's on-shelf transforms come with it: the shelf's
own, and the vanilla models 26.x gives one (block/block, fences, walls, anvil, skulls, shield, beds). Beds turn the
transform 180 degrees: 1.21.1 draws bed items with the bed renderer, which faces the other way (checked on every
display entry). Two small mixins carry 26.x behaviour 1.21.1 hard-codes: tridents and spyglasses use their flat model
on a shelf, and comparators tell the shelf which side they read. Compasses and clocks on a shelf use the shelf as their
holder, as in 26.x.

### Modded woods

Mojang drew each vanilla shelf texture by hand, so a modded wood has no Mojang shelf art. `tools/modshelves.py`
recolors Mojang's shelf art from the vanilla wood whose stripped log looks closest: each color moves to the same place
in the modded stripped log's pixel distribution, and Mojang's extra shadows darken by the same ratio. Recipes need the
wood's mod (`neoforge:mod_loaded`); without it the shelf still loads (our own texture, map color falls back to wood)
and stays out of the creative tab. Adding a wood: one line in `MOD_WOODS`, then rerun the generator.

## Copper chest

Mojang's CopperChestBlock on the vanilla chest block entity (as 26.x). Two mixins carry what 1.21.1 hard-codes: the
hinge sounds per age (ChestBlockEntity.playSound) and the copper texture before the holiday one (Sheets.chooseMaterial).

Not taken: recipes 1.21.1 cannot run or already has (bundle, which 1.21.1 keeps behind a feature flag; the per-flower
suspicious stews 1.21.1's special recipe already makes; dyes and stews from flowers 1.21.1 lacks).

## Spears

Mojang's 26.x spear, part for part (`spear/` holds the ported classes, `client/spear/` the animations). Left click is
the jab: at full attack charge it hits every entity on a line 2 to 4.5 blocks ahead (creative 6.5) and costs 1
durability per entity hit; a spear never breaks blocks. Holding right click is the charge: no slowdown, sprint allowed;
after the tier's delay, an entity on the line takes knockback, then damage (1 + relative speed x tier multiplier) and
a mount loses its rider while the attacker is fast enough, each until the tier's time runs out, at most once per 10 ticks
per entity. "Mob Kabob" (5 mobs in one charge) is `haven_backports:adventure/spear_many_mobs`.

Mobs: zombies (husks and zombie villagers too) roll an iron spear in 1 of 6 weapon rolls, zombified piglins carry a
golden spear 1 time in 20, piglins 1 in 10 of their non-crossbow spawns; all charge with it (26.x SpearUseGoal, and the
piglin brain's spear behaviors). Drowned and skeletons do not pick spears up. Piglins love golden spears.

Lunge (enchanting table, librarian books, enchanted loot and mob gear, as every non-treasure enchantment; spears only):
each jab pushes the player 0.458 blocks per level forward, costs 4 exhaustion per level and 1 durability; not while
riding, gliding, in water, or with food below 7 (creative: always). Spears also take Sharpness, Smite, Bane of
Arthropods, Knockback, Fire Aspect, Looting, Unbreaking, Mending and Curse of Vanishing, not Sweeping Edge: those six
vanilla enchantment files are overridden with 26.x's `#enchantable/melee_weapon` targets (1.21.1's file otherwise),
and that tag also lists 1.21.1's `#enchantable/sword`, so other mods' additions to it (Farmer's Delight knives, Iron's
Spellbooks staves) keep their enchantments.

1.21.1 has none of 26.x's item components, so a spear is a `SpearItem` holding them; the jab is a payload of its own
(26.x adds a STAB player action). Engine-wide 26.x changes are kept for spears only: the first-person hand does not dip
after a jab (other items keep 1.21.1's dip, owner's choice), and the swing length comes from the spear in the swinging
hand (1.21.1 sends no swing animation). Not ported: 26.x-only sources (husk camel jockeys, zombie horse jockeys, which
need natural zombie horse spawns; abandoned camp loot), the smithing table's empty spear slot icon, and 26.x's mob
weapon preference (`piglin_preferred_weapons`). Iron, golden and copper spears do not smelt into nuggets: 26.x's
nugget recipes also name nautilus armor.

## Download

The Haven pack delivers it (`mods/haven-backports.pw.toml`).
