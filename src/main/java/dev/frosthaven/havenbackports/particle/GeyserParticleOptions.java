package dev.frosthaven.havenbackports.particle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/** 26.x GeyserParticleOptions: a geyser particle and the water depth above the potent sulfur. */
public record GeyserParticleOptions(ParticleType<GeyserParticleOptions> type, int waterBlocks) implements ParticleOptions {
    public static MapCodec<GeyserParticleOptions> codec(ParticleType<GeyserParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(ExtraCodecs.POSITIVE_INT.fieldOf("water_blocks").forGetter(o -> o.waterBlocks))
                .apply(i, w -> new GeyserParticleOptions(type, w)));
    }

    public static StreamCodec<? super ByteBuf, GeyserParticleOptions> streamCodec(ParticleType<GeyserParticleOptions> type) {
        return StreamCodec.composite(ByteBufCodecs.INT, o -> o.waterBlocks, w -> new GeyserParticleOptions(type, w));
    }

    @Override
    public ParticleType<GeyserParticleOptions> getType() {
        return this.type;
    }
}
