package dev.frosthaven.havenbackports.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * 26.x FireflyParticle (the firefly bush's fireflies) on 1.21.1's TextureSheetParticle: drifts, turns at random, fades
 * its light in and out over a 200 to 300 tick life and dies inside a block. 26.x's light value is the same packed int
 * (block light in the low bits) 1.21.1 reads. 26.x's provider takes the level's random; 1.21.1 passes none.
 */
public class FireflyParticle extends TextureSheetParticle {
    FireflyParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za) {
        super(level, x, y, z, xa, ya, za);
        this.speedUpWhenYMotionIsBlocked = true;
        this.friction = 0.96F;
        this.quadSize *= 0.75F;
        this.yd *= 0.8F;
        this.xd *= 0.8F;
        this.zd *= 0.8F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return (int) (255.0F * fade(this.progress(this.age + partialTick), 0.1F, 0.3F));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.getBlockState(BlockPos.containing(this.x, this.y, this.z)).isAir()) {
            this.remove();
        } else {
            this.setAlpha(fade(this.progress(this.age), 0.3F, 0.5F));
            if (this.random.nextFloat() > 0.95F || this.age == 1) {
                this.setParticleSpeed(-0.05F + 0.1F * this.random.nextFloat(), -0.05F + 0.1F * this.random.nextFloat(),
                        -0.05F + 0.1F * this.random.nextFloat());
            }
        }
    }

    private float progress(float age) {
        return Math.clamp(age / this.lifetime, 0.0F, 1.0F);
    }

    private static float fade(float progress, float fadeIn, float fadeOut) {
        if (progress >= 1.0F - fadeIn) {
            return (1.0F - progress) / fadeIn;
        }
        return progress <= fadeOut ? progress / fadeOut : 1.0F;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xa, double ya, double za) {
            RandomSource random = level.random;
            FireflyParticle p = new FireflyParticle(level, x, y, z, 0.5 - random.nextDouble(), random.nextBoolean() ? ya : -ya, 0.5 - random.nextDouble());
            p.pickSprite(this.sprites);
            p.setLifetime(random.nextIntBetweenInclusive(200, 300));
            p.scale(1.5F);
            p.setAlpha(0.0F);
            return p;
        }
    }
}
