package dev.frosthaven.havenbackports.client.ghast;

import dev.frosthaven.havenbackports.ghast.HappyGhast;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** Mojang's 26.x HappyGhastHarnessModel: the harness and the goggles, down while ridden (y 14, xRot 0), else up (y 9, -0.7854). */
public class HappyGhastHarnessModel extends HierarchicalModel<HappyGhast> {
    private final ModelPart root;
    private final ModelPart goggles;
    private final MeshScale scale;

    public HappyGhastHarnessModel(ModelPart root, boolean baby) {
        this.root = root;
        this.goggles = root.getChild("goggles");
        this.scale = baby ? HappyGhastModel.BABY : HappyGhastModel.ADULT;
    }

    public static LayerDefinition createHarnessLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("harness", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        mesh.getRoot().addOrReplaceChild("goggles", CubeListBuilder.create().texOffs(0, 32).addBox(-8.0F, -2.5F, -2.5F, 16.0F, 5.0F, 5.0F,
                new CubeDeformation(0.15F)), PartPose.offset(0.0F, 14.0F, -5.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(HappyGhast ghast, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        scale.apply(root);
        if (ghast.isVehicle()) {
            goggles.xRot = 0.0F;
            goggles.y = 14.0F;
        } else {
            goggles.xRot = -0.7854F;
            goggles.y = 9.0F;
        }
    }
}
