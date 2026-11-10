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
| Happy ghast | Happy Ghast (ghastling and adult), Dried Ghast, Harness in 16 colors, Happy Ghast Spawn Egg |
| Copper golem | Copper Golem in every oxidation stage, Copper Golem Statue in every stage (waxed or not), Copper Golem Spawn Egg |
| Cushions | Cushion in all 16 colors (sit on it) |
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

Not taken: recipes 1.21.1 cannot run or already has (bundle, which 1.21.1 keeps behind a feature flag; the per-flower
suspicious stews 1.21.1's special recipe already makes; dyes and stews from flowers 1.21.1 lacks).

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
rope harness showing (EntityMixin for the tick, EntityRendererMixin for the ropes; llamas keep one rope, as in newer
Minecraft).

1.21.1 lacks parts of the engine this uses. Each has a small equivalent: the harness is a 1.21.1 `Equipable` with
Mojang's equip, dispense and shear rules (no `equippable` component); the camera distance comes from NeoForge's
`CalculateDetachedCameraDistanceEvent`; the precise position sync while it holds still is a teleport packet per
tick; the riding statistic, standing on top, the allow-flight kick fix and the dried ghast in fossils are mixins
(ServerPlayerMixin, EntityMixin, ServerGamePacketListenerImplMixin, NetherFossilPieceMixin); `LivingEntity.jumping` is
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
cushions (seat blocks): kept apart, no merge. Not taken: the short time a cushion dropped by lightning cannot burn.

Furnace fuel and composting follow newer Minecraft's Items class for every item of ours (`tools/fuels.py`): wool
slabs and cushions burn 50 ticks, wool stairs 100, shelves 300 (the modded-wood shelves are not Mojang items and do
not burn).

## Download

The Haven pack delivers it (`mods/haven-backports.pw.toml`).
