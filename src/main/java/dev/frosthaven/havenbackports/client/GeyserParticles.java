package dev.frosthaven.havenbackports.client;

import dev.frosthaven.havenbackports.Sulfur;
import dev.frosthaven.havenbackports.particle.GeyserBaseParticleOptions;
import dev.frosthaven.havenbackports.particle.GeyserParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/** 26.x geyser particles: GeyserEruptionParticle (an emitter), GeyserBaseParticle (base and poof), GeyserPlumeParticle.
 *  26.x SingleQuadParticle layer OPAQUE is 1.21.1's PARTICLE_SHEET_OPAQUE. */
final class GeyserParticles {
    private GeyserParticles() {}

    /** 26.x GeyserEruptionParticle: for 20 ticks, base particles every 2 ticks, water + 2 plume particles each tick,
     *  20 poof particles every 10 ticks. */
    static final class Eruption extends NoRenderParticle {
        private final int waterBlocks;
        private final double xa, ya, za;
        private final GeyserParticleOptions plume;
        private final GeyserBaseParticleOptions base;
        private final GeyserBaseParticleOptions poof;

        Eruption(ClientLevel level, double x, double y, double z, double xa, double ya, double za, GeyserParticleOptions options) {
            super(level, x, y, z);
            this.xa = xa;
            this.ya = ya;
            this.za = za;
            this.waterBlocks = options.waterBlocks();
            this.lifetime = 20;
            this.plume = new GeyserParticleOptions(Sulfur.GEYSER_PLUME.get(), this.waterBlocks);
            this.base = new GeyserBaseParticleOptions(Sulfur.GEYSER_BASE.get(), this.waterBlocks, 1.5F);
            this.poof = new GeyserBaseParticleOptions(Sulfur.GEYSER_POOF.get(), this.waterBlocks, 2.0F);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.age % 2 == 0) {
                for (int i = 0; i < 2; i++) {
                    this.level.addParticle(this.base, this.x, this.y, this.z, this.xa, this.ya, this.za);
                }
            }
            for (int i = 0; i < this.waterBlocks + 2; i++) {
                this.level.addParticle(this.plume, this.x, this.y, this.z, this.xa, this.ya, this.za);
            }
            if (this.age % 10 == 0) {
                for (int i = 0; i < 20; i++) {
                    this.level.addParticle(this.poof, this.x, this.y, this.z, this.xa, this.ya, this.za);
                }
            }
        }

        static ParticleProvider<GeyserParticleOptions> provider() {
            return (o, level, x, y, z, xa, ya, za) -> new Eruption(level, x, y, z, xa, ya, za, o);
        }
    }

    /** 26.x GeyserBaseParticle on 1.21.1's BaseAshSmokeParticle. */
    static final class Base extends BaseAshSmokeParticle {
        Base(ClientLevel level, double x, double y, double z, double xa, double ya, double za, int waterBlocks, float burstImpulseBase, SpriteSet sprites) {
            super(level, x, y, z, burstImpulseBase + 0.25F * waterBlocks, burstImpulseBase + 0.25F * waterBlocks, burstImpulseBase + 0.25F * waterBlocks,
                    xa, ya, za, 3.0F + 0.125F * waterBlocks, sprites, 0.0F, 0, 0.0F, true);
            this.friction = 0.725F;
            this.rCol = 1.0F;
            this.gCol = 1.0F;
            this.bCol = 1.0F;
            this.yd = Math.abs(this.yd);
            this.lifetime = (int) (25.0F * (0.8F + 0.2F * level.getRandom().nextFloat()));
        }

        record Provider(SpriteSet sprites) implements ParticleProvider<GeyserBaseParticleOptions> {
            @Override
            public Particle createParticle(GeyserBaseParticleOptions o, ClientLevel level, double x, double y, double z, double xa, double ya, double za) {
                var random = level.getRandom();
                double rx = x + (random.nextFloat() - 0.5F) * 0.5F;
                double ry = y + (random.nextFloat() - 0.5F) * 0.5F + 0.2F;
                double rz = z + (random.nextFloat() - 0.5F) * 0.5F;
                return new Base(level, rx, ry, rz, xa, ya, za, o.waterBlocks(), o.burstImpulseBase(), this.sprites);
            }
        }
    }

    /** 26.x GeyserPlumeParticle: rises to 5 blocks per water block, slowing as it climbs, growing wider. */
    static final class Plume extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final double startY;
        private final double maxY;
        private final float initialPropulsion;
        private final float sprayX;
        private final float sprayZ;
        private final float minSize;
        private final float maxSize;
        private boolean done;

        Plume(ClientLevel level, double x, double y, double z, double xa, double ya, double za, GeyserParticleOptions o, SpriteSet sprites) {
            super(level, x, y, z, xa, ya, za);
            int plumeHeight = 5 * Math.max(1, o.waterBlocks());
            this.hasPhysics = true;
            this.speedUpWhenYMotionIsBlocked = true;
            this.lifetime = plumeHeight * 5;
            this.yd = 0.0;
            this.startY = y;
            this.maxY = this.startY + plumeHeight - 1.0;
            this.sprayX = (level.getRandom().nextFloat() - 0.5F) * 0.2F;
            this.sprayZ = (level.getRandom().nextFloat() - 0.5F) * 0.2F;
            this.friction = 1.0F;
            this.initialPropulsion = (o.waterBlocks() == 1 ? 1.5F : 1.0F) * plumeHeight * 1.45F;
            this.gravity = -this.initialPropulsion;
            float randomized = this.quadSize * 0.75F;
            this.minSize = randomized * (2.0F + plumeHeight / 8.0F);
            this.maxSize = randomized * (3.0F + plumeHeight / 8.0F);
            this.quadSize = this.minSize;
            this.sprites = sprites;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.done && (this.yd < 0.0 || this.y > this.maxY || this.y == this.yo)) {
                this.lifetime = Math.min(this.lifetime, this.age + 5);
                this.friction = 0.0F;
                this.done = true;
            }
            double linear = Math.clamp((this.y - this.startY) / (this.maxY - this.startY), 0.0, 1.0);
            this.gravity = this.initialPropulsion * (float) Math.pow(linear, 3.0) * 0.12F;
            this.xd = linear * this.sprayX;
            this.zd = linear * this.sprayZ;
            this.setSpriteFromAge(this.sprites);
            this.quadSize = this.minSize + (float) (linear * (this.maxSize - this.minSize));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }

        record Provider(SpriteSet sprites) implements ParticleProvider<GeyserParticleOptions> {
            @Override
            public Particle createParticle(GeyserParticleOptions o, ClientLevel level, double x, double y, double z, double xa, double ya, double za) {
                var random = level.getRandom();
                return new Plume(level, x + (random.nextFloat() - 0.5F) * 0.2F, y + random.nextFloat(), z + (random.nextFloat() - 0.5F) * 0.2F,
                        xa, ya, za, o, this.sprites);
            }
        }
    }
}
