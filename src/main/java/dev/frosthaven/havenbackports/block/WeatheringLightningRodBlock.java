package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mojang's WeatheringLightningRodBlock (26.x): a LightningRodBlock that oxidizes; random ticks call changeOverTime
 * while a next stage exists (NeoForge's oxidizables data map). Its unaffected stage is the vanilla
 * minecraft:lightning_rod, which LightningRodBlockMixin lets age into the exposed rod.
 */
public class WeatheringLightningRodBlock extends LightningRodBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringLightningRodBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringLightningRodBlock::getAge),
            propertiesCodec()).apply(i, WeatheringLightningRodBlock::new));
    private final WeatherState weatherState;

    public WeatheringLightningRodBlock(WeatherState weatherState, Properties properties) {
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
