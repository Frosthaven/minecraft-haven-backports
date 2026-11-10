package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.spear.SpearState;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x AbstractMinecart.tick: computeSpeed right after checkBelowWorld (a minecart's tick skips baseTick). */
@Mixin(AbstractMinecart.class)
abstract class AbstractMinecartMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;checkBelowWorld()V", shift = At.Shift.AFTER))
    private void haven_backports$computeSpeed(CallbackInfo ci) {
        ((SpearState.Known) this).haven_backports$computeSpeed();
    }
}
