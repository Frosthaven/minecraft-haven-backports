package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.IceCaves;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/** 26.x IcicleBlock, line for line. 1.21.1 forms: 26.x's WATER_EVAPORATES environment attribute is 1.21.1's
 *  ultra-warm dimension type; the landing level event 1055 (ICICLE_LAND, volume 2, pitch 0.9 to 1.0) is played by
 *  the server, as for the sulfur spike. */
public class IcicleBlock extends SpeleothemBlock {
    private static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    private static final List<Direction> ONLY_GROW_STALACTITES = List.of(Direction.DOWN);

    public IcicleBlock(BlockState blockToGrowOn, Properties properties) {
        super(blockToGrowOn, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(ATTACHED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        this.fallOnDamage(level, state, pos, entity, fallDistance);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ATTACHED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        boolean attached = isStalactiteStartPos(state, context.getLevel(), context.getClickedPos())
                || isStalagmiteStartPos(state, context.getLevel(), context.getClickedPos());
        return state.setValue(ATTACHED, attached);
    }

    @Override
    protected void landingSound(Level level, BlockPos pos) {
        level.playSound(null, pos, IceCaves.ICICLE_LAND.get(), SoundSource.BLOCKS, 2.0F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    @Override
    protected int getMaxGrowthLength() {
        return 4;
    }

    @Override
    protected boolean canGrow(LevelReader level, BlockPos pos) {
        return super.canGrow(level, pos) && !this.wouldMelt(level, pos) && isStalactite(level.getBlockState(pos));
    }

    @Override
    protected boolean shouldMergeTips() {
        return false;
    }

    @Override
    protected List<Direction> getNaturalGrowthDirections() {
        return ONLY_GROW_STALACTITES;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (isFreeHangingStalactite(state) && !(random.nextFloat() > 0.02F)) {
            this.spawnDripParticle(level, pos, state, pos.above(), null);
        }
    }

    @Nullable
    @Override
    protected ParticleOptions getDripParticle(Level level, @Nullable Fluid fluidAbove, BlockPos posAbove) {
        return new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SNOW.defaultBlockState());
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.isStartPos(state, level, pos) && this.wouldMelt(level, pos)) {
            if (!isStalagmite(state) && (!isTip(state, true) || level.getBlockState(pos.below()).isAir())) {
                spawnFallingStalactite(state, level, pos);
            } else {
                level.destroyBlock(pos, true);
            }
        } else {
            super.randomTick(state, level, pos, random);
        }
    }

    private boolean isStartPos(BlockState state, LevelReader level, BlockPos pos) {
        return isStalactiteStartPos(state, level, pos) || isStalagmiteStartPos(state, level, pos);
    }

    private boolean wouldMelt(LevelReader level, BlockPos pos) {
        return level.dimensionType().ultraWarm() || level.getBrightness(LightLayer.BLOCK, pos) > 4
                || level.getBlockState(pos.below()).is(IceCaves.MELTS_ICICLE_ABOVE) || level.getBlockState(pos.below(2)).is(IceCaves.MELTS_ICICLE_ABOVE);
    }
}
