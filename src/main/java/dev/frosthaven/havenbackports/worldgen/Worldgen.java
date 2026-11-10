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
    private static final DeferredRegister<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, HavenBackports.ID);
    private static final DeferredRegister<net.minecraft.util.valueproviders.IntProviderType<?>> INT_PROVIDERS =
            DeferredRegister.create(Registries.INT_PROVIDER_TYPE, HavenBackports.ID);
    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.SurfaceRules.ConditionSource>>
            CONDITIONS = DeferredRegister.create(Registries.MATERIAL_CONDITION, HavenBackports.ID);

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

    public static final DeferredHolder<Feature<?>, LakeFeature> LAKE = FEATURES.register("lake", LakeFeature::new);
    public static final DeferredHolder<Feature<?>, RootSystemFeature> ROOT_SYSTEM = FEATURES.register("root_system", RootSystemFeature::new);
    public static final DeferredHolder<Feature<?>, SequenceFeature> SEQUENCE = FEATURES.register("sequence", SequenceFeature::new);
    public static final DeferredHolder<Feature<?>, TemplateFeature> TEMPLATE = FEATURES.register("template", TemplateFeature::new);
    public static final DeferredHolder<Feature<?>, SpeleothemFeature> SPELEOTHEM = FEATURES.register("speleothem", SpeleothemFeature::new);
    public static final DeferredHolder<Feature<?>, SpeleothemClusterFeature> SPELEOTHEM_CLUSTER =
            FEATURES.register("speleothem_cluster", SpeleothemClusterFeature::new);
    public static final DeferredHolder<BlockPredicateType<?>, BlockPredicateType<MatchingFluidsPredicate>> MATCHING_FLUIDS =
            PREDICATES.register("matching_fluids", () -> () -> MatchingFluidsPredicate.CODEC);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>,
            net.minecraft.world.level.levelgen.placement.PlacementModifierType<OffsetPlacement>> OFFSET =
            PLACEMENTS.register("offset", () -> () -> OffsetPlacement.CODEC);
    public static final DeferredHolder<net.minecraft.util.valueproviders.IntProviderType<?>,
            net.minecraft.util.valueproviders.IntProviderType<TrapezoidInt>> TRAPEZOID = INT_PROVIDERS.register("trapezoid", () -> () -> TrapezoidInt.CODEC);

    public static final DeferredHolder<TrunkPlacerType<?>, TrunkPlacerType<DarkOakTrunkPlacer>> DARK_OAK_TRUNK_PLACER =
            TRUNKS.register("dark_oak_trunk_placer", () -> new TrunkPlacerType<>(DarkOakTrunkPlacer.CODEC));
    public static final DeferredHolder<Feature<?>, SimpleBlockFeature> SIMPLE_BLOCK = FEATURES.register("simple_block", SimpleBlockFeature::new);
    public static final DeferredHolder<TreeDecoratorType<?>, TreeDecoratorType<PaleMossDecorator>> PALE_MOSS =
            DECORATORS.register("pale_moss", () -> new TreeDecoratorType<>(PaleMossDecorator.CODEC));
    public static final DeferredHolder<TreeDecoratorType<?>, TreeDecoratorType<CreakingHeartDecorator>> CREAKING_HEART =
            DECORATORS.register("creaking_heart", () -> new TreeDecoratorType<>(CreakingHeartDecorator.CODEC));

    static {
        CONDITIONS.register("noise_threshold_3d", () -> NoiseThreshold3d.CODEC.codec());
    }

    private Worldgen() {}

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
        TRUNKS.register(modBus);
        FOLIAGE.register(modBus);
        DECORATORS.register(modBus);
        PREDICATES.register(modBus);
        PLACEMENTS.register(modBus);
        INT_PROVIDERS.register(modBus);
        CONDITIONS.register(modBus);
    }
}
