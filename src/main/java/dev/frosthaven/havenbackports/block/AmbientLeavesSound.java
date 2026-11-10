package dev.frosthaven.havenbackports.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.x AmbientLeavesBlockSoundPlayer.of(sound, tag): chance 300, 1 satisfying neighbour, 3 same leaves. The loop is
 * Mojang's, quirk included: the sound plays when the running counts are exactly 1 and 3 at some neighbour.
 */
public record AmbientLeavesSound(Supplier<SoundEvent> ambientSound, TagKey<Block> satisfyingBlocks) {
    public void play(Level level, BlockPos pos, Block block, RandomSource random) {
        if (random.nextInt(300) == 0) {
            int nearbyLogs = 0;
            int nearbyLeaves = 0;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockState neighborState = level.getBlockState(pos.relative(direction));
                if (neighborState.is(this.satisfyingBlocks)) {
                    nearbyLogs++;
                }
                if (neighborState.is(block)) {
                    nearbyLeaves++;
                }
                if (nearbyLogs == 1 && nearbyLeaves == 3) {
                    level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), this.ambientSound.get(), SoundSource.AMBIENT, 1.0F, 1.0F, false);
                    return;
                }
            }
        }
    }
}
