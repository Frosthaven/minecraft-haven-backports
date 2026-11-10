package dev.frosthaven.havenbackports.golem;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;

/** Mojang's 26.x CopperGolemState (synced as COPPER_GOLEM_STATE), and 26.x WeatherState.next/previous (clamped). */
public enum CopperGolemState {
    IDLE, GETTING_ITEM, GETTING_NO_ITEM, DROPPING_ITEM, DROPPING_NO_ITEM;

    public static final StreamCodec<ByteBuf, CopperGolemState> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(CopperGolemState::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), CopperGolemState::ordinal);
    /** 26.x WeatherState.STREAM_CODEC (ids 0..3, clamped), for the WEATHERING_COPPER_STATE serializer. */
    public static final StreamCodec<ByteBuf, WeatherState> WEATHER_STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(WeatherState::ordinal, WeatherState.values(), ByIdMap.OutOfBoundsStrategy.CLAMP), WeatherState::ordinal);

    public static WeatherState next(WeatherState s) {
        return WeatherState.values()[Math.min(s.ordinal() + 1, 3)];
    }

    public static WeatherState previous(WeatherState s) {
        return WeatherState.values()[Math.max(s.ordinal() - 1, 0)];
    }
}
