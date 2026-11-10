package dev.frosthaven.havenbackports.cube.client;

import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.cube.Cube;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client of the Sulfur Cube: renderer, its four model layers (26.x ModelLayers / LayerDefinitions), the goo particle. */
@EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CubeClient {
    static final ModelLayerLocation OUTER = layer("sulfur_cube", "main");
    static final ModelLayerLocation INNER = layer("sulfur_cube", "inner");
    static final ModelLayerLocation SMALL_OUTER = layer("sulfur_cube_small", "main");
    static final ModelLayerLocation SMALL_INNER = layer("sulfur_cube_small", "inner");

    private CubeClient() {}

    private static ModelLayerLocation layer(String path, String part) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, path), part);
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Cube.SULFUR_CUBE.get(), SulfurCubeRenderer::new);
    }

    @SubscribeEvent
    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(OUTER, SulfurCubeModel::createOuterBodyLayer);
        e.registerLayerDefinition(INNER, SulfurCubeModel::createInnerBodyLayer);
        e.registerLayerDefinition(SMALL_OUTER, SulfurCubeModel::createSmallOuterBodyLayer);
        e.registerLayerDefinition(SMALL_INNER, SulfurCubeModel::createSmallInnerBodyLayer);
    }

    @SubscribeEvent
    static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Cube.SULFUR_CUBE_GOO.get(), GooParticle.Provider::new);
    }
}
