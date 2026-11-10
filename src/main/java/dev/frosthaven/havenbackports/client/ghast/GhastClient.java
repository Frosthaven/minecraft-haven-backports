package dev.frosthaven.havenbackports.client.ghast;

import dev.frosthaven.havenbackports.Ghasts;
import dev.frosthaven.havenbackports.ghast.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * Client side of the happy ghast: renderer and model layers (26.x LayerDefinitions), the third-person camera distance
 * (26.x Camera.setup: max(own scale x CAMERA_DISTANCE, mount scale x mount's CAMERA_DISTANCE); a player's is 4, a happy
 * ghast's 8) and the riding loop sound LocalPlayer.startRiding starts (volume 0 to 1, x5 by speed).
 */
public final class GhastClient {
    private static int ridingSoundId;

    private GhastClient() {}

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(HappyGhastRenderer.ADULT, () -> HappyGhastModel.createBodyLayer(false, CubeDeformation.NONE));
        e.registerLayerDefinition(HappyGhastRenderer.BABY, () -> HappyGhastModel.createBodyLayer(true, CubeDeformation.NONE));
        e.registerLayerDefinition(HappyGhastRenderer.HARNESS, HappyGhastHarnessModel::createHarnessLayer);
        e.registerLayerDefinition(HappyGhastRenderer.BABY_HARNESS, HappyGhastHarnessModel::createHarnessLayer);
        e.registerLayerDefinition(HappyGhastRenderer.ROPES, () -> HappyGhastModel.createBodyLayer(false, new CubeDeformation(0.2F)));
        e.registerLayerDefinition(HappyGhastRenderer.BABY_ROPES, () -> HappyGhastModel.createBodyLayer(true, new CubeDeformation(0.2F)));
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Ghasts.HAPPY_GHAST.get(), HappyGhastRenderer::new);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(GhastClient::camera);
        NeoForge.EVENT_BUS.addListener(GhastClient::mount);
    }

    private static void camera(CalculateDetachedCameraDistanceEvent e) {
        if (e.getCamera().getEntity() instanceof LivingEntity living && living.getVehicle() instanceof HappyGhast ghast) {
            float own = e.getEntityScalingFactor();
            float distance = Math.max(own * e.getDistance(), ghast.getScale() * (float) HappyGhast.CAMERA_DISTANCE);
            e.setDistance(distance / own);
        }
    }

    private static void mount(EntityMountEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.isMounting() && e.getLevel().isClientSide() && e.getEntityMounting() == mc.player) {
            int id = ++ridingSoundId;
            if (e.getEntityBeingMounted() instanceof HappyGhast ghast) {
                mc.getSoundManager().play(new RidingEntitySoundInstance(mc.player, id, () -> ridingSoundId, ghast, false,
                        Ghasts.HAPPY_GHAST_RIDING.get(), ghast.getSoundSource(), 0.0F, 1.0F, 5.0F));
            }
        }
    }
}
