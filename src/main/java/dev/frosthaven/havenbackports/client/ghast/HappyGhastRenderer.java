package dev.frosthaven.havenbackports.client.ghast;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.ghast.HappyGhast;
import dev.frosthaven.havenbackports.ghast.HarnessItem;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Mojang's 26.x HappyGhastRenderer (an AgeableMobRenderer, shadow 2): textures entity/ghast/happy_ghast and
 * happy_ghast_baby. 26.x draws the harness through SimpleEquipmentLayer with the harness's equipment asset
 * (equipment/<color>_harness.json -> entity/equipment/happy_ghast_body/<color>_harness); 1.21.1 has no equipment assets,
 * so HarnessLayer maps the item to that same texture and draws it as the equipment renderer does (armor cutout, no cull).
 */
public class HappyGhastRenderer extends MobRenderer<HappyGhast, HappyGhastModel> {
    public static final ModelLayerLocation ADULT = layer("happy_ghast"), BABY = layer("happy_ghast_baby");
    public static final ModelLayerLocation HARNESS = layer("happy_ghast_harness"), BABY_HARNESS = layer("happy_ghast_baby_harness");
    private static final ResourceLocation TEXTURE = texture("entity/ghast/happy_ghast"), BABY_TEXTURE = texture("entity/ghast/happy_ghast_baby");
    private final HappyGhastModel adultModel, babyModel;

    public HappyGhastRenderer(EntityRendererProvider.Context context) {
        super(context, new HappyGhastModel(context.bakeLayer(ADULT), false), 2.0F);
        this.adultModel = this.model;
        this.babyModel = new HappyGhastModel(context.bakeLayer(BABY), true);
        this.addLayer(new HarnessLayer(this, new HappyGhastHarnessModel(context.bakeLayer(HARNESS), false),
                new HappyGhastHarnessModel(context.bakeLayer(BABY_HARNESS), true)));
    }

    static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, name), "main");
    }

    static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/" + path + ".png");
    }

    @Override
    public void render(HappyGhast ghast, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        this.model = ghast.isBaby() ? babyModel : adultModel;   // AgeableMobRenderer
        super.render(ghast, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HappyGhast ghast) {
        return ghast.isBaby() ? BABY_TEXTURE : TEXTURE;
    }

    static final class HarnessLayer extends RenderLayer<HappyGhast, HappyGhastModel> {
        private final HappyGhastHarnessModel adult, baby;

        HarnessLayer(RenderLayerParent<HappyGhast, HappyGhastModel> parent, HappyGhastHarnessModel adult, HappyGhastHarnessModel baby) {
            super(parent);
            this.adult = adult;
            this.baby = baby;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, HappyGhast ghast, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (ghast.getBodyArmorItem().getItem() instanceof HarnessItem harness) {
                HappyGhastHarnessModel model = ghast.isBaby() ? baby : adult;
                model.setupAnim(ghast, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                ResourceLocation tex = texture("entity/equipment/happy_ghast_body/" + harness.color().getName() + "_harness");
                model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
            }
        }
    }
}
