package dev.frosthaven.havenbackports.client.nautilus;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.Nautiluses;
import dev.frosthaven.havenbackports.nautilus.AbstractNautilus;
import dev.frosthaven.havenbackports.nautilus.NautilusArmorItem;
import dev.frosthaven.havenbackports.nautilus.ZombieNautilus;
import dev.frosthaven.havenbackports.variant.FarmVariant;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Mojang's 26.x NautilusRenderer and ZombieNautilusRenderer: the adult or baby model (26.x AgeableMobRenderer; 1.21.1's
 * MobRenderer holds one model, so it is chosen per frame), the body armor layer (entity/equipment/nautilus_body/<asset>)
 * and the saddle layer (nautilus_saddle/saddle), each a 26.x SimpleEquipmentLayer drawn while that slot holds an item.
 * The zombie nautilus takes its variant's model (normal, or warm with corals) and texture.
 */
public class NautilusRenderer extends MobRenderer<AbstractNautilus, NautilusModel> {
    public static final ModelLayerLocation ADULT = layer("nautilus"), BABY = layer("nautilus_baby"), SADDLE = layer("nautilus_saddle"),
            ARMOR = layer("nautilus_armor"), ZOMBIE = layer("zombie_nautilus"), ZOMBIE_CORAL = layer("zombie_nautilus_coral");
    private static final ResourceLocation TEXTURE = tex("textures/entity/nautilus/nautilus.png"),
            BABY_TEXTURE = tex("textures/entity/nautilus/nautilus_baby.png"), SADDLE_TEXTURE = tex("textures/entity/equipment/nautilus_saddle/saddle.png");
    private final NautilusModel adult;
    private final NautilusModel baby;
    private final NautilusModel coral;

    private NautilusRenderer(EntityRendererProvider.Context context, ModelLayerLocation adult, ModelLayerLocation baby, ModelLayerLocation coral) {
        super(context, new NautilusModel(context.bakeLayer(adult)), 0.7F);
        this.adult = this.model;
        this.baby = baby == null ? this.model : new NautilusModel(context.bakeLayer(baby));
        this.coral = coral == null ? this.model : new NautilusModel(context.bakeLayer(coral));
        this.addLayer(new Equipment(this, new NautilusModel(context.bakeLayer(ARMOR)), true));
        this.addLayer(new Equipment(this, new NautilusModel(context.bakeLayer(SADDLE)), false));
    }

    public static NautilusRenderer nautilus(EntityRendererProvider.Context context) {
        return new NautilusRenderer(context, ADULT, BABY, null);
    }

    public static NautilusRenderer zombie(EntityRendererProvider.Context context) {
        return new NautilusRenderer(context, ZOMBIE, null, ZOMBIE_CORAL);
    }

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(ADULT, NautilusModel::createBodyLayer);
        e.registerLayerDefinition(BABY, NautilusModel::createBabyBodyLayer);
        e.registerLayerDefinition(SADDLE, NautilusModel::createSaddleLayer);
        e.registerLayerDefinition(ARMOR, NautilusModel::createArmorLayer);
        e.registerLayerDefinition(ZOMBIE, NautilusModel::createBodyLayer);
        e.registerLayerDefinition(ZOMBIE_CORAL, NautilusModel::createCoralLayer);
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Nautiluses.NAUTILUS.get(), NautilusRenderer::nautilus);
        e.registerEntityRenderer(Nautiluses.ZOMBIE_NAUTILUS.get(), NautilusRenderer::zombie);
    }

    @Override
    public void render(AbstractNautilus entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
        if (entity instanceof ZombieNautilus zombie) {
            this.model = "warm".equals(zombie.getVariant().value().model()) ? this.coral : this.adult;
        } else {
            this.model = entity.isBaby() ? this.baby : this.adult;
        }
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractNautilus entity) {
        if (entity instanceof ZombieNautilus zombie) {
            FarmVariant v = zombie.getVariant().value();
            return v.assetId().withPath(p -> "textures/" + p + ".png");
        }
        return entity.isBaby() ? BABY_TEXTURE : TEXTURE;
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(tex(name), "main");
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, path);
    }

    /** 26.x SimpleEquipmentLayer for NAUTILUS_BODY (the armor's asset) or NAUTILUS_SADDLE. */
    static final class Equipment extends RenderLayer<AbstractNautilus, NautilusModel> {
        private final NautilusModel model;
        private final boolean body;

        Equipment(RenderLayerParent<AbstractNautilus, NautilusModel> parent, NautilusModel model, boolean body) {
            super(parent);
            this.model = model;
            this.body = body;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractNautilus entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack stack = this.body ? entity.getBodyArmorItem() : entity.getSaddle();
            ResourceLocation texture = this.body ? (stack.getItem() instanceof NautilusArmorItem armor ? armor.texture() : null)
                    : (stack.isEmpty() ? null : SADDLE_TEXTURE);
            if (texture != null) {
                coloredCutoutModelCopyLayerRender(this.getParentModel(), this.model, texture, poseStack, buffer, light, entity,
                        limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTick, -1);
            }
        }
    }
}
