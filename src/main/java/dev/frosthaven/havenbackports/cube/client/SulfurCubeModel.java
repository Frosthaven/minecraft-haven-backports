package dev.frosthaven.havenbackports.cube.client;

import dev.frosthaven.havenbackports.cube.SulfurCube;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * 26.x SulfurCubeModel and SmallSulfurCubeModel (one class: only the layer definitions differ), translucent entity
 * render type, on 1.21.1's HierarchicalModel. The cube does not animate (26.x: no setupAnim).
 */
public class SulfurCubeModel extends HierarchicalModel<SulfurCube> {
    private final ModelPart root;

    public SulfurCubeModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
    }

    public static LayerDefinition createOuterBodyLayer() {
        return layer(0, 0, -9.0F, 18.0F, 128);
    }

    public static LayerDefinition createInnerBodyLayer() {
        return layer(0, 36, -8.0F, 16.0F, 128);
    }

    public static LayerDefinition createSmallOuterBodyLayer() {
        return layer(0, 0, -5.0F, 10.0F, 64);
    }

    public static LayerDefinition createSmallInnerBodyLayer() {
        return layer(0, 20, -4.0F, 8.0F, 64);
    }

    private static LayerDefinition layer(int u, int v, float from, float size, int textureSize) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().texOffs(u, v).addBox(from, from, from, size, size, size), PartPose.ZERO);
        return LayerDefinition.create(mesh, textureSize, textureSize);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(SulfurCube entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }
}
