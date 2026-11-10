package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.ghast.HappyGhast;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x Entity.canCollideWith asks the other entity canBeCollidedWith(this); 1.21.1 asks canBeCollidedWith() with no
 * argument. A happy ghast answers per entity (a player above it may stand on it; it is solid while it holds still), so
 * for a happy ghast the 26.x question is asked. One instanceof on this hot path; every other pair is untouched.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void haven_backports$happyGhast(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (other instanceof HappyGhast ghast) {
            Entity self = (Entity) (Object) this;
            cir.setReturnValue(ghast.canBeCollidedWith(self) && !self.isPassengerOfSameVehicle(other));
        }
    }
}
