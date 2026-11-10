package dev.frosthaven.havenbackports.worldgen;

import dev.frosthaven.havenbackports.HavenBackports;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Worldgen types 26.x has and 1.21.1 lacks, each Mojang's class ported (tools/worldgen.py maps the 26.x type ids to
 * these). Names are Mojang's, in the haven_backports namespace.
 */
public final class Worldgen {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, HavenBackports.ID);
    private static final DeferredRegister<TrunkPlacerType<?>> TRUNKS = DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, HavenBackports.ID);
    private static final DeferredRegister<FoliagePlacerType<?>> FOLIAGE = DeferredRegister.create(Registries.FOLIAGE_PLACER_TYPE, HavenBackports.ID);
    private static final DeferredRegister<TreeDecoratorType<?>> DECORATORS = DeferredRegister.create(Registries.TREE_DECORATOR_TYPE, HavenBackports.ID);
    private static final DeferredRegister<BlockPredicateType<?>> PREDICATES = DeferredRegister.create(Registries.BLOCK_PREDICATE_TYPE, HavenBackports.ID);

    public static final DeferredHolder<Feature<?>, WeightedRandomSelectorFeature> WEIGHTED_RANDOM_SELECTOR =
            FEATURES.register("weighted_random_selector", WeightedRandomSelectorFeature::new);
    public static final DeferredHolder<Feature<?>, FallenTreeFeature> FALLEN_TREE = FEATURES.register("fallen_tree", FallenTreeFeature::new);
    public static final DeferredHolder<TrunkPlacerType<?>, TrunkPlacerType<PoplarTrunkPlacer>> POPLAR_TRUNK_PLACER =
            TRUNKS.register("poplar_trunk_placer", () -> new TrunkPlacerType<>(PoplarTrunkPlacer.CODEC));
    public static final DeferredHolder<FoliagePlacerType<?>, FoliagePlacerType<PoplarFoliagePlacer>> POPLAR_FOLIAGE_PLACER =
            FOLIAGE.register("poplar_foliage_placer", () -> new FoliagePlacerType<>(PoplarFoliagePlacer.CODEC));
    public static final DeferredHolder<TreeDecoratorType<?>, TreeDecoratorType<PlaceOnGroundDecorator>> PLACE_ON_GROUND =
            DECORATORS.register("place_on_ground", () -> new TreeDecoratorType<>(PlaceOnGroundDecorator.CODEC));
    public static final DeferredHolder<TreeDecoratorType<?>, TreeDecoratorType<AttachedToLogsDecorator>> ATTACHED_TO_LOGS =
            DECORATORS.register("attached_to_logs", () -> new TreeDecoratorType<>(AttachedToLogsDecorator.CODEC));
    public static final DeferredHolder<TreeDecoratorType<?>, TreeDecoratorType<ShelfMushroomDecorator>> SHELF_MUSHROOM =
            DECORATORS.register("shelf_mushroom", () -> new TreeDecoratorType<>(ShelfMushroomDecorator.CODEC));
    public static final DeferredHolder<BlockPredicateType<?>, BlockPredicateType<BelowHeightmapPredicate>> BELOW_HEIGHTMAP =
            PREDICATES.register("below_heightmap", () -> () -> BelowHeightmapPredicate.CODEC);

    private Worldgen() {}

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
        TRUNKS.register(modBus);
        FOLIAGE.register(modBus);
        DECORATORS.register(modBus);
        PREDICATES.register(modBus);
    }
}
