package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.x SaplingBlock with a 26.x TreeGrower ({@link TreeGrower26}). advanceTree and isValidBonemealTarget are 26.x's
 * (the target check asks the grower whether it can grow, which draws from the level's random as 26.x does, and
 * needs the shortest tree's base height inside the build height). 1.21.1's randomTick (with NeoForge's loaded-area
 * guard) and 45% bonemeal success are 26.x's too. The 1.21.1 TreeGrower passed to super only names the sapling
 * (SaplingBlock's codec); it never grows anything.
 */
public class SaplingBlock26 extends SaplingBlock {
    private final Supplier<TreeGrower26> grower;

    public SaplingBlock26(Supplier<TreeGrower26> grower, String name, Properties properties) {
        super(new TreeGrower(name, Optional.empty(), Optional.empty(), Optional.empty()), properties);
        this.grower = grower;
    }

    @Override
    public MapCodec<? extends SaplingBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), 4);
        } else {
            this.grower.get().growTree(level, level.getChunkSource().getGenerator(), pos, state, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            if (!this.grower.get().canGrow(serverLevel, pos, state)) {
                return false;
            }
            int heightOffset = this.grower.get().getMinimumHeight(serverLevel).orElse(0);
            return !level.isOutsideBuildHeight(pos.above(heightOffset));   // 26.x isInsideBuildHeight
        }
        return false;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return level.getRandom().nextFloat() < 0.45;
    }
}
