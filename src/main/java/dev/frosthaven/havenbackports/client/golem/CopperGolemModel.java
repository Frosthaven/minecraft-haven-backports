package dev.frosthaven.havenbackports.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.golem.CopperGolem;
import dev.frosthaven.havenbackports.golem.CopperGolemState;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.entity.animation.json.AnimationHolder;
import net.neoforged.neoforge.client.entity.animation.json.AnimationLoader;

/**
 * Mojang's 26.x CopperGolemModel. The keyframe animations are Mojang's CopperGolemAnimation, converted by the generator
 * to NeoForge JSON (neoforge/animations/entity/copper_golem/*); 26.x's KeyframeAnimation.applyWalk and apply are 1.21.1's
 * animateWalk and animate.
 */
public class CopperGolemModel extends HierarchicalModel<CopperGolem> implements ArmedModel {
    private static final AnimationHolder WALK = anim("walk"), WALK_ITEM = anim("walk_item"), IDLE = anim("idle"),
            GET_ITEM = anim("chest_interaction_noitem_get"), GET_NO_ITEM = anim("chest_interaction_noitem_noget"),
            DROP_ITEM = anim("chest_interaction_item_drop"), DROP_NO_ITEM = anim("chest_interaction_item_nodrop");
    private final ModelPart root, body, head, rightArm, leftArm;
    private CopperGolemState state = CopperGolemState.IDLE;

    public CopperGolemModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
    }

    private static AnimationHolder anim(String name) {
        return AnimationLoader.INSTANCE.getAnimationHolder(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "copper_golem/" + name));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(CopperGolem golem, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        root.y = CopperGolemLayers.ROOT_Y;
        this.state = golem.getState();
        head.xRot = headPitch * (float) (Math.PI / 180.0);
        head.yRot = netHeadYaw * (float) (Math.PI / 180.0);
        if (golem.getMainHandItem().isEmpty() && golem.getOffhandItem().isEmpty()) {
            this.animateWalk(WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        } else {
            this.animateWalk(WALK_ITEM, limbSwing, limbSwingAmount, 2.0F, 2.5F);
            poseHeldItemArmsIfStill();
        }
        this.animate(golem.idleAnimationState, IDLE, ageInTicks);
        this.animate(golem.interactionGetItemAnimationState, GET_ITEM, ageInTicks);
        this.animate(golem.interactionGetNoItemAnimationState, GET_NO_ITEM, ageInTicks);
        this.animate(golem.interactionDropItemAnimationState, DROP_ITEM, ageInTicks);
        this.animate(golem.interactionDropNoItemAnimationState, DROP_NO_ITEM, ageInTicks);
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack pose) {
        root.translateAndRotate(pose);
        body.translateAndRotate(pose);
        (arm == HumanoidArm.RIGHT ? rightArm : leftArm).translateAndRotate(pose);
        if (state == CopperGolemState.IDLE) {
            pose.mulPose(Axis.YP.rotationDegrees(arm == HumanoidArm.RIGHT ? -90.0F : 90.0F));
            pose.translate(0.0F, 0.0F, 0.125F);
        } else {
            pose.scale(0.55F, 0.55F, 0.55F);
            pose.translate(-0.125F, 0.3125F, -0.1875F);
        }
    }

    /** 26.x translateToHead (after root): body, head, up 0.125, scaled 1.0625. */
    public void translateToHead(PoseStack pose) {
        root.translateAndRotate(pose);
        body.translateAndRotate(pose);
        head.translateAndRotate(pose);
        pose.translate(0.0F, 0.125F, 0.0F);
        pose.scale(1.0625F, 1.0625F, 1.0625F);
    }

    /** 26.x applyBlockOnAntennaTransform. */
    public void applyBlockOnAntennaTransform(PoseStack pose) {
        root.translateAndRotate(pose);
        body.translateAndRotate(pose);
        head.translateAndRotate(pose);
        pose.translate(0.0, -1.75, 0.0);
    }

    private void poseHeldItemArmsIfStill() {
        rightArm.xRot = Math.min(rightArm.xRot, -0.87266463F);
        leftArm.xRot = Math.min(leftArm.xRot, -0.87266463F);
        rightArm.yRot = Math.min(rightArm.yRot, -0.1134464F);
        leftArm.yRot = Math.max(leftArm.yRot, 0.1134464F);
        rightArm.zRot = Math.min(rightArm.zRot, -0.064577185F);
        leftArm.zRot = Math.max(leftArm.zRot, 0.064577185F);
    }
}
