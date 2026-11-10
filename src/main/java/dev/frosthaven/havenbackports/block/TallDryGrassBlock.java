package dev.frosthaven.havenbackports.block;

import dev.frosthaven.havenbackports.Flora;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 26.x TallDryGrassBlock: the dry grass sound; bone meal grows short dry grass on a free side it can stand on. */
public class TallDryGrassBlock extends DryVegetationBlock implements BonemealableBlock {
    private static final VoxelShape SHAPE = Shapes26.column(14.0, 14.0, 0.0, 16.0);

    public TallDryGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        dryGrassSounds(level, pos, random);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return BushBlock26.spreadableNeighbour(Direction.Plane.HORIZONTAL.stream().toList(), level, pos,
                Flora.SHORT_DRY_GRASS.get().defaultBlockState()).isPresent();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockState grass = Flora.SHORT_DRY_GRASS.get().defaultBlockState();
        BushBlock26.spreadableNeighbour(Direction.Plane.HORIZONTAL.shuffledCopy(level.getRandom()), level, pos, grass)
                .ifPresent(p -> level.setBlockAndUpdate(p, grass));
    }
}
