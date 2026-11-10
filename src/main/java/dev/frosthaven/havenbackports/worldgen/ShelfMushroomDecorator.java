package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Dappled;
import dev.frosthaven.havenbackports.block.ShelfMushroomBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/** 26.x ShelfMushroomDecorator, line for line. */
public class ShelfMushroomDecorator extends TreeDecorator {
    public static final MapCodec<ShelfMushroomDecorator> CODEC = Codec.floatRange(0.0F, 1.0F).fieldOf("probability")
            .xmap(ShelfMushroomDecorator::new, d -> d.placementProbability);
    private final float placementProbability;

    public ShelfMushroomDecorator(float probability) {
        this.placementProbability = probability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return Worldgen.SHELF_MUSHROOM.get();
    }

    @Override
    public void place(Context context) {
        RandomSource random = context.random();
        if (!(random.nextFloat() >= this.placementProbability)) {
            List<BlockPos> logs = context.logs();
            if (!logs.isEmpty()) {
                if (isFallenLog(logs)) {
                    placeOnFallenLog(context, logs, random);
                } else {
                    placeOnStandingTree(context, logs, random);
                }
            }
        }
    }

    private static void placeOnStandingTree(Context context, List<BlockPos> logs, RandomSource random) {
        Direction[] directions = pickTwoPerpendicularDirections(random);
        int treeBaseY = logs.getFirst().getY();
        for (BlockPos logPos : logs) {
            if (isWithinDecoratableHeight(logPos, treeBaseY)) {
                for (Direction facing : directions) {
                    if (!(random.nextFloat() > 0.25F) && tryPlaceMushroomOnStandingTree(context, logPos, facing, random)) {
                        break;
                    }
                }
            }
        }
    }

    private static void placeOnFallenLog(Context context, List<BlockPos> logs, RandomSource random) {
        Direction[] directions = perpendicularToFallenLog(logs);
        for (BlockPos logPos : logs) {
            for (Direction facing : directions) {
                if (!(random.nextFloat() > 0.25F)) {
                    tryPlaceMushroomOnFallenTree(context, logPos, facing, random);
                }
            }
        }
    }

    private static boolean tryPlaceMushroomOnStandingTree(Context context, BlockPos logPos, Direction facing, RandomSource random) {
        BlockPos mushroomPos = mushroomPosFor(logPos, facing);
        if (!isBlockReplaceableWithShelfMushroom(context, mushroomPos)) {
            return false;
        } else if (hasShelfMushroomAt(context, mushroomPos.below())) {
            return false;
        }
        placeMushroom(context, mushroomPos, facing, random);
        return true;
    }

    private static void tryPlaceMushroomOnFallenTree(Context context, BlockPos logPos, Direction facing, RandomSource random) {
        BlockPos mushroomPos = mushroomPosFor(logPos, facing);
        if (isBlockReplaceableWithShelfMushroom(context, mushroomPos)
                && !hasHorizontallyAdjacentShelfMushroom(context, mushroomPos) && !hasHorizontallyAdjacentShelfMushroom(context, logPos)) {
            placeMushroom(context, mushroomPos, facing, random);
        }
    }

    private static boolean isFallenLog(List<BlockPos> logs) {
        return logs.getFirst().getY() == logs.getLast().getY();
    }

    private static Direction[] pickTwoPerpendicularDirections(RandomSource random) {
        Direction first = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        return new Direction[]{first, first.getClockWise()};
    }

    private static Direction[] perpendicularToFallenLog(List<BlockPos> logs) {
        BlockPos first = logs.getFirst();
        BlockPos last = logs.getLast();
        Direction.Axis logAxis = first.getX() != last.getX() ? Direction.Axis.X : Direction.Axis.Z;
        return logAxis == Direction.Axis.X ? new Direction[]{Direction.NORTH, Direction.SOUTH} : new Direction[]{Direction.EAST, Direction.WEST};
    }

    private static boolean isWithinDecoratableHeight(BlockPos pos, int treeBaseY) {
        int dy = pos.getY() - treeBaseY;
        return dy >= 1 && dy <= 4;
    }

    private static BlockPos mushroomPosFor(BlockPos logPos, Direction facing) {
        return logPos.offset(facing.getStepX(), 0, facing.getStepZ());
    }

    private static void placeMushroom(Context context, BlockPos pos, Direction facing, RandomSource random) {
        context.setBlock(pos, Dappled.SHELF_MUSHROOM.get().defaultBlockState().setValue(ShelfMushroomBlock.AGE, random.nextInt(2))
                .setValue(ShelfMushroomBlock.FACING, facing));
    }

    private static boolean hasShelfMushroomAt(Context context, BlockPos pos) {
        return Decorators.checkBlock(context, pos, state -> state.is(Dappled.SHELF_MUSHROOM.get()));
    }

    private static boolean hasHorizontallyAdjacentShelfMushroom(Context context, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (hasShelfMushroomAt(context, pos.relative(dir))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBlockReplaceableWithShelfMushroom(Context context, BlockPos pos) {
        return Decorators.isReplaceable(context, pos) && !Decorators.isWaterOrWaterNearby(context, pos);
    }
}
