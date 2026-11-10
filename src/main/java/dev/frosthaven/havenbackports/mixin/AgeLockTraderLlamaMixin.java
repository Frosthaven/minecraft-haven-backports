package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.x TraderLlama.canDespawn: an age-locked trader llama never despawns. */
@Mixin(TraderLlama.class)
abstract class AgeLockTraderLlamaMixin {
    @Inject(method = "canDespawn", at = @At("RETURN"), cancellable = true)
    private void haven_backports$keepLocked(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && AgeLock.isAgeLocked((TraderLlama) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
