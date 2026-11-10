package dev.frosthaven.havenbackports.ghast;

import dev.frosthaven.havenbackports.Ghasts;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x DriedGhastBlock: in water it gains a hydration level every 5000 ticks and at level 3 becomes a
 * ghastling facing the block's way; out of water it dries back down. Block.column(10, 10, 0, 10) is box(3, 0, 3, 13,
 * 10, 13); BlockStateProperties.DRIED_GHAST_HYDRATION_LEVELS is "hydration" 0..3.
 */
public class DriedGhastBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<DriedGhastBlock> CODEC = simpleCodec(DriedGhastBlock::new);
    public static final int MAX_HYDRATION_LEVEL = 3;
    public static final IntegerProperty HYDRATION_LEVEL = IntegerProperty.create("hydration", 0, MAX_HYDRATION_LEVEL);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int HYDRATION_TICK_DELAY = 5000;
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 10.0, 13.0);

    public DriedGhastBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HYDRATION_LEVEL, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HYDRATION_LEVEL, WATERLOGGED);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public int getHydrationLevel(BlockState state) {
        return state.getValue(HYDRATION_LEVEL);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            if (getHydrationLevel(state) != MAX_HYDRATION_LEVEL) {
                level.playSound(null, pos, Ghasts.DRIED_GHAST_TRANSITION.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                level.setBlock(pos, state.setValue(HYDRATION_LEVEL, getHydrationLevel(state) + 1), 2);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
            } else {
                spawnGhastling(level, pos, state);
            }
        } else if (getHydrationLevel(state) > 0) {
            level.setBlock(pos, state.setValue(HYDRATION_LEVEL, getHydrationLevel(state) - 1), 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
        }
    }

    private void spawnGhastling(ServerLevel level, BlockPos pos, BlockState state) {
        level.removeBlock(pos, false);
        HappyGhast ghastling = Ghasts.HAPPY_GHAST.get().create(level);   // 26.x: EntitySpawnReason.BREEDING
        if (ghastling != null) {
            Vec3 at = Vec3.atBottomCenterOf(pos);
            ghastling.setBaby(true);
            float rotation = state.getValue(FACING).toYRot();
            ghastling.setYHeadRot(rotation);
            ghastling.moveTo(at.x(), at.y(), at.z(), rotation, 0.0F);
            level.addFreshEntity(ghastling);
            level.playSound(null, ghastling, Ghasts.GHASTLING_SPAWN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        if (!state.getValue(WATERLOGGED)) {
            if (random.nextInt(40) == 0 && level.getBlockState(pos.below()).is(Ghasts.TRIGGERS_AMBIENT_DRIED_GHAST_BLOCK_SOUNDS)) {
                level.playLocalSound(x, y, z, Ghasts.DRIED_GHAST_AMBIENT.get(), SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0.0, 0.02, 0.0);
            }
        } else {
            if (random.nextInt(40) == 0) {
                level.playLocalSound(x, y, z, Ghasts.DRIED_GHAST_AMBIENT_WATER.get(), SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER, x + (random.nextFloat() * 2.0F - 1.0F) / 3.0F, y + 0.4,
                        z + (random.nextFloat() * 2.0F - 1.0F) / 3.0F, 0.0, random.nextFloat(), 0.0);
            }
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((state.getValue(WATERLOGGED) || state.getValue(HYDRATION_LEVEL) > 0) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, HYDRATION_TICK_DELAY);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState replaced = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(WATERLOGGED, replaced.isSourceOfType(Fluids.WATER)).setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (!state.getValue(WATERLOGGED) && fluid.isSourceOfType(Fluids.WATER)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(WATERLOGGED, true), 3);
                level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
                level.playSound(null, pos, Ghasts.DRIED_GHAST_PLACE_IN_WATER.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return true;
        }
        return false;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        level.playSound(null, pos, (state.getValue(WATERLOGGED) ? Ghasts.DRIED_GHAST_PLACE_IN_WATER : Ghasts.DRIED_GHAST_PLACE).get(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
