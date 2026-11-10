package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 26.x's 3D noise_threshold surface condition (NoiseThresholdCondition with is_3d: the noise at the block, sampled once
 * per block). 1.21.1's noise_threshold samples at y = 0 only. Reads 1.21.1's package-private context
 * (accesstransformer.cfg).
 */
public record NoiseThreshold3d(ResourceKey<NormalNoise.NoiseParameters> noise, double minThreshold, double maxThreshold)
        implements SurfaceRules.ConditionSource {
    public static final KeyDispatchDataCodec<NoiseThreshold3d> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec(i -> i.group(
                    ResourceKey.codec(Registries.NOISE).fieldOf("noise").forGetter(NoiseThreshold3d::noise),
                    Codec.DOUBLE.fieldOf("min_threshold").forGetter(NoiseThreshold3d::minThreshold),
                    Codec.DOUBLE.fieldOf("max_threshold").forGetter(NoiseThreshold3d::maxThreshold))
            .apply(i, NoiseThreshold3d::new)));

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        NormalNoise n = context.randomState.getOrCreateNoise(this.noise);
        return new SurfaceRules.LazyYCondition(context) {
            @Override
            protected boolean compute() {
                double v = n.getValue(this.context.blockX, this.context.blockY, this.context.blockZ);
                return v >= NoiseThreshold3d.this.minThreshold && v <= NoiseThreshold3d.this.maxThreshold;
            }
        };
    }
}
