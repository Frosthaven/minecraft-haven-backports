package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** 26.x SpeleothemFeature (1.21.1's pointed dripstone feature made general), line for line. */
public class SpeleothemFeature extends Feature<SpeleothemFeature.Config> {
    public record Config(BlockState baseBlock, BlockState pointedBlock, HolderSet<Block> replaceableBlocks, float chanceOfTallerGeneration,
                         float chanceOfDirectionalSpread, float chanceOfSpreadRadius2, float chanceOfSpreadRadius3) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                        BlockState.CODEC.fieldOf("base_block").forGetter(Config::baseBlock),
                        BlockState.CODEC.fieldOf("pointed_block").forGetter(Config::pointedBlock),
                        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("replaceable_blocks").forGetter(Config::replaceableBlocks),
                        Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance_of_taller_generation", 0.2F).forGetter(Config::chanceOfTallerGeneration),
                        Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance_of_directional_spread", 0.7F).forGetter(Config::chanceOfDirectionalSpread),
                        Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance_of_spread_radius2", 0.5F).forGetter(Config::chanceOfSpreadRadius2),
                        Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance_of_spread_radius3", 0.5F).forGetter(Config::chanceOfSpreadRadius3))
                .apply(i, Config::new));
    }

    public SpeleothemFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config c = context.config();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        Optional<Direction> tip = getTipDirection(c, level, origin, random);
        if (tip.isEmpty()) {
            return false;
        }
        createPatchOfBaseBlocks(c, level, random, origin.relative(tip.get().getOpposite()));
        int height = random.nextFloat() < c.chanceOfTallerGeneration() && SpeleothemUtils.isEmptyOrWater(level.getBlockState(origin.relative(tip.get())))
                ? 2 : 1;
        SpeleothemUtils.growSpeleothem(level, origin, tip.get(), height, false, c.baseBlock().getBlock(), c.pointedBlock().getBlock(),
                c.replaceableBlocks(), SpeleothemUtils.BaseBlockTransformer.NONE);
        return true;
    }

    private static Optional<Direction> getTipDirection(Config c, LevelAccessor level, BlockPos pos, RandomSource random) {
        boolean above = SpeleothemUtils.isBase(level.getBlockState(pos.above()), c.baseBlock().getBlock(), c.replaceableBlocks());
        boolean below = SpeleothemUtils.isBase(level.getBlockState(pos.below()), c.baseBlock().getBlock(), c.replaceableBlocks());
        if (above && below) {
            return Optional.of(random.nextBoolean() ? Direction.DOWN : Direction.UP);
        } else if (above) {
            return Optional.of(Direction.DOWN);
        }
        return below ? Optional.of(Direction.UP) : Optional.empty();
    }

    private static void createPatchOfBaseBlocks(Config c, LevelAccessor level, RandomSource random, BlockPos pos) {
        Block base = c.baseBlock().getBlock();
        SpeleothemUtils.placeBaseBlockIfPossible(level, pos, base, c.replaceableBlocks());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!(random.nextFloat() > c.chanceOfDirectionalSpread())) {
                BlockPos p1 = pos.relative(direction);
                SpeleothemUtils.placeBaseBlockIfPossible(level, p1, base, c.replaceableBlocks());
                if (!(random.nextFloat() > c.chanceOfSpreadRadius2())) {
                    BlockPos p2 = p1.relative(Direction.getRandom(random));
                    SpeleothemUtils.placeBaseBlockIfPossible(level, p2, base, c.replaceableBlocks());
                    if (!(random.nextFloat() > c.chanceOfSpreadRadius3())) {
                        SpeleothemUtils.placeBaseBlockIfPossible(level, p2.relative(Direction.getRandom(random)), base, c.replaceableBlocks());
                    }
                }
            }
        }
    }
}
