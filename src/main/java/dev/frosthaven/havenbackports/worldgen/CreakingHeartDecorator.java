package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.PaleGarden;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/** 26.x CreakingHeartDecorator, line for line: a natural, dormant creaking heart in a log surrounded by logs. */
public class CreakingHeartDecorator extends TreeDecorator {
    public static final MapCodec<CreakingHeartDecorator> CODEC = Codec.floatRange(0.0F, 1.0F).fieldOf("probability")
            .xmap(CreakingHeartDecorator::new, d -> d.probability);
    private final float probability;

    public CreakingHeartDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return Worldgen.CREAKING_HEART.get();
    }

    @Override
    public void place(Context context) {
        RandomSource random = context.random();
        List<BlockPos> logs = context.logs();
        if (logs.isEmpty() || random.nextFloat() >= this.probability) {
            return;
        }
        List<BlockPos> placements = new ArrayList<>(logs);
        Util.shuffle(placements, random);
        Optional<BlockPos> target = placements.stream().filter(pos -> {
            for (Direction dir : Direction.values()) {
                if (!Decorators.checkBlock(context, pos.relative(dir), s -> s.is(BlockTags.LOGS))) {
                    return false;
                }
            }
            return true;
        }).findFirst();
        target.ifPresent(pos -> context.setBlock(pos, PaleGarden.naturalDormantHeart()));
    }
}
