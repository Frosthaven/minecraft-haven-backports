package dev.frosthaven.havenbackports.client;

import dev.frosthaven.havenbackports.Sulfur;
import dev.frosthaven.havenbackports.block.PotentSulfurBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** 26.x NoxiousGasParticle, NoxiousGasCloudParticle and SulfurBubbleParticle (26.x TRANSLUCENT layer is 1.21.1's
 *  PARTICLE_SHEET_TRANSLUCENT, OPAQUE is PARTICLE_SHEET_OPAQUE). */
final class GasParticles {
    private GasParticles() {}

    static final class Gas extends BaseAshSmokeParticle {
        private final float fadeOutStartingPoint;

        Gas(ClientLevel level, double x, double y, double z, double xa, double ya, double za, float scale, SpriteSet sprites) {
            super(level, x, y, z, 0.1F, 0.1F, 0.1F, xa, ya, za, scale, sprites, 0.3F, 5, -0.02F, true);
            this.rCol = 1.0F;
            this.gCol = 1.0F;
            this.bCol = 1.0F;
            this.lifetime = (int) (6.0 / (this.random.nextFloat() * 0.5 + 0.5) * scale);
            this.fadeOutStartingPoint = this.lifetime / 2.0F;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.age > this.fadeOutStartingPoint) {
                this.setAlpha((this.lifetime - (this.age - this.fadeOutStartingPoint)) / this.lifetime);
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double xa, double ya, double za) {
                return new Gas(level, x, y, z, xa, ya, za, 3.0F, this.sprites);
            }
        }
    }

    /** Emits noxious gas every 2 ticks for 20 ticks where the gas reaches (PotentSulfurBlockEntity). */
    static final class Cloud extends NoRenderParticle {
        Cloud(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
            this.lifetime = 20;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.age % 2 == 0) {
                BlockPos source = BlockPos.containing(this.x, this.y, this.z);
                RandomSource random = this.level.getRandom();
                Vec3 dir = new Vec3(random.nextFloat() - 0.5F, 0.0, random.nextFloat() - 0.5F).normalize();
                float distance = random.nextFloat() * 3.0F;
                Vec3 p = Vec3.atCenterOf(source).add(dir.scale(distance)).subtract(0.0, 0.25, 0.0);
                if (PotentSulfurBlockEntity.canBeReachedByNoxiousGas(this.level, source, p)) {
                    this.level.addAlwaysVisibleParticle(Sulfur.NOXIOUS_GAS.get(), p.x, p.y, p.z, 0.0, 0.0, 0.0);
                }
            }
        }

        static ParticleProvider<SimpleParticleType> provider() {
            return (t, level, x, y, z, xa, ya, za) -> new Cloud(level, x, y, z);
        }
    }

    static final class Bubble extends TextureSheetParticle {
        private final double yStart;
        private final double yEnd;
        private final float sizeStart;
        private double yPrev;

        Bubble(ClientLevel level, double x, double y, double z, double xa, double za, SpriteSet sprites) {
            super(level, x, y, z);
            this.pickSprite(sprites);
            this.gravity = -0.04F;
            this.friction = 0.85F;
            this.setSize(0.02F, 0.02F);
            this.xd = xa * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
            this.zd = za * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
            this.sizeStart = 0.02F + 0.02F * this.random.nextFloat();
            this.quadSize = this.sizeStart;
            this.lifetime = Integer.MAX_VALUE;
            this.yStart = this.yo;
            this.yEnd = this.yo + 4.0 - 1.0;
            this.yPrev = y;
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.removed && !this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).isSourceOfType(Fluids.WATER)) {
                this.remove();
            }
            if (!this.removed && this.y >= this.yEnd) {
                this.remove();
            }
            if (!this.removed && this.y <= this.yPrev) {
                this.remove();
            }
            this.xd = this.xd + wiggle();
            this.zd = this.zd + wiggle();
            this.move(this.xd, 0.0, this.zd);
            float progress = (float) ((this.y - this.yStart) / (this.yEnd - this.yStart));
            this.quadSize = this.sizeStart + progress * (0.15F - this.sizeStart);
            this.yPrev = this.y;
        }

        private double wiggle() {
            return this.random.nextFloat() * 0.003F * (this.random.nextBoolean() ? 1 : -1) * 0.5;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }

        record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double xa, double ya, double za) {
                return new Bubble(level, x, y, z, xa, ya, this.sprites);
            }
        }
    }
}
