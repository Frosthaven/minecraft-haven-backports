package dev.frosthaven.havenbackports.creaking;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Night26;
import dev.frosthaven.havenbackports.PaleGarden;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * 26.x CreakingHeartBlock on 1.21.1 APIs. Forms 1.21.1 forced:
 * - gameplay/creaking_active (26.x day timeline) is Night26.active: an empty value (no day cycle) keeps the state;
 *   an uprooted heart that gets its logs there turns dormant (26.x's default for the attribute is false).
 * - BlockStateProperties.NATURAL is 26.x only: a property of the same name.
 * - affectNeighborsAfterRemoval and the block entity's preRemoveSideEffects are onRemove.
 * - 1.21.1 has no Explosion.shouldAffectBlocklikeEntities: true for DESTROY and DESTROY_WITH_DECAY, as 26.x's enum.
 * - 1.21.1 renders a BaseEntityBlock invisible by default: the model shape is set back.
 */
public class CreakingHeartBlock extends BaseEntityBlock {
    public static final MapCodec<CreakingHeartBlock> CODEC = simpleCodec(CreakingHeartBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<CreakingHeartState> STATE = EnumProperty.create("creaking_heart_state", CreakingHeartState.class);
    public static final BooleanProperty NATURAL = BooleanProperty.create("natural");

    public CreakingHeartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(AXIS, Direction.Axis.Y).setValue(STATE, CreakingHeartState.UPROOTED)
                .setValue(NATURAL, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreakingHeartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return state.getValue(STATE) != CreakingHeartState.UPROOTED
                ? createTickerHelper(type, CreakingContent.CREAKING_HEART_ENTITY.get(), CreakingHeartBlockEntity::serverTick) : null;
    }

    /** gameplay/creaking_active at the heart (26.x EnvironmentAttributes.CREAKING_ACTIVE; false without a day cycle). */
    static boolean creakingActive(Level level) {
        return Night26.active(level).orElse(false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (creakingActive(level) && state.getValue(STATE) != CreakingHeartState.UPROOTED
                && random.nextInt(16) == 0 && isSurroundedByLogs(level, pos)) {
            level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), CreakingContent.CREAKING_HEART_IDLE.get(), SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState newState = updateState(state, level, pos);
        if (newState != state) {
            level.setBlockAndUpdate(pos, newState);
        }
    }

    private static BlockState updateState(BlockState state, Level level, BlockPos pos) {
        boolean hasLogs = hasRequiredLogs(state, level, pos);
        boolean disabled = state.getValue(STATE) == CreakingHeartState.UPROOTED;
        return hasLogs && disabled ? state.setValue(STATE, creakingActive(level) ? CreakingHeartState.AWAKE : CreakingHeartState.DORMANT) : state;
    }

    public static boolean hasRequiredLogs(BlockState state, LevelReader level, BlockPos pos) {
        Direction.Axis axis = state.getValue(AXIS);
        // 26.x Axis.getDirections(): both directions along the axis
        for (Direction dir : new Direction[]{Direction.get(Direction.AxisDirection.POSITIVE, axis), Direction.get(Direction.AxisDirection.NEGATIVE, axis)}) {
            BlockState neighbour = level.getBlockState(pos.relative(dir));
            if (!neighbour.is(PaleGarden.PALE_OAK_LOGS) || !neighbour.hasProperty(AXIS) || neighbour.getValue(AXIS) != axis) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSurroundedByLogs(LevelAccessor level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!level.getBlockState(pos.relative(dir)).is(PaleGarden.PALE_OAK_LOGS)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateState(this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis()), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return RotatedPillarBlock.rotatePillar(state, rotation);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, STATE, NATURAL);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CreakingHeartBlockEntity heart) {
                heart.removeProtector(null);
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        Explosion.BlockInteraction interaction = explosion.getBlockInteraction();
        boolean affectsBlocklike = interaction == Explosion.BlockInteraction.DESTROY || interaction == Explosion.BlockInteraction.DESTROY_WITH_DECAY;
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof CreakingHeartBlockEntity heart && affectsBlocklike) {
            heart.removeProtector(explosion.damageSource);
            if (explosion.getIndirectSourceEntity() instanceof Player player) {
                this.tryAwardExperience(player, state, serverLevel, pos);
            }
        }
        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.getBlockEntity(pos) instanceof CreakingHeartBlockEntity heart) {
            heart.removeProtector(player.damageSources().playerAttack(player));
            this.tryAwardExperience(player, state, level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Natural hearts only, 20 to 24 XP; not for creative (26.x preventsBlockDrops) or spectators. */
    private void tryAwardExperience(Player player, BlockState state, Level level, BlockPos pos) {
        if (!player.getAbilities().instabuild && !player.isSpectator() && state.getValue(NATURAL) && level instanceof ServerLevel serverLevel) {
            this.popExperience(serverLevel, pos, level.getRandom().nextIntBetweenInclusive(20, 24));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(STATE) == CreakingHeartState.UPROOTED) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof CreakingHeartBlockEntity heart ? heart.getAnalogOutputSignal() : 0;
    }
}
