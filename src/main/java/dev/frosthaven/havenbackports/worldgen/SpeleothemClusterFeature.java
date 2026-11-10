package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.ClampedNormalFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** 26.x SpeleothemClusterFeature (1.21.1's dripstone cluster made general, with placement options), line for line. */
public class SpeleothemClusterFeature extends Feature<SpeleothemClusterFeature.Config> {
    public enum PlacementMode implements StringRepresentable {
        FLOOR_AND_CEILING("floor_and_ceiling"), FLOOR_ONLY("floor_only"), CEILING_ONLY("ceiling_only");

        static final Codec<PlacementMode> CODEC = StringRepresentable.fromEnum(PlacementMode::values);
        private final String name;

        PlacementMode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public record PlacementOptions(PlacementMode placementMode, SpeleothemUtils.BaseBlockTransformer baseBlockTransformer, boolean allowWaterPlacement) {
        static final PlacementOptions DEFAULT = new PlacementOptions(PlacementMode.FLOOR_AND_CEILING, SpeleothemUtils.BaseBlockTransformer.NONE, true);
        static final Codec<PlacementOptions> CODEC = RecordCodecBuilder.create(i -> i.group(
                        PlacementMode.CODEC.fieldOf("placement_mode").forGetter(PlacementOptions::placementMode),
                        SpeleothemUtils.BaseBlockTransformer.CODEC.fieldOf("base_block_transformer").forGetter(PlacementOptions::baseBlockTransformer),
                        Codec.BOOL.fieldOf("allow_water_placement").forGetter(PlacementOptions::allowWaterPlacement))
                .apply(i, PlacementOptions::new));
    }

    public record Config(BlockState baseBlock, BlockState pointedBlock, HolderSet<Block> replaceableBlocks, int floorToCeilingSearchRange,
                         IntProvider height, IntProvider radius, int maxStalagmiteStalactiteHeightDiff, int heightDeviation,
                         IntProvider layerThickness, FloatProvider density, FloatProvider wetness, float chanceAtMaxDistance,
                         int maxDistanceFromEdge, int maxDistanceFromCenter, PlacementOptions placementOptions) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                        BlockState.CODEC.fieldOf("base_block").forGetter(Config::baseBlock),
                        BlockState.CODEC.fieldOf("pointed_block").forGetter(Config::pointedBlock),
                        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("replaceable_blocks").forGetter(Config::replaceableBlocks),
                        Codec.intRange(1, 512).fieldOf("floor_to_ceiling_search_range").forGetter(Config::floorToCeilingSearchRange),
                        IntProvider.codec(1, 128).fieldOf("height").forGetter(Config::height),
                        IntProvider.codec(1, 128).fieldOf("radius").forGetter(Config::radius),
                        Codec.intRange(0, 64).fieldOf("max_stalagmite_stalactite_height_diff").forGetter(Config::maxStalagmiteStalactiteHeightDiff),
                        Codec.intRange(1, 64).fieldOf("height_deviation").forGetter(Config::heightDeviation),
                        IntProvider.codec(0, 128).fieldOf("speleothem_block_layer_thickness").forGetter(Config::layerThickness),
                        FloatProvider.codec(0.0F, 2.0F).fieldOf("density").forGetter(Config::density),
                        FloatProvider.codec(0.0F, 2.0F).fieldOf("wetness").forGetter(Config::wetness),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("chance_of_speleothem_at_max_distance_from_center").forGetter(Config::chanceAtMaxDistance),
                        Codec.intRange(1, 64).fieldOf("max_distance_from_edge_affecting_chance_of_speleothem").forGetter(Config::maxDistanceFromEdge),
                        Codec.intRange(1, 64).fieldOf("max_distance_from_center_affecting_height_bias").forGetter(Config::maxDistanceFromCenter),
                        PlacementOptions.CODEC.optionalFieldOf("placement_options", PlacementOptions.DEFAULT).forGetter(Config::placementOptions))
                .apply(i, Config::new));
    }

    public SpeleothemClusterFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config c = context.config();
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        if (!SpeleothemUtils.isEmptyOrWater(level, origin)) {
            return false;
        }
        int height = c.height().sample(random);
        float wetness = c.wetness().sample(random);
        float density = c.density().sample(random);
        int xRadius = c.radius().sample(random);
        int zRadius = c.radius().sample(random);
        for (int dx = -xRadius; dx <= xRadius; dx++) {
            for (int dz = -zRadius; dz <= zRadius; dz++) {
                double chance = chanceOfStalagmiteOrStalactite(c, xRadius, zRadius, dx, dz);
                placeColumn(c, level, random, origin.offset(dx, 0, dz), dx, dz, wetness, chance, height, density);
            }
        }
        return true;
    }

    private static int availableHeight(WorldGenLevel level, BlockPos base, Direction direction, int desired, Predicate<BlockState> canPlace) {
        BlockPos pos = base;
        int available = 0;
        while (available < desired && canPlace.test(level.getBlockState(pos))) {
            available++;
            pos = pos.relative(direction);
        }
        return available;
    }

    private static void placeColumn(Config c, WorldGenLevel level, RandomSource random, BlockPos pos, int dx, int dz, float chanceOfWater,
            double chance, int clusterHeight, float density) {
        Predicate<BlockState> canPlace = s -> c.placementOptions().allowWaterPlacement() ? SpeleothemUtils.isEmptyOrWater(s) : s.isAir();
        Optional<Column> base = Column.scan(level, pos, c.floorToCeilingSearchRange(), canPlace, SpeleothemUtils::isNeitherEmptyNorWater);
        if (base.isEmpty()) {
            return;
        }
        Column scanned = base.get();
        Column placement = filterPlacementColumn(c, scanned);
        OptionalInt ceiling = placement.getCeiling();
        OptionalInt baseFloor = placement.getFloor();
        if (ceiling.isEmpty() && baseFloor.isEmpty()) {
            return;
        }
        boolean wantPool = random.nextFloat() < chanceOfWater;
        Column column;
        if (wantPool && baseFloor.isPresent() && canPlacePool(c, level, pos.atY(baseFloor.getAsInt()))) {
            int floorY = baseFloor.getAsInt();
            column = placement.withFloor(OptionalInt.of(floorY - 1));
            level.setBlock(pos.atY(floorY), Blocks.WATER.defaultBlockState(), 2);
        } else {
            column = placement;
        }
        OptionalInt floor = column.getFloor();
        boolean wantStalactite = random.nextDouble() < chance;
        int stalactiteHeight;
        if (ceiling.isPresent() && wantStalactite && !isLava(level, pos.atY(ceiling.getAsInt()))) {
            int thickness = c.layerThickness().sample(random);
            replaceBlocksWithBaseBlocks(c, level, pos.atY(ceiling.getAsInt()), thickness, Direction.UP);
            int maxHeight = maxHeightForThisColumn(c, scanned, clusterHeight);
            if (c.placementOptions().placementMode() == PlacementMode.FLOOR_AND_CEILING && floor.isPresent()) {
                maxHeight = Math.min(clusterHeight, ceiling.getAsInt() - floor.getAsInt());
            }
            stalactiteHeight = speleothemHeight(c, random, dx, dz, density, maxHeight);
        } else {
            stalactiteHeight = 0;
        }
        boolean wantStalagmite = random.nextDouble() < chance;
        int stalagmiteHeight;
        if (floor.isPresent() && wantStalagmite && !isLava(level, pos.atY(floor.getAsInt()))) {
            int thickness = c.layerThickness().sample(random);
            replaceBlocksWithBaseBlocks(c, level, pos.atY(floor.getAsInt()), thickness, Direction.DOWN);
            if (ceiling.isPresent()) {
                stalagmiteHeight = Math.max(0, stalactiteHeight + Mth.randomBetweenInclusive(random, -c.maxStalagmiteStalactiteHeightDiff(),
                        c.maxStalagmiteStalactiteHeightDiff()));
            } else {
                stalagmiteHeight = speleothemHeight(c, random, dx, dz, density, maxHeightForThisColumn(c, scanned, clusterHeight));
            }
        } else {
            stalagmiteHeight = 0;
        }
        int actualStalactite;
        int actualStalagmite;
        if (ceiling.isPresent() && floor.isPresent() && ceiling.getAsInt() - stalactiteHeight <= floor.getAsInt() + stalagmiteHeight) {
            int floorY = floor.getAsInt();
            int ceilingY = ceiling.getAsInt();
            int lowestBottom = Math.max(ceilingY - stalactiteHeight, floorY + 1);
            int highestTop = Math.min(floorY + stalagmiteHeight, ceilingY - 1);
            int bottom = Mth.randomBetweenInclusive(random, lowestBottom, highestTop + 1);
            actualStalactite = ceilingY - bottom;
            actualStalagmite = bottom - 1 - floorY;
        } else {
            actualStalactite = stalactiteHeight;
            actualStalagmite = stalagmiteHeight;
        }
        if (ceiling.isPresent()) {
            actualStalactite = availableHeight(level, pos.atY(ceiling.getAsInt() - 1), Direction.DOWN, actualStalactite, canPlace);
        }
        if (floor.isPresent()) {
            actualStalagmite = availableHeight(level, pos.atY(floor.getAsInt() + 1), Direction.UP, actualStalagmite, canPlace);
        }
        boolean mergeTips = random.nextBoolean() && actualStalactite > 0 && actualStalagmite > 0 && column.getHeight().isPresent()
                && actualStalactite + actualStalagmite == column.getHeight().getAsInt();
        Block baseBlock = c.baseBlock().getBlock();
        Block pointed = c.pointedBlock().getBlock();
        if (ceiling.isPresent()) {
            SpeleothemUtils.growSpeleothem(level, pos.atY(ceiling.getAsInt() - 1), Direction.DOWN, actualStalactite, mergeTips, baseBlock, pointed,
                    c.replaceableBlocks(), c.placementOptions().baseBlockTransformer());
        }
        if (floor.isPresent()) {
            SpeleothemUtils.growSpeleothem(level, pos.atY(floor.getAsInt() + 1), Direction.UP, actualStalagmite, mergeTips, baseBlock, pointed,
                    c.replaceableBlocks(), c.placementOptions().baseBlockTransformer());
        }
    }

    private static Column filterPlacementColumn(Config c, Column scanned) {
        OptionalInt floor = c.placementOptions().placementMode() != PlacementMode.CEILING_ONLY ? scanned.getFloor() : OptionalInt.empty();
        OptionalInt ceiling = c.placementOptions().placementMode() != PlacementMode.FLOOR_ONLY ? scanned.getCeiling() : OptionalInt.empty();
        return Column.create(floor, ceiling);
    }

    private static int maxHeightForThisColumn(Config c, Column scanned, int clusterHeight) {
        return c.placementOptions().placementMode() != PlacementMode.FLOOR_AND_CEILING && scanned.getHeight().isPresent()
                ? Math.min(clusterHeight, scanned.getHeight().getAsInt()) : clusterHeight;
    }

    private static boolean isLava(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.LAVA);
    }

    private static int speleothemHeight(Config c, RandomSource random, int dx, int dz, float density, int maxHeight) {
        if (random.nextFloat() > density) {
            return 0;
        }
        int distance = Math.abs(dx) + Math.abs(dz);
        float mean = (float) Mth.clampedMap(distance, 0.0, c.maxDistanceFromCenter(), maxHeight / 2.0, 0.0);
        return (int) ClampedNormalFloat.sample(random, mean, c.heightDeviation(), 0.0F, maxHeight);
    }

    private static boolean canPlacePool(Config c, WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER) || state.is(c.baseBlock().getBlock()) || state.is(c.pointedBlock().getBlock())) {
            return false;
        }
        if (level.getBlockState(pos.above()).getFluidState().is(FluidTags.WATER)) {
            return false;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!canBeAdjacentToWater(level, pos.relative(direction))) {
                return false;
            }
        }
        return canBeAdjacentToWater(level, pos.below());
    }

    private static boolean canBeAdjacentToWater(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.getFluidState().is(FluidTags.WATER);
    }

    private static void replaceBlocksWithBaseBlocks(Config c, WorldGenLevel level, BlockPos first, int maxCount, Direction direction) {
        BlockPos.MutableBlockPos pos = first.mutable();
        for (int i = 0; i < maxCount; i++) {
            if (!SpeleothemUtils.placeBaseBlockIfPossible(level, pos, c.baseBlock().getBlock(), c.replaceableBlocks())) {
                return;
            }
            pos.move(direction);
        }
    }

    private static double chanceOfStalagmiteOrStalactite(Config c, int xRadius, int zRadius, int dx, int dz) {
        int fromEdge = Math.min(xRadius - Math.abs(dx), zRadius - Math.abs(dz));
        return Mth.clampedMap((float) fromEdge, 0.0F, (float) c.maxDistanceFromEdge(), c.chanceAtMaxDistance(), 1.0F);
    }
}
