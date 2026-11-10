package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Puts placed features into vanilla biomes where 26.x lists them (NeoForge's add_features only appends to a step, and
 * the order of a step decides which feature sees the other's blocks; a different order between biomes is also a feature
 * order cycle). Each entry, in 26.x order: take the place of `replaces` when the biome still has it, else go after
 * `after` (the last feature before it in 26.x's list that the biome has; none: first in the step), else (another mod
 * took `after` out) at the end of the step. Other mods'
 * modifiers keep working: nothing else in the step moves. tools/biome_mods.py writes these and checks for cycles.
 */
public record PlaceFeaturesModifier(HolderSet<Biome> biomes, GenerationStep.Decoration step, List<Entry> features) implements BiomeModifier {
    public record Entry(Holder<PlacedFeature> feature, Optional<ResourceKey<PlacedFeature>> after, Optional<ResourceKey<PlacedFeature>> replaces) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                PlacedFeature.CODEC.fieldOf("feature").forGetter(Entry::feature),
                ResourceKey.codec(Registries.PLACED_FEATURE).optionalFieldOf("after").forGetter(Entry::after),
                ResourceKey.codec(Registries.PLACED_FEATURE).optionalFieldOf("replaces").forGetter(Entry::replaces)).apply(i, Entry::new));
    }

    public static final MapCodec<PlaceFeaturesModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(PlaceFeaturesModifier::biomes),
            GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(PlaceFeaturesModifier::step),
            Entry.CODEC.listOf().fieldOf("features").forGetter(PlaceFeaturesModifier::features)).apply(i, PlaceFeaturesModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !this.biomes.contains(biome)) {
            return;
        }
        List<Holder<PlacedFeature>> list = builder.getGenerationSettings().getFeatures(this.step);
        for (Entry e : this.features) {
            int replaced = e.replaces().map(k -> indexOf(list, k)).orElse(-1);
            if (replaced >= 0) {
                list.set(replaced, e.feature());
                continue;
            }
            if (e.after().isEmpty()) {
                list.add(0, e.feature());   // first in 26.x's step
                continue;
            }
            int after = indexOf(list, e.after().get());
            if (after >= 0) {
                list.add(after + 1, e.feature());
            } else {
                list.add(e.feature());
            }
        }
    }

    private static int indexOf(List<Holder<PlacedFeature>> list, ResourceKey<PlacedFeature> key) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).is(key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
