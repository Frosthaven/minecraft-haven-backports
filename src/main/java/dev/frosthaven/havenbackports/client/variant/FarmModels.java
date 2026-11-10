package dev.frosthaven.havenbackports.client.variant;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Mojang's 26.x adult farm-animal models the warm and cold variants use: PigModel (64 x 64, mirrored left legs) and
 * ColdPigModel (a fur layer), CowModel's 26.x base (64 x 64, with the muzzle) with WarmCowModel's horns and
 * ColdCowModel's fur and horns, and ColdChickenModel (a fur tuft and a head of fur) in 1.21.1 ChickenModel's layout
 * (the beak and wattle are root parts there, posed with the head). The temperate ones stay 1.21.1's models.
 */
final class FarmModels {
    private FarmModels() {}

    /** 26.x QuadrupedModel.createBodyMesh(legSize, mirrorLeftLeg, mirrorRightLeg, g). */
    private static MeshDefinition quadruped(int legSize, boolean mirrorLeft, boolean mirrorRight, CubeDeformation g) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F, g), PartPose.offset(0.0F, 18 - legSize, -6.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(28, 8).addBox(-5.0F, -10.0F, -7.0F, 10.0F, 16.0F, 8.0F, g),
                PartPose.offsetAndRotation(0.0F, 17 - legSize, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F));
        CubeListBuilder right = CubeListBuilder.create().mirror(mirrorRight).texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, legSize, 4.0F, g);
        CubeListBuilder left = CubeListBuilder.create().mirror(mirrorLeft).texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, legSize, 4.0F, g);
        root.addOrReplaceChild("right_hind_leg", right, PartPose.offset(-3.0F, 24 - legSize, 7.0F));
        root.addOrReplaceChild("left_hind_leg", left, PartPose.offset(3.0F, 24 - legSize, 7.0F));
        root.addOrReplaceChild("right_front_leg", right, PartPose.offset(-3.0F, 24 - legSize, -5.0F));
        root.addOrReplaceChild("left_front_leg", left, PartPose.offset(3.0F, 24 - legSize, -5.0F));
        return mesh;
    }

    private static MeshDefinition basePig(CubeDeformation g) {
        MeshDefinition mesh = quadruped(6, true, false, g);
        mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F, g)
                .texOffs(16, 16).addBox(-2.0F, 0.0F, -9.0F, 4.0F, 3.0F, 1.0F, g), PartPose.offset(0.0F, 12.0F, -6.0F));
        return mesh;
    }

    static LayerDefinition pig() {
        return LayerDefinition.create(basePig(CubeDeformation.NONE), 64, 64);
    }

    static LayerDefinition coldPig() {
        MeshDefinition mesh = basePig(CubeDeformation.NONE);
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create().texOffs(28, 8).addBox(-5.0F, -10.0F, -7.0F, 10.0F, 16.0F, 8.0F)
                .texOffs(28, 32).addBox(-5.0F, -10.0F, -7.0F, 10.0F, 16.0F, 8.0F, new CubeDeformation(0.5F)),
                PartPose.offsetAndRotation(0.0F, 11.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static MeshDefinition baseCow() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -6.0F, 8.0F, 8.0F, 6.0F)
                .texOffs(1, 33).addBox(-3.0F, 1.0F, -7.0F, 6.0F, 3.0F, 1.0F)
                .texOffs(22, 0).addBox("right_horn", -5.0F, -5.0F, -5.0F, 1.0F, 3.0F, 1.0F)
                .texOffs(22, 0).addBox("left_horn", 4.0F, -5.0F, -5.0F, 1.0F, 3.0F, 1.0F), PartPose.offset(0.0F, 4.0F, -8.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(18, 4).addBox(-6.0F, -10.0F, -7.0F, 12.0F, 18.0F, 10.0F)
                .texOffs(52, 0).addBox(-2.0F, 2.0F, -8.0F, 4.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F));
        CubeListBuilder left = CubeListBuilder.create().mirror().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F);
        CubeListBuilder right = CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F);
        root.addOrReplaceChild("right_hind_leg", right, PartPose.offset(-4.0F, 12.0F, 7.0F));
        root.addOrReplaceChild("left_hind_leg", left, PartPose.offset(4.0F, 12.0F, 7.0F));
        root.addOrReplaceChild("right_front_leg", right, PartPose.offset(-4.0F, 12.0F, -5.0F));
        root.addOrReplaceChild("left_front_leg", left, PartPose.offset(4.0F, 12.0F, -5.0F));
        return mesh;
    }

    static LayerDefinition warmCow() {
        MeshDefinition mesh = baseCow();
        mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -6.0F, 8.0F, 8.0F, 6.0F)
                .texOffs(1, 33).addBox(-3.0F, 1.0F, -7.0F, 6.0F, 3.0F, 1.0F)
                .texOffs(27, 0).addBox(-8.0F, -3.0F, -5.0F, 4.0F, 2.0F, 2.0F)
                .texOffs(39, 0).addBox(-8.0F, -5.0F, -5.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(27, 0).mirror().addBox(4.0F, -3.0F, -5.0F, 4.0F, 2.0F, 2.0F).mirror(false)
                .texOffs(39, 0).mirror().addBox(6.0F, -5.0F, -5.0F, 2.0F, 2.0F, 2.0F).mirror(false), PartPose.offset(0.0F, 4.0F, -8.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    static LayerDefinition coldCow() {
        MeshDefinition mesh = baseCow();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create().texOffs(20, 32).addBox(-6.0F, -10.0F, -7.0F, 12.0F, 18.0F, 10.0F, new CubeDeformation(0.5F))
                .texOffs(18, 4).addBox(-6.0F, -10.0F, -7.0F, 12.0F, 18.0F, 10.0F).texOffs(52, 0).addBox(-2.0F, 2.0F, -8.0F, 4.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 5.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F));
        PartDefinition head = mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -6.0F, 8.0F, 8.0F, 6.0F)
                .texOffs(9, 33).addBox(-3.0F, 1.0F, -7.0F, 6.0F, 3.0F, 1.0F), PartPose.offset(0.0F, 4.0F, -8.0F));
        head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(0, 40).addBox(-1.5F, -4.5F, -0.5F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(-4.5F, -2.5F, -3.5F, 1.5708F, 0.0F, 0.0F));
        head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, -3.0F, -0.5F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(5.5F, -2.5F, -5.0F, 1.5708F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    static LayerDefinition coldChicken() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 3.0F)
                .texOffs(44, 0).addBox(-3.0F, -7.0F, -2.015F, 6.0F, 3.0F, 4.0F), PartPose.offset(0.0F, 15.0F, -4.0F));
        root.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(14, 0).addBox(-2.0F, -4.0F, -4.0F, 4.0F, 2.0F, 2.0F), PartPose.offset(0.0F, 15.0F, -4.0F));
        root.addOrReplaceChild("red_thing", CubeListBuilder.create().texOffs(14, 4).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 2.0F, 2.0F), PartPose.offset(0.0F, 15.0F, -4.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 9).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 8.0F, 6.0F)
                .texOffs(38, 9).addBox(0.0F, 3.0F, -1.0F, 0.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, (float) (Math.PI / 2), 0.0F, 0.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(26, 0).addBox(-1.0F, 0.0F, -3.0F, 3.0F, 5.0F, 3.0F);
        root.addOrReplaceChild("right_leg", leg, PartPose.offset(-2.0F, 19.0F, 1.0F));
        root.addOrReplaceChild("left_leg", leg, PartPose.offset(1.0F, 19.0F, 1.0F));
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(24, 13).addBox(0.0F, 0.0F, -3.0F, 1.0F, 4.0F, 6.0F), PartPose.offset(-4.0F, 13.0F, 0.0F));
        root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(24, 13).addBox(-1.0F, 0.0F, -3.0F, 1.0F, 4.0F, 6.0F), PartPose.offset(4.0F, 13.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }
}
