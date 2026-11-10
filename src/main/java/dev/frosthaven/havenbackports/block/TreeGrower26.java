package dev.frosthaven.havenbackports.block;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x TreeGrower: weighted lists of trees, mega (2x2) trees and flower trees, and the shortest tree type for the
 * bonemeal height check. 1.21.1's TreeGrower holds one tree and one secondary chance, which cannot express 26.x's
 * three equal poplars or pale oak's mega-only list. Block flags are 1.21.1's (4) where 26.x uses 818 / 260 (26.x
 * engine flags). NeoForge's BlockGrowFeatureEvent fires as 1.21.1's TreeGrower fires it.
 */
public record TreeGrower26(SimpleWeightedRandomList<ResourceKey<ConfiguredFeature<?, ?>>> trees,
                           SimpleWeightedRandomList<ResourceKey<ConfiguredFeature<?, ?>>> megaTrees,
                           SimpleWeightedRandomList<ResourceKey<ConfiguredFeature<?, ?>>> flowerTrees,
                           @Nullable ResourceKey<ConfiguredFeature<?, ?>> shortestTreeType) {

    @Nullable
    private ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers) {
        return hasFlowers && !this.flowerTrees.isEmpty() ? this.flowerTrees.getRandomValue(random).orElse(null)
                : this.trees.getRandomValue(random).orElse(null);
    }

    @Nullable
    private ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random) {
        return this.megaTrees.getRandomValue(random).orElse(null);
    }

    @Nullable
    private static Holder<ConfiguredFeature<?, ?>> holder(ServerLevel level, ResourceKey<ConfiguredFeature<?, ?>> key) {
        return level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(key).orElse(null);
    }

    public boolean growTree(ServerLevel level, ChunkGenerator generator, BlockPos pos, BlockState state, RandomSource random) {
        ResourceKey<ConfiguredFeature<?, ?>> megaKey = this.getConfiguredMegaFeature(random);
        if (megaKey != null) {
            Holder<ConfiguredFeature<?, ?>> h = holder(level, megaKey);
            var event = EventHooks.fireBlockGrowFeature(level, random, pos, h);
            h = event.getFeature();
            if (event.isCanceled()) {
                return false;
            }
            if (h != null) {
                Optional<TwoByTwo> twoByTwo = findTwoByTwoSaplingPos(level, state, pos);
                if (twoByTwo.isPresent()) {
                    List<Pair<BlockState, BlockPos>> saplings = twoByTwo.get().saplings();
                    saplings.forEach(p -> removeSapling(level, p.getSecond()));
                    if (h.value().place(level, generator, random, pos.offset(twoByTwo.get().dx(), 0, twoByTwo.get().dz()))) {
                        return true;
                    }
                    resetSaplings(level, saplings);
                    return false;
                }
            }
        }
        ResourceKey<ConfiguredFeature<?, ?>> key = this.getConfiguredFeature(random, this.hasFlowers(level, pos));
        if (key == null) {
            return false;
        }
        Holder<ConfiguredFeature<?, ?>> h = holder(level, key);
        var event = EventHooks.fireBlockGrowFeature(level, random, pos, h);
        h = event.getFeature();
        if (event.isCanceled() || h == null) {
            return false;
        }
        removeSapling(level, pos);
        if (h.value().place(level, generator, random, pos)) {
            return true;
        }
        resetSaplings(level, List.of(Pair.of(state, pos)));
        return false;
    }

    private static void removeSapling(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), 4);
    }

    private static void resetSaplings(ServerLevel level, List<Pair<BlockState, BlockPos>> saplings) {
        for (Pair<BlockState, BlockPos> s : saplings) {
            level.setBlock(s.getSecond(), s.getFirst(), 4);
        }
    }

    private boolean hasFlowers(LevelAccessor level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            if (level.getBlockState(p).is(BlockTags.FLOWERS)) {
                return true;
            }
        }
        return false;
    }

    /** 26.x getMinimumHeight: the shortest tree's trunk base height. */
    public OptionalInt getMinimumHeight(ServerLevel level) {
        if (this.shortestTreeType == null) {
            return OptionalInt.empty();
        }
        Holder<ConfiguredFeature<?, ?>> h = holder(level, this.shortestTreeType);
        if (h != null && h.value().config() instanceof TreeConfiguration tree) {
            return OptionalInt.of(tree.trunkPlacer.baseHeight);   // accesstransformer.cfg (26.x getBaseHeight)
        }
        return OptionalInt.empty();
    }

    public boolean canGrow(ServerLevel level, BlockPos pos, BlockState state) {
        ResourceKey<ConfiguredFeature<?, ?>> key = this.getConfiguredFeature(level.getRandom(), this.hasFlowers(level, pos));
        ResourceKey<ConfiguredFeature<?, ?>> megaKey = this.getConfiguredMegaFeature(level.getRandom());
        return key == null && megaKey != null ? findTwoByTwoSaplingPos(level, state, pos).isPresent() : true;
    }

    private record TwoByTwo(int dx, int dz, List<Pair<BlockState, BlockPos>> saplings) {}

    private static Optional<TwoByTwo> findTwoByTwoSaplingPos(ServerLevel level, BlockState state, BlockPos pos) {
        for (int dx = 0; dx >= -1; dx--) {
            for (int dz = 0; dz >= -1; dz--) {
                List<Pair<BlockState, BlockPos>> around = List.of(
                        Pair.of(level.getBlockState(pos.offset(dx, 0, dz)), pos.offset(dx, 0, dz)),
                        Pair.of(level.getBlockState(pos.offset(dx + 1, 0, dz)), pos.offset(dx + 1, 0, dz)),
                        Pair.of(level.getBlockState(pos.offset(dx, 0, dz + 1)), pos.offset(dx, 0, dz + 1)),
                        Pair.of(level.getBlockState(pos.offset(dx + 1, 0, dz + 1)), pos.offset(dx + 1, 0, dz + 1)));
                if (around.stream().allMatch(p -> p.getFirst().is(state.getBlock()))) {
                    return Optional.of(new TwoByTwo(dx, dz, around));
                }
            }
        }
        return Optional.empty();
    }
}
