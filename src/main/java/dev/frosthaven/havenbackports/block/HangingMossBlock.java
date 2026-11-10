package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.PaleGarden;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 26.x HangingMossBlock, line for line (1.21.1 forms: updateShape's argument order, propagatesSkylightDown's). */
public class HangingMossBlock extends Block implements BonemealableBlock {
    private static final VoxelShape SHAPE_BASE = Shapes26.column(14.0, 14.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_TIP = Shapes26.column(14.0, 14.0, 2.0, 16.0);
    public static final BooleanProperty TIP = BooleanProperty.create("tip");   // 26.x BlockStateProperties.TIP

    public HangingMossBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TIP, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(HangingMossBlock::new);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TIP) ? SHAPE_TIP : SHAPE_BASE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(500) == 0) {
            BlockState above = level.getBlockState(pos.above());
            if (above.is(PaleGarden.PALE_OAK_LOGS) || above.is(PaleGarden.PALE_OAK_LEAVES.get())) {
                level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), PaleGarden.PALE_HANGING_MOSS_IDLE.get(), SoundSource.AMBIENT, 1.0F, 1.0F, false);
            }
        }
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.canStayAtPosition(level, pos);
    }

    private boolean canStayAtPosition(BlockGetter level, BlockPos pos) {
        BlockPos above = pos.relative(Direction.UP);
        BlockState state = level.getBlockState(above);
        return MultifaceBlock.canAttachTo(level, Direction.UP, above, state) || state.is(this);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (!this.canStayAtPosition(level, pos)) {
            level.scheduleTick(pos, this, 1);
        }
        return state.setValue(TIP, !level.getBlockState(pos.below()).is(this));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.canStayAtPosition(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIP);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos grow = this.getTip(level, pos).below();
        return level.getBlockState(grow).isAir() && !level.isOutsideBuildHeight(grow);
    }

    public BlockPos getTip(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos m = pos.mutable();
        BlockState s;
        do {
            m.move(Direction.DOWN);
            s = level.getBlockState(m);
        } while (s.is(this));
        return m.relative(Direction.UP).immutable();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos tip = this.getTip(level, pos).below();
        if (level.getBlockState(tip).isAir()) {
            level.setBlockAndUpdate(tip, state.setValue(TIP, true));
        }
    }
}
