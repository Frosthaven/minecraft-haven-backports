package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havenbackports.spear.SpearItem;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x LocalPlayer reads the use item's USE_EFFECTS: a spear (canSprint, speed 1.0) neither slows the player nor
 * blocks or resets sprinting while charging (isSlowDueToUsingItem false, itemUseSpeedMultiplier 1). 1.21.1 tests
 * isUsingItem in those places: aiStep's input slowdown and sprint start, and canStartSprinting.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @ModifyExpressionValue(method = {"aiStep", "canStartSprinting"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean haven_backports$spearCanSprint(boolean using) {
        return using && !(((LocalPlayer) (Object) this).getUseItem().getItem() instanceof SpearItem);
    }
}
