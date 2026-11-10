# Haven Backports

Content from newer Minecraft versions, backported to 1.21.1 (NeoForge) for the Haven pack. Required on the server and
on every client.

Everything comes from Mojang's own files: textures, models, blockstates, recipes, loot tables, tags and block
properties are read from the newer version's client jar (`tools/backport.py`), and block behaviour follows Mojang's
code in that jar (it ships unobfuscated). Nothing is redrawn or invented. Vanilla textures the new blocks reuse
(wool, concrete, copper, calcite...) are the same pixels in 1.21.1 and the newer version, so they come from the game itself. Where another mod in the pack already adds the
same block in its own art, it is merged into the backported one (Haven Consolidated carries out the merge), so there is
one block per concept and it looks the way Mojang made it.

## Content

| Set | Blocks and items |
|---|---|
| Calcite | Calcite Pillar, Stairs, Slab, Wall; Polished Calcite with Stairs, Slab, Wall; Polished Calcite Bricks with Stairs, Slab, Wall |
| Snow and ice | Snow Bricks with Stairs, Slab, Wall; Packed Ice Bricks with Stairs, Slab, Wall; Chiseled Packed Ice (new packed ice sounds) |
| Wool and concrete | Stairs and Slab for all 16 colors of each; wool ones re-dye with any dye and burn like wool |
| Copper | Copper Bars, Chain, Lantern and Lightning Rod in every oxidation stage, waxed or not (honeycomb, axe, rain); Copper Torch; Copper Nugget |
| Shelves | Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry, Bamboo, Crimson and Warped Shelf; plus one for each of the pack's 21 modded woods (see below). Pale Oak and Poplar come with their woods |
| Copper chest | Copper Chest in every oxidation stage, waxed or not; keeps its items when it ages, is waxed or scraped |
| Copper gear | Copper Sword, Shovel, Pickaxe, Axe, Hoe; Copper Helmet, Chestplate, Leggings, Boots (trims; copper on copper uses Mojang's darker copper) |
| Horse armor | Copper Horse Armor (chest loot, as newer Minecraft), Netherite Horse Armor (smithing) |
| Music discs | Tears (a ghast killed by a player with its own fireball), Lava Chicken (a player kills a baby zombie riding a chicken), Bounce (mineshaft chests in the Sulfur Caves: the chest entry waits for that biome); Mojang's songs, lengths and comparator values |
| Banner patterns | Field Masoned (paper + bricks) and Bordure Indented (paper + vines) Banner Patterns; as in newer Minecraft, the loom offers those two patterns only with their pattern item |
| Explorer maps | Each explorer map is its own item, as in newer Minecraft: Ocean Monument, Woodland Mansion, Buried Trial Chambers, Jungle Pyramid, Swamp Hut, Desert, Plains, Savanna, Snowy and Taiga Village Maps (cartographer) and the Buried Treasure Map (shipwreck and underwater ruin chests) |
| Ancient city | Chiseled Soulshale (ancient city chests, with its own sounds; newer Minecraft's ancient city chest table) |
| Bundles | The Bundle without the experiment (string over leather; village chests), the 16 dyed Bundles (bundle + dye keeps the contents), newer Minecraft's bundle behaviour and tooltip |
| Recipes | Name Tag (paper + metal nugget), Saddle; stonecutter: cobblestone set from stone, every deepslate variant from deepslate |

Merged into the backported ones (Haven Consolidated): New Slab Variants' calcite, wool and concrete slabs; Chipped's calcite bricks, calcite pillar and packed ice bricks; Create's calcite pillar;
Create's and Create Deco's copper bars and Macaw's Lights' copper chain (into the waxed ones: they never oxidized);
Create's copper nugget (its nugget and ingot recipes stay, making ours); Create Stuff 'N Additions' copper tools and
armor (CSA: 131 uses, +0 damage, iron mining level, armor 2/6/5/1; Mojang: 190 uses, +1 damage, stone mining level,
armor 2/4/3/1).

Mobs pick armor as in newer Minecraft (MobMixin): the tiers are leather, copper, gold, chainmail, iron, diamond, and the roll is
newer Minecraft's. newer Minecraft's chest loot that names our items is taken whole (copper horse armor; leather in place of saddles, which
newer Minecraft makes craftable); tables that name spears stay at 1.21.1 until spears are in.

Kept apart: TFMG's colored concrete slabs and stairs (TFMG's own concrete, its own textures); Handcrafted's shelves
(a different block: decoration, no slots).

## Shelves

Mojang's shelf, method for method: right-click a slot to swap the held item with it; powered by redstone, up to 3 shelves
side by side join, and right-clicking swaps the whole row with the hotbar. Comparators read one bit per filled slot from
the back only. Items stand in each slot at a quarter size, centred by their model's box.

1.21.1 has no "on shelf" item display, so the mod adds one (`haven_backports:on_shelf`, a NeoForge enum extension; it
is checked on join, so server and client need the same build). Mojang's on-shelf transforms come with it: the shelf's
own, and the vanilla models newer Minecraft gives one (block/block, fences, walls, anvil, skulls, shield, beds). Beds turn the
transform 180 degrees: 1.21.1 draws bed items with the bed renderer, which faces the other way (checked on every
display entry). Two small mixins carry newer Minecraft behaviour 1.21.1 hard-codes: tridents and spyglasses use their flat model
on a shelf, and comparators tell the shelf which side they read. Compasses and clocks on a shelf use the shelf as their
holder, as in newer Minecraft.

### Modded woods

Mojang drew each vanilla shelf texture by hand, so a modded wood has no Mojang shelf art. `tools/modshelves.py`
recolors Mojang's shelf art from the vanilla wood whose stripped log looks closest: each color moves to the same place
in the modded stripped log's pixel distribution, and Mojang's extra shadows darken by the same ratio. Recipes need the
wood's mod (`neoforge:mod_loaded`); without it the shelf still loads (our own texture, map color falls back to wood)
and stays out of the creative tab. Adding a wood: one line in `MOD_WOODS`, then rerun the generator.

## Copper chest

Mojang's CopperChestBlock on the vanilla chest block entity (as newer Minecraft). Two mixins carry what 1.21.1 hard-codes: the
hinge sounds per age (ChestBlockEntity.playSound) and the copper texture before the holiday one (Sheets.chooseMaterial).

Not taken: recipes 1.21.1 cannot run or already has (the per-flower
suspicious stews 1.21.1's special recipe already makes; dyes and stews from flowers 1.21.1 lacks).

## Lightning rods

Newer Minecraft makes the lightning rod copper that ages; its unaffected stage is still `minecraft:lightning_rod`, so
the vanilla rod is kept and made the head of the chain rather than replaced: NeoForge's oxidizables and waxables data
maps run vanilla rod -> exposed -> weathered -> oxidized (ours), each waxable, and a small mixin gives the vanilla rod
the random tick Mojang's WeatheringLightningRodBlock has (LightningRodBlockMixin; only for rods with a next stage, so
other mods' rods are untouched). Every stage is a lightning rod point of interest (NeoForge's ExtendPoiTypesEvent), in
`#minecraft:lightning_rods`, and newer Minecraft's Channeling (which names that tag) replaces 1.21.1's. Lightning
follows newer Minecraft too (LightningBoltMixin): a struck rod goes back to unaffected and the cleaning spreads from
it, where 1.21.1 reset the block under the rod instead. Rods already placed keep working and start to age.

## Explorer maps and the cartographer

The cartographer trades as in newer Minecraft: Mojang's trade set read from the snapshot (`tools/maps.py`), each trade
one 1.21.1 trade listing (`map/CartographerTrades`, NeoForge's VillagerTradesEvent at the highest priority, so other
mods' cartographer trades still add to it). Village maps depend on the villager's own type, banners too; levels 2, 3
and 5 sell the explorer maps. The maps are filled by Mojang's 26.x loot functions, ported under our ids
(`haven_backports:exploration_map`, `filtered`, `discard`, the `map_id` item predicate), with the villager or chest as
the origin; a map that finds no structure is not offered. Three mixins open 1.21.1's map hard-codes to every map item
as newer Minecraft does: the cartography table and item frame (any item with a map id) and map cloning
(`#minecraft:clonable_maps`). Shipwreck and underwater ruin chests keep 1.21.1's tables with newer Minecraft's Buried
Treasure Map entry until the nautilus armor and spears they also name are in. Left out: the Abandoned Camp Map and the
Buried Ancient City, Buried Mineshaft, Desert Pyramid and Warm Ocean Ruins Maps, whose only source is the Abandoned
Camp's chests (a structure 1.21.1 lacks). Maps made before keep working as 1.21.1 filled maps. The wandering trader
sells no maps in newer Minecraft, so it is unchanged.

## Bundles

1.21.1's `minecraft:bundle` item has no feature flag (Items.BUNDLE sets no required features); only its creative tab
place and its recipe live in the experimental "bundle" data pack. So nothing per world is switched on: the item already
loads in every world and on a dedicated server, and the mod adds newer Minecraft's recipe (`haven_backports:bundle`),
its tab place (only where 1.21.1 did not already put it), and newer Minecraft's village chests that hold one. Worlds
made with the experiment keep its rabbit hide recipe as well. Every bundle behaves as newer Minecraft's
(BundleItemMixin -> `bundle/BundleLogic`, Mojang's 26.x BundleItem): scroll over a bundle in a container to pick the
item that comes out next (sent to the server, never saved, as in newer Minecraft), right-click takes that one out,
holding use drops the contents one item at a time, a full bundle shows a red bar and "Full", an item it refuses plays
its own sound. The tooltip is newer Minecraft's grid with the selected item named and a fullness bar
(`client/BundleTooltipRenderer`); in the GUI a bundle with a selected item opens around it (`client/BundleRenderer`,
ItemRendererBundleMixin), and a shift-click or hotbar swap clears the pick (AbstractContainerScreenMixin, newer
Minecraft's slot-click hook). Dye recipes keep the contents through `haven_backports:crafting_transmute`, a port of
Mojang's TransmuteRecipe. Kept from 1.21.1: the bundle's texture (newer Minecraft redrew it); the "filled" look is
gone, as newer Minecraft dropped it; the first-person use animation (newer Minecraft only adds an arm swing).

## Not ported

The mannequin (newer Minecraft's `minecraft:mannequin` entity): a player-shaped figure for map makers that Mojang gives
no item, spawn egg, recipe, loot or structure; it exists only through `/summon`. With no way to obtain it in
survival, it is left out.

## Download

The Haven pack delivers it (`mods/haven-backports.pw.toml`).
