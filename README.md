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
| Spears | Wooden, Stone, Copper, Iron, Golden, Diamond and Netherite Spear; the Lunge enchantment (see below) |
| Music discs | Tears (a ghast killed by a player with its own fireball), Lava Chicken (a player kills a baby zombie riding a chicken), Bounce (mineshaft chests in the Sulfur Caves: the chest entry waits for that biome); Mojang's songs, lengths and comparator values |
| Banner patterns | Field Masoned (paper + bricks) and Bordure Indented (paper + vines) Banner Patterns; as in newer Minecraft, the loom offers those two patterns only with their pattern item |
| Explorer maps | Each explorer map is its own item, as in newer Minecraft: Ocean Monument, Woodland Mansion, Buried Trial Chambers, Jungle Pyramid, Swamp Hut, Desert, Plains, Savanna, Snowy and Taiga Village Maps (cartographer) and the Buried Treasure Map (shipwreck and underwater ruin chests) |
| Ancient city | Chiseled Soulshale (ancient city chests, with its own sounds; newer Minecraft's ancient city chest table) |
| Bundles | The Bundle without the experiment (string over leather; village chests), the 16 dyed Bundles (bundle + dye keeps the contents), newer Minecraft's bundle behaviour and tooltip |
| Happy ghast | Happy Ghast (ghastling and adult), Dried Ghast, Harness in 16 colors, Happy Ghast Spawn Egg |
| Copper golem | Copper Golem in every oxidation stage, Copper Golem Statue in every stage (waxed or not), Copper Golem Spawn Egg |
| Cushions | Cushion in all 16 colors (sit on it) |
| Straw bed | Straw Bed (4 from 3 hay bales) |
| Farm animal variants | Warm and cold pigs, cows and chickens; Blue Egg, Brown Egg; wolf sound variants |
| Desert mobs | Camel Husk, Parched, their Spawn Eggs |
| Recipes | Name Tag (paper + metal nugget), Saddle; stonecutter: cobblestone set from stone, every deepslate variant from deepslate |

Merged into the backported ones (Haven Consolidated): New Slab Variants' calcite, wool and concrete slabs; Chipped's calcite bricks, calcite pillar and packed ice bricks; Create's calcite pillar;
Create's and Create Deco's copper bars and Macaw's Lights' copper chain (into the waxed ones: they never oxidized);
Create's copper nugget (its nugget and ingot recipes stay, making ours); Create Stuff 'N Additions' copper tools and
armor (CSA: 131 uses, +0 damage, iron mining level, armor 2/6/5/1; Mojang: 190 uses, +1 damage, stone mining level,
armor 2/4/3/1).

Mobs pick armor as in newer Minecraft (MobMixin): the tiers are leather, copper, gold, chainmail, iron, diamond, and the roll is
newer Minecraft's. newer Minecraft's chest loot that names our items is taken whole (copper horse armor, spears; leather in place of saddles, which
newer Minecraft makes craftable). Tables that also name items not backported stay at 1.21.1: buried treasure and underwater ruins
(nautilus armor, buried treasure map) and the village weaponsmith (newer Minecraft adds a bundle, which 1.21.1 keeps behind an
experimental feature flag).

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
weapon preference (`piglin_preferred_weapons`). Iron, golden and copper spears do not smelt into nuggets: newer Minecraft's
nugget recipes also name nautilus armor.

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
cushions (seat blocks): kept apart, no merge. Not taken: the short time a cushion dropped by lightning cannot burn,
and the cushions in abandoned camp barrels (a structure 1.21.1 lacks).

## Straw bed

Mojang's StrawBedBlock with the AbstractBedBlock newer Minecraft gives every bed (`block/StrawBedBlock`): a low bed
of block models. In the overworld you sleep in it when it is dark like any bed, but it never sets your spawn point and
it breaks when you get up; in the nether and the end it breaks when used (newer Minecraft's straw_bed_rule). 1.21.1
has no bed rules, so NeoForge events carry them (PlayerSetSpawnEvent, PlayerWakeUpEvent) and it is a bed to NeoForge
(`isBed`), not a BedBlock (no block entity, no bounce, no explosion). You lie at its own height (StrawBedLivingEntityMixin) and
it counts "Sleep in a straw bed", not "Sleep in a bed", and not the bed advancement (ServerPlayerMixin). Villagers do
not claim it, as in newer Minecraft. Not taken: its loot in abandoned camps, a structure 1.21.1 lacks.

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

Furnace fuel and composting follow newer Minecraft's Items class for every item of ours (`tools/fuels.py`): wool
slabs and cushions burn 50 ticks, wool stairs 100, shelves 300 (the modded-wood shelves are not Mojang items and do
not burn).

## Not ported

The mannequin (newer Minecraft's `minecraft:mannequin` entity): a player-shaped figure for map makers that Mojang gives
no item, spawn egg, recipe, loot or structure; it exists only through `/summon`. With no way to obtain it in
survival, it is left out.

## Download

The Haven pack delivers it (`mods/haven-backports.pw.toml`).
