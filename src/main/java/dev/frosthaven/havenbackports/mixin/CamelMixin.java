package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Husks;
import dev.frosthaven.havenbackports.husk.CamelHusk;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.animal.camel.Camel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 26.x Camel.tick plays getDashReadySound() when the dash cooldown runs out; 1.21.1 names SoundEvents.CAMEL_DASH_READY
 * there. A camel husk plays CAMEL_HUSK_DASH_READY (26.x CamelHusk.getDashReadySound); other camels are unchanged.
 */
@Mixin(Camel.class)
abstract class CamelMixin {
    @ModifyArg(method = "tick", index = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private SoundEvent haven_backports$dashReadySound(SoundEvent sound) {
        return (Object) this instanceof CamelHusk ? Husks.CAMEL_HUSK_DASH_READY.get() : sound;
    }
}
