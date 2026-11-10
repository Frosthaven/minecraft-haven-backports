package dev.frosthaven.havenbackports.client.golem;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Mojang's 26.x CopperGolemModel layers, part for part: the golem (also the standing statue) and the running, sitting
 * and star statue poses. 26.x applies MeshTransformer.translating(0, 24, 0) to the golem's mesh root; 1.21.1 meshes
 * have no root transform, so CopperGolemModel sets the root's y to 24 (the statues set it to 0, as 26.x does).
 */
public final class CopperGolemLayers {
    static final float ROOT_Y = 24.0F;

    private CopperGolemLayers() {}

    public static void register(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(CopperGolemRenderer.LAYER, CopperGolemLayers::createBodyLayer);
        e.registerLayerDefinition(CopperGolemRenderer.RUNNING, CopperGolemLayers::createRunningPoseBodyLayer);
        e.registerLayerDefinition(CopperGolemRenderer.SITTING, CopperGolemLayers::createSittingPoseBodyLayer);
        e.registerLayerDefinition(CopperGolemRenderer.STAR, CopperGolemLayers::createStarPoseBodyLayer);
    }

    static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();
        PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 15).addBox(-4.0F, -6.0F, -3.0F, 8.0F, 6.0F, 6.0F, CubeDeformation.NONE),
            PartPose.offset(0.0F, -5.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -5.0F, -5.0F, 8.0F, 5.0F, 10.0F, new CubeDeformation(0.015F))
                .texOffs(56, 0)
                .addBox(-1.0F, -2.0F, -6.0F, 2.0F, 3.0F, 2.0F, CubeDeformation.NONE)
                .texOffs(37, 8)
                .addBox(-1.0F, -9.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.015F))
                .texOffs(37, 0)
                .addBox(-2.0F, -13.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(-0.015F)),
            PartPose.offset(0.0F, -6.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(36, 16).addBox(-3.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F, CubeDeformation.NONE),
            PartPose.offset(-4.0F, -6.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(50, 16).addBox(0.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F, CubeDeformation.NONE),
            PartPose.offset(4.0F, -6.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 27).addBox(-4.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F, CubeDeformation.NONE),
            PartPose.offset(0.0F, -5.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(16, 27).addBox(0.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F, CubeDeformation.NONE),
            PartPose.offset(0.0F, -5.0F, 0.0F)
        );
        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    static LayerDefinition createRunningPoseBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(-1.064F, -5.0F, 0.0F));
        body.addOrReplaceChild(
            "body_r1",
            CubeListBuilder.create().texOffs(0, 15).addBox(-4.02F, -6.116F, -3.5F, 8.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.1F, 0.1F, 0.7F, 0.1204F, -0.0064F, -0.0779F)
        );
        body.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -5.1F, -5.0F, 8.0F, 5.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(56, 0)
                .addBox(-1.02F, -2.1F, -6.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(37, 8)
                .addBox(-1.02F, -9.1F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.015F))
                .texOffs(37, 0)
                .addBox(-2.0F, -13.1F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(-0.015F)),
            PartPose.offset(0.7F, -5.6F, -1.8F)
        );
        PartDefinition right_arm = body.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-4.0F, -6.0F, 0.0F));
        right_arm.addOrReplaceChild(
            "right_arm_r1",
            CubeListBuilder.create().texOffs(36, 16).addBox(-3.052F, -1.11F, -2.036F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.7F, -0.248F, -1.62F, 1.0036F, 0.0F, 0.0F)
        );
        PartDefinition left_arm = body.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(4.0F, -6.0F, 0.0F));
        left_arm.addOrReplaceChild(
            "left_arm_r1",
            CubeListBuilder.create().texOffs(50, 16).addBox(0.032F, -1.1F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.732F, 0.0F, 0.0F, -0.8715F, -0.0535F, -0.0449F)
        );
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-3.064F, -5.0F, 0.0F));
        right_leg.addOrReplaceChild(
            "right_leg_r1",
            CubeListBuilder.create().texOffs(0, 27).addBox(-1.856F, -0.1F, -1.09F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.048F, 0.0F, -0.9F, -0.8727F, 0.0F, 0.0F)
        );
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(0.936F, -5.0F, 0.0F));
        left_leg.addOrReplaceChild(
            "left_leg_r1",
            CubeListBuilder.create().texOffs(16, 27).addBox(-2.088F, -0.1F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    static LayerDefinition createSittingPoseBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();
        PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(3, 19)
                .addBox(-3.0F, -4.0F, -4.525F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 15)
                .addBox(-4.0F, -3.0F, -3.525F, 8.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, -3.0F, 2.325F)
        );
        body.addOrReplaceChild(
            "body_r1",
            CubeListBuilder.create().texOffs(3, 18).addBox(-4.0F, -3.0F, -2.2F, 8.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.0F, -1.0F, -4.325F, 0.0F, 0.0F, -3.1416F)
        );
        PartDefinition head = body.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(37, 8)
                .addBox(-1.0F, -7.0F, -3.3F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.015F))
                .texOffs(37, 0)
                .addBox(-2.0F, -11.0F, -4.3F, 4.0F, 4.0F, 4.0F, new CubeDeformation(-0.015F))
                .texOffs(0, 0)
                .addBox(-4.0F, -3.0F, -7.325F, 8.0F, 5.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(56, 0)
                .addBox(-1.0F, 0.0F, -8.325F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, -6.0F, -0.2F)
        );
        PartDefinition right_arm = body.addOrReplaceChild(
            "right_arm", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.0F, -5.6F, -1.8F, 0.4363F, 0.0F, 0.0F)
        );
        right_arm.addOrReplaceChild(
            "right_arm_r1",
            CubeListBuilder.create().texOffs(36, 16).addBox(-3.075F, -0.9733F, -1.9966F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.0F, 0.0893F, 0.1198F, -1.0472F, 0.0F, 0.0F)
        );
        PartDefinition left_arm = body.addOrReplaceChild(
            "left_arm", CubeListBuilder.create(), PartPose.offsetAndRotation(4.0F, -5.6F, -1.7F, 0.4363F, 0.0F, 0.0F)
        );
        left_arm.addOrReplaceChild(
            "left_arm_r1",
            CubeListBuilder.create().texOffs(50, 16).addBox(0.075F, -1.0443F, -1.8997F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.0F, -0.0015F, -0.0808F, -1.0472F, 0.0F, 0.0F)
        );
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-2.1F, -2.1F, -2.075F));
        right_leg.addOrReplaceChild(
            "right_leg_r1",
            CubeListBuilder.create().texOffs(0, 27).addBox(-2.0F, 0.975F, 0.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.05F, -1.9F, 1.075F, -1.5708F, 0.0F, 0.0F)
        );
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(2.0F, -2.0F, -2.075F));
        left_leg.addOrReplaceChild(
            "left_leg_r1",
            CubeListBuilder.create().texOffs(16, 27).addBox(-2.0F, 0.975F, 0.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.05F, -2.0F, 1.075F, -1.5708F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    static LayerDefinition createStarPoseBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();
        PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 15).addBox(-4.0F, -6.0F, -3.0F, 8.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, -5.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -5.0F, -5.0F, 8.0F, 5.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(56, 0)
                .addBox(-1.0F, -2.0F, -6.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(37, 8)
                .addBox(-1.0F, -9.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.015F))
                .texOffs(37, 0)
                .addBox(-2.0F, -13.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(-0.015F)),
            PartPose.offset(0.0F, -6.0F, 0.0F)
        );
        PartDefinition right_arm = body.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-4.0F, -6.0F, 0.0F));
        right_arm.addOrReplaceChild(
            "right_arm_r1",
            CubeListBuilder.create().texOffs(36, 16).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.9199F)
        );
        right_arm.addOrReplaceChild("rightItem", CubeListBuilder.create(), PartPose.offset(-1.0F, 7.4F, -1.0F));
        PartDefinition left_arm = body.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(4.0F, -6.0F, 0.0F));
        left_arm.addOrReplaceChild(
            "left_arm_r1",
            CubeListBuilder.create().texOffs(50, 16).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-1.0F, 1.0F, 0.0F, 0.0F, 0.0F, -1.9199F)
        );
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-3.0F, -5.0F, 0.0F));
        right_leg.addOrReplaceChild(
            "right_leg_r1",
            CubeListBuilder.create().texOffs(0, 27).addBox(-2.0F, -2.5F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.35F, 2.0F, 0.01F, 0.0F, 0.0F, 0.2618F)
        );
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.0F, -5.0F, 0.0F));
        left_leg.addOrReplaceChild(
            "left_leg_r1",
            CubeListBuilder.create().texOffs(16, 27).addBox(-2.0F, -2.5F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.65F, 2.0F, 0.0F, 0.0F, 0.0F, -0.2618F)
        );
        return LayerDefinition.create(meshDefinition, 64, 64);
    }
}
