package dev.frosthaven.havenbackports.client.spear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.havenbackports.spear.KineticWeapon;
import dev.frosthaven.havenbackports.spear.SpearItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * 26.x {@code SpearAnimations}, verbatim. 26.x reads a render state; 1.21.1 renders from the entity, so the state fields
 * are parameters ({@link Pose} holds the ones thirdPersonHandUse reads). {@code rotateDegrees(axis, d)} is 1.21.1's
 * {@code mulPose(axis.rotationDegrees(d))}; "has KINETIC_WEAPON" is "is a {@link SpearItem}".
 */
public final class SpearAnimations {
    private SpearAnimations() {}

    /** The HumanoidRenderState fields thirdPersonHandUse reads. */
    public record Pose(boolean isFallFlying, float swimAmount, float ticksUsingItem, boolean isUsingItem, HumanoidArm useItemArm) {}

    private static float progress(float time, float start, float end) {
        return Math.clamp(Mth.inverseLerp(time, start, end), 0.0F, 1.0F);
    }

    private static KineticWeapon kinetic(ItemStack item) {
        return item.getItem() instanceof SpearItem spear ? spear.kinetic() : null;
    }

    public static void thirdPersonHandUse(ModelPart arm, ModelPart head, HumanoidArm holdingArm, ItemStack item, Pose state) {
        int invert = holdingArm == HumanoidArm.RIGHT ? 1 : -1;
        arm.yRot = -0.1F * invert + head.yRot;
        arm.xRot = (float) (-Math.PI / 2) + head.xRot + 0.8F;
        if (state.isFallFlying() || state.swimAmount() > 0.0F) {
            arm.xRot -= 0.9599311F;
        }
        arm.yRot = (float) (Math.PI / 180.0) * Math.clamp((180.0F / (float) Math.PI) * arm.yRot, -60.0F, 60.0F);
        arm.xRot = (float) (Math.PI / 180.0) * Math.clamp((180.0F / (float) Math.PI) * arm.xRot, -120.0F, 30.0F);
        if (!(state.ticksUsingItem() <= 0.0F) && (!state.isUsingItem() || state.useItemArm() == holdingArm)) {
            KineticWeapon kineticWeapon = kinetic(item);
            if (kineticWeapon != null) {
                UseParams params = UseParams.fromKineticWeapon(kineticWeapon, state.ticksUsingItem());
                arm.yRot = arm.yRot + -invert * params.swayScaleFast() * (float) (Math.PI / 180.0) * params.swayIntensity() * 1.0F;
                arm.zRot = arm.zRot + -invert * params.swayScaleSlow() * (float) (Math.PI / 180.0) * params.swayIntensity() * 0.5F;
                arm.xRot = arm.xRot + (float) (Math.PI / 180.0) * (-40.0F * params.raiseProgressStart() + 30.0F * params.raiseProgressMiddle()
                        + -20.0F * params.raiseProgressEnd() + 20.0F * params.lowerProgress() + 10.0F * params.raiseBackProgress()
                        + 0.6F * params.swayScaleSlow() * params.swayIntensity());
            }
        }
    }

    public static void thirdPersonUseItem(float swingAnimation, float ticksSinceKineticHitFeedback, PoseStack poseStack, float timeHeld,
            HumanoidArm arm, ItemStack actualItem) {
        KineticWeapon kineticWeapon = kinetic(actualItem);
        if (kineticWeapon != null && timeHeld != 0.0F) {
            float attack = Ease.inQuad(progress(swingAnimation, 0.05F, 0.2F));
            float retract = Ease.inOutExpo(progress(swingAnimation, 0.4F, 1.0F));
            UseParams params = UseParams.fromKineticWeapon(kineticWeapon, timeHeld);
            int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
            float raiseProgressModified = 1.0F - Ease.outBack(1.0F - params.raiseProgress());
            float hitFeedback = hitFeedbackAmount(ticksSinceKineticHitFeedback);
            poseStack.translate(0.0, -hitFeedback * 0.4, (double) (-kineticWeapon.forwardMovement() * (raiseProgressModified - params.raiseBackProgress()) + hitFeedback));
            poseStack.rotateAround(Axis.XN.rotationDegrees(70.0F * (params.raiseProgress() - params.raiseBackProgress()) - 40.0F * (attack - retract)),
                    0.0F, -0.03125F, 0.125F);
            poseStack.rotateAround(Axis.YP.rotationDegrees(invert * 90 * (params.raiseProgress() - params.swayProgress() + 3.0F * retract + attack)),
                    0.0F, 0.0F, 0.125F);
        }
    }

    public static void thirdPersonAttackHand(HumanoidModel<?> model, float animation, HumanoidArm arm) {
        model.rightArm.yRot = model.rightArm.yRot - model.body.yRot;
        model.leftArm.yRot = model.leftArm.yRot - model.body.yRot;
        model.leftArm.xRot = model.leftArm.xRot - model.body.yRot;
        float prepare = Ease.inOutSine(progress(animation, 0.0F, 0.05F));
        float attack = Ease.inQuad(progress(animation, 0.05F, 0.2F));
        float retract = Ease.inOutExpo(progress(animation, 0.4F, 1.0F));
        (arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm).xRot += (90.0F * prepare - 120.0F * attack + 30.0F * retract) * (float) (Math.PI / 180.0);
    }

