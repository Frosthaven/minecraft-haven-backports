package dev.frosthaven.havenbackports.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/** 26.x GeyserBaseParticleOptions: water depth and the burst impulse of a geyser's base and poof particles. */
public record GeyserBaseParticleOptions(ParticleType<GeyserBaseParticleOptions> type, int waterBlocks, float burstImpulseBase)
        implements ParticleOptions {
    public static MapCodec<GeyserBaseParticleOptions> codec(ParticleType<GeyserBaseParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("water_blocks").forGetter(o -> o.waterBlocks),
                        Codec.FLOAT.fieldOf("burst_impulse_base").forGetter(o -> o.burstImpulseBase))
                .apply(i, (w, b) -> new GeyserBaseParticleOptions(type, w, b)));
    }

    public static StreamCodec<? super ByteBuf, GeyserBaseParticleOptions> streamCodec(ParticleType<GeyserBaseParticleOptions> type) {
        return StreamCodec.composite(ByteBufCodecs.INT, o -> o.waterBlocks, ByteBufCodecs.FLOAT, o -> o.burstImpulseBase,
                (w, b) -> new GeyserBaseParticleOptions(type, w, b));
    }

    @Override
    public ParticleType<GeyserBaseParticleOptions> getType() {
        return this.type;
    }
}
