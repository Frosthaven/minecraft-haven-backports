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
| Copper | Copper Bars, Chain and Lantern in every oxidation stage, waxed or not (honeycomb, axe, rain); Copper Torch; Copper Nugget |
| Shelves | Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry, Bamboo, Crimson and Warped Shelf; plus one for each of the pack's 21 modded woods (see below). Pale Oak and Poplar come with their woods |
| Copper chest | Copper Chest in every oxidation stage, waxed or not; keeps its items when it ages, is waxed or scraped |
| Copper gear | Copper Sword, Shovel, Pickaxe, Axe, Hoe; Copper Helmet, Chestplate, Leggings, Boots (trims; copper on copper uses Mojang's darker copper) |
| Horse armor | Copper Horse Armor (chest loot, as newer Minecraft), Netherite Horse Armor (smithing) |
| Dappled Forest | The biome (Mojang's place in the climate table); Poplar Log, Wood, Stripped Log and Wood, Planks, Stairs, Slab, Fence, Fence Gate, Door, Trapdoor, Pressure Plate, Button, Sign, Hanging Sign, Shelf, Boat, Boat with Chest; red, orange and yellow Poplar Leaves; Poplar Sapling (and potted); Leaf Litter; Red Shrub; Shelf Mushroom |
| Sulfur Caves | The cave biome (Mojang's place in the climate table); Potent Sulfur (geysers, noxious gas); Sulfur Spike; the Sulfur Cube, its bucket and spawn egg; the Sulfur and Cinnabar stone sets come from it |
| Pale Garden | The biome (Mojang's place in the climate table); Pale Oak Log, Wood, Stripped Log and Wood, Planks, Stairs, Slab, Fence, Fence Gate, Door, Trapdoor, Pressure Plate, Button, Sign, Hanging Sign, Shelf, Boat, Boat with Chest; Pale Oak Leaves; Pale Oak Sapling (and potted); Pale Moss Block, Carpet, Hanging Moss; Open and Closed Eyeblossom (and potted); Resin Clump, Block of Resin, Resin Bricks with Stairs, Slab, Wall, Chiseled Resin Bricks, Resin Brick; Creaking Heart; the Creaking and its spawn egg |
| Ice Caves | The cave biome (Mojang's place in the climate table); Icicle, Brittle Ice, Ice Crystal; the Frostbite, its spawn egg, the Ice Ball; the Freezing effect and potions (and arrows); Music Disc Flurry |
| Newer flora | Bush, Firefly Bush, Wildflowers, Short and Tall Dry Grass, Cactus Flower, Golden Dandelion (and potted) |
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

Not taken: recipes 1.21.1 cannot run or already has (bundle, which 1.21.1 keeps behind a feature flag; dyes and stews from
flowers 1.21.1 lacks, until those flowers are in).

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

Not in 1.21.1, so not here: the cold pig, cow and chicken variants newer Minecraft spawns in the biome (1.21.1 has no
farm animal variants); the abandoned camp structure (it needs blocks and entities not backported yet); the leaves'
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

Newer Minecraft's flora goes into the vanilla biomes it adds them to, at the place in each biome's list where newer
Minecraft has it (a small biome modifier of ours; NeoForge's own only appends, and the order decides which feature sees
the other's blocks): bushes in plains, forests, birch forests, rivers and windswept hills; firefly bushes near water
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

## Download

The Haven pack delivers it (`mods/haven-backports.pw.toml`).
