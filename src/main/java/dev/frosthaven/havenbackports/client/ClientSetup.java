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
 *  leaf litter uses BlockTintSources.dryFoliage). The copper fire flame draws like vanilla's flame (26.x
 *  ParticleResources: FlameParticle.Provider); shelves draw their items (26.x BlockEntityRenderers: SHELF -> ShelfRenderer); spears' hands and arm pose. */
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
        e.registerSpriteSet(dev.frosthaven.havenbackports.Flora.FIREFLY.get(), FireflyParticle.Provider::new);
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(Content.SHELF_ENTITY.get(), ShelfRenderer::new);
        dev.frosthaven.havenbackports.client.ghast.GhastClient.renderers(e);
        e.registerEntityRenderer(dev.frosthaven.havenbackports.Golems.COPPER_GOLEM.get(), dev.frosthaven.havenbackports.client.golem.CopperGolemRenderer::new);
        e.registerBlockEntityRenderer(dev.frosthaven.havenbackports.Golems.STATUE_ENTITY.get(), dev.frosthaven.havenbackports.client.golem.CopperGolemStatueRenderer::new);
        e.registerEntityRenderer(dev.frosthaven.havenbackports.Cushions.CUSHION.get(), dev.frosthaven.havenbackports.client.decoration.CushionRenderer::new);
        dev.frosthaven.havenbackports.client.variant.FarmRenderers.renderers(e);
        e.registerEntityRenderer(dev.frosthaven.havenbackports.Husks.CAMEL_HUSK.get(), dev.frosthaven.havenbackports.client.husk.CamelHuskRenderer::new);
        e.registerEntityRenderer(dev.frosthaven.havenbackports.Husks.PARCHED.get(), dev.frosthaven.havenbackports.client.husk.ParchedRenderer::new);
        dev.frosthaven.havenbackports.client.nautilus.NautilusRenderer.renderers(e);
    }

    @SubscribeEvent
    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        dev.frosthaven.havenbackports.client.ghast.GhastClient.layers(e);
        dev.frosthaven.havenbackports.client.golem.CopperGolemLayers.register(e);
        e.registerLayerDefinition(dev.frosthaven.havenbackports.client.decoration.CushionRenderer.LAYER, dev.frosthaven.havenbackports.client.decoration.CushionRenderer::createBodyLayer);
        dev.frosthaven.havenbackports.client.variant.FarmRenderers.layers(e);
        e.registerLayerDefinition(dev.frosthaven.havenbackports.client.husk.ParchedRenderer.LAYER, dev.frosthaven.havenbackports.client.husk.ParchedRenderer::createBodyLayer);
        dev.frosthaven.havenbackports.client.nautilus.NautilusRenderer.layers(e);
    }

    @SubscribeEvent
    static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) {
        dev.frosthaven.havenbackports.client.nautilus.NautilusClient.screens(e);
    }

    @SubscribeEvent
    static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) {
        dev.frosthaven.havenbackports.client.ghast.GhastClient.init();
        dev.frosthaven.havenbackports.client.nautilus.NautilusClient.init();
    }

    @SubscribeEvent
    static void boatLayers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (Woods.Wood w : Woods.all()) {
            e.registerLayerDefinition(ModelLayers.createBoatModelName(w.boatType().get()), BoatModel::createBodyModel);
            e.registerLayerDefinition(ModelLayers.createChestBoatModelName(w.boatType().get()), ChestBoatModel::createBodyModel);
        }
    }

    @SubscribeEvent
    static void woodSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> Woods.all().forEach(w -> Sheets.addWoodType(w.type())));
        // 26.x ClientLevel.playPlayerSound (the dry grasses' ambient sound): a sound bound to the local player
        dev.frosthaven.havenbackports.block.DryVegetationBlock.PLAYER_SOUND = (sound, source) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                mc.getSoundManager().play(new net.minecraft.client.resources.sounds.EntityBoundSoundInstance(sound, source, 1.0F, 1.0F,
                        mc.player, mc.level.random.nextLong()));
            }
        };
    }

    @SubscribeEvent
    static void colorResolvers(RegisterColorHandlersEvent.ColorResolvers e) {
        e.register(DryFoliage.RESOLVER);
    }

    @SubscribeEvent
    static void blockColors(RegisterColorHandlersEvent.Block e) {
        e.register((state, level, pos, tint) -> level != null && pos != null ? level.getBlockTint(pos, DryFoliage.RESOLVER) : DryFoliage.DEFAULT,
                Dappled.LEAF_LITTER.get());
        // 26.x BlockColors: the bush takes the grass tint (as short grass); wildflowers' stems (tint 1) as pink petals'
        e.register((state, level, pos, tint) -> level != null && pos != null
                ? net.minecraft.client.renderer.BiomeColors.getAverageGrassColor(level, pos)
                : net.minecraft.world.level.GrassColor.getDefaultColor(), dev.frosthaven.havenbackports.Flora.BUSH.get());
        e.register((state, level, pos, tint) -> tint == 0 ? -1 : level != null && pos != null
                ? net.minecraft.client.renderer.BiomeColors.getAverageGrassColor(level, pos)
                : net.minecraft.world.level.GrassColor.getDefaultColor(), dev.frosthaven.havenbackports.Flora.WILDFLOWERS.get());
    }

    /** 26.x items/bush.json: a minecraft:grass tint at temperature 0.5, downfall 1.0 (1.21.1 tints items in code). */
    @SubscribeEvent
    static void itemColors(RegisterColorHandlersEvent.Item e) {
        e.register((stack, tint) -> net.minecraft.util.FastColor.ARGB32.opaque(net.minecraft.world.level.GrassColor.get(0.5, 1.0)), dev.frosthaven.havenbackports.Flora.BUSH.get());
    }

    @SubscribeEvent
    static void chestItems(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) {
        ChestTextures.items(e);
        dev.frosthaven.havenbackports.client.spear.SpearClient.register(e);
        dev.frosthaven.havenbackports.client.golem.CopperGolemStatueRenderer.items(e);
    }

    @SubscribeEvent
    static void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) manager -> ItemBounds.clear());
        e.registerReloadListener(new DryFoliage());
    }
}
