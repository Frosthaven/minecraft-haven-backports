package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;

/** 26.x BelowHeightmapPredicate, line for line. */
public record BelowHeightmapPredicate(Heightmap.Types heightmap) implements BlockPredicate {
    public static final MapCodec<BelowHeightmapPredicate> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Heightmap.Types.CODEC.fieldOf("heightmap").forGetter(BelowHeightmapPredicate::heightmap)).apply(i, BelowHeightmapPredicate::new));

    @Override
    public boolean test(WorldGenLevel level, BlockPos origin) {
        return origin.getY() < level.getHeight(this.heightmap, origin.getX(), origin.getZ());
    }

    @Override
    public BlockPredicateType<?> type() {
        return Worldgen.BELOW_HEIGHTMAP.get();
    }
}
