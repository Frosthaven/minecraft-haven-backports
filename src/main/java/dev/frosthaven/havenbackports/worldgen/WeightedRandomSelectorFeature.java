package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 26.x WeightedRandomSelectorFeature: one placed feature picked by weight (WeightedList.getRandom: one
 * nextInt(total weight); 1.21.1's SimpleWeightedRandomList picks the same way, same JSON shape). getFeatures lists
 * the children for 1.21.1's feature order check (26.x getSubFeatures).
 */
public class WeightedRandomSelectorFeature extends Feature<WeightedRandomSelectorFeature.Config> {
    public record Config(SimpleWeightedRandomList<Holder<PlacedFeature>> features) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                SimpleWeightedRandomList.wrappedCodec(PlacedFeature.CODEC).fieldOf("features").forGetter(Config::features))
                .apply(i, Config::new));

        @Override
        public Stream<ConfiguredFeature<?, ?>> getFeatures() {
            return this.features.unwrap().stream().flatMap(w -> w.data().value().getFeatures());
        }
    }

    public WeightedRandomSelectorFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        return context.config().features().getRandomValue(context.random())
                .map(p -> p.value().place(context.level(), context.chunkGenerator(), context.random(), context.origin()))
                .orElse(false);
    }
}
