package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 26.x BushBlock (the bush and red shrub class) on 1.21.1's BushBlock (26.x VegetationBlock: the same placement on
 * dirt-like blocks and farmland). Bonemeal places the same block on a free horizontal neighbour it can survive on
 * (26.x BonemealableBlock.findSpreadableNeighbourPos, ported here).
 */
public class BushBlock26 extends BushBlock implements BonemealableBlock {
    private final VoxelShape shape;

    public BushBlock26(Properties properties) {
        this(properties, 13);
    }

    public BushBlock26(Properties properties, int shapeHeight) {
        super(properties);
        this.shape = Shapes26.column(16.0, 16.0, 0.0, shapeHeight);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shape;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return spreadableNeighbour(Direction.Plane.HORIZONTAL.stream().toList(), level, pos, state).isPresent();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        spreadableNeighbour(Direction.Plane.HORIZONTAL.shuffledCopy(level.getRandom()), level, pos, state)
                .ifPresent(p -> level.setBlockAndUpdate(p, this.defaultBlockState()));
    }

    /** 26.x BonemealableBlock.getSpreadableNeighbourPos. */
    public static Optional<BlockPos> spreadableNeighbour(List<Direction> directions, LevelReader level, BlockPos pos, BlockState toPlace) {
        for (Direction direction : directions) {
            BlockPos n = pos.relative(direction);
            if (level.isEmptyBlock(n) && toPlace.canSurvive(level, n)) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }
}
