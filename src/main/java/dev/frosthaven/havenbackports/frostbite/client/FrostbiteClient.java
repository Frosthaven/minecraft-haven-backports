package dev.frosthaven.havenbackports.frostbite.client;

import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.frostbite.FrostbiteContent;
import net.minecraft.client.model.DrownedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Client side of the frostbite and the ice ball: renderers (26.x EntityRenderers: FrostbiteRenderer, ThrownItemRenderer),
 * the model layers (26.x LayerDefinitions: HumanoidModel.createBodyLayerWithCustomLeftArmsAndLegs, plain and with
 * 0.25 deformation for the outer layer; 1.21.1 builds the same mesh as DrownedModel.createBodyLayer) and the freezing
 * particle (26.x ParticleResources: FreezingParticle.Provider).
 */
@EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FrostbiteClient {
    static final ModelLayerLocation FROSTBITE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "frostbite"), "main");
    static final ModelLayerLocation FROSTBITE_OUTER_LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "frostbite"), "outer");

    private FrostbiteClient() {}

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(FrostbiteContent.FROSTBITE.get(), FrostbiteRenderer::new);
        e.registerEntityRenderer(FrostbiteContent.ICE_BALL.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(FROSTBITE, () -> DrownedModel.createBodyLayer(CubeDeformation.NONE));
        e.registerLayerDefinition(FROSTBITE_OUTER_LAYER, () -> DrownedModel.createBodyLayer(new CubeDeformation(0.25F)));
    }

    @SubscribeEvent
    static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(FrostbiteContent.FREEZING_PARTICLE.get(), FreezingParticle.Provider::new);
    }
}
