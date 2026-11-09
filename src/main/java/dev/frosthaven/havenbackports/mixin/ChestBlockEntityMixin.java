package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.block.CopperChestBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 26.x chests play their block's own open and close sounds (ChestBlock.getOpenChestSound); 1.21.1 hard-codes them. */
@Mixin(ChestBlockEntity.class)
abstract class ChestBlockEntityMixin {
    @ModifyVariable(method = "playSound", at = @At("HEAD"), argsOnly = true)
    private static SoundEvent haven_backports$hinge(SoundEvent sound, Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CopperChestBlock chest) {
            if (sound == SoundEvents.CHEST_OPEN) return chest.getOpenChestSound();
            if (sound == SoundEvents.CHEST_CLOSE) return chest.getCloseChestSound();
        }
        return sound;
    }
}
