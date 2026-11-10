package dev.frosthaven.havenbackports.client;

import dev.frosthaven.havenbackports.Content;
import dev.frosthaven.havenbackports.HavenBackports;
import net.minecraft.client.particle.FlameParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Client: the copper fire flame draws like vanilla's flame (Mojang's 26.x ParticleResources: FlameParticle.Provider);
 *  shelves draw their items (26.x BlockEntityRenderers: SHELF -> ShelfRenderer). */
@EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Content.COPPER_FIRE_FLAME.get(), FlameParticle.Provider::new);
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(Content.SHELF_ENTITY.get(), ShelfRenderer::new);
        dev.frosthaven.havenbackports.client.ghast.GhastClient.renderers(e);
    }

    @SubscribeEvent
    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        dev.frosthaven.havenbackports.client.ghast.GhastClient.layers(e);
    }

    @SubscribeEvent
    static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) {
        dev.frosthaven.havenbackports.client.ghast.GhastClient.init();
    }

    @SubscribeEvent
    static void chestItems(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) {
        ChestTextures.items(e);
    }

    @SubscribeEvent
    static void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) manager -> ItemBounds.clear());
    }
}
