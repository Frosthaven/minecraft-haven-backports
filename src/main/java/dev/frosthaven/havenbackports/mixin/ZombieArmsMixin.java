package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.frosthaven.havenbackports.client.spear.SpearArmPose;
import net.minecraft.client.model.AbstractZombieModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PiglinModel;
import net.minecraft.client.model.ZombieVillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.x AnimationUtils.animateZombieArms skips the raised zombie arms when either arm has the SPEAR pose; 1.21.1's
 *  callers: zombies, zombie villagers, zombified piglins (PiglinModel). Illagers never get SPEAR (as in 26.x). */
@Mixin({AbstractZombieModel.class, ZombieVillagerModel.class, PiglinModel.class})
abstract class ZombieArmsMixin {
    @WrapWithCondition(method = "setupAnim", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/AnimationUtils;animateZombieArms(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;ZFF)V"))
    private boolean haven_backports$notWithSpear(ModelPart leftArm, ModelPart rightArm, boolean aggressive, float attackTime, float ageInTicks) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        return model.leftArmPose != SpearArmPose.pose() && model.rightArmPose != SpearArmPose.pose();
    }
}
