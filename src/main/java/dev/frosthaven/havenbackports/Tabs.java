package dev.frosthaven.havenbackports;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Creative tab places, as Mojang's 26.4 CreativeModeTabs lists them (read from the snapshot jar). Each run is inserted
 * after the vanilla item Mojang lists right before it; a run whose anchor 1.21.1 lacks in that tab goes at the end.
 */
final class Tabs {
    /** The colored-blocks tab's color order (1.21.1 and 26.4 alike), not DyeColor's id order. */
    private static final String[] PALETTE = {"white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"};

    private Tabs() {}

    static void build(BuildCreativeModeTabContentsEvent e) {
        ResourceKey<CreativeModeTab> tab = e.getTabKey();
        if (tab == CreativeModeTabs.BUILDING_BLOCKS) {
            // ... packed ice, packed ice bricks family, chiseled packed ice, snow bricks family, calcite family ...
            after(e, "packed_ice", "packed_ice_bricks", "packed_ice_brick_stairs", "packed_ice_brick_slab", "packed_ice_brick_wall",
                    "chiseled_packed_ice", "snow_bricks", "snow_brick_stairs", "snow_brick_slab", "snow_brick_wall");
            after(e, "calcite", "calcite_pillar", "calcite_stairs", "calcite_slab", "calcite_wall", "polished_calcite",
                    "polished_calcite_stairs", "polished_calcite_slab", "polished_calcite_wall", "polished_calcite_bricks",
                    "polished_calcite_brick_stairs", "polished_calcite_brick_slab", "polished_calcite_brick_wall");
            // ... cut red sandstone slab, CINNABAR set, SULFUR set (26.4 order) ...
            List<String> stones = new ArrayList<>();
            for (String n : new String[]{"cinnabar", "sulfur"}) {
                stones.addAll(List.of(n, n + "_stairs", n + "_slab", n + "_wall", "chiseled_" + n, "polished_" + n,
                        "polished_" + n + "_stairs", "polished_" + n + "_slab", "polished_" + n + "_wall",
                        n + "_bricks", n + "_brick_stairs", n + "_brick_slab", n + "_brick_wall"));
            }
            after(e, "cut_red_sandstone_slab", stones.toArray(String[]::new));
            // ... mud brick wall, RESIN_BRICKS, stairs, slab, wall, CHISELED_RESIN_BRICKS, sandstone ...
            after(e, "mud_brick_wall", "resin_bricks", "resin_brick_stairs", "resin_brick_slab", "resin_brick_wall", "chiseled_resin_bricks");
            // ... cherry button, [pale oak set], POPLAR log, wood, stripped log and wood, planks ... button
            for (Woods.Wood w : Woods.all()) {
                String n = w.name();
                after(e, "cherry_button", n + "_log", n + "_wood", "stripped_" + n + "_log", "stripped_" + n + "_wood", n + "_planks",
                        n + "_stairs", n + "_slab", n + "_fence", n + "_fence_gate", n + "_door", n + "_trapdoor", n + "_pressure_plate", n + "_button");
            }
            // copper rows: ... cut copper slab, COPPER_BARS, door, trapdoor, bulb, COPPER_CHAIN ... for every stage, waxed too
            for (String waxed : new String[]{"", "waxed_"}) {
                for (WeatherState s : WeatherState.values()) {
                    String p = waxed + (s == WeatherState.UNAFFECTED ? "" : s.getSerializedName() + "_");
                    after(e, p + "cut_copper_slab", p + "copper_bars");
                    after(e, p + "copper_bulb", p + "copper_chain");
                }
            }
        } else if (tab == CreativeModeTabs.NATURAL_BLOCKS) {
            // ... pointed dripstone, prismarine, CINNABAR, SULFUR, SULFUR_SPIKE, POTENT_SULFUR, magma block ...
            after(e, "prismarine", "cinnabar", "sulfur", "sulfur_spike", "potent_sulfur");
            // ... packed ice, blue ice, BRITTLE_ICE, ICICLE, ICE_CRYSTAL ...
            after(e, "blue_ice", "brittle_ice", "icicle", "ice_crystal");
            // ... cherry log, [pale oak log], POPLAR_LOG, mushroom stem; cherry leaves, ..., poplar leaves; cherry sapling,
            // ..., POPLAR_SAPLING; red mushroom, SHELF_MUSHROOM; fern, SHORT_DRY_GRASS, BUSH, RED_SHRUB; pink petals,
            // WILDFLOWERS, LEAF_LITTER; spore blossom, FIREFLY_BUSH; large fern, TALL_DRY_GRASS
            after(e, "cherry_log", "poplar_log");
            after(e, "cherry_leaves", "red_poplar_leaves", "orange_poplar_leaves", "yellow_poplar_leaves");
            after(e, "cherry_sapling", "poplar_sapling");
            after(e, "cherry_log", "pale_oak_log");   // inserted after cherry: before poplar, as 26.4
            after(e, "cherry_leaves", "pale_oak_leaves");
            after(e, "cherry_sapling", "pale_oak_sapling");
            // ... moss carpet, PALE_MOSS_BLOCK, PALE_MOSS_CARPET, PALE_HANGING_MOSS, stone ...; torchflower,
            // CACTUS_FLOWER, CLOSED_EYEBLOSSOM, OPEN_EYEBLOSSOM, wither rose; honey block, RESIN_BLOCK, froglights
            after(e, "moss_carpet", "pale_moss_block", "pale_moss_carpet", "pale_hanging_moss");
            after(e, "torchflower", "cactus_flower", "closed_eyeblossom", "open_eyeblossom");
            after(e, "honey_block", "resin_block");
            after(e, "red_mushroom", "shelf_mushroom");
            after(e, "fern", "short_dry_grass", "bush", "red_shrub");
            after(e, "pink_petals", "wildflowers", "leaf_litter");
            after(e, "spore_blossom", "firefly_bush");
            after(e, "large_fern", "tall_dry_grass");
        } else if (tab == CreativeModeTabs.FOOD_AND_DRINKS) {
            after(e, "beetroot", "golden_dandelion");   // 26.x: ... poisonous potato, beetroot, GOLDEN_DANDELION, dried kelp ...
        } else if (tab == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            // torch, soul torch, COPPER_TORCH, redstone torch, lantern, soul lantern, COPPER_LANTERN, chain, COPPER_CHAIN
            after(e, "soul_torch", "copper_torch");
            List<String> lanterns = new ArrayList<>(), chains = new ArrayList<>();
            for (String waxed : new String[]{"", "waxed_"}) {
                for (WeatherState s : WeatherState.values()) {
                    String p = waxed + (s == WeatherState.UNAFFECTED ? "" : s.getSerializedName() + "_");
                    lanterns.add(p + "copper_lantern");
                    chains.add(p + "copper_chain");
                }
            }
            after(e, "soul_lantern", lanterns.toArray(String[]::new));
            after(e, "chain", chains.toArray(String[]::new));
            // ... chest, COPPER_CHEST (each stage, then waxed), barrel ...
            after(e, "chest", Chests.ALL.stream().map(c -> c.getId().getPath()).toArray(String[]::new));
            // bookshelf, chiseled bookshelf, OAK_SHELF ... WARPED_SHELF (pale oak and poplar come with their woods)
            // ... then the modded woods' shelves, for the mods that are loaded
            List<String> shelves = new ArrayList<>(List.of("oak_shelf", "spruce_shelf", "birch_shelf", "jungle_shelf", "acacia_shelf",
                    "dark_oak_shelf", "mangrove_shelf", "cherry_shelf"));
            Woods.all().reversed().forEach(w -> shelves.add(w.name() + "_shelf"));
            shelves.addAll(List.of("bamboo_shelf", "crimson_shelf", "warped_shelf"));
            ModdedShelves.WOODS.stream().filter(w -> ModList.get().isLoaded(w.mod())).forEach(w -> shelves.add(w.id()));
            after(e, "chiseled_bookshelf", shelves.toArray(String[]::new));
            // ... cherry sign, cherry hanging sign, [pale oak], POPLAR_SIGN, POPLAR_HANGING_SIGN, bamboo sign ...
            List<String> signs = new ArrayList<>();
            Woods.all().reversed().forEach(w -> signs.addAll(List.of(w.name() + "_sign", w.name() + "_hanging_sign")));
            after(e, "cherry_hanging_sign", signs.toArray(String[]::new));
        } else if (tab == CreativeModeTabs.REDSTONE_BLOCKS) {
            // ... barrel, chiseled bookshelf, OAK_SHELF, furnace ...
            after(e, "chest", "waxed_copper_chest");
            after(e, "chiseled_bookshelf", "oak_shelf");
        } else if (tab == CreativeModeTabs.COLORED_BLOCKS) {
            // WOOL, WOOL_STAIRS, WOOL_SLAB, CARPET ... CONCRETE, CONCRETE_STAIRS, CONCRETE_SLAB, CONCRETE_POWDER
            List<String> wool = new ArrayList<>(), concrete = new ArrayList<>();
            for (String c : PALETTE) wool.add(c + "_wool_stairs");
            for (String c : PALETTE) wool.add(c + "_wool_slab");
            for (String c : PALETTE) concrete.add(c + "_concrete_stairs");
            for (String c : PALETTE) concrete.add(c + "_concrete_slab");
            after(e, "pink_wool", wool.toArray(String[]::new));
            after(e, "pink_concrete", concrete.toArray(String[]::new));
        } else if (tab == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            after(e, "music_disc_pigstep", "music_disc_flurry");   // 26.x: after tears, lava chicken, bounce (not in 1.21.1)
            // ... stone hoe, COPPER_SHOVEL, COPPER_PICKAXE, COPPER_AXE, COPPER_HOE, iron shovel ...
            after(e, "stone_hoe", "copper_shovel", "copper_pickaxe", "copper_axe", "copper_hoe");
            // ... cherry chest boat, [pale oak boats], POPLAR_BOAT, POPLAR_CHEST_BOAT, bamboo raft ...
            List<String> boats = new ArrayList<>();
            Woods.all().reversed().forEach(w -> boats.addAll(List.of(w.name() + "_boat", w.name() + "_chest_boat")));
            after(e, "cherry_chest_boat", boats.toArray(String[]::new));
            // ... axolotl bucket, tadpole bucket, SULFUR_CUBE_BUCKET ...
            after(e, "tadpole_bucket", "sulfur_cube_bucket");
        } else if (tab == CreativeModeTabs.COMBAT) {
            after(e, "snowball", "ice_ball");
            after(e, "stone_sword", "copper_sword");
            after(e, "stone_axe", "copper_axe");
            after(e, "leather_boots", "copper_helmet", "copper_chestplate", "copper_leggings", "copper_boots");
            after(e, "leather_horse_armor", "copper_horse_armor");
            after(e, "diamond_horse_armor", "netherite_horse_armor");
        } else if (tab == CreativeModeTabs.SPAWN_EGGS) {
            after(e, "drowned_spawn_egg", "frostbite_spawn_egg");
            // ... trial spawner, CREAKING_HEART ...; ... breeze, CREAKING_SPAWN_EGG ...; ... sniffer,
            // SULFUR_CUBE_SPAWN_EGG ... (26.4 lists eggs by name)
            after(e, "trial_spawner", "creaking_heart");
            after(e, "breeze_spawn_egg", "creaking_spawn_egg");
            after(e, "sniffer_spawn_egg", "sulfur_cube_spawn_egg");
        } else if (tab == CreativeModeTabs.INGREDIENTS) {
            after(e, "snowball", "ice_ball");
            // ... diamond, ancient debris, COPPER_NUGGET, iron nugget ...; honeycomb, RESIN_CLUMP; nether brick, RESIN_BRICK
            after(e, "ancient_debris", "copper_nugget");
            after(e, "honeycomb", "resin_clump");
            after(e, "nether_brick", "resin_brick");
        }
    }

    // Woods.all() is in registration order (poplar, pale oak); 26.4 lists pale oak first, so runs that list every wood
    // take it reversed, and runs inserted one wood at a time after the same anchor take it as it is.

    /** Inserts our items, in order, after the vanilla item; at the tab's end when 1.21.1 lacks that item there. */
    private static void after(BuildCreativeModeTabContentsEvent e, String vanilla, String... ours) {
        Item anchorItem = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(vanilla));
        ItemStack anchor = new ItemStack(anchorItem);
        boolean anchored = e.getParentEntries().contains(anchor);
        for (String id : ours) {
            ItemStack stack = new ItemStack(Content.ITEM.get(id).get());
            if (anchored) {
                e.insertAfter(anchor, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                anchor = stack;
            } else {
                e.accept(stack);
            }
        }
    }
}
