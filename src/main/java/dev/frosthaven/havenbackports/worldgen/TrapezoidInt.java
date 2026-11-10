package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviderType;

/** 26.x TrapezoidInt (int provider "trapezoid"), line for line. 1.21.1 has only the height provider of that name. */
public class TrapezoidInt extends IntProvider {
    public static final MapCodec<TrapezoidInt> CODEC = RecordCodecBuilder.<TrapezoidInt>mapCodec(i -> i.group(
                    Codec.INT.fieldOf("min").forGetter(t -> t.min),
                    Codec.INT.fieldOf("max").forGetter(t -> t.max),
                    Codec.INT.fieldOf("plateau").forGetter(t -> t.plateau))
            .apply(i, TrapezoidInt::new))
            .validate(c -> c.max < c.min ? DataResult.error(() -> "Max must be larger than min: [" + c.min + ", " + c.max + "]")
                    : c.plateau > c.max - c.min ? DataResult.error(() -> "Plateau can at most be the full span: [" + c.min + ", " + c.max + "]")
                    : DataResult.success(c));
    private final int min;
    private final int max;
    private final int plateau;

    public TrapezoidInt(int min, int max, int plateau) {
        this.min = min;
        this.max = max;
        this.plateau = plateau;
    }

    @Override
    public int sample(RandomSource random) {
        if (this.plateau == 0 && this.max == -this.min) {
            return random.nextInt(this.max + 1) - random.nextInt(this.max + 1);
        }
        int range = this.max - this.min;
        if (this.plateau == range) {
            return Mth.randomBetweenInclusive(random, this.min, this.max);
        }
        int plateauStart = (range - this.plateau) / 2;
        int plateauEnd = range - plateauStart;
        return this.min + Mth.randomBetweenInclusive(random, 0, plateauEnd) + Mth.randomBetweenInclusive(random, 0, plateauStart);
    }

    @Override
    public int getMinValue() {
        return this.min;
    }

    @Override
    public int getMaxValue() {
        return this.max;
    }

    @Override
    public IntProviderType<?> getType() {
        return Worldgen.TRAPEZOID.get();
    }
}
