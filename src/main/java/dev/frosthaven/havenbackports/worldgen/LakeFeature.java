package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * 26.x LakeFeature, line for line: 1.21.1's lake plus the three predicates 26.x added (can_place_feature,
 * can_replace_with_air_or_fluid, can_replace_with_barrier; 1.21.1 hard-codes vanilla tags there).
 */
public class LakeFeature extends Feature<LakeFeature.Config> {
    public record Config(BlockStateProvider fluid, BlockStateProvider barrier, BlockPredicate canPlaceFeature,
                         BlockPredicate canReplaceWithAirOrFluid, BlockPredicate canReplaceWithBarrier) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                        BlockStateProvider.CODEC.fieldOf("fluid").forGetter(Config::fluid),
                        BlockStateProvider.CODEC.fieldOf("barrier").forGetter(Config::barrier),
                        BlockPredicate.CODEC.fieldOf("can_place_feature").forGetter(Config::canPlaceFeature),
                        BlockPredicate.CODEC.fieldOf("can_replace_with_air_or_fluid").forGetter(Config::canReplaceWithAirOrFluid),
                        BlockPredicate.CODEC.fieldOf("can_replace_with_barrier").forGetter(Config::canReplaceWithBarrier))
                .apply(i, Config::new));
    }

    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();

    public LakeFeature() {
        super(Config.CODEC);
    }

    private static boolean edge(boolean[] grid, int xx, int zz, int yy) {
        return !grid[(xx * 16 + zz) * 8 + yy] && (xx < 15 && grid[((xx + 1) * 16 + zz) * 8 + yy] || xx > 0 && grid[((xx - 1) * 16 + zz) * 8 + yy]
                || zz < 15 && grid[(xx * 16 + zz + 1) * 8 + yy] || zz > 0 && grid[(xx * 16 + (zz - 1)) * 8 + yy]
                || yy < 7 && grid[(xx * 16 + zz) * 8 + yy + 1] || yy > 0 && grid[(xx * 16 + zz) * 8 + (yy - 1)]);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config c = context.config();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (origin.getY() <= level.getMinBuildHeight() + 4) {
            return false;
        }
        origin = origin.offset(-8, -4, -8);
        boolean[] grid = new boolean[2048];
        int spots = random.nextInt(4) + 4;
        for (int i = 0; i < spots; i++) {
            double xr = random.nextDouble() * 6.0 + 3.0;
            double yr = random.nextDouble() * 4.0 + 2.0;
            double zr = random.nextDouble() * 6.0 + 3.0;
            double xp = random.nextDouble() * (16.0 - xr - 2.0) + 1.0 + xr / 2.0;
            double yp = random.nextDouble() * (8.0 - yr - 4.0) + 2.0 + yr / 2.0;
            double zp = random.nextDouble() * (16.0 - zr - 2.0) + 1.0 + zr / 2.0;
            for (int xx = 1; xx < 15; xx++) {
                for (int zz = 1; zz < 15; zz++) {
                    for (int yy = 1; yy < 7; yy++) {
                        double xd = (xx - xp) / (xr / 2.0);
                        double yd = (yy - yp) / (yr / 2.0);
                        double zd = (zz - zp) / (zr / 2.0);
                        if (xd * xd + yd * yd + zd * zd < 1.0) {
                            grid[(xx * 16 + zz) * 8 + yy] = true;
                        }
                    }
                }
            }
        }
        BlockState fluid = c.fluid().getState(random, origin);
        for (int xx = 0; xx < 16; xx++) {
            for (int zz = 0; zz < 16; zz++) {
                for (int yy = 0; yy < 8; yy++) {
                    if (edge(grid, xx, zz, yy)) {
                        BlockPos p = origin.offset(xx, yy, zz);
                        BlockState s = level.getBlockState(p);
                        if (yy >= 4 && s.liquid()) {
                            return false;
                        }
                        if (yy < 4 && !s.isSolid() && s != fluid) {
                            return false;
                        }
                        if (!c.canPlaceFeature().test(level, p)) {
                            return false;
                        }
                    }
                }
            }
        }
        for (int xx = 0; xx < 16; xx++) {
            for (int zz = 0; zz < 16; zz++) {
                for (int yy = 0; yy < 8; yy++) {
                    if (grid[(xx * 16 + zz) * 8 + yy]) {
                        BlockPos p = origin.offset(xx, yy, zz);
                        if (c.canReplaceWithAirOrFluid().test(level, p)) {
                            boolean air = yy >= 4;
                            level.setBlock(p, air ? AIR : fluid, 2);
                            if (air) {
                                level.scheduleTick(p, AIR.getBlock(), 0);
                                this.markAboveForPostProcessing(level, p);
                            }
                        }
                    }
                }
            }
        }
        BlockState barrier = c.barrier().getState(random, origin);
        if (!barrier.isAir()) {
            for (int xx = 0; xx < 16; xx++) {
                for (int zz = 0; zz < 16; zz++) {
                    for (int yy = 0; yy < 8; yy++) {
                        if (edge(grid, xx, zz, yy) && (yy < 4 || random.nextInt(2) != 0)) {
                            BlockPos p = origin.offset(xx, yy, zz);
                            if (level.getBlockState(p).isSolid() && c.canReplaceWithBarrier().test(level, p)) {
                                level.setBlock(p, barrier, 2);
                                this.markAboveForPostProcessing(level, p);
                            }
                        }
                    }
                }
            }
        }
        if (fluid.getFluidState().is(FluidTags.WATER)) {
            for (int xx = 0; xx < 16; xx++) {
                for (int zz = 0; zz < 16; zz++) {
                    BlockPos p = origin.offset(xx, 4, zz);
                    if (level.getBiome(p).value().shouldFreeze(level, p, false) && c.canReplaceWithAirOrFluid().test(level, p)) {
                        level.setBlock(p, Blocks.ICE.defaultBlockState(), 2);
                    }
                }
            }
        }
        return true;
    }
}
