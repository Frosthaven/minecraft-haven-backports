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
            // copper rows: ... cut copper slab, COPPER_BARS, door, trapdoor, bulb, COPPER_CHAIN ... for every stage, waxed too
            for (String waxed : new String[]{"", "waxed_"}) {
                for (WeatherState s : WeatherState.values()) {
                    String p = waxed + (s == WeatherState.UNAFFECTED ? "" : s.getSerializedName() + "_");
                    after(e, p + "cut_copper_slab", p + "copper_bars");
                    after(e, p + "copper_bulb", p + "copper_chain");
                }
            }
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
                    "dark_oak_shelf", "mangrove_shelf", "cherry_shelf", "bamboo_shelf", "crimson_shelf", "warped_shelf"));
            ModdedShelves.WOODS.stream().filter(w -> ModList.get().isLoaded(w.mod())).forEach(w -> shelves.add(w.id()));
            after(e, "chiseled_bookshelf", shelves.toArray(String[]::new));
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
            // ... stone hoe, COPPER_SHOVEL, COPPER_PICKAXE, COPPER_AXE, COPPER_HOE, iron shovel ...
            after(e, "stone_hoe", "copper_shovel", "copper_pickaxe", "copper_axe", "copper_hoe");
        } else if (tab == CreativeModeTabs.COMBAT) {
            after(e, "stone_sword", "copper_sword");
            after(e, "stone_axe", "copper_axe");
            after(e, "leather_boots", "copper_helmet", "copper_chestplate", "copper_leggings", "copper_boots");
            after(e, "leather_horse_armor", "copper_horse_armor");
            after(e, "diamond_horse_armor", "netherite_horse_armor");
        } else if (tab == CreativeModeTabs.INGREDIENTS) {
            // ... diamond, ancient debris, COPPER_NUGGET, iron nugget ...
            after(e, "ancient_debris", "copper_nugget");
        }
    }

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
