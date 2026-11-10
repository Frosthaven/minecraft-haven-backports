package dev.frosthaven.havenbackports.client.variant;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.variant.FarmVariant;
import dev.frosthaven.havenbackports.variant.VariantHolder;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 26.x PigRenderer, CowRenderer and ChickenRenderer draw each climate variant with its model and texture
 * (FarmVariant model and asset_id). The vanilla renderers are kept (the pig's saddle layer with them) and only the
 * model and texture are chosen per animal: a variant whose asset is vanilla (temperate) keeps 1.21.1's model and
 * texture; warm and cold use Mojang's 26.x models (FarmModels). Babies are drawn as 1.21.1 draws them (a small adult).
 */
public final class FarmRenderers {
    static final ModelLayerLocation PIG = layer("pig"), COLD_PIG = layer("cold_pig"), WARM_COW = layer("warm_cow"), COLD_COW = layer("cold_cow"),
            COLD_CHICKEN = layer("cold_chicken");

    private FarmRenderers() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, name), "main");
    }

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(PIG, FarmModels::pig);
        e.registerLayerDefinition(COLD_PIG, FarmModels::coldPig);
        e.registerLayerDefinition(WARM_COW, FarmModels::warmCow);
        e.registerLayerDefinition(COLD_COW, FarmModels::coldCow);
        e.registerLayerDefinition(COLD_CHICKEN, FarmModels::coldChicken);
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(EntityType.PIG, Pigs::new);
        e.registerEntityRenderer(EntityType.COW, Cows::new);
        e.registerEntityRenderer(EntityType.CHICKEN, Chickens::new);
    }

    @SuppressWarnings("unchecked")
    static FarmVariant variant(Object mob) {
        return ((VariantHolder<FarmVariant>) mob).haven_backports$variant().value();
    }

    static boolean vanilla(FarmVariant v) {
        return v.assetId().getNamespace().equals("minecraft");
    }

    static ResourceLocation texture(FarmVariant v) {
        return v.assetId().withPath(p -> "textures/" + p + ".png");
    }

    static final class Pigs extends PigRenderer {
        private final PigModel<Pig> base, normal, cold;

        Pigs(EntityRendererProvider.Context context) {
            super(context);
            this.base = this.model;
            this.normal = new PigModel<>(context.bakeLayer(PIG));
            this.cold = new PigModel<>(context.bakeLayer(COLD_PIG));
        }

        @Override
        public void render(Pig pig, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
            FarmVariant v = variant(pig);
            this.model = vanilla(v) ? base : v.model().equals("cold") ? cold : normal;
            super.render(pig, yaw, partialTicks, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(Pig pig) {
            FarmVariant v = variant(pig);
            return vanilla(v) ? super.getTextureLocation(pig) : texture(v);
        }
    }

    static final class Cows extends CowRenderer {
        private final CowModel<Cow> base, warm, cold;

        Cows(EntityRendererProvider.Context context) {
            super(context);
            this.base = this.model;
            this.warm = new CowModel<>(context.bakeLayer(WARM_COW));
            this.cold = new CowModel<>(context.bakeLayer(COLD_COW));
        }

        @Override
        public void render(Cow cow, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
            FarmVariant v = variant(cow);
            this.model = vanilla(v) ? base : v.model().equals("cold") ? cold : warm;
            super.render(cow, yaw, partialTicks, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(Cow cow) {
            FarmVariant v = variant(cow);
            return vanilla(v) ? super.getTextureLocation(cow) : texture(v);
        }
    }

    static final class Chickens extends ChickenRenderer {
        private final ChickenModel<Chicken> base, cold;

        Chickens(EntityRendererProvider.Context context) {
            super(context);
            this.base = this.model;
            this.cold = new ChickenModel<>(context.bakeLayer(COLD_CHICKEN));
        }

        @Override
        public void render(Chicken chicken, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
            FarmVariant v = variant(chicken);
            this.model = !vanilla(v) && v.model().equals("cold") ? cold : base;   // warm: 26.x's normal model, 1.21.1's shape
            super.render(chicken, yaw, partialTicks, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(Chicken chicken) {
            FarmVariant v = variant(chicken);
            return vanilla(v) ? super.getTextureLocation(chicken) : texture(v);
        }
    }
}
