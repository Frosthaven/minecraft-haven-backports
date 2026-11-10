package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.client.spear.SpearAnimations;
import dev.frosthaven.havenbackports.client.spear.SpearArmPose;
import dev.frosthaven.havenbackports.client.spear.SpearClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x HumanoidModel and HumanoidMobRenderer for spears:
 * <ul>
 * <li>HumanoidMobRenderer.getArmPose: a humanoid mob's arm is SPEAR when it holds a spear or stabs with it, else
 * EMPTY; bow and trident poses (1.21.1's skeleton and drowned prepareMobModel, run before this) win, as their 26.x
 * renderer overrides do. 1.21.1 renders mobs with a shared model, so this sets both arms every frame.</li>
 * <li>setupAttackAnimation, swing STAB: the shared body-twist prelude (26.x scales the arm offsets by ageScale), then
 * thirdPersonAttackHand. Other swings stay 1.21.1's.</li>
 * <li>setupSwimAnimation: an arm with the SPEAR pose keeps it while swimming (26.x zeroes that arm's swim amount;
 * 1.21.1 already does so for the swinging arm). The fifth and sixth swimAmount reads in setupAnim are 1.21.1's
 * right-arm and left-arm swim amounts.</li>
 * </ul>
 */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
    @Inject(method = "prepareMobModel(Lnet/minecraft/world/entity/LivingEntity;FFF)V", at = @At("TAIL"))
    private void haven_backports$mobSpearPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTick, CallbackInfo ci) {
        if (entity instanceof Mob) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            model.rightArmPose = haven_backports$pose(model.rightArmPose, entity, HumanoidArm.RIGHT);
            model.leftArmPose = haven_backports$pose(model.leftArmPose, entity, HumanoidArm.LEFT);
        }
    }

    private static HumanoidModel.ArmPose haven_backports$pose(HumanoidModel.ArmPose current, LivingEntity entity, HumanoidArm arm) {
        if (current != HumanoidModel.ArmPose.EMPTY && current != SpearArmPose.pose()) {
            return current;
        }
        return SpearClient.usesSpearPose(SpearClient.heldByArm(entity, arm), arm, entity) ? SpearArmPose.pose() : HumanoidModel.ArmPose.EMPTY;
    }

    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void haven_backports$stab(LivingEntity entity, float ageInTicks, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        float swingAnimation = model.attackTime;
        if (swingAnimation <= 0.0F || !entity.swinging || entity.swingingArm == null) {
            return;
        }
        HumanoidArm swingArm = SpearClient.arm(entity, entity.swingingArm);
        if (!SpearClient.isStab(entity, swingArm)) {
            return;
        }
        ci.cancel();
        model.body.yRot = Mth.sin(Mth.sqrt(swingAnimation) * (float) (Math.PI * 2)) * 0.2F;
        if (swingArm == HumanoidArm.LEFT) {
            model.body.yRot *= -1.0F;
        }
        float ageScale = entity.getAgeScale();
        model.rightArm.z = Mth.sin(model.body.yRot) * 5.0F * ageScale;
        model.rightArm.x = -Mth.cos(model.body.yRot) * 5.0F * ageScale;
        model.leftArm.z = -Mth.sin(model.body.yRot) * 5.0F * ageScale;
        model.leftArm.x = Mth.cos(model.body.yRot) * 5.0F * ageScale;
        model.rightArm.yRot = model.rightArm.yRot + model.body.yRot;
        model.leftArm.yRot = model.leftArm.yRot + model.body.yRot;
        model.leftArm.xRot = model.leftArm.xRot + model.body.yRot;
        SpearAnimations.thirdPersonAttackHand(model, swingAnimation, swingArm);
    }

    @ModifyExpressionValue(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/HumanoidModel;swimAmount:F", ordinal = 4))
    private float haven_backports$rightSwim(float swim) {
        return ((HumanoidModel<?>) (Object) this).rightArmPose == SpearArmPose.pose() ? 0.0F : swim;
    }

    @ModifyExpressionValue(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/HumanoidModel;swimAmount:F", ordinal = 5))
    private float haven_backports$leftSwim(float swim) {
        return ((HumanoidModel<?>) (Object) this).leftArmPose == SpearArmPose.pose() ? 0.0F : swim;
    }
}
