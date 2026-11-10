package dev.frosthaven.havenbackports.map;

import dev.frosthaven.havenbackports.HavenBackports;
import java.util.List;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Explorer maps as 26.4 makes them: one MapItem per destination (Items.mapProperties: an empty MAP_DECORATIONS
 * component), filled by the 26.x loot functions ported here and sold by the 26.x cartographer (CartographerTrades).
 * Their sources are in tools/maps.py. The 26.x MapDecorationTypes 1.21.1 lacks register here under our ids (the same
 * name and asset as 26.x, haven_backports namespace; tools/maps.py checks the list against 26.x).
 */
public final class ExplorerMaps {
    public static final List<String> IDS = List.of("ocean_monument_map", "woodland_mansion_map", "buried_trial_chambers_map",
            "jungle_pyramid_map", "swamp_hut_map", "desert_village_map", "plains_village_map", "savanna_village_map",
            "snowy_village_map", "taiga_village_map", "buried_treasure_map", "abandoned_camp_map", "buried_ancient_city_map",
            "buried_mineshaft_map", "desert_pyramid_map", "warm_ocean_ruins_map");
    /** 26.x ItemTags.CLONABLE_MAPS: what the map cloning recipe copies (1.21.1: minecraft:filled_map only). */
    public static final TagKey<Item> CLONABLE_MAPS = ItemTags.create(ResourceLocation.withDefaultNamespace("clonable_maps"));

    static final DeferredRegister<MapDecorationType> DECORATIONS = DeferredRegister.create(Registries.MAP_DECORATION_TYPE, HavenBackports.ID);
    static {
        decoration("abandoned_camp", "abandoned_camp");
        decoration("ancient_city", "ancient_city");
        decoration("desert_pyramid", "desert_pyramid");
        decoration("mineshaft", "mineshaft");
        decoration("ocean_ruin_warm", "warm_ocean_ruins");
    }
    static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, HavenBackports.ID);
    static final DeferredRegister<ItemSubPredicate.Type<?>> PREDICATES = DeferredRegister.create(Registries.ITEM_SUB_PREDICATE_TYPE, HavenBackports.ID);
    static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<ExplorationMapFunction>> EXPLORATION_MAP =
            FUNCTIONS.register("exploration_map", () -> new LootItemFunctionType<>(ExplorationMapFunction.CODEC));
    static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<FilteredFunction>> FILTERED =
            FUNCTIONS.register("filtered", () -> new LootItemFunctionType<>(FilteredFunction.CODEC));
    static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<DiscardItem>> DISCARD =
            FUNCTIONS.register("discard", () -> new LootItemFunctionType<>(DiscardItem.CODEC));
    static final DeferredHolder<ItemSubPredicate.Type<?>, ItemSubPredicate.Type<MapIdPredicate>> MAP_ID =
            PREDICATES.register("map_id", () -> new ItemSubPredicate.Type<>(MapIdPredicate.CODEC));

    private ExplorerMaps() {}

    /**
     * 26.x MapDecorationTypes.register(name, asset, showOnItemFrame true, trackCount false). 1.21.1's record also holds
     * a map color (26.x has none: the item texture carries it, so none) and explorationMapElement, true as 1.21.1
     * gives its own explorer decorations (its register passes trackCount and explorationMapElement swapped).
     */
    private static void decoration(String name, String asset) {
        DECORATIONS.register(name, () -> new MapDecorationType(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, asset),
                true, MapDecorationType.NO_MAP_COLOR, true, false));
    }

    /** Registers the loot types and hooks the trades (the items register with the mod's others, HavenBackports). */
    public static void register(IEventBus modBus) {
        DECORATIONS.register(modBus);
        FUNCTIONS.register(modBus);
        PREDICATES.register(modBus);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, VillagerTradesEvent.class, CartographerTrades::replace);
    }

    public static Item.Properties properties() {
        return new Item.Properties().component(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
    }

    /**
     * A 26.x map item. 1.21.1's ItemStack names the map id under the name only for minecraft:filled_map; 26.x shows
     * it for every item with a map id (addToTooltip(MAP_ID)), so the line is added here, in the same place.
     */
    public static final class ExplorerMapItem extends MapItem {
        public ExplorerMapItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
            MapId id = stack.get(DataComponents.MAP_ID);
            if (!flag.isAdvanced() && !stack.has(DataComponents.CUSTOM_NAME) && id != null) {
                lines.add(MapItem.getTooltipForId(id));
            }
            super.appendHoverText(stack, context, lines, flag);
        }
    }
}
