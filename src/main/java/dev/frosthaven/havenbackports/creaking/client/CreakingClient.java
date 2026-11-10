package dev.frosthaven.havenbackports.creaking.client;

import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.creaking.CreakingContent;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client side of the creaking: renderer and model layer (26.x EntityRenderers, LayerDefinitions) and the
 *  block_crumble particle (26.x ParticleResources: TerrainParticle.CrumblingProvider). */
@EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CreakingClient {
    private CreakingClient() {}

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(CreakingContent.CREAKING.get(), CreakingRenderer::new);
    }

    @SubscribeEvent
    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(CreakingRenderer.LAYER, CreakingModel::createBodyLayer);
    }

    /** 26.x CrumblingProvider: a terrain particle of the block (none for air, a moving piston, or a block that makes
     *  no terrain particles) that stays still for 1 to 10 ticks. 1.21.1's TerrainParticle.createTerrainParticle is
     *  package-private: its checks are repeated here. */
    @SubscribeEvent
    static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpecial(CreakingContent.BLOCK_CRUMBLE.get(), (ParticleProvider<BlockParticleOption>) (options, level, x, y, z, xa, ya, za) -> {
            BlockState state = options.getState();
            if (state.isAir() || state.is(Blocks.MOVING_PISTON) || !state.shouldSpawnTerrainParticles()) {
                return null;
            }
            Particle particle = new TerrainParticle(level, x, y, z, xa, ya, za, state).updateSprite(state, options.getPos());
            particle.setParticleSpeed(0.0, 0.0, 0.0);
            particle.setLifetime(level.random.nextInt(10) + 1);
            return particle;
        });
    }
}
