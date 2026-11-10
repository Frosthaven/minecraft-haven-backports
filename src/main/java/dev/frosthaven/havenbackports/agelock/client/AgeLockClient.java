package dev.frosthaven.havenbackports.agelock.client;

import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** 26.x ParticleResources: PAUSE_MOB_GROWTH and RESET_MOB_GROWTH, SimpleVerticalParticle (down when locking, up when
 *  unlocking). 26.x's OPAQUE layer is 1.21.1's PARTICLE_SHEET_OPAQUE. */
@EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AgeLockClient {
    private AgeLockClient() {}

    @SubscribeEvent
    static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(AgeLock.PAUSE_MOB_GROWTH.get(), sprites -> provider(sprites, false));
        e.registerSpriteSet(AgeLock.RESET_MOB_GROWTH.get(), sprites -> provider(sprites, true));
    }

    private static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, boolean upwards) {
        return (type, level, x, y, z, xa, ya, za) -> new Vertical(level, x, y, z, xa, ya, za, sprites, upwards);
    }

    /** 26.x SimpleVerticalParticle: the given speed (no random spread), no gravity, 0.03 up or down, size x0.5..1.1,
     *  8 ticks. */
    static final class Vertical extends TextureSheetParticle {
        Vertical(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, boolean upwards) {
            super(level, x, y, z, xa, ya, za);
            this.xd = xa;
            this.zd = za;
            this.yd = ya;
            this.gravity = 0.0F;
            this.yd += upwards ? 0.03 : -0.03;
            this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.5F);
            this.lifetime = 8;
            this.pickSprite(sprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }
}
