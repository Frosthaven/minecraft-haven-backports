package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.world.entity.animal.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.x Sheep.ate: eating grass ages a lamb up only if canAgeUp (1.21.1: isBaby). */
@Mixin(Sheep.class)
abstract class AgeLockSheepMixin {
    @WrapOperation(method = "ate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Sheep;isBaby()Z"))
    private boolean haven_backports$canAgeUp(Sheep self, Operation<Boolean> original) {
        return original.call(self) && !AgeLock.isAgeLocked(self);
    }
}
