package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 26.x RootSystemFeature, line for line: 1.21.1's root system plus 26.x's level test (level_test_distance,
 * max_level_deviation: the four points that far out must have ground that far below and air that far above).
 */
public class RootSystemFeature extends Feature<RootSystemFeature.Config> {
    public record Config(Holder<PlacedFeature> treeFeature, int requiredVerticalSpaceForTree, int levelTestDistance, int maxLevelDeviation,
                         int rootRadius, HolderSet<Block> rootReplaceable, BlockStateProvider rootStateProvider, int rootPlacementAttempts,
                         int rootColumnMaxHeight, int hangingRootRadius, int hangingRootsVerticalSpan, BlockStateProvider hangingRootStateProvider,
                         int hangingRootPlacementAttempts, int allowedVerticalWaterForTree, BlockPredicate allowedTreePosition)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                        PlacedFeature.CODEC.fieldOf("feature").forGetter(Config::treeFeature),
                        Codec.intRange(1, 64).fieldOf("required_vertical_space_for_tree").forGetter(Config::requiredVerticalSpaceForTree),
                        Codec.intRange(0, 16).fieldOf("level_test_distance").forGetter(Config::levelTestDistance),
                        Codec.intRange(0, 64).fieldOf("max_level_deviation").forGetter(Config::maxLevelDeviation),
                        Codec.intRange(1, 64).fieldOf("root_radius").forGetter(Config::rootRadius),
                        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("root_replaceable").forGetter(Config::rootReplaceable),
                        BlockStateProvider.CODEC.fieldOf("root_state_provider").forGetter(Config::rootStateProvider),
                        Codec.intRange(1, 256).fieldOf("root_placement_attempts").forGetter(Config::rootPlacementAttempts),
                        Codec.intRange(1, 4096).fieldOf("root_column_max_height").forGetter(Config::rootColumnMaxHeight),
                        Codec.intRange(1, 64).fieldOf("hanging_root_radius").forGetter(Config::hangingRootRadius),
                        Codec.intRange(1, 16).fieldOf("hanging_roots_vertical_span").forGetter(Config::hangingRootsVerticalSpan),
                        BlockStateProvider.CODEC.fieldOf("hanging_root_state_provider").forGetter(Config::hangingRootStateProvider),
                        Codec.intRange(1, 256).fieldOf("hanging_root_placement_attempts").forGetter(Config::hangingRootPlacementAttempts),
                        Codec.intRange(1, 64).fieldOf("allowed_vertical_water_for_tree").forGetter(Config::allowedVerticalWaterForTree),
                        BlockPredicate.CODEC.fieldOf("allowed_tree_position").forGetter(Config::allowedTreePosition))
                .apply(i, Config::new));

        @Override
        public Stream<ConfiguredFeature<?, ?>> getFeatures() {
            return this.treeFeature.value().getFeatures();
        }
    }

    public RootSystemFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        if (!level.getBlockState(origin).isAir()) {
            return false;
        }
        BlockPos.MutableBlockPos working = origin.mutable();
        if (placeDirtAndTree(context.config(), level, context.chunkGenerator(), context.random(), working, origin)) {
            placeRoots(context.config(), level, context.random(), origin, working);
        }
        return true;
    }

    private static boolean spaceForTree(Config c, WorldGenLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos up = pos.mutable();
        for (int i = 1; i <= c.requiredVerticalSpaceForTree(); i++) {
            up.move(Direction.UP);
            BlockState state = level.getBlockState(up);
            if (!(state.isAir() || i + 1 <= c.allowedVerticalWaterForTree() && state.getFluidState().is(FluidTags.WATER))) {
                return false;
            }
        }
        if (c.levelTestDistance() > 0) {
            BlockPos.MutableBlockPos corner = pos.mutable();
            for (int ix = 0; ix < 4; ix++) {
                corner.move(Direction.from2DDataValue(ix), c.levelTestDistance());
                BlockState below = level.getBlockState(corner.below(c.maxLevelDeviation()));
                BlockState above = level.getBlockState(corner.above(c.maxLevelDeviation()));
                if (below.isAir() || !above.isAir()) {
                    return false;
                }
                corner.set(pos);
            }
        }
        return true;
    }

    private static boolean placeDirtAndTree(Config c, WorldGenLevel level, ChunkGenerator generator, RandomSource random,
            BlockPos.MutableBlockPos working, BlockPos pos) {
        for (int y = 0; y < c.rootColumnMaxHeight(); y++) {
            working.move(Direction.UP);
            if (level.getHeight(Heightmap.Types.WORLD_SURFACE, working.getX(), working.getZ()) < working.getY()) {
                return false;
            }
            if (c.allowedTreePosition().test(level, working) && spaceForTree(c, level, working)) {
                BlockPos below = working.below();
                if (level.getFluidState(below).is(FluidTags.LAVA) || !level.getBlockState(below).isSolid()) {
                    return false;
                }
                if (c.treeFeature().value().place(level, generator, random, working)) {
                    placeDirt(c, pos, pos.getY() + y, level, random);
                    return true;
                }
            }
        }
        return false;
    }

    private static void placeDirt(Config c, BlockPos origin, int targetHeight, WorldGenLevel level, RandomSource random) {
        int ox = origin.getX();
        int oz = origin.getZ();
        BlockPos.MutableBlockPos working = origin.mutable();
        for (int y = origin.getY(); y < targetHeight; y++) {
            working.set(ox, y, oz);
            for (int i = 0; i < c.rootPlacementAttempts(); i++) {
                working.setWithOffset(working, random.nextInt(c.rootRadius()) - random.nextInt(c.rootRadius()), 0,
                        random.nextInt(c.rootRadius()) - random.nextInt(c.rootRadius()));
                if (level.getBlockState(working).is(c.rootReplaceable())) {
                    level.setBlock(working, c.rootStateProvider().getState(random, working), 2);
                }
                working.setX(ox);
                working.setZ(oz);
            }
        }
    }

    private static void placeRoots(Config c, WorldGenLevel level, RandomSource random, BlockPos pos, BlockPos.MutableBlockPos working) {
        for (int i = 0; i < c.hangingRootPlacementAttempts(); i++) {
            working.setWithOffset(pos, random.nextInt(c.hangingRootRadius()) - random.nextInt(c.hangingRootRadius()),
                    random.nextInt(c.hangingRootsVerticalSpan()) - random.nextInt(c.hangingRootsVerticalSpan()),
                    random.nextInt(c.hangingRootRadius()) - random.nextInt(c.hangingRootRadius()));
            if (level.isEmptyBlock(working)) {
                BlockState target = c.hangingRootStateProvider().getState(random, working);
                if (target.canSurvive(level, working) && level.getBlockState(working.above()).isFaceSturdy(level, working, Direction.DOWN)) {
                    level.setBlock(working, target, 2);
                }
            }
        }
    }
}