    /** {@code mainHandItem}: 26.x state.getMainHandItemStack(). */
    public static void thirdPersonAttackItem(float animation, ItemStack mainHandItem, PoseStack poseStack) {
        if (!(animation <= 0.0F)) {
            KineticWeapon kineticWeapon = kinetic(mainHandItem);
            float jetForward = kineticWeapon != null ? kineticWeapon.forwardMovement() : 0.0F;
            float attack = Ease.inQuad(progress(animation, 0.05F, 0.2F));
            float retract = Ease.inOutExpo(progress(animation, 0.4F, 1.0F));
            poseStack.rotateAround(Axis.XN.rotationDegrees(70.0F * (attack - retract)), 0.0F, -0.125F, 0.125F);
            poseStack.translate(0.0F, jetForward * (attack - retract), 0.0F);
        }
    }

    private static float hitFeedbackAmount(float ticksSinceFeedbackStart) {
        return 0.4F * (Ease.outQuart(progress(ticksSinceFeedbackStart, 1.0F, 3.0F)) - Ease.inOutSine(progress(ticksSinceFeedbackStart, 3.0F, 10.0F)));
    }

    public static void firstPersonUse(float ticksSinceKineticHitFeedback, PoseStack poseStack, float timeHeld, HumanoidArm arm, ItemStack itemStack) {
        KineticWeapon kineticWeapon = kinetic(itemStack);
        if (kineticWeapon != null) {
            UseParams params = UseParams.fromKineticWeapon(kineticWeapon, timeHeld);
            int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate(
                    (double) (invert * (params.raiseProgress() * 0.15F + params.raiseProgressEnd() * -0.05F + params.swayProgress() * -0.1F + params.swayScaleSlow() * 0.005F)),
                    (double) (params.raiseProgress() * -0.075F + params.raiseProgressMiddle() * 0.075F + params.swayScaleFast() * 0.01F),
                    params.raiseProgressStart() * 0.05 + params.raiseProgressEnd() * -0.05 + params.swayScaleSlow() * 0.005F);
            poseStack.rotateAround(Axis.XP.rotationDegrees(-65.0F * Ease.inOutBack(params.raiseProgress()) - 35.0F * params.lowerProgress()
                    + 100.0F * params.raiseBackProgress() + -0.5F * params.swayScaleFast()), 0.0F, 0.1F, 0.0F);
            poseStack.rotateAround(Axis.YN.rotationDegrees(invert * (-90.0F * progress(params.raiseProgress(), 0.5F, 0.55F)
                    + 90.0F * params.swayProgress() + 2.0F * params.swayScaleSlow())), invert * 0.15F, 0.0F, 0.0F);
            poseStack.translate(0.0F, -hitFeedbackAmount(ticksSinceKineticHitFeedback), 0.0F);
        }
    }

    public static void firstPersonAttack(float attack, PoseStack poseStack, int invert, HumanoidArm arm) {
        float startingAmount = Ease.inOutSine(progress(attack, 0.0F, 0.05F));
        float middleAmount = Ease.outBack(progress(attack, 0.05F, 0.2F));
        float endingAmount = Ease.inOutExpo(progress(attack, 0.4F, 1.0F));
        poseStack.translate(invert * 0.1F * (startingAmount - middleAmount), -0.075F * (startingAmount - endingAmount), 0.65F * (startingAmount - middleAmount));
        poseStack.mulPose(Axis.XP.rotationDegrees(-70.0F * (startingAmount - endingAmount)));
        poseStack.translate(0.0, 0.0, -0.25 * (endingAmount - middleAmount));
    }

    private record UseParams(float raiseProgress, float raiseProgressStart, float raiseProgressMiddle, float raiseProgressEnd, float swayProgress,
            float lowerProgress, float raiseBackProgress, float swayIntensity, float swayScaleSlow, float swayScaleFast) {
        static UseParams fromKineticWeapon(KineticWeapon kineticWeapon, float time) {
            int finishRaisingTick = kineticWeapon.delayTicks();
            int finishSwayingTick = kineticWeapon.dismountConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0) + finishRaisingTick;
            int startSwayingTick = finishSwayingTick - 20;
            int finishLoweringTick = kineticWeapon.knockbackConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0) + finishRaisingTick;
            int startLoweringTick = finishLoweringTick - 40;
            int finishRaisingBackTick = kineticWeapon.damageConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0) + finishRaisingTick;
            float raiseProgress = progress(time, 0.0F, (float) finishRaisingTick);
            float raiseProgressStart = progress(raiseProgress, 0.0F, 0.5F);
            float raiseProgressMiddle = progress(raiseProgress, 0.5F, 0.8F);
            float raiseProgressEnd = progress(raiseProgress, 0.8F, 1.0F);
            float swayProgress = progress(time, (float) startSwayingTick, (float) finishSwayingTick);
            float lowerProgress = Ease.outCubic(Ease.inOutElastic(progress(time - 20.0F, (float) startLoweringTick, (float) finishLoweringTick)));
            float raiseBackProgress = progress(time, (float) (finishRaisingBackTick - 5), (float) finishRaisingBackTick);
            float swayIntensity = 2.0F * Ease.outCirc(swayProgress) - 2.0F * Ease.inCirc(raiseBackProgress);
            float swayScaleSlow = Mth.sin(time * 19.0F * (float) (Math.PI / 180.0)) * swayIntensity;
            float swayScaleFast = Mth.sin(time * 30.0F * (float) (Math.PI / 180.0)) * swayIntensity;
            return new UseParams(raiseProgress, raiseProgressStart, raiseProgressMiddle, raiseProgressEnd, swayProgress, lowerProgress,
                    raiseBackProgress, swayIntensity, swayScaleSlow, swayScaleFast);
        }
    }
}
