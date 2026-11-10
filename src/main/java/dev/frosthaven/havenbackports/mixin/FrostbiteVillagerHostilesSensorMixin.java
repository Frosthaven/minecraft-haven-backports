package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.frostbite.FrostbiteContent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x VillagerHostilesSensor.ACCEPTABLE_DISTANCE_FROM_HOSTILES puts the frostbite at 8 blocks (as the zombie):
 * villagers flee it. 1.21.1's map is a private constant; this answers for the frostbite only.
 */
@Mixin(VillagerHostilesSensor.class)
abstract class FrostbiteVillagerHostilesSensorMixin {
    @Inject(method = "isHostile", at = @At("HEAD"), cancellable = true)
    private void haven_backports$frostbiteIsHostile(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity.getType() == FrostbiteContent.FROSTBITE.get()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isClose", at = @At("HEAD"), cancellable = true)
    private void haven_backports$frostbiteIsClose(LivingEntity attacker, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target.getType() == FrostbiteContent.FROSTBITE.get()) {
            cir.setReturnValue(target.distanceToSqr(attacker) <= 8.0F * 8.0F);
        }
    }
}
