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
 * Puts 26.x's placed features into vanilla biomes. An entry with `replaces` takes that feature's place when the biome
 * still has it, and is left out when it does not (another mod removed what 26.x replaced). Every other entry is
 * appended to the step, in 26.x order. Inserting next to a vanilla feature (`after`) is not safe in a modded pack:
 * where a mod rewrites a biome's list (Geophilic) or appends its own features (Molten Vents), the same two features
 * end up in a different order in two biomes, a feature order cycle that stops chunk generation. Appending keeps one
 * order everywhere, because modifiers run in the same order for every biome. `after` stays in the data as 26.x's
 * place, for the record only. tools/biome_mods.py writes these and checks for cycles.
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
            if (e.replaces().isPresent()) {
                int replaced = indexOf(list, e.replaces().get());
                if (replaced >= 0) {
                    list.set(replaced, e.feature());
                }
                continue;
            }
            list.add(e.feature());
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
