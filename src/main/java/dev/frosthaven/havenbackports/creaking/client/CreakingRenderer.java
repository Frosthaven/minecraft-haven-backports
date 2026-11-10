package dev.frosthaven.havenbackports.creaking.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.creaking.Creaking;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * 26.x CreakingRenderer. A creaking that tears down renders with death time 0 and no red overlay (26.x sets that in
 * its render state; 1.21.1 reads the entity, so render() zeroes deathTime and hurtTime for the frame and puts them
 * back). Its eyes glow while it is active, and flicker while it tears down.
 */
public class CreakingRenderer extends MobRenderer<Creaking, CreakingModel> {
    static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "creaking"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/creaking/creaking.png");
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/creaking/creaking_eyes.png");

    public CreakingRenderer(EntityRendererProvider.Context context) {
        super(context, new CreakingModel(context.bakeLayer(LAYER)), 0.6F);
        this.addLayer(new EyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Creaking entity) {
        return TEXTURE;
    }

    @Override
    public void render(Creaking entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!entity.isTearingDown()) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }
        int deathTime = entity.deathTime;
        int hurtTime = entity.hurtTime;
        entity.deathTime = 0;
        entity.hurtTime = 0;
        try {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        } finally {
            entity.deathTime = deathTime;
            entity.hurtTime = hurtTime;
        }
    }

    /** 26.x LivingEntityEmissiveLayer(creaking_eyes, alpha eyesGlowing ? 1 : 0, eyes model, RenderTypes::eyes, always
     *  visible): the head only, drawn with the eyes render type. */
    static final class EyesLayer extends RenderLayer<Creaking, CreakingModel> {
        EyesLayer(RenderLayerParent<Creaking, CreakingModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Creaking entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            boolean glowing = entity.isTearingDown() ? entity.hasGlowingEyes() : entity.isActive();
            if (!glowing) {
                return;
            }
            CreakingModel model = this.getParentModel();
            model.root().getAllParts().forEach(p -> p.skipDraw = true);
            model.head.skipDraw = false;
            try {
                model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(EYES)), packedLight,
                        LivingEntityRenderer.getOverlayCoords(entity, 0.0F), FastColor.ARGB32.color(255, 255, 255, 255));
            } finally {
                model.root().getAllParts().forEach(p -> p.skipDraw = false);
            }
        }
    }
}
