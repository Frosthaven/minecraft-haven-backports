package dev.frosthaven.havenbackports.client;

import dev.frosthaven.havenbackports.Content;
import dev.frosthaven.havenbackports.Dappled;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.PaleGarden;
import dev.frosthaven.havenbackports.Sulfur;
import dev.frosthaven.havenbackports.Woods;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client: particle providers (26.x ParticleResources), block entity renderers, sign materials and boat layers for
 *  the new woods (1.21.1 builds them from WoodType and Boat.Type), and the dry foliage tint (26.x BlockColors:
 *  leaf litter uses BlockTintSources.dryFoliage). */
@EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Content.COPPER_FIRE_FLAME.get(), FlameParticle.Provider::new);
        e.registerSpriteSet(Dappled.RED_POPLAR_LEAVES.get(), FallingLeavesParticle.PoplarProvider::new);
        e.registerSpriteSet(Dappled.ORANGE_POPLAR_LEAVES.get(), FallingLeavesParticle.PoplarProvider::new);
        e.registerSpriteSet(Dappled.YELLOW_POPLAR_LEAVES.get(), FallingLeavesParticle.PoplarProvider::new);
        e.registerSpriteSet(PaleGarden.PALE_OAK_LEAVES_PARTICLE.get(), FallingLeavesParticle.PaleOakProvider::new);
        e.registerSpriteSet(PaleGarden.TRAIL.get(), TrailParticle.Provider::new);
        // 26.x ParticleResources: sulfur bubbles, noxious gas (and its cloud emitter), geyser emitter, base, poof, plume
        e.registerSpriteSet(Sulfur.SULFUR_BUBBLES.get(), GasParticles.Bubble.Provider::new);
        e.registerSpriteSet(Sulfur.NOXIOUS_GAS.get(), GasParticles.Gas.Provider::new);
        e.registerSpecial(Sulfur.NOXIOUS_GAS_CLOUD.get(), GasParticles.Cloud.provider());
        e.registerSpecial(Sulfur.GEYSER.get(), GeyserParticles.Eruption.provider());
        e.registerSpriteSet(Sulfur.GEYSER_BASE.get(), GeyserParticles.Base.Provider::new);
        e.registerSpriteSet(Sulfur.GEYSER_POOF.get(), GeyserParticles.Base.Provider::new);
        e.registerSpriteSet(Sulfur.GEYSER_PLUME.get(), GeyserParticles.Plume.Provider::new);
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(Content.SHELF_ENTITY.get(), ShelfRenderer::new);
    }

    @SubscribeEvent
    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (Woods.Wood w : Woods.all()) {
            e.registerLayerDefinition(ModelLayers.createBoatModelName(w.boatType().get()), BoatModel::createBodyModel);
            e.registerLayerDefinition(ModelLayers.createChestBoatModelName(w.boatType().get()), ChestBoatModel::createBodyModel);
        }
    }

    @SubscribeEvent
    static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> Woods.all().forEach(w -> Sheets.addWoodType(w.type())));
    }

    @SubscribeEvent
    static void colorResolvers(RegisterColorHandlersEvent.ColorResolvers e) {
        e.register(DryFoliage.RESOLVER);
    }

    @SubscribeEvent
    static void blockColors(RegisterColorHandlersEvent.Block e) {
        e.register((state, level, pos, tint) -> level != null && pos != null ? level.getBlockTint(pos, DryFoliage.RESOLVER) : DryFoliage.DEFAULT,
                Dappled.LEAF_LITTER.get());
    }

    @SubscribeEvent
    static void chestItems(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) {
        ChestTextures.items(e);
    }

    @SubscribeEvent
    static void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) manager -> ItemBounds.clear());
        e.registerReloadListener(new DryFoliage());
    }
}
