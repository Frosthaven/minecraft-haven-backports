package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Sulfur;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 26.x SulfurSpikeBlock: grows under sulfur, at most 2 long; lands with block.sulfur_spike.land (26.x level event
 *  1052: volume 2.0, pitch 0.9 to 1.0). */
public class SulfurSpikeBlock extends SpeleothemBlock {
    public SulfurSpikeBlock(BlockState blockToGrowOn, Properties properties) {
        super(blockToGrowOn, properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void landingSound(Level level, BlockPos pos) {
        level.playSound(null, pos, Sulfur.SULFUR_SPIKE_LAND.get(), SoundSource.BLOCKS, 2.0F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    @Override
    protected int getMaxGrowthLength() {
        return 2;
    }
}
