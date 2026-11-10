package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.x UntintedParticleLeavesBlock (FallingParticlesLeavesBlock, LeavesBlock.animateTick) on 1.21.1's LeavesBlock:
 * rain drip (1.21.1's own), then the ambient sound, then a falling leaf (chance per tick, only over a block without a
 * full top face). Used by poplar and pale oak leaves.
 */
public class ParticleLeavesBlock extends LeavesBlock {
    private final float leafParticleChance;
    private final Supplier<? extends ParticleOptions> leafParticle;
    private final Optional<AmbientLeavesSound> ambient;

    public ParticleLeavesBlock(float leafParticleChance, Supplier<? extends ParticleOptions> leafParticle, Optional<AmbientLeavesSound> ambient,
            Properties properties) {
        super(properties);
        this.leafParticleChance = leafParticleChance;
        this.leafParticle = leafParticle;
        this.ambient = ambient;
    }

    @Override
    public MapCodec<? extends LeavesBlock> codec() {
        return MapCodec.unit(this);   // constructed in code only, as the other ported blocks with extra arguments
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        this.ambient.ifPresent(a -> a.play(level, pos, this, random));
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (!(random.nextFloat() >= this.leafParticleChance) && !isFaceFull(belowState.getCollisionShape(level, below), Direction.UP)) {
            ParticleUtils.spawnParticleBelow(level, pos, random, this.leafParticle.get());
        }
    }
}
