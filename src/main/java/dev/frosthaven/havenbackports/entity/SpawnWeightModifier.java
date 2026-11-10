package dev.frosthaven.havenbackports.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * A spawn weight newer Minecraft changed in a 1.21.1 biome together with a mob of ours (the desert skeleton: 100 in
 * 1.21.1, 50 in 26.x, beside the parched at 50). NeoForge's add_spawns and remove_spawns run in fixed phases (add
 * before remove), so they cannot express "the same entry at a new weight"; this modifier does, in the MODIFY phase.
 * Only an entry still at 1.21.1's weight changes, so a pack's own spawn change is kept. Data: tools/spawns.py.
 */
public record SpawnWeightModifier(HolderSet<Biome> biomes, EntityType<?> entity, int oldWeight, int weight) implements BiomeModifier {
    public static final MapCodec<SpawnWeightModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(SpawnWeightModifier::biomes),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(SpawnWeightModifier::entity),
            ExtraCodecs.POSITIVE_INT.fieldOf("old_weight").forGetter(SpawnWeightModifier::oldWeight),
            ExtraCodecs.POSITIVE_INT.fieldOf("weight").forGetter(SpawnWeightModifier::weight)
    ).apply(i, SpawnWeightModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY || !this.biomes.contains(biome)) {
            return;
        }
        for (MobCategory category : MobCategory.values()) {
            List<SpawnerData> spawns = builder.getMobSpawnSettings().getSpawner(category);
            spawns.replaceAll(s -> s.type == this.entity && s.getWeight().asInt() == this.oldWeight
                    ? new SpawnerData(s.type, this.weight, s.minCount, s.maxCount) : s);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
