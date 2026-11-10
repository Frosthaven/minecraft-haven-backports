package dev.frosthaven.havenbackports.frostbite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

/** 26.x Monster.checkColdVariantSpawnRules (the frostbite's and the stray's): the monster rules; outside
 *  #spawns_cold_variants_underground (the ice caves) also the sky above any powder snow, as 1.21.1's stray rule. */
public final class ColdVariantSpawn {
    static final TagKey<Biome> SPAWNS_COLD_VARIANTS_UNDERGROUND = TagKey.create(Registries.BIOME,
            ResourceLocation.withDefaultNamespace("spawns_cold_variants_underground"));

    private ColdVariantSpawn() {}

    public static boolean check(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                RandomSource random) {
        boolean canSpawn = Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
        if (level.getBiome(pos).is(SPAWNS_COLD_VARIANTS_UNDERGROUND)) {
            return canSpawn;
        }
        BlockPos sky = pos;
        do {
            sky = sky.above();
        } while (level.getBlockState(sky).is(Blocks.POWDER_SNOW));
        return canSpawn && (MobSpawnType.isSpawner(reason) || level.canSeeSky(sky.below()));
    }
}
