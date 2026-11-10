package dev.frosthaven.havenbackports.client.decoration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.decoration.Cushion;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Mojang's 26.x CushionRenderer and CushionModel (one 16 x 4 x 16 box, entity/cushion/<color>_cushion, cutout no cull). */
public class CushionRenderer extends EntityRenderer<Cushion> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "cushion"), "main");
    private final ModelPart model;

    public CushionRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = context.bakeLayer(LAYER);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cushion", CubeListBuilder.create().texOffs(0, 0).addBox(-31.0F, -4.0F, -1.0F, 16.0F, 4.0F, 16.0F,
                new CubeDeformation(-0.005F)), PartPose.offset(23.0F, 4.0F, -7.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(Cushion cushion, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - Direction.fromYRot(cushion.getYRot()).toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(180.0F));
        pose.translate(0.0, -0.25, 0.0);
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(cushion))), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(cushion, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Cushion cushion) {
        return ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/cushion/" + cushion.getColor().getName() + "_cushion.png");
    }
}
