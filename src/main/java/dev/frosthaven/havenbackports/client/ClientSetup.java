package dev.frosthaven.havenbackports.client;

import dev.frosthaven.havenbackports.Content;
import dev.frosthaven.havenbackports.HavenBackports;
import net.minecraft.client.particle.FlameParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client: the copper fire flame draws like vanilla's flame (Mojang's 26.x ParticleResources: FlameParticle.Provider). */
@EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Content.COPPER_FIRE_FLAME.get(), FlameParticle.Provider::new);
    }
}
