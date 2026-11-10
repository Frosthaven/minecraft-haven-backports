package dev.frosthaven.havenbackports.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.client.spear.SpearAnimations;
import dev.frosthaven.havenbackports.client.spear.SpearArmPose;
import dev.frosthaven.havenbackports.client.spear.SpearClient;
import dev.frosthaven.havenbackports.spear.Stab;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x ItemInHandLayer.submitArmWithItem: a STAB swing on this arm moves the held item (thirdPersonAttackItem), and
 * while this arm uses an item its arm pose animates it (only SPEAR does: thirdPersonUseItem). Players' layer calls this.
 */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void haven_backports$spear(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack,
            MultiBufferSource buffer, int light, CallbackInfo ci) {
        float partial = SpearClient.partialTick(entity);
        float swing = entity.getAttackAnim(partial);
        if (SpearClient.isStab(entity, arm)) {
            SpearAnimations.thirdPersonAttackItem(swing, entity.getMainHandItem(), poseStack);
        }
        float ticksUsingItem = entity.isUsingItem() && SpearClient.arm(entity, entity.getUsedItemHand()) == arm ? entity.getTicksUsingItem() + partial : 0.0F;
        if (ticksUsingItem != 0.0F && ((ItemInHandLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model
                && (arm == HumanoidArm.RIGHT ? model.rightArmPose : model.leftArmPose) == SpearArmPose.pose()) {
            SpearAnimations.thirdPersonUseItem(swing, Stab.ticksSinceLastKineticHitFeedback(entity, partial), poseStack, ticksUsingItem, arm, stack);
        }
    }
}
