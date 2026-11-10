package dev.frosthaven.havenbackports.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/** 26.x CrackingIceBlock (the cracking base 26.x gives frosted and brittle ice), line for line. Only brittle ice
 *  uses it here: 1.21.1's frosted ice keeps its own class. */
public abstract class CrackingIceBlock extends IceBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    protected CrackingIceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    protected void propagateMelting(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            neighborPos.setWithOffset(pos, direction);
            BlockState neighbour = level.getBlockState(neighborPos);
            if (neighbour.is(this) && !this.slightlyMelt(neighbour, level, neighborPos)) {
                level.scheduleTick(neighborPos, this, this.getTickDelay(random));
            }
        }
    }

    protected boolean slightlyMelt(BlockState state, Level level, BlockPos pos) {
        int age = state.getValue(AGE);
        if (age < 3) {
            level.setBlock(pos, state.setValue(AGE, age + 1), 2);
            SoundEvent sound = this.getCrackingIceSound();
            if (sound != null) {
                level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            this.playCrackingIceParticle(state, level, pos);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
            return false;
        }
        this.melt(state, level, pos);
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    protected abstract int getTickDelay(RandomSource random);

    protected void playCrackingIceParticle(BlockState state, Level level, BlockPos pos) {
    }

    @Nullable
    protected SoundEvent getCrackingIceSound() {
        return null;
    }
}
