package dev.frosthaven.havenbackports.particle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.frosthaven.havenbackports.PaleGarden;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;

/** 26.x TrailParticleOption (particle "trail", 1.21.4): a particle that flies to a target in a set time, in a color.
 *  Eyeblossoms and the creaking heart use it. */
public record TrailParticleOption(Vec3 target, int color, int duration) implements ParticleOptions {
    public static final MapCodec<TrailParticleOption> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Vec3.CODEC.fieldOf("target").forGetter(TrailParticleOption::target),
                    com.mojang.serialization.Codec.INT.fieldOf("color").forGetter(TrailParticleOption::color),
                    ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(TrailParticleOption::duration))
            .apply(i, TrailParticleOption::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrailParticleOption> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Vec3.CODEC), TrailParticleOption::target, ByteBufCodecs.INT, TrailParticleOption::color,
            ByteBufCodecs.VAR_INT, TrailParticleOption::duration, TrailParticleOption::new);

    @Override
    public ParticleType<TrailParticleOption> getType() {
        return PaleGarden.TRAIL.get();
    }
}
