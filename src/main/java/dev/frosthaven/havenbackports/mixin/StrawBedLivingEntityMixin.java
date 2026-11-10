package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.block.StrawBedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x LivingEntity.setPosToBed lies the sleeper at the bed's sleep height + 0.125 (AbstractBedBlock.getSleepHeight:
 * the top of its shape); 1.21.1 uses a bed's fixed 0.6875. For a straw bed (a lower block) the 26.x height is used.
 */
@Mixin(LivingEntity.class)
abstract class StrawBedLivingEntityMixin {
    @Inject(method = "setPosToBed", at = @At("TAIL"))
    private void haven_backports$strawBedHeight(BlockPos pos, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        BlockState state = self.level().getBlockState(pos);
        if (state.getBlock() instanceof StrawBedBlock) {
            self.setPos(pos.getX() + 0.5, pos.getY() + StrawBedBlock.sleepHeight(state, self.level(), pos) + 0.125, pos.getZ() + 0.5);
        }
    }
}
