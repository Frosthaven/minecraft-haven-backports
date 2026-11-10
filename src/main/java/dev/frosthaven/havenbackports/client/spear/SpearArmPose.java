package dev.frosthaven.havenbackports.client.spear;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * 26.x {@code HumanoidModel.ArmPose.SPEAR}, added to 1.21.1's enum by META-INF/enumextensions.json
 * ({@code HAVEN_BACKPORTS_SPEAR}, one-handed, this transformer). The transformer is 26.x's poseRight/LeftArm SPEAR case
 * (SpearAnimations.thirdPersonHandUse) with the render-state fields read from the entity. 26.x's zombie and zombified
 * piglin render states (UndeadRenderState) take the main-hand item for either arm.
 */
public final class SpearArmPose {
    public static final IArmPoseTransformer TRANSFORMER = SpearArmPose::apply;
    /** Read by META-INF/enumextensions.json: ArmPose(twoHanded false, TRANSFORMER). */
    public static final EnumProxy<HumanoidModel.ArmPose> PARAMETERS = new EnumProxy<>(HumanoidModel.ArmPose.class, false, TRANSFORMER);
    private static HumanoidModel.ArmPose pose;

    private SpearArmPose() {}

    public static HumanoidModel.ArmPose pose() {
        if (pose == null) {
            pose = HumanoidModel.ArmPose.valueOf("HAVEN_BACKPORTS_SPEAR");
        }
        return pose;
    }

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ItemStack item = entity instanceof Zombie || entity instanceof ZombifiedPiglin ? entity.getMainHandItem() : SpearClient.heldByArm(entity, arm);
        float partial = SpearClient.partialTick(entity);
        SpearAnimations.thirdPersonHandUse(part, model.head, arm, item, new SpearAnimations.Pose(entity.isFallFlying(), model.swimAmount,
                entity.isUsingItem() ? entity.getTicksUsingItem() + partial : 0.0F, entity.isUsingItem(), SpearClient.arm(entity, entity.getUsedItemHand())));
    }
}
