package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havenbackports.spear.SpearItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 26.x Minecraft.continueAttack does nothing while a PIERCING_WEAPON is held (no mining, no stopDestroyBlock); the
 *  jab is on the click only (client/spear/SpearClient). The 1.21.1 block is skipped as if an item were in use. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow
    public LocalPlayer player;

    @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean haven_backports$spearHeld(boolean using) {
        return using || this.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof SpearItem;
    }
}
