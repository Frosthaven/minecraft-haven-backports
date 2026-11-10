package dev.frosthaven.havenbackports.variant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.core.BlockPos;

/**
 * Mojang's 26.x PigVariant, CowVariant and ChickenVariant (one shape: asset_id, baby_asset_id, model, spawn_conditions)
 * with 26.x's spawn selection (VariantUtils.selectVariantToSpawn and PriorityProvider.pick: the highest priority whose
 * condition holds, a random one among ties). Farm animals' conditions are biome checks (26.x BiomeCheck); another
 * condition type in the data stops loading. The baby asset is read and kept: 1.21.1 draws babies as small adults.
 */
public record FarmVariant(ResourceLocation assetId, Optional<ResourceLocation> babyAssetId, String model, List<Selector> spawnConditions) {
    public record BiomeCheck(HolderSet<Biome> biomes) {
        static final Codec<BiomeCheck> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(c -> "minecraft:biome"),
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(BiomeCheck::biomes)).apply(i, (type, biomes) -> {
            if (!type.equals("minecraft:biome")) {
                throw new IllegalArgumentException("spawn condition " + type + " is not ported");
            }
            return new BiomeCheck(biomes);
        }));
    }

    public record Selector(Optional<BiomeCheck> condition, int priority) {
        static final Codec<Selector> CODEC = RecordCodecBuilder.create(i -> i.group(
                BiomeCheck.CODEC.optionalFieldOf("condition").forGetter(Selector::condition),
                Codec.INT.fieldOf("priority").forGetter(Selector::priority)).apply(i, Selector::new));
    }

    public static final Codec<FarmVariant> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("asset_id").forGetter(FarmVariant::assetId),
            ResourceLocation.CODEC.optionalFieldOf("baby_asset_id").forGetter(FarmVariant::babyAssetId),
            Codec.STRING.optionalFieldOf("model", "normal").forGetter(FarmVariant::model),
            Selector.CODEC.listOf().fieldOf("spawn_conditions").forGetter(FarmVariant::spawnConditions)).apply(i, FarmVariant::new));
    /** What clients need: the look (asset and model), not the spawn rules. */
    public static final Codec<FarmVariant> NETWORK_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("asset_id").forGetter(FarmVariant::assetId),
            Codec.STRING.optionalFieldOf("model", "normal").forGetter(FarmVariant::model)).apply(i, (a, m) -> new FarmVariant(a, Optional.empty(), m, List.of())));

    /** 26.x VariantUtils.selectVariantToSpawn for a spawn at pos. */
    public static <T> Optional<Holder<FarmVariant>> select(ServerLevelAccessor level, BlockPos pos, ResourceKey<Registry<FarmVariant>> key, RandomSource random) {
        Holder<Biome> biome = level.getBiome(pos);
        record Entry(Holder<FarmVariant> variant, int priority, Optional<BiomeCheck> condition) {}
        List<Entry> entries = new ArrayList<>();
        level.registryAccess().registryOrThrow(key).holders().forEach(h -> h.value().spawnConditions()
                .forEach(s -> entries.add(new Entry(h, s.priority(), s.condition()))));
        entries.sort(Comparator.comparingInt(Entry::priority).reversed());
        int highest = Integer.MIN_VALUE;
        List<Holder<FarmVariant>> picked = new ArrayList<>();
        for (Entry e : entries) {
            if (e.priority() < highest) {
                break;
            }
            if (e.condition().map(c -> c.biomes().contains(biome)).orElse(true)) {
                highest = e.priority();
                picked.add(e.variant());
            }
        }
        return picked.isEmpty() ? Optional.empty() : Optional.of(picked.get(random.nextInt(picked.size())));
    }
}
