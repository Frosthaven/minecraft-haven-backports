package dev.frosthaven.havenbackports.block;

import dev.frosthaven.havenbackports.Night26;
import dev.frosthaven.havenbackports.PaleGarden;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;

/** 26.x FlowerPotBlock.randomTick for the potted eyeblossoms: the potted bloom opens and closes by time as a planted
 *  one does (the long sound, the trail particle; no cascade). */
public class PottedEyeblossomBlock extends FlowerPotBlock {
    private final boolean open;

    public PottedEyeblossomBlock(boolean open, Supplier<? extends Block> plant, Properties properties) {
        super(() -> (FlowerPotBlock) Blocks.FLOWER_POT, plant, properties);
        this.open = open;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean shouldBeOpen = Night26.active(level).orElse(this.open);
        if (this.open != shouldBeOpen) {
            level.setBlockAndUpdate(pos, (this.open ? PaleGarden.POTTED_CLOSED_EYEBLOSSOM : PaleGarden.POTTED_OPEN_EYEBLOSSOM).get().defaultBlockState());
            EyeblossomBlock.Type next = EyeblossomBlock.Type.fromBoolean(this.open).transform();
            next.spawnTransformParticle(level, pos, random);
            level.playSound(null, pos, next.longSwitchSound().get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        super.randomTick(state, level, pos, random);
    }
}
