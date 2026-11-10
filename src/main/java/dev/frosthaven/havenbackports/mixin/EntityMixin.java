package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.entity.Leash26;
import dev.frosthaven.havenbackports.entity.QuadLeash;
import dev.frosthaven.havenbackports.ghast.HappyGhast;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x Entity.canCollideWith asks the other entity canBeCollidedWith(this); 1.21.1 asks canBeCollidedWith() with no
 * argument. A happy ghast answers per entity (a player above it may stand on it; it is solid while it holds still), so
 * for a happy ghast the 26.x question is asked. One instanceof on this hot path; every other pair is untouched.
 *
 * <p>A mob on a lead to a happy ghast runs 26.x's leash tick (Leash26: springs, and four of them for a mob that
 * supports the quad leash) instead of 1.21.1's pull, which is where 26.x's quad leash lives. Other leashes keep 1.21.1's.
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

    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Leashable;tickLeash(Lnet/minecraft/world/entity/Entity;)V"))
    private void haven_backports$leashToGhast(Entity entity, Operation<Void> original) {
        if (!(entity instanceof HappyGhast) && ((Leashable) entity).getLeashHolder() instanceof HappyGhast && entity.level() instanceof ServerLevel level) {
            Leash26.tick(level, (Entity & Leashable) entity, QuadLeash.rules(entity));
        } else {
            original.call(entity);
        }
    }
}
