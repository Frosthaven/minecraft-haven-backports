package dev.frosthaven.havenbackports.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.client.BundleRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x draws a bundle with a selected item open in the GUI, the item inside (its item definition's select on the
 * display context); 1.21.1 picks one baked model per item, so this hands that case to BundleRenderer.
 */
@Mixin(ItemRenderer.class)
abstract class ItemRendererBundleMixin {
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"), cancellable = true)
    private void haven_backports$openBundle(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers,
                                           int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (BundleRenderer.render((ItemRenderer) (Object) this, stack, context, leftHand, pose, buffers, light, overlay)) {
            ci.cancel();
        }
    }
}
