package dev.frosthaven.havenbackports.golem;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Golems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x CopperGolemStatueBlock (the waxed statues are this class): four poses that any item but an axe cycles,
 * the comparator reads pose + 1, pick block keeps the name and pose. 1.21.1 forms: Block.column(10, 0, 14) is
 * box(3, 0, 3, 13, 14, 13); 26.x's InteractionResult.PASS (let the item act) is SKIP_DEFAULT_BLOCK_INTERACTION;
 * shouldChangedStateKeepBlockEntity (oxidizing, waxing, scraping keep the name) is onRemove leaving the block entity.
 */
public class CopperGolemStatueBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Pose> POSE = EnumProperty.create("copper_golem_pose", Pose.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 14.0, 13.0);
    private final WeatherState weatheringState;

    public CopperGolemStatueBlock(WeatherState state, Properties properties) {
        super(properties);
        this.weatheringState = state;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POSE, Pose.STANDING).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return MapCodec.unit(this);   // registered blocks, not data-driven
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POSE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState replaced = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, replaced.isSourceOfType(Fluids.WATER));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public WeatherState getWeatheringState() {
        return weatheringState;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ItemTags.AXES)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        updatePose(level, state, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    protected void updatePose(Level level, BlockState state, BlockPos pos, Player player) {
        level.playSound(null, pos, Golems.BECOME_STATUE.get(), SoundSource.BLOCKS);
        level.setBlockAndUpdate(pos, state.setValue(POSE, state.getValue(POSE).next()));
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.WATER && state.getFluidState().is(FluidTags.WATER);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperGolemStatueBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(Golems.COPPER_GOLEM_STATUES)) {
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
        if (!state.is(newState.getBlock())) {
            level.updateNeighbourForOutputSignal(pos, state.getBlock());   // 26.x affectNeighborsAfterRemoval
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(POSE).ordinal() + 1;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof CopperGolemStatueBlockEntity statue
                ? statue.getItem(this.asItem().getDefaultInstance(), state.getValue(POSE)) : super.getCloneItemStack(level, pos, state);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    public enum Pose implements StringRepresentable {
        STANDING("standing"), SITTING("sitting"), RUNNING("running"), STAR("star");

        private final String name;

        Pose(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** 26.x getNextPose: ByIdMap.continuous with OutOfBoundsStrategy.ZERO, so star wraps to standing. */
        public Pose next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Mojang's 26.x WeatheringCopperGolemStatueBlock: oxidizes on random ticks; an axe on an unaffected statue wakes it. */
    public static class Weathering extends CopperGolemStatueBlock implements net.minecraft.world.level.block.WeatheringCopper {
        public Weathering(WeatherState state, Properties properties) {
            super(state, properties);
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return net.minecraft.world.level.block.WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
            this.changeOverTime(state, level, pos, random);
        }

        @Override
        public WeatherState getAge() {
            return getWeatheringState();
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (level.getBlockEntity(pos) instanceof CopperGolemStatueBlockEntity statue) {
                if (!stack.is(ItemTags.AXES)) {
                    if (stack.is(net.minecraft.world.item.Items.HONEYCOMB)) {
                        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
                    }
                    updatePose(level, state, pos, player);
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
                if (getAge() == WeatherState.UNAFFECTED) {
                    CopperGolem golem = statue.removeStatue(state);
                    stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
                    if (golem != null) {
                        level.addFreshEntity(golem);
                        level.removeBlock(pos, false);
                        return ItemInteractionResult.sidedSuccess(level.isClientSide());
                    }
                }
            }
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
    }
}
