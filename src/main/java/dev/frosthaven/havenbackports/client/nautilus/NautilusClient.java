package dev.frosthaven.havenbackports.client.nautilus;

import dev.frosthaven.havenbackports.Nautiluses;
import dev.frosthaven.havenbackports.client.ghast.RidingEntitySoundInstance;
import dev.frosthaven.havenbackports.nautilus.AbstractNautilus;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * Client side of the nautilus: the inventory screen and the riding loop 26.x LocalPlayer.startRiding starts for a
 * nautilus (its riding sound, underwater only, volume 0 to 1, x5 by speed).
 */
public final class NautilusClient {
    private static int ridingSoundId;

    private NautilusClient() {}

    public static void screens(RegisterMenuScreensEvent e) {
        e.register(Nautiluses.MENU.get(), NautilusInventoryScreen::new);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(NautilusClient::mount);
    }

    private static void mount(EntityMountEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.isMounting() && e.getLevel().isClientSide() && e.getEntityMounting() == mc.player
                && e.getEntityBeingMounted() instanceof AbstractNautilus nautilus) {
            int id = ++ridingSoundId;
            mc.getSoundManager().play(new RidingEntitySoundInstance(mc.player, id, () -> ridingSoundId, nautilus, true,
                    nautilus.getRidingSound(), nautilus.getSoundSource(), 0.0F, 1.0F, 5.0F));
        }
    }
}
