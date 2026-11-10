package dev.frosthaven.havenbackports.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

/** 26.x TreeDecorator.Context helpers 1.21.1's Context lacks, and 26.x TreeFeature.getLowestTrunkOrRootOfTree. */
final class Decorators {
    private Decorators() {}

    static boolean checkBlock(TreeDecorator.Context context, BlockPos pos, Predicate<BlockState> predicate) {
        return context.level().isStateAtPosition(pos, predicate);
    }

    static boolean isReplaceable(TreeDecorator.Context context, BlockPos pos) {
        return checkBlock(context, pos, BlockBehaviour.BlockStateBase::canBeReplaced);
    }

    static boolean isWaterOrWaterNearby(TreeDecorator.Context context, BlockPos pos) {
        return checkBlock(context, pos, s -> s.is(Blocks.WATER)) || checkBlock(context, pos.east(), s -> s.is(Blocks.WATER))
                || checkBlock(context, pos.west(), s -> s.is(Blocks.WATER)) || checkBlock(context, pos.north(), s -> s.is(Blocks.WATER))
                || checkBlock(context, pos.south(), s -> s.is(Blocks.WATER));
    }

    static List<BlockPos> getLowestTrunkOrRootOfTree(TreeDecorator.Context context) {
        List<BlockPos> out = new ArrayList<>();
        List<BlockPos> roots = context.roots();
        List<BlockPos> logs = context.logs();
        if (roots.isEmpty()) {
            out.addAll(logs);
        } else if (!logs.isEmpty() && roots.get(0).getY() == logs.get(0).getY()) {
            out.addAll(logs);
            out.addAll(roots);
        } else {
            out.addAll(roots);
        }
        return out;
    }
}
