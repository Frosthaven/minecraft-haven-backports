package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Sulfur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x PotentSulfurBlock, line for line: dry without a water source above; wet with one; continuous over
 * #causes_continuous_geyser_eruptions (lava); dormant / erupting over #causes_periodic_geyser_eruptions (magma).
 * 1.21.1 forms: updateShape's argument order; the tickers are {@link PotentSulfurBlockEntity}'s.
 */
public class PotentSulfurBlock extends BaseEntityBlock {
    public static final EnumProperty<PotentSulfurState> STATE = EnumProperty.create("potent_sulfur_state", PotentSulfurState.class);

    public PotentSulfurBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(STATE, PotentSulfurState.DRY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(PotentSulfurBlock::new);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;   // 26.x renders every block entity block's model; 1.21.1's default here is invisible
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PotentSulfurBlockEntity(pos, state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos,
            BlockPos neighborPos) {
        return validBlockState(state, level, pos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return validBlockState(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    private static BlockState validBlockState(BlockState state, LevelReader level, BlockPos pos) {
        if (!level.getFluidState(pos.above()).isSourceOfType(Fluids.WATER)) {
            return state.setValue(STATE, PotentSulfurState.DRY);
        }
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Sulfur.CAUSES_CONTINUOUS_GEYSER_ERUPTIONS) && isSourceIfFluid(below)) {
            return state.setValue(STATE, PotentSulfurState.CONTINUOUS);
        } else if (below.is(Sulfur.CAUSES_PERIODIC_GEYSER_ERUPTIONS) && isSourceIfFluid(below)) {
            boolean isGeyser = state.getValue(STATE) == PotentSulfurState.ERUPTING || state.getValue(STATE) == PotentSulfurState.DORMANT;
            if (!isGeyser && level.getBlockEntity(pos) instanceof PotentSulfurBlockEntity entity) {
                entity.resetCountdown();
            }
            return state.getValue(STATE) == PotentSulfurState.ERUPTING ? state : state.setValue(STATE, PotentSulfurState.DORMANT);
        }
        return state.setValue(STATE, PotentSulfurState.WET);
    }

    private static boolean isSourceIfFluid(BlockState below) {
        FluidState fluid = below.getFluidState();
        return fluid.isEmpty() || fluid.isSource();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (state.getValue(STATE) == PotentSulfurState.ERUPTING || state.getValue(STATE) == PotentSulfurState.CONTINUOUS) {
            level.blockEvent(pos, this, 0, 0);
            level.playSound(null, pos, state.getValue(STATE) == PotentSulfurState.CONTINUOUS ? Sulfur.GEYSER_CONTINUOUS_START.get()
                    : Sulfur.GEYSER_ERUPTION_START.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(state));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(STATE) != PotentSulfurState.DRY && level.getFluidState(pos.above()).isSourceOfType(Fluids.WATER)) {
            spawnBubbleParticlesAt(level, random, pos.getX(), pos.getY() + 1, pos.getZ());
            spawnBubbleParticlesAt(level, random, pos.getX(), pos.getY() + 1, pos.getZ());
            if (random.nextInt(10) == 0) {
                level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), Sulfur.NOXIOUS_GAS_SOUND.get(), SoundSource.AMBIENT, 1.0F, 1.0F, false);
            }
        }
    }

    private static void spawnBubbleParticlesAt(Level level, RandomSource random, double x, double y, double z) {
        level.addAlwaysVisibleParticle(Sulfur.SULFUR_BUBBLES.get(), x + random.nextFloat(), y + random.nextFloat(), z + random.nextFloat(), 0.0, 0.0, 0.0);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
        if (level.getBlockEntity(pos) instanceof PotentSulfurBlockEntity entity) {
            entity.eruptionTick = level.getGameTime();
        }
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        boolean client = level.isClientSide();
        BlockEntityTicker<PotentSulfurBlockEntity> ticker = switch (state.getValue(STATE)) {
            case DRY -> null;
            case WET -> client ? PotentSulfurBlockEntity.CLIENT_NOXIOUS_GAS_TICKER : PotentSulfurBlockEntity.SERVER_NAUSEA_EFFECT_TICKER;
            case DORMANT -> client ? PotentSulfurBlockEntity.CLIENT_NOXIOUS_GAS_TICKER
                    : PotentSulfurBlockEntity.and(PotentSulfurBlockEntity.SERVER_WAITING_COUNTDOWN_TICKER, PotentSulfurBlockEntity.SERVER_NAUSEA_EFFECT_TICKER);
            case ERUPTING -> client
                    ? PotentSulfurBlockEntity.and(PotentSulfurBlockEntity.plume(Sulfur.GEYSER_ERUPTION_ACTIVE), PotentSulfurBlockEntity.LAUNCH_ENTITY_TICKER)
                    : PotentSulfurBlockEntity.and(PotentSulfurBlockEntity.LAUNCH_ENTITY_TICKER, PotentSulfurBlockEntity.SERVER_WAITING_COUNTDOWN_TICKER);
            case CONTINUOUS -> client
                    ? PotentSulfurBlockEntity.and(PotentSulfurBlockEntity.plume(Sulfur.GEYSER_CONTINUOUS_ACTIVE), PotentSulfurBlockEntity.LAUNCH_ENTITY_TICKER)
                    : PotentSulfurBlockEntity.LAUNCH_ENTITY_TICKER;
        };
        return ticker == null ? null : createTickerHelper(type, Sulfur.POTENT_SULFUR_ENTITY.get(), ticker);
    }
}
