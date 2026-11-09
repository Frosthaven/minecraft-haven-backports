package dev.frosthaven.havenbackports.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Mojang's 26.x {@code WeatheringCopperChestBlock}: ages only while nobody has it open; a pair ages from its left half. */
public class WeatheringCopperChestBlock extends CopperChestBlock implements WeatheringCopper {
    public WeatheringCopperChestBlock(WeatherState weatherState, Supplier<SoundEvent> openSound, Supplier<SoundEvent> closeSound, Properties properties) {
        super(weatherState, openSound, closeSound, properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(TYPE) != ChestType.RIGHT && level.getBlockEntity(pos) instanceof ChestBlockEntity chest
                && ChestBlockEntity.getOpenCount(level, pos) == 0) {
            changeOverTime(state, level, pos, random);
        }
    }

    @Override
    public WeatherState getAge() {
        return getState();
    }

    @Override
    public boolean isWaxed() {
        return false;
    }
}
