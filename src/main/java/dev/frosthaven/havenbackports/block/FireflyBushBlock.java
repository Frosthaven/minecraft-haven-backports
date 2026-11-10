package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Flora;
import dev.frosthaven.havenbackports.Night26;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 26.x FireflyBushBlock on 1.21.1's BushBlock (26.x VegetationBlock). Fireflies (up to 0.7 per animate tick, within
 * 10 x 5 x 10 blocks, where the light is 13 or less); the idle sound one in 30 when open to the sky and 26.x's
 * firefly_bush_sounds attribute is on (the overworld day timeline: 12600 to 23401, as {@link Night26}). Bone meal
 * places another bush on a free side it can stand on.
 */
public class FireflyBushBlock extends BushBlock implements BonemealableBlock {
    public FireflyBushBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(30) == 0 && Night26.active(level).orElse(false)
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) <= pos.getY()) {
            level.playLocalSound(pos, Flora.FIREFLY_BUSH_IDLE.get(), SoundSource.AMBIENT, 1.0F, 1.0F, false);
        }
        if (level.getMaxLocalRawBrightness(pos) <= 13 && random.nextDouble() <= 0.7) {
            double x = pos.getX() + random.nextDouble() * 10.0 - 5.0;
            double y = pos.getY() + random.nextDouble() * 5.0;
            double z = pos.getZ() + random.nextDouble() * 10.0 - 5.0;
            level.addParticle(Flora.FIREFLY.get(), x, y, z, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return BushBlock26.spreadableNeighbour(Direction.Plane.HORIZONTAL.stream().toList(), level, pos, state).isPresent();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BushBlock26.spreadableNeighbour(Direction.Plane.HORIZONTAL.shuffledCopy(level.getRandom()), level, pos, state)
                .ifPresent(p -> level.setBlockAndUpdate(p, this.defaultBlockState()));
    }
}
