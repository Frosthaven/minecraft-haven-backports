package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.PaleGarden;
import dev.frosthaven.havenbackports.block.HangingMossBlock;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/**
 * 26.x PaleMossDecorator, line for line: a pale moss patch at the trunk's foot, hanging moss under logs and leaves.
 * 1.21.1's decorator context holds a LevelSimulatedReader; trees place in a WorldGenLevel (world generation and
 * saplings alike), which the moss patch needs.
 */
public class PaleMossDecorator extends TreeDecorator {
    public static final MapCodec<PaleMossDecorator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.floatRange(0.0F, 1.0F).fieldOf("leaves_probability").forGetter(p -> p.leavesProbability),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("trunk_probability").forGetter(p -> p.trunkProbability),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("ground_probability").forGetter(p -> p.groundProbability))
            .apply(i, PaleMossDecorator::new));
    static final ResourceKey<ConfiguredFeature<?, ?>> PALE_MOSS_PATCH = ResourceKey.create(Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "pale_moss_patch"));
    private final float leavesProbability;
    private final float trunkProbability;
    private final float groundProbability;

    public PaleMossDecorator(float leavesProbability, float trunkProbability, float groundProbability) {
        this.leavesProbability = leavesProbability;
        this.trunkProbability = trunkProbability;
        this.groundProbability = groundProbability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return Worldgen.PALE_MOSS.get();
    }

    @Override
    public void place(Context context) {
        RandomSource random = context.random();
        List<BlockPos> logs = Util.shuffledCopy(context.logs(), random);
        if (logs.isEmpty()) {
            return;
        }
        BlockPos origin = Collections.min(logs, Comparator.comparingInt(Vec3i::getY));
        if (random.nextFloat() < this.groundProbability && context.level() instanceof WorldGenLevel level) {
            level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap(r -> r.getHolder(PALE_MOSS_PATCH))
                    .ifPresent(patch -> patch.value().place(level, level.getLevel().getChunkSource().getGenerator(), random, origin.above()));
        }
        context.logs().forEach(pos -> {
            if (random.nextFloat() < this.trunkProbability && context.isAir(pos.below())) {
                addMossHanger(pos.below(), context);
            }
        });
        context.leaves().forEach(pos -> {
            if (random.nextFloat() < this.leavesProbability && context.isAir(pos.below())) {
                addMossHanger(pos.below(), context);
            }
        });
    }

    private static void addMossHanger(BlockPos pos, Context context) {
        var moss = PaleGarden.PALE_HANGING_MOSS.get().defaultBlockState();
        while (context.isAir(pos.below()) && !(context.random().nextFloat() < 0.5)) {
            context.setBlock(pos, moss.setValue(HangingMossBlock.TIP, false));
            pos = pos.below();
        }
        context.setBlock(pos, moss.setValue(HangingMossBlock.TIP, true));
    }
}
