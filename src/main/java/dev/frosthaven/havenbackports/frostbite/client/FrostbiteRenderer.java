package dev.frosthaven.havenbackports.frostbite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.frostbite.Frostbite;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/**
 * 26.x FrostbiteRenderer: the zombie renderer with the frostbite texture, zombie armor, and FrostbiteOuterLayer (the
 * frost overlay, 0.25 larger, not drawn on babies). 26.x gives every baby zombie its own small model and texture
 * (frostbite_baby.png among them); 1.21.1 draws baby zombies as the scaled adult model, and so does this one.
 */
public class FrostbiteRenderer extends AbstractZombieRenderer<Frostbite, ZombieModel<Frostbite>> {
    private static final ResourceLocation FROSTBITE_LOCATION = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/zombie/frostbite.png");
    static final ResourceLocation FROSTBITE_OUTER_LAYER_LOCATION =
            ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/zombie/frostbite_outer_layer.png");

    public FrostbiteRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(FrostbiteClient.FROSTBITE)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)), new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
        this.addLayer(new OuterLayer(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return FROSTBITE_LOCATION;
    }

    /** 26.x FrostbiteOuterLayer (as 1.21.1's DrownedOuterLayer: the parent's pose copied onto the larger mesh). */
    static final class OuterLayer extends RenderLayer<Frostbite, ZombieModel<Frostbite>> {
        private final ZombieModel<Frostbite> model;

        OuterLayer(RenderLayerParent<Frostbite, ZombieModel<Frostbite>> renderer, EntityModelSet modelSet) {
            super(renderer);
            this.model = new ZombieModel<>(modelSet.bakeLayer(FrostbiteClient.FROSTBITE_OUTER_LAYER));
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Frostbite entity, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!entity.isBaby()) {
                coloredCutoutModelCopyLayerRender(this.getParentModel(), this.model, FROSTBITE_OUTER_LAYER_LOCATION, poseStack, buffer, packedLight,
                        entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTicks, -1);
            }
        }
    }
}
