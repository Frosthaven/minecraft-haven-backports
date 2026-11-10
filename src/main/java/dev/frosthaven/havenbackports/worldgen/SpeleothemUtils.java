package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;

/** 26.x SpeleothemUtils (the parts the speleothem features use), line for line; thickness is 1.21.1's
 *  DripstoneThickness (see SpeleothemBlock). */
final class SpeleothemUtils {
    private SpeleothemUtils() {}

    static boolean isEmptyOrWater(LevelAccessor level, BlockPos pos) {
        return level.isStateAtPosition(pos, SpeleothemUtils::isEmptyOrWater);
    }

    static void growSpeleothem(LevelAccessor level, BlockPos start, Direction tip, int height, boolean mergedTip, Block baseBlock,
            Block pointedBlock, HolderSet<Block> replaceable, BaseBlockTransformer transformer) {
        if (isBase(level.getBlockState(start.relative(tip.getOpposite())), baseBlock, replaceable)) {
            BlockPos.MutableBlockPos pos = start.mutable();
            buildBaseToTipColumn(tip, height, mergedTip, state -> {
                if (state.is(pointedBlock)) {
                    state = state.setValue(BlockStateProperties.WATERLOGGED, level.isWaterAt(pos));
                }
                level.setBlock(pos, state, 2);
                pos.move(tip);
            }, pointedBlock, transformer);
        }
    }

    static void buildBaseToTipColumn(Direction direction, int totalLength, boolean mergedTip, Consumer<BlockState> consumer, Block pointedBlock,
            BaseBlockTransformer transformer) {
        for (int remaining = totalLength; remaining > 0; remaining--) {
            boolean isColumnBase = remaining == totalLength;
            DripstoneThickness thickness;
            if (remaining == 2) {
                thickness = DripstoneThickness.FRUSTUM;
            } else if (remaining == 1) {
                thickness = mergedTip ? DripstoneThickness.TIP_MERGE : DripstoneThickness.TIP;
            } else if (isColumnBase) {
                thickness = DripstoneThickness.BASE;
            } else {
                thickness = DripstoneThickness.MIDDLE;
            }
            BlockState state = pointedBlock.defaultBlockState().setValue(BlockStateProperties.VERTICAL_DIRECTION, direction)
                    .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, thickness);
            consumer.accept(isColumnBase ? transformer.transform(state) : state);
        }
    }

    static boolean placeBaseBlockIfPossible(LevelAccessor level, BlockPos pos, Block baseBlock, HolderSet<Block> replaceable) {
        if (level.getBlockState(pos).is(replaceable)) {
            level.setBlock(pos, baseBlock.defaultBlockState(), 2);
            return true;
        }
        return false;
    }

    static boolean isBase(BlockState state, Block baseBlock, HolderSet<Block> replaceable) {
        return state.is(baseBlock) || state.is(replaceable);
    }

    static boolean isEmptyOrWater(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER);
    }

    static boolean isEmptyOrWaterOrLava(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA);
    }

    static boolean isEmptyOrWaterOrLava(LevelAccessor level, BlockPos pos) {
        return level.isStateAtPosition(pos, SpeleothemUtils::isEmptyOrWaterOrLava);
    }

    static boolean isBaseOrLava(BlockState state, Block baseBlock, HolderSet<Block> replaceable) {
        return isBase(state, baseBlock, replaceable) || state.is(Blocks.LAVA);
    }

    static double getSpeleothemHeight(double xzDistanceFromCenter, double speleothemRadius, double scale, double bluntness) {
        if (xzDistanceFromCenter < bluntness) {
            xzDistanceFromCenter = bluntness;
        }
        double r = xzDistanceFromCenter / speleothemRadius * 0.384;
        double part1 = 0.75 * Math.pow(r, 1.3333333333333333);
        double part2 = Math.pow(r, 0.6666666666666666);
        double part3 = 0.3333333333333333 * Math.log(r);
        double heightRelativeToMaxRadius = Math.max(scale * (part1 - part2 - part3), 0.0);
        return heightRelativeToMaxRadius / 0.384 * speleothemRadius;
    }

    static boolean isCircleMostlyEmbeddedInStone(LevelAccessor level, BlockPos center, int xzRadius) {
        if (isEmptyOrWaterOrLava(level, center)) {
            return false;
        }
        float angleIncrement = 6.0F / xzRadius;
        for (float angle = 0.0F; angle < (float) (Math.PI * 2); angle += angleIncrement) {
            int dx = (int) (Mth.cos(angle) * xzRadius);
            int dz = (int) (Mth.sin(angle) * xzRadius);
            if (isEmptyOrWaterOrLava(level, center.offset(dx, 0, dz))) {
                return false;
            }
        }
        return true;
    }

    static boolean isNeitherEmptyNorWater(BlockState state) {
        return !state.isAir() && !state.is(Blocks.WATER);
    }

    enum BaseBlockTransformer implements StringRepresentable {
        NONE("none", UnaryOperator.identity()),
        SET_ATTACHED("set_attached", state -> state.trySetValue(BlockStateProperties.ATTACHED, true));

        static final Codec<BaseBlockTransformer> CODEC = StringRepresentable.fromEnum(BaseBlockTransformer::values);
        private final String name;
        private final UnaryOperator<BlockState> transformer;

        BaseBlockTransformer(String name, UnaryOperator<BlockState> transformer) {
            this.name = name;
            this.transformer = transformer;
        }

        BlockState transform(BlockState state) {
            return this.transformer.apply(state);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
