package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.x AbstractHorse.handleEating ages a baby up only if it is not age locked (the one isBaby() call there). Llama:
 *  AgeLockLlamaMixin; Camel: AgeLockCamelMixin. */
@Mixin(AbstractHorse.class)
abstract class AgeLockEatingMixin {
    @WrapOperation(method = "handleEating", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/horse/AbstractHorse;isBaby()Z"))
    private boolean haven_backports$canAgeUp(AbstractHorse self, Operation<Boolean> original) {
        return original.call(self) && !AgeLock.isAgeLocked(self);
    }
}
