package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.spear.Stab;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x Player.attack ends with postPiercingAttack (Lunge) once the target was attackable and not a deflected
 *  projectile; in 1.21.1 that point is NeoForge's moved resetAttackStrengthTicker call. */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;resetAttackStrengthTicker()V"))
    private void haven_backports$postPiercing(Entity target, CallbackInfo ci) {
        Stab.postPiercingAttack((Player) (Object) this);
    }
}
