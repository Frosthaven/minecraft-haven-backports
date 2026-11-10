package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.x BeehiveBlockEntity.updateBeeAge: the time a bee spent in the hive does not change a locked bee's age. */
@Mixin(BeehiveBlockEntity.Occupant.class)
abstract class AgeLockBeehiveMixin {
    @WrapOperation(method = "setBeeReleaseData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Bee;setAge(I)V"))
    private static void haven_backports$keepLockedAge(Bee bee, int age, Operation<Void> original) {
        if (!AgeLock.isAgeLocked(bee)) {
            original.call(bee, age);
        }
    }
}
