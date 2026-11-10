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
| Dappled Forest | The biome (Mojang's place in the climate table); Poplar Log, Wood, Stripped Log and Wood, Planks, Stairs, Slab, Fence, Fence Gate, Door, Trapdoor, Pressure Plate, Button, Sign, Hanging Sign, Shelf, Boat, Boat with Chest; red, orange and yellow Poplar Leaves; Poplar Sapling (and potted); Leaf Litter; Red Shrub; Shelf Mushroom |
| Sulfur Caves | The cave biome (Mojang's place in the climate table); Potent Sulfur (geysers, noxious gas); Sulfur Spike; the Sulfur Cube, its bucket and spawn egg; the Sulfur and Cinnabar stone sets come from it |
| Pale Garden | The biome (Mojang's place in the climate table); Pale Oak Log, Wood, Stripped Log and Wood, Planks, Stairs, Slab, Fence, Fence Gate, Door, Trapdoor, Pressure Plate, Button, Sign, Hanging Sign, Shelf, Boat, Boat with Chest; Pale Oak Leaves; Pale Oak Sapling (and potted); Pale Moss Block, Carpet, Hanging Moss; Open and Closed Eyeblossom (and potted); Resin Clump, Block of Resin, Resin Bricks with Stairs, Slab, Wall, Chiseled Resin Bricks, Resin Brick; Creaking Heart; the Creaking and its spawn egg |
| Ice Caves | The cave biome (Mojang's place in the climate table); Icicle, Brittle Ice, Ice Crystal; the Frostbite, its spawn egg, the Ice Ball; the Freezing effect and potions (and arrows); Music Disc Flurry |
| Newer flora | Bush, Firefly Bush, Wildflowers, Short and Tall Dry Grass, Cactus Flower, Golden Dandelion (and potted) |
| Spears | Wooden, Stone, Copper, Iron, Golden, Diamond and Netherite Spear; the Lunge enchantment (see below) |
| Music discs | Tears (a ghast killed by a player with its own fireball), Lava Chicken (a player kills a baby zombie riding a chicken), Bounce (mineshaft chests in the Sulfur Caves); Mojang's songs, lengths and comparator values |
| Banner patterns | Field Masoned (paper + bricks) and Bordure Indented (paper + vines) Banner Patterns; as in newer Minecraft, the loom offers those two patterns only with their pattern item |
| Explorer maps | Each explorer map is its own item, as in newer Minecraft: Ocean Monument, Woodland Mansion, Buried Trial Chambers, Jungle Pyramid, Swamp Hut, Desert, Plains, Savanna, Snowy and Taiga Village Maps (cartographer), the Buried Treasure Map (shipwreck and underwater ruin chests), the Abandoned Camp Map and the Buried Ancient City, Buried Mineshaft, Desert Pyramid and Warm Ocean Ruins Maps (abandoned camp chests) |
| Abandoned camp | The structure in 18 biome variants (see below), with its barrels and chests |
| Ancient city | Chiseled Soulshale (ancient city chests, with its own sounds; newer Minecraft's ancient city chest table) |
| Bundles | The Bundle without the experiment (string over leather; village chests), the 16 dyed Bundles (bundle + dye keeps the contents), newer Minecraft's bundle behaviour and tooltip |
| Happy ghast | Happy Ghast (ghastling and adult), Dried Ghast, Harness in 16 colors, Happy Ghast Spawn Egg |
| Copper golem | Copper Golem in every oxidation stage, Copper Golem Statue in every stage (waxed or not), Copper Golem Spawn Egg |
| Cushions | Cushion in all 16 colors (sit on it) |
| Straw bed | Straw Bed (4 from 3 hay bales) |
| Farm animal variants | Warm and cold pigs, cows and chickens; Blue Egg, Brown Egg; wolf sound variants |
| Desert mobs | Camel Husk, Parched, their Spawn Eggs |
| Nautilus | Nautilus, Zombie Nautilus, their Spawn Eggs; Copper, Iron, Golden, Diamond and Netherite Nautilus Armor; Breath of the Nautilus |
| Recipes | Name Tag (paper + metal nugget), Saddle; stonecutter: cobblestone set from stone, every deepslate variant from deepslate |

Merged into the backported ones (Haven Consolidated): New Slab Variants' calcite, wool and concrete slabs; Chipped's calcite bricks, calcite pillar and packed ice bricks; Create's calcite pillar;
Create's and Create Deco's copper bars and Macaw's Lights' copper chain (into the waxed ones: they never oxidized);
Create's copper nugget (its nugget and ingot recipes stay, making ours); Create Stuff 'N Additions' copper tools and
armor (CSA: 131 uses, +0 damage, iron mining level, armor 2/6/5/1; Mojang: 190 uses, +1 damage, stone mining level,
armor 2/4/3/1).

Mobs pick armor as in newer Minecraft (MobMixin): the tiers are leather, copper, gold, chainmail, iron, diamond, and the roll is
newer Minecraft's. Vanilla loot that newer Minecraft changes for our items (copper horse armor, spears; leather in place of
saddles, which newer Minecraft makes craftable; buried treasure, shipwreck and underwater ruin chests with nautilus armor,
the buried treasure map and spears; village chests with bundles; mob drops) is a patch on top of the table that loads, not
a replacement: with no other mod in the table it is newer Minecraft's table exactly, and a mod's or datapack's own version
(e.g. Luki's Grand Capitals' village chests) keeps all of its entries and gets our additions. Removals and changes apply only
where the vanilla entry is still there; when a mod reshapes a pool so that no pool holds its vanilla entries, our additions
go in a pool of their own with the same chance per roll, and the server log names the table.

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

Not taken: recipes 1.21.1 cannot run (dyes and stews from flowers 1.21.1 lacks). Newer Minecraft's per-flower
suspicious stew recipes are taken (1.21.1's special recipe also makes them), so the shelf mushroom counts as a mushroom.

## Biomes

New biomes take Mojang's own place in the overworld's climate table (OverworldBiomeBuilderMixin edits 1.21.1's
table cells to newer Minecraft's), so they show up where newer Minecraft puts them, with or without TerraBlender, in the
vanilla region of a TerraBlender world (BOP's regions use BOP's own tables and keep their biomes). Existing worlds get
them in chunks that were never generated; saved chunks keep their biomes. `/locate biome` and Nature's Compass read
the biome source, so they can point into an old chunk where the new biome would be now; that chunk keeps its old
biome. Surface rules (coarse dirt patches...) go into 1.21.1's own overworld noise settings next to the same vanilla
rule as in newer Minecraft. Biome tags: Mojang's, plus the common `c:` tags NeoForge gives the closest vanilla biome.

### Dappled Forest

Everything as newer Minecraft generates it: the poplar trees with leaf litter, fallen poplar logs with mushrooms,
spruce, red shrubs, brown mushrooms, coarse dirt patches, the orange grass and foliage. Poplar saplings grow one of
the three poplar colours at random (one in three each); they drop from poplar leaves. The wandering trader sells
poplar saplings and shelf mushrooms (common offers) and poplar logs (newer Minecraft's uncommon offers, which 1.21.1's
trader draws as its one rare offer). Leaf litter is smelted from any leaves and tinted with newer Minecraft's dry
foliage colours. Shelf mushrooms bounce what lands on them (0.75 of the fall speed for mobs and players, 0.6 for
other entities, as newer Minecraft) and halve fall damage; they count as a mushroom in suspicious stew (newer
Minecraft's per-flower stew recipes are taken) and rabbit stew. Poplar leaves rustle near a log and drop leaf
particles; the Falling Leaves mod adds its own as well.

Pigs, cows and chickens spawn in their cold variants here, as newer Minecraft has it (the farm animal variants).
Not in 1.21.1, so not here: the abandoned camp structure (it needs blocks and entities not backported yet); the leaves'
newer mipmap mode. Newer Minecraft names the three poplar leaves only "Poplar Leaves", so all three show that name.
Signs use newer Minecraft's sign art on 1.21.1's sign models (the same pixels, moved to 1.21.1's texture layout; checked
on every vanilla wood).

### Sulfur Caves

An underground biome under coasts and inland areas, as newer Minecraft generates it: sulfur and cinnabar bands in the
stone (newer Minecraft's 3D noise), sulfur springs (Mojang's structure templates) with roots of sulfur, sulfur pools
with potent sulfur at the bottom, sulfur spike clusters and single spikes, glow lichen (which grows on sulfur and
cinnabar too, everywhere). Its music is newer Minecraft's: the eleven tracks 1.21.1 already has play 1.21.1's files,
the three new ones (Ebb, Nightly, Shores) ship here.

Potent sulfur under a water source is wet: it bubbles and gives nausea to whoever breathes the gas above it. Over a
magma block it is a periodic geyser (dormant, then erupting, timed per position and world seed); over lava it erupts
all the time. An eruption pushes mobs, items and players up, higher with more water above (up to 4 blocks), and
shows the geyser plume. Sulfur spikes grow under sulfur and fall like pointed dripstone. The wandering trader sells
sulfur spikes.

With Create and Haven Consolidated, sulfur, polished sulfur, sulfur bricks and chiseled sulfur crush into the pack's
Sulfur Dust exactly as sulfur ore does (create_mf's crushing: 1, then 75% and 30% chances).

The Sulfur Cube spawns in the caves. It swallows a block or an item you give it (or one dropped near it) and takes
on that block's archetype (newer Minecraft's 13: bouncy, cold, explosive, hot, sticky...): its bounce, friction, air
drag, speed and damage change, a hot one burns what touches it, a cold one freezes water it slides over, one with TNT
primes from fire, flint and steel or redstone and explodes. Shears pop the block out; a bucket catches the cube with
its block; babies grow on slime balls; dispensers feed cubes in front of them. Its physics (bounciness, friction and
air drag attributes) are newer Minecraft's and apply to the cube only: no other mob moves differently.

Not in 1.21.1, so not here: the tempt range attribute (the cube's own range 8 is fixed), the TNT-explodes game rule,
the sheared-equipment advancement trigger, knockback that knows it came from an effect, the bounce game event.

1.21.1 has no level event for a landing sulfur spike: the server plays the same sound. Dripstone caves keep 1.21.1's
humidity range (newer Minecraft narrowed it; no new biome takes that space).

### Pale Garden

Everything as newer Minecraft generates it: pale oak trees (newer Minecraft's dark oak trunk, which also keeps the
leaves on the branch feet), some with a creaking heart in the trunk and resin clumps on the logs; pale moss on the
ground and over stone, pale moss carpets, pale hanging moss under the leaves, open and closed eyeblossoms. The biome
has no music (newer Minecraft fades the track out; 1.21.1 stops it) and its own grey sky, fog and water colours.
Pale oak saplings grow a 2x2 pale oak (bone meal on pale moss spreads it as newer Minecraft does). Eyeblossoms open at
night and close at day with their sounds and trail particles; each gives its dye. The wandering trader sells pale oak
saplings, pale oak logs, pale moss blocks, pale hanging moss and open eyeblossoms. Resin clumps also come from woodland
mansion chests (newer Minecraft's loot table, which also drops the name tag from them) and smelt into resin bricks;
nine make a block of resin. Eyeblossoms go into suspicious stew (open: blindness, closed: nausea, as newer Minecraft).

A creaking heart between two pale oak logs on the same axis wakes at night and spawns a creaking. The creaking
freezes while a player looks at it (a carved pumpkin on the head lets it move), takes no damage while its heart lives
(each hit sends trail particles to the heart and grows 2 to 3 resin clumps on the logs nearby) and crumbles at day, when
it strays over 34 blocks from the heart or when the heart breaks. A natural heart gives 20 to 24 experience; silk touch
keeps it, otherwise it drops resin clumps. Illagers flee creakings and parrots imitate them.

1.21.1 has no tag that keeps a mob out of boats: the creaking refuses to ride a boat at all (so `/ride` cannot seat
it either). Newer Minecraft took moss out of the dirt tag into a moss tag with pale moss; 1.21.1 still asks the dirt
tag (plants, saplings), so pale moss joins it as moss is there (one side effect: big dripleaf, which 1.21.1 lets grow
on any dirt, grows on pale moss too). Vanilla's kill-a-mob advancements stay 1.21.1's files (the creaking is not
added to them, as for the Sulfur Cube).

Not in 1.21.1, so not here: resin armor trims (1.21.1's trim materials need an item model index Mojang never gave
resin, and the pack's armor models have no overrides for it); the firefly bush near water (added with the newer flora).

### Ice Caves

An underground biome under frozen oceans, as newer Minecraft generates it: packed ice and calcite bands in the stone
(newer Minecraft's 3D noise), large icicle columns, icicle clusters on the ceilings, ice crystals on packed ice, snow,
its own ores (coal, iron, gold, redstone, diamond, lapis, copper) and water crevasses capped with brittle ice. Its music
is the Sulfur Caves' (newer Minecraft shares it).

Icicles grow down from packed ice (up to 4 long), drip snow dust, melt and fall near light above 4, in ultra-warm
dimensions or over magma, and hurt more to land on (as pointed dripstone). Brittle ice cracks under living things that
walk on it (not strays and frostbites; mobs only with mob griefing; not in spawn protection) and breaks at the fourth
stage with no drop; water flowing next to blue ice turns into it; silk touch keeps it. Ice crystals give light 4 and
chime over packed ice. The wandering trader sells icicles and ice crystals.

The Frostbite spawns in the caves and, as newer Minecraft adds it, in the frozen biomes (snowy plains, snowy slopes,
jagged peaks at weight 10; ice spikes, frozen peaks, frozen river, frozen and deep frozen ocean at 25), through NeoForge
biome modifiers, so other mods' biome edits stay. It is a zombie that throws ice balls when it holds them (one in four
spawns with some) and freezes what it hits; it walks on powder snow, drowns into a zombie, and villagers flee it. A
zombie that stands in powder snow for 7 seconds shakes and becomes a frostbite; a husk or a drowned becomes a zombie the
same way. Strays now spawn under a roof in the ice caves as newer Minecraft lets them (its cold-variant spawn rule).

Ice balls drop from frostbites; thrown, they hurt by speed. Freezing builds frost every tick, stops thawing and hurts
1.5 when frozen through. Brewing: water + ice ball gives a mundane potion, awkward + ice ball Freezing, + redstone the
long one; splash, lingering and tipped arrows as usual. Music Disc Flurry: igloo chests (30%, newer Minecraft's loot).

1.21.1 draws baby frostbites as small adults (newer Minecraft has a new baby zombie model for all of them); a
frostbite calls plain zombies as reinforcements (1.21.1 hard-codes the type); only vanilla zombies, husks and drowned
freeze-convert, so other mods' zombies are left alone. Newer Minecraft's other changes to these biomes (strays in frozen
oceans, zombie horses) are not newer content of this mod.

Not in 1.21.1, so not here: a shulker on brittle ice does not crack it; iron golems can still be built on brittle ice
(1.21.1 hard-codes that check); newer Minecraft's ambient sound for all ice and its face culling between ice blocks
(brittle ice hides its faces toward ice; vanilla ice still draws its face toward brittle ice).

### Newer flora in vanilla biomes

Newer Minecraft's flora goes into the vanilla biomes it adds them to. A feature newer Minecraft changed in place (the cactus with its flower, forest trees with leaf litter, meadow's grass) takes the vanilla feature's place; every new feature is appended to its step by one biome modifier that names every biome it goes in, ours too. Inserting next to a vanilla feature is not safe in a modded pack: where a mod rewrites a biome's list (Geophilic) or appends its own (Molten Vents), the same two features end up in different orders in two biomes, a feature order cycle that stops chunk generation. One modifier runs at one point among all mods' modifiers, so the order is the same everywhere (checked in the full pack with every dimension's biomes): bushes in plains, forests, birch forests, rivers and windswept hills; firefly bushes near water
in most overworld biomes and in swamps (and the Pale Garden); wildflowers in birch forests and meadows (meadow's grass
patch becomes newer Minecraft's); dry grass in deserts and badlands; cactus flowers on generated cacti; leaf litter
under the trees of forests, dark forests and wooded badlands (newer Minecraft's trees with leaf litter, and its fallen
logs in forests). Other mods' biome changes stay. Chunks already generated keep their old flora.

Bushes and firefly bushes spread with bone meal; fireflies glow around firefly bushes in the dark and buzz at night under
the sky. Wildflowers stack to four per block and take the grass colour. Short dry grass grows tall with bone meal, tall
dry grass spreads; on sand or terracotta they make the desert's windy sound. Cacti grow a flower on top (more often when
three tall). The golden dandelion is crafted (a dandelion and gold nuggets, newer Minecraft's recipe); used on a baby
animal it stops it growing up (used again, it grows on from newborn); it keeps that through buckets and reloads.
Villagers and skeleton or zombie horses cannot be locked. The wandering trader sells wildflowers, firefly bushes,
golden dandelions and tall dry grass. Dyes and suspicious stew as newer Minecraft.

Not in 1.21.1, so not here: newer Minecraft's vegetation tag (these plants stand on 1.21.1's dirt, as other plants
do); the flower bed and flower pot shading change (engine-wide, kept at 1.21.1); bees still follow a player who holds a
golden dandelion (1.21.1's bee food is every flower); the dead bush and sand ambient sounds (engine-wide).

## Spears

Mojang's newer Minecraft spear, part for part (`spear/` holds the ported classes, `client/spear/` the animations). Left click is
the jab: at full attack charge it hits every entity on a line 2 to 4.5 blocks ahead (creative 6.5) and costs 1
durability per entity hit; a spear never breaks blocks. Holding right click is the charge: no slowdown, sprint allowed;
after the tier's delay, an entity on the line takes knockback, then damage (1 + relative speed x tier multiplier) and
a mount loses its rider while the attacker is fast enough, each until the tier's time runs out, at most once per 10 ticks
per entity. "Mob Kabob" (5 mobs in one charge) is `haven_backports:adventure/spear_many_mobs`.

Mobs: zombies (husks and zombie villagers too) roll an iron spear in 1 of 6 weapon rolls, zombified piglins carry a
golden spear 1 time in 20, piglins 1 in 10 of their non-crossbow spawns; all charge with it (newer Minecraft SpearUseGoal, and the
piglin brain's spear behaviors). Drowned and skeletons do not pick spears up. Piglins love golden spears.

Lunge (enchanting table, librarian books, enchanted loot and mob gear, as every non-treasure enchantment; spears only):
each jab pushes the player 0.458 blocks per level forward, costs 4 exhaustion per level and 1 durability; not while
riding, gliding, in water, or with food below 7 (creative: always). Spears also take Sharpness, Smite, Bane of
Arthropods, Knockback, Fire Aspect, Looting, Unbreaking, Mending and Curse of Vanishing, not Sweeping Edge: those six
vanilla enchantment files are overridden with newer Minecraft's `#enchantable/melee_weapon` targets (1.21.1's file otherwise),
and that tag also lists 1.21.1's `#enchantable/sword`, so other mods' additions to it (Farmer's Delight knives, Iron's
Spellbooks staves) keep their enchantments.

1.21.1 has none of newer Minecraft's item components, so a spear is a `SpearItem` holding them; the jab is a payload of its own
(newer Minecraft adds a STAB player action). Engine-wide newer Minecraft changes are kept for spears only: the first-person hand does not dip
after a jab (other items keep 1.21.1's dip, owner's choice), and the swing length comes from the spear in the swinging
hand (1.21.1 sends no swing animation). Husks also ride camel husks with an iron spear (see Desert mobs). Not ported: newer Minecraft-only sources (zombie
horse jockeys, which need natural zombie horse spawns; abandoned camp loot), the smithing table's empty spear slot icon, and newer Minecraft's mob
weapon preference (`piglin_preferred_weapons`). Iron, golden and copper spears and nautilus armor smelt and blast into
nuggets, as in newer Minecraft (its iron and gold nugget recipes replace 1.21.1's).

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
and 5 sell the explorer maps. The maps are filled by Mojang's newer loot functions, ported under our ids
(`haven_backports:exploration_map`, `filtered`, `discard`, the `map_id` item predicate), with the villager or chest as
the origin; a map that finds no structure is not offered. Three mixins open 1.21.1's map hard-codes to every map item
as newer Minecraft does: the cartography table and item frame (any item with a map id) and map cloning
(`#minecraft:clonable_maps`). Shipwreck and underwater ruin chests are newer Minecraft's tables, with the Buried Treasure
Map (and nautilus armor and spears). The abandoned camp's chests give the Abandoned Camp Map and the Buried Ancient City,
Buried Mineshaft, Desert Pyramid and Warm Ocean Ruins Maps (their only source, as in newer Minecraft); their map
markers are newer Minecraft's decoration types, registered under our ids. Maps made before keep working as 1.21.1 filled maps. The wandering trader
sells no maps in newer Minecraft, so it is unchanged.

## Bundles

1.21.1's `minecraft:bundle` item has no feature flag (Items.BUNDLE sets no required features); only its creative tab
place and its recipe live in the experimental "bundle" data pack. So nothing per world is switched on: the item already
loads in every world and on a dedicated server, and the mod adds newer Minecraft's recipe (`haven_backports:bundle`),
its tab place (only where 1.21.1 did not already put it), and newer Minecraft's village chests that hold one. Worlds
made with the experiment keep its rabbit hide recipe as well. Every bundle behaves as newer Minecraft's
(BundleItemMixin -> `bundle/BundleLogic`, Mojang's newer BundleItem): scroll over a bundle in a container to pick the
item that comes out next (sent to the server, never saved, as in newer Minecraft), right-click takes that one out,
holding use drops the contents one item at a time, a full bundle shows a red bar and "Full", an item it refuses plays
its own sound. The tooltip is newer Minecraft's grid with the selected item named and a fullness bar
(`client/BundleTooltipRenderer`); in the GUI a bundle with a selected item opens around it (`client/BundleRenderer`,
ItemRendererBundleMixin), and a shift-click or hotbar swap clears the pick (AbstractContainerScreenMixin, newer
Minecraft's slot-click hook). Dye recipes keep the contents through `haven_backports:crafting_transmute`, a port of
Mojang's TransmuteRecipe. Kept from 1.21.1: the bundle's texture (newer Minecraft redrew it); the "filled" look is
gone, as newer Minecraft dropped it; the first-person use animation (newer Minecraft only adds an arm swing).

## Happy ghast

Mojang's HappyGhast, method for method (`ghast/`). How to get one, as in newer Minecraft: a Dried Ghast (crafted from 8
ghast tears around soul sand; piglin bartering, weight 10, from newer Minecraft's table; lying in half of all nether
fossils, NetherFossilPieceMixin) in water hydrates every 5000 ticks and at level 3 becomes a ghastling; out of water it
dries back. A ghastling grows up like any baby animal (snowballs speed it up). Harnesses (leather, glass, wool; dye
recipes) go on an adult by hand or from a dispenser and come off with shears. A harnessed adult carries up to 4
players; the first one steers (look and move, jump to rise). Players can stand and build on top of it: it holds still
while one is there. Third person sits 8 blocks back. It heals fast in clouds or rain. Its own lead is newer
Minecraft's spring lead (elastic at 10, snaps at 16); sneak-clicking it moves the mobs on your leads onto it. Mobs on
a lead to a happy ghast use newer Minecraft's spring lead too (elastic at 6, snaps at 12), and horses, donkeys, mules,
camels, zombie and skeleton horses, sniffers and boats hang from it on four ropes (the quad leash), with the ghast's
rope harness showing (GhastEntityMixin for the tick, EntityRendererMixin for the ropes; llamas keep one rope, as in newer
Minecraft).

1.21.1 lacks parts of the engine this uses. Each has a small equivalent: the harness is a 1.21.1 `Equipable` with
Mojang's equip, dispense and shear rules (no `equippable` component); the camera distance comes from NeoForge's
`CalculateDetachedCameraDistanceEvent`; the precise position sync while it holds still is a teleport packet per
tick; the riding statistic, standing on top, the allow-flight kick fix and the dried ghast in fossils are mixins
(ServerPlayerMixin, GhastEntityMixin, ServerGamePacketListenerImplMixin, NetherFossilPieceMixin); `LivingEntity.jumping` is
opened by the access transformer. Not taken: newer Minecraft's leash changes for leashes not tied to a happy ghast
(spring physics, shears cutting leads, the wider single rope); the plain ghast as a quad-leash holder; sneaking with a lead in hand leashes the ghast (1.21.1 handles leads before the mob does); the
`place_dried_ghast_in_water` advancement. Clouds are at newer Minecraft's height (192.33 to 196.33) in dimensions
that draw overworld clouds.

## Copper golem

Mojang's CopperGolem, method for method (`golem/`). A carved pumpkin or jack o'lantern on any copper block (waxed or
not) makes one, in that block's age; the block becomes the matching copper chest (CarvedPumpkinBlockMixin, and
dispensers place pumpkins for it). It carries up to 16 items at a time from copper chests to chests and trapped chests
that already hold that item or are empty (Mojang's rule: no other containers), opening the lid as it works
(ContainerOpenersCounterMixin) and waiting while someone else has it open. It ages like copper unless waxed
(honeycomb); an axe takes off the wax or a stage, lightning a stage. A fully oxidized golem may become a statue in a
random pose that keeps its name; any item but an axe changes the pose (comparators read it), an axe on an unaffected
statue wakes it. Statues age, wax and scrape like copper blocks. Iron golems give poppies to copper golems too
(OfferFlowerGoalMixin); shears take the poppy off its antenna. Its walk, idle and chest animations are Mojang's
CopperGolemAnimation, converted by the generator (`tools/animations.py`) into NeoForge JSON animations.

1.21.1 lacks the SADDLE equipment slot newer Minecraft keeps the antenna item in: it is the golem's own synced stack,
saved as `antenna` and always dropped (only the gift fills it, a guaranteed drop in newer Minecraft), so `/data` paths
differ. The chest's open and close game event has no source entity.

## Cushions

Mojang's Cushion (`decoration/`): crafted from 3 wool slabs of a color (ours), re-dyed with any dye, placed on top of
a block (on cauldrons, hoppers and composters, on their rim), sat on by right-clicking. It breaks when its support
goes, in fire, lava or lightning, or when hit, and drops itself with its name. A different thing from Handcrafted's
cushions (seat blocks): kept apart, no merge. Not taken: the short time a cushion dropped by lightning cannot burn. Abandoned
camps hold cushions (placed, and in their barrels).

## Straw bed

Mojang's StrawBedBlock with the AbstractBedBlock newer Minecraft gives every bed (`block/StrawBedBlock`): a low bed
of block models. In the overworld you sleep in it when it is dark like any bed, but it never sets your spawn point and
it breaks when you get up; in the nether and the end it breaks when used (newer Minecraft's straw_bed_rule). 1.21.1
has no bed rules, so NeoForge events carry them (PlayerSetSpawnEvent, PlayerWakeUpEvent) and it is a bed to NeoForge
(`isBed`), not a BedBlock (no block entity, no bounce, no explosion). You lie at its own height (StrawBedLivingEntityMixin) and
it counts "Sleep in a straw bed", not "Sleep in a bed", and not the bed advancement (ServerPlayerMixin). Villagers do
not claim it, as in newer Minecraft. Abandoned camps hold it (placed, and in their barrels).

## Farm animal variants

Newer Minecraft's climate variants (`variant/`): pigs, cows and chickens that spawn in cold biomes (snowy, frozen,
taiga, windswept, the end ...) are cold ones and in warm biomes (desert, jungle, savanna, badlands, the nether ...) warm
ones, by Mojang's pig_variant, cow_variant and chicken_variant data and biome tags; a baby takes a parent's variant.
Cold and warm ones have Mojang's own models (fur, horns) and textures; temperate ones keep 1.21.1's look. A warm
chicken lays brown eggs, a cold one blue eggs (Mojang's chicken_lay); they hatch warm and cold chicks, throw and
dispense like eggs and make cakes and pumpkin pies (Mojang's #eggs recipes). Wolves get one of Mojang's 7 sound
variants (classic, puglin, sad, angry, grumpy, big, cute) and puppies their own sounds. The variants live on the
vanilla mobs through small mixins (AgeableMobMixin, PigMixin, CowMixin, ChickenMixin, WolfMixin): server and client
need the same build. Not taken: newer Minecraft's baby models and baby textures (1.21.1 draws babies as small
adults), the pig, cow and chicken sound variants (big, mini, moody, picky; their baby sounds), and the spawn tags'
biomes 1.21.1 lacks (they join when those biomes are backported and the generator reruns).

## Desert mobs

Mojang's CamelHusk and Parched (`husk/`). The parched, a desert skeleton, spawns in deserts as in newer Minecraft
(weight 50, groups of 4, sky access; the desert skeleton goes from weight 100 to 50 with it: a biome modifier of ours
changes it only while it is still at 1.21.1's 100, so a pack's own change wins). It does not burn in daylight, cannot
get Weakness and shoots arrows of Weakness (30 s); it drops arrows, bones and, killed by a player, a tipped arrow of
Weakness. A naturally spawned husk has a 10% chance to arrive riding a camel husk with an iron spear in hand and a
parched behind it (HuskMixin, newer Minecraft's Husk.finalizeSpawn); the husk charges with the spear at the camel husk's
speed (x4), and a husk killed on a camel husk may drop a rabbit's foot. The camel husk is a camel that despawns, never
breeds or grows up, eats rabbit's feet and can be saddled and ridden by a player when no mob sits in front; it drops
rotten flesh. Its own sounds come through CamelHusk (and CamelMixin for the dash-ready sound 1.21.1's Camel names
inline); a mob-ridden camel husk does not panic (CamelPanicMixin). Parrots imitate both (NeoForge's parrot_imitations
data map). Monster Hunter and Monsters Hunted count them, as in newer Minecraft. Not taken: newer Minecraft's ridden
camels floating in water (an engine change for every horse and camel).

## Nautilus

Mojang's Nautilus and ZombieNautilus (`nautilus/`). Nautiluses spawn alone in the oceans as in newer Minecraft
(weight 5 in ocean, lukewarm, warm and their deep forms; 2 in cold and frozen ones), 5 to 25 blocks below sea level;
a natural or structure drowned with a trident rides a zombie nautilus half of the time (DrownedMixin; not babies, not in
rivers). Out of water a nautilus dries out. Feed an untamed one a pufferfish (or a pufferfish bucket, which gives the
water bucket back): 1 in 3 tames it. Tame ones breed with fish, heal from fish, stay within 32 blocks of home (16 when
saddled or a baby) and take a saddle and nautilus armor by hand, from a dispenser or in their inventory screen
(sneak-use); shears take the armor off, then the saddle. Ride one by using it: with a saddle you steer by looking, the
jump key charges a dash along your view (stronger in water), and while riding you have Breath of the Nautilus (you do
not run out of air, it does not refill). Unprovoked ones charge fish (pufferfish) now and then, and anything that hurts
them. A zombie nautilus is faster, never a baby, burns in daylight unless armored, and in warm oceans grows corals
(hidden under armor). Nautilus armor comes from buried treasure, shipwreck and underwater ruin chests (netherite by
smithing); armor values 4 (copper), 5, 7, 11, 19. Parrots imitate zombie nautiluses; nautiluses count for Two by Two,
zombie nautiluses for Monster Hunter and Monsters Hunted, the effect for How Did We Get Here?.

1.21.1 lacks parts of the engine this uses. The saddle slot newer Minecraft adds is the nautilus's own synced stack
(saved as `saddle`; it drops on death); nautilus armor is a body ArmorItem with Mojang's equip rules (no equippable
component); the inventory screen is a NeoForge menu (1.21.1 opens mount screens only for horses); the effect's air
rule is NeoForge's LivingBreatheEvent; the zombie nautilus variant registry is data (`zombie_nautilus_variant`, the farm
variants' shape); poison immunity and staying off boats read newer Minecraft's tags on the nautilus only. Not taken: the
smithing table's empty nautilus armor slot icon, the `player_sheared_equipment` advancement trigger, and the variant as
an item component (spawn eggs give the biome's variant).

Furnace fuel and composting follow newer Minecraft's Items class for every item of ours (`tools/fuels.py`): wool
slabs and cushions burn 50 ticks, wool stairs 100, shelves 300 (the modded-wood shelves are not Mojang items and do
not burn).

## Abandoned camp

Newer Minecraft's abandoned camp (`tools/camp.py`): one jigsaw structure per biome, `haven_backports:abandoned_camp_<biome>`
for bamboo_jungle, birch_forest, cherry_grove, dappled_forest, flower_forest, forest, meadow, old_growth_pine_taiga,
old_growth_birch_forest, old_growth_spruce_taiga, pale_garden, savanna, snowy_taiga, sparse_jungle, swamp, taiga,
windswept_forest and wooded_badlands (the Dappled Forest and Pale Garden variants in our biomes). All 18 share one
structure set (`haven_backports:abandoned_camp`: random spread, spacing 37, separation 8, salt 91231127, weight 1
each): each grid cell picks one variant and builds it only in that variant's biome
(`#minecraft:has_structure/abandoned_camp_<biome>`), so a given variant is rare. A camp is a tent (10 per biome)
joined to a campsite (4 per biome, plus 45 shared default sites with a chest, a barrel or a special piece), with trees
from newer Minecraft's features, terrain beard_thin, at the surface. Every piece is Mojang's template converted
block for block (no block substituted); campsite chests, copper chests and barrels hold newer Minecraft's tables
`minecraft:chests/abandoned_camp_common_chest`, `chests/abandoned_camp_secret_chest` and
`barrels/abandoned_camp_barrel` (files of their own: 1.21.1 has no such tables). Cushions in the pieces rest on the
block they are placed on, as in newer Minecraft (StructureTemplateMixin, for our entities only). `/locate structure
#minecraft:abandoned_camp` finds the nearest of any variant.

## Not ported

The mannequin (newer Minecraft's `minecraft:mannequin` entity): a player-shaped figure for map makers that Mojang gives
no item, spawn egg, recipe, loot or structure; it exists only through `/summon`. With no way to obtain it in
survival, it is left out.

## Download

The Haven pack delivers it (`mods/haven-backports.pw.toml`).
