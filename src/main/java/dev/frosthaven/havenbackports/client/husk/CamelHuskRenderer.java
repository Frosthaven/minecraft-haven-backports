package dev.frosthaven.havenbackports.client.husk;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.husk.CamelHusk;
import net.minecraft.client.model.CamelModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Mojang's 26.x CamelHuskRenderer: the adult camel model (1.21.1's CAMEL layer: the same cubes and UVs as 26.x's
 * AdultCamelModel) with camel_husk.png, and the saddle as its own layer with camel_husk_saddle/saddle.png on the saddle
 * cubes (26.x CamelSaddleModel: saddle, bridle, reins only while ridden; drawn while saddled). 1.21.1's CamelModel
 * keeps the saddle cubes in the body model, drawn from the body texture: the body pass hides them and the saddle
 * pass draws only them (ModelPart.skipDraw on the rest, so their children still draw).
 */
public class CamelHuskRenderer extends MobRenderer<CamelHusk, CamelModel<CamelHusk>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/camel/camel_husk.png");
    private static final ResourceLocation SADDLE = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID,
            "textures/entity/equipment/camel_husk_saddle/saddle.png");

    public CamelHuskRenderer(EntityRendererProvider.Context context) {
        super(context, new Body(context.bakeLayer(ModelLayers.CAMEL)), 0.7F);
        this.addLayer(new SaddleLayer(this, new Saddle(context.bakeLayer(ModelLayers.CAMEL))));
    }

    @Override
    public ResourceLocation getTextureLocation(CamelHusk entity) {
        return TEXTURE;
    }

    /** The camel without its saddle cubes. */
    static final class Body extends CamelModel<CamelHusk> {
        private final ModelPart[] saddle;

        Body(ModelPart root) {
            super(root);
            ModelPart head = root.getChild("body").getChild("head");
            this.saddle = new ModelPart[]{root.getChild("body").getChild("saddle"), head.getChild("bridle"), head.getChild("reins")};
        }

        @Override
        public void setupAnim(CamelHusk entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            for (ModelPart part : this.saddle) {
                part.visible = false;
            }
        }
    }

    /** Only the saddle cubes (CamelModel shows saddle and bridle while saddled, reins while also ridden). */
    static final class Saddle extends CamelModel<CamelHusk> {
        Saddle(ModelPart root) {
            super(root);
            ModelPart body = root.getChild("body");
            ModelPart head = body.getChild("head");
            for (ModelPart part : new ModelPart[]{body, body.getChild("hump"), body.getChild("tail"), head, head.getChild("left_ear"),
                    head.getChild("right_ear"), root.getChild("left_hind_leg"), root.getChild("right_hind_leg"),
                    root.getChild("left_front_leg"), root.getChild("right_front_leg")}) {
                part.skipDraw = true;
            }
        }
    }

    static final class SaddleLayer extends RenderLayer<CamelHusk, CamelModel<CamelHusk>> {
        private final Saddle model;

        SaddleLayer(RenderLayerParent<CamelHusk, CamelModel<CamelHusk>> parent, Saddle model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int light, CamelHusk entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isSaddled()) {
                coloredCutoutModelCopyLayerRender(this.getParentModel(), this.model, SADDLE, poseStack, buffer, light, entity,
                        limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTick, -1);
            }
        }
    }
}
