package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mojang's WeatheringCopperBarsBlock (26.x): a IronBarsBlock that oxidizes. As in the snapshot: random ticks call changeOverTime, and the
 * block ticks only while a next stage exists (NeoForge's oxidizables data map, written by tools/backport.py).
 */
public class WeatheringCopperBarsBlock extends IronBarsBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperBarsBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringCopperBarsBlock::getAge),
            propertiesCodec()).apply(i, WeatheringCopperBarsBlock::new));
    private final WeatherState weatherState;

    public WeatheringCopperBarsBlock(WeatherState weatherState, Properties properties) {
        super(properties);
        this.weatherState = weatherState;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.changeOverTime(state, level, pos, random);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }

    @Override
    public WeatherState getAge() {
        return this.weatherState;
    }
}
