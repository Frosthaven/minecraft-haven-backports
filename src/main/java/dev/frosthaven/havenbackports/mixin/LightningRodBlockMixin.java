package dev.frosthaven.havenbackports.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.x's minecraft:lightning_rod is the unaffected stage of a WeatheringCopperCollection (WeatheringLightningRodBlock:
 * random ticks age it while a next stage exists). 1.21.1's is a plain LightningRodBlock, so this gives a
 * LightningRodBlock with a next stage in NeoForge's oxidizables data map (the vanilla rod -> our exposed rod) the
 * same random tick, aging as an unaffected copper block. Rods without a next stage (waxed ones, other mods' rods)
 * keep 1.21.1's behaviour. Not done: making the vanilla rod a WeatheringCopper (an interface on every
 * LightningRodBlock), so neighbouring copper does not count it as unaffected when it ages.
 */
@Mixin(LightningRodBlock.class)
abstract class LightningRodBlockMixin extends RodBlock {
    private static final WeatheringCopper HAVEN_BACKPORTS$UNAFFECTED = () -> WeatheringCopper.WeatherState.UNAFFECTED;

    LightningRodBlockMixin(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock()).isPresent() || super.isRandomlyTicking(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (WeatheringCopper.getNext(state.getBlock()).isPresent()) {
            HAVEN_BACKPORTS$UNAFFECTED.changeOverTime(state, level, pos, random);
        } else {
            super.randomTick(state, level, pos, random);
        }
    }
}
