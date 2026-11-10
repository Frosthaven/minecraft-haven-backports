package dev.frosthaven.havenbackports.cube.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.cube.SulfurCube;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 26.x SulfurCubeRenderer (with AbstractCubeMobRenderer's size and squish), on 1.21.1's MobRenderer: the outer
 * translucent cube, the inner layer (the absorbed block or the inner cube), the TNT-style swell before it explodes.
 * 26.x's custom head layer draws a swallowed skull; no skull is swallowable (#sulfur_cube_swallowable), so it is
 * left out.
 */
public class SulfurCubeRenderer extends MobRenderer<SulfurCube, SulfurCubeModel> {
    private static final ResourceLocation OUTER = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/sulfur_cube/sulfur_cube_outer.png");
    private static final ResourceLocation OUTER_SMALL =
            ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/sulfur_cube/sulfur_cube_outer_small.png");
    private final SulfurCubeModel normalModel;
    private final SulfurCubeModel smallModel;

    public SulfurCubeRenderer(EntityRendererProvider.Context context) {
        super(context, new SulfurCubeModel(context.bakeLayer(CubeClient.OUTER)), 0.25F);
        this.normalModel = this.model;
        this.smallModel = new SulfurCubeModel(context.bakeLayer(CubeClient.SMALL_OUTER));
        this.addLayer(new SulfurCubeInnerLayer(this, context.getModelSet(), context.getBlockRenderDispatcher()));
    }

    @Override
    public void render(SulfurCube entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light) {
        this.model = entity.isBaby() ? this.smallModel : this.normalModel;
        this.shadowRadius = 0.25F * entity.getSize();   // AbstractCubeMobRenderer.getShadowRadius
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    @Override
    protected void scale(SulfurCube entity, PoseStack poseStack, float partialTicks) {
        poseStack.scale(0.999F, 0.999F, 0.999F);   // downscaleSlightly
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float size = entity.getSize();
        float squish = Mth.lerp(partialTicks, entity.oSquish, entity.squish);
        float ss = entity.hasBodyItem() ? 0.0F : squish / (size * 0.5F + 1.0F);   // SulfurCubeRenderer.applySizeAndSquish
        float w = 1.0F / (ss + 1.0F);
        poseStack.scale(w * size, 1.0F / w * size, w * size);
        float fuse = fuseRemaining(entity, partialTicks);
        if (fuse < 10.0F && fuse > 0.0F) {
            float s = 1.0F + swellAmount(fuse);
            poseStack.scale(s, s, s);
        }
        float vOffset = entity.isBaby() ? 1.24F : 0.98F;
        float extra = entity.isBaby() ? 1.0F : 0.5F;
        float onePixelUpIfVisible = (entity.isInvisible() ? 0.0F : 1.0F) / 16.0F;
        poseStack.scale(extra, extra, extra);
        poseStack.translate(-0.0F, vOffset - onePixelUpIfVisible, -0.0F);
    }

    static float fuseRemaining(SulfurCube entity, float partialTicks) {
        return entity.isPrimed() ? entity.getFuse() - partialTicks + 1.0F : 0.0F;
    }

    /** 26.x TntRenderer.getSwellAmount. */
    static float swellAmount(float fuse) {
        float g = Math.clamp(1.0F - fuse / 10.0F, 0.0F, 1.0F);
        g *= g;
        g *= g;
        return g * 0.3F;
    }

    @Override
    public ResourceLocation getTextureLocation(SulfurCube entity) {
        return entity.isBaby() ? OUTER_SMALL : OUTER;
    }
}
