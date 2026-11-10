package dev.frosthaven.havenbackports.client.ghast;

import dev.frosthaven.havenbackports.ghast.HappyGhast;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Mojang's 26.x HappyGhastModel: a 16-cube body (an inner body for the ghastling), nine tentacles, 64x64, the mesh
 * scaled by 4 (and 0.2375 for the ghastling, BABY_TRANSFORMER); the body squeezes to 0.9375 under a harness.
 * Tentacles swing as GhastModel.animateTentacles. The ropes layer is this model with deformation 0.2.
 */
public class HappyGhastModel extends HierarchicalModel<HappyGhast> {
    public static final MeshScale ADULT = MeshScale.IDENTITY.scaling(4.0F);
    public static final MeshScale BABY = ADULT.scaling(HappyGhast.BABY_SCALE);
    private static final float BODY_SQUEEZE = 0.9375F;
    private static final float[][] TENTACLES = {   // x, z, length
            {-3.75F, -5.0F, 5}, {1.25F, -5.0F, 7}, {6.25F, -5.0F, 4}, {-6.25F, 0.0F, 5}, {-1.25F, 0.0F, 5},
            {3.75F, 0.0F, 7}, {-3.75F, 5.0F, 8}, {1.25F, 5.0F, 8}, {6.25F, 5.0F, 5}};
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart[] tentacles = new ModelPart[9];
    private final MeshScale scale;

    public HappyGhastModel(ModelPart root, boolean baby) {
        this.root = root;
        this.body = root.getChild("body");
        for (int i = 0; i < tentacles.length; i++) {
            tentacles[i] = body.getChild("tentacle" + i);
        }
        this.scale = baby ? BABY : ADULT;
    }

    public static LayerDefinition createBodyLayer(boolean baby, CubeDeformation deformation) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F, deformation), PartPose.offset(0.0F, 16.0F, 0.0F));
        if (baby) {
            body.addOrReplaceChild("inner_body", CubeListBuilder.create().texOffs(0, 32).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F,
                    deformation.extend(-0.5F)), PartPose.offset(0.0F, 8.0F, 0.0F));
        }
        for (int i = 0; i < TENTACLES.length; i++) {
            body.addOrReplaceChild("tentacle" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, TENTACLES[i][2], 2.0F, deformation),
                    PartPose.offset(TENTACLES[i][0], 7.0F, TENTACLES[i][1]));
        }
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
        if (!ghast.getBodyArmorItem().isEmpty()) {
            body.xScale = BODY_SQUEEZE;
            body.yScale = BODY_SQUEEZE;
            body.zScale = BODY_SQUEEZE;
        }
        for (int i = 0; i < tentacles.length; i++) {
            tentacles[i].xRot = 0.2F * Mth.sin(ageInTicks * 0.3F + i) + 0.4F;
        }
    }
}
