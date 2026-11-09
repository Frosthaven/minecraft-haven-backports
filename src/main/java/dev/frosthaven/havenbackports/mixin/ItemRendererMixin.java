package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.OnShelf;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 26.x's trident and spyglass item definitions pick the flat item model for gui, ground, fixed and on_shelf; 1.21.1
 * hard-codes the first three in ItemRenderer.render (local {@code flag}, slot 9). This adds on_shelf to it.
 */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
    @ModifyVariable(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "STORE", ordinal = 0), index = 9)
    private boolean haven_backports$flatOnShelf(boolean flat, net.minecraft.world.item.ItemStack stack, ItemDisplayContext context) {
        return flat || context == OnShelf.context();
    }
}
