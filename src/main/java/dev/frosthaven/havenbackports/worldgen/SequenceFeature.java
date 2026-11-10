package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** 26.x SequenceFeature: places each placed feature in order at the origin, stopping at the first that places
 *  nothing. 1.21.1's PlacedFeature.place applies the placement the way 26.x's FeaturePlacer does (depth first). */
public class SequenceFeature extends Feature<SequenceFeature.Config> {
    public record Config(HolderSet<PlacedFeature> features) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.nonEmptyHolderSet(PlacedFeature.LIST_CODEC).fieldOf("features").forGetter(Config::features)).apply(i, Config::new));

        @Override
        public Stream<ConfiguredFeature<?, ?>> getFeatures() {
            return this.features.stream().flatMap(f -> f.value().getFeatures());
        }
    }

    public SequenceFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        for (Holder<PlacedFeature> f : context.config().features()) {
            if (!f.value().place(context.level(), context.chunkGenerator(), context.random(), context.origin())) {
                return false;
            }
        }
        return true;
    }
}
