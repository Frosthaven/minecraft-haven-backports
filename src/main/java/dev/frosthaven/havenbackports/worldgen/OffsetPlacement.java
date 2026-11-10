package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** 26.x OffsetPlacement: x, y and z each from their own int provider, sampled in that order. 1.21.1's random_offset
 *  shares one provider for x and z. */
public class OffsetPlacement extends PlacementModifier {
    public static final MapCodec<OffsetPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    IntProvider.codec(-16, 16).fieldOf("x").forGetter(p -> p.x),
                    IntProvider.codec(-16, 16).fieldOf("y").forGetter(p -> p.y),
                    IntProvider.codec(-16, 16).fieldOf("z").forGetter(p -> p.z))
            .apply(i, OffsetPlacement::new));
    private final IntProvider x;
    private final IntProvider y;
    private final IntProvider z;

    public OffsetPlacement(IntProvider x, IntProvider y, IntProvider z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int dx = this.x.sample(random);
        int dy = this.y.sample(random);
        int dz = this.z.sample(random);
        return Stream.of(pos.offset(dx, dy, dz));
    }

    @Override
    public PlacementModifierType<?> type() {
        return Worldgen.OFFSET.get();
    }
}
