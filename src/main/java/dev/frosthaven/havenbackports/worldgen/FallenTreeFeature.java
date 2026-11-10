package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

/**
 * 26.x FallenTreeFeature, line for line. 26.x makes the feature its own config record; 1.21.1 splits a Feature and
 * its FeatureConfiguration (the same four fields, the same codec).
 */
public class FallenTreeFeature extends Feature<FallenTreeFeature.Config> {
    public record Config(BlockStateProvider trunkProvider, IntProvider logLength, List<TreeDecorator> stumpDecorators,
                         List<TreeDecorator> logDecorators) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                        BlockStateProvider.CODEC.fieldOf("trunk_provider").forGetter(Config::trunkProvider),
                        IntProvider.codec(0, 16).fieldOf("log_length").forGetter(Config::logLength),
                        TreeDecorator.CODEC.listOf().fieldOf("stump_decorators").forGetter(Config::stumpDecorators),
                        TreeDecorator.CODEC.listOf().fieldOf("log_decorators").forGetter(Config::logDecorators))
                .apply(i, Config::new));
    }

    public FallenTreeFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        this.placeFallenTree(context.config(), context.origin(), context.level(), context.random());
        return true;
    }

    private void placeFallenTree(Config c, BlockPos origin, WorldGenLevel level, RandomSource random) {
        this.placeStump(c, level, random, origin.mutable());
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int logLength = c.logLength().sample(random) - 2;
        BlockPos.MutableBlockPos logStartPos = origin.relative(direction, 2 + random.nextInt(2)).mutable();
        this.setGroundHeightForFallenLogStartPos(level, logStartPos);
        if (this.canPlaceEntireFallenLog(level, logLength, logStartPos, direction)) {
            this.placeFallenLog(c, level, random, logLength, logStartPos, direction);
        }
    }

    private void setGroundHeightForFallenLogStartPos(WorldGenLevel level, BlockPos.MutableBlockPos logStartPos) {
        logStartPos.move(Direction.UP, 1);
        for (int i = 0; i < 6; i++) {
            if (this.mayPlaceOn(level, logStartPos)) {
                return;
            }
            logStartPos.move(Direction.DOWN);
        }
    }

    private void placeStump(Config c, WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos stumpPos) {
        BlockPos stump = this.placeLogBlock(c, level, random, stumpPos, Function.identity());
        this.decorateLogs(level, random, Set.of(stump), c.stumpDecorators());
    }

    private boolean canPlaceEntireFallenLog(WorldGenLevel level, int logLength, BlockPos.MutableBlockPos logStartPos, Direction direction) {
        int gapInGround = 0;
        for (int i = 0; i < logLength; i++) {
            if (!TreeFeature.validTreePos(level, logStartPos)) {
                return false;
            }
            if (!this.isOverSolidGround(level, logStartPos)) {
                if (++gapInGround > 2) {
                    return false;
                }
            } else {
                gapInGround = 0;
            }
            logStartPos.move(direction);
        }
        logStartPos.move(direction.getOpposite(), logLength);
        return true;
    }

    private void placeFallenLog(Config c, WorldGenLevel level, RandomSource random, int logLength, BlockPos.MutableBlockPos logStartPos,
            Direction direction) {
        Set<BlockPos> fallenLog = new HashSet<>();
        for (int i = 0; i < logLength; i++) {
            fallenLog.add(this.placeLogBlock(c, level, random, logStartPos, getSidewaysStateModifier(direction)));
            logStartPos.move(direction);
        }
        this.decorateLogs(level, random, fallenLog, c.logDecorators());
    }

    private boolean mayPlaceOn(LevelAccessor level, BlockPos blockPos) {
        return TreeFeature.validTreePos(level, blockPos) && this.isOverSolidGround(level, blockPos);
    }

    private boolean isOverSolidGround(LevelAccessor level, BlockPos blockPos) {
        return level.getBlockState(blockPos.below()).isFaceSturdy(level, blockPos, Direction.UP);
    }

    private BlockPos placeLogBlock(Config c, WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos blockPos,
            Function<BlockState, BlockState> sidewaysStateModifier) {
        level.setBlock(blockPos, sidewaysStateModifier.apply(c.trunkProvider().getState(random, blockPos)), 3);
        this.markAboveForPostProcessing(level, blockPos);
        return blockPos.immutable();
    }

    private void decorateLogs(WorldGenLevel level, RandomSource random, Set<BlockPos> logs, List<TreeDecorator> decorators) {
        if (!decorators.isEmpty()) {
            BiConsumer<BlockPos, BlockState> setter = (pos, state) -> level.setBlock(pos, state, 19);
            TreeDecorator.Context ctx = new TreeDecorator.Context(level, setter, random, logs, Set.of(), Set.of());
            decorators.forEach(d -> d.place(ctx));
        }
    }

    private static Function<BlockState, BlockState> getSidewaysStateModifier(Direction direction) {
        return state -> state.trySetValue(RotatedPillarBlock.AXIS, direction.getAxis());
    }
}
