package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Dappled;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x ShelfMushroomBlock, line for line. 26.x's block properties bounceRestitution(0.75) and
 * fallDistanceReduction(0.5) have no 1.21.1 field: the bounce is {@link Bouncy} (through EntityMixin) and the fall
 * reduction is 26.x Block.fallOn here.
 */
public class ShelfMushroomBlock extends HorizontalDirectionalBlock implements BonemealableBlock, Bouncy {
    public static final MapCodec<ShelfMushroomBlock> CODEC = simpleCodec(ShelfMushroomBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_1;
    private static final float BOUNCE_RESTITUTION = 0.75F;
    private static final float FALL_DISTANCE_REDUCTION = 0.5F;
    private static final List<Map<Direction, VoxelShape>> SHAPES = IntStream.rangeClosed(0, 1)
            .mapToObj(i -> Shapes26.rotateHorizontal(Shapes.or(
                    Shapes26.column(10 + i * 4, 7 + i * 3, 1 + i, 3 + i * 2).move(0.0, (8 - i * 2) / 16.0, -(i * 1.5 - 4.5) / 16.0).optimize(),
                    Shapes26.column(6 + i * 2, 4 + i * 2, 0.0, 1 + i).move(0.0, (8 - i * 2) / 16.0, -(i - 6) / 16.0).optimize())))
            .toList();

    public ShelfMushroomBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AGE)).get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState();
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                state = state.setValue(FACING, direction.getOpposite());
                if (state.canSurvive(level, pos)) {
                    return state;
                }
            }
        }
        return null;
    }

    /** 26.x Block.fallOn: fall damage on reducedFallDistance = fallDistance * (1 - fallDistanceReduction). */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance * (1.0F - FALL_DISTANCE_REDUCTION), 1.0F, entity.damageSources().fall());
    }

    @Override
    public float bounceRestitution() {
        return BOUNCE_RESTITUTION;
    }

    @Override
    public void bounceOn(Entity entity) {
        if (!(entity instanceof ItemEntity) && !(entity instanceof PrimedTnt)) {
            entity.level().playSound(null, entity.getBlockPosBelowThatAffectsMyMovement(), Dappled.SHELF_MUSHROOM_BOUNCE.get(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos,
            BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) < 1;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
