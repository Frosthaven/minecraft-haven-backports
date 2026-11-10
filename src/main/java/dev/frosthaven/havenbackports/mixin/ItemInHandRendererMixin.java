package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havenbackports.client.spear.SpearClient;
import dev.frosthaven.havenbackports.spear.SpearItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x FirstPersonHandsAndItems lowers the main hand by the item-swap scale (reset only when the main-hand item
 * changes), so a jab does not dip the hand. 1.21.1 uses the attack strength scale, which every attack resets. Owner's
 * decision: only spears follow 26.x here; every other item keeps 1.21.1's dip.
 */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"))
    private float haven_backports$swapScale(float attackStrength, @com.llamalad7.mixinextras.sugar.Local LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof SpearItem ? SpearClient.itemSwapScale(player, 1.0F) : attackStrength;
    }
}
