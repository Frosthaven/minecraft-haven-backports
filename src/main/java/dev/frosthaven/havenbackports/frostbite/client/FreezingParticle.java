package dev.frosthaven.havenbackports.frostbite.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** 26.x FreezingParticle: a snowflake that drifts down spinning (the spin slows), lives 10 to 144 ticks, and stays 20
 *  ticks once it lands; opaque layer. */
public class FreezingParticle extends TextureSheetParticle {
    private final float rotSpeed;
    private boolean landed;

    FreezingParticle(ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux) {
        super(level, x, y, z, xAux, yAux, zAux);
        this.xd = (this.random.nextFloat() * 2.0F - 1.0F) * 0.01F;
        this.yd = (this.random.nextFloat() * 2.0F - 1.0F) * 0.01F * 0.25F;
        this.zd = (this.random.nextFloat() * 2.0F - 1.0F) * 0.01F;
        this.gravity = 0.125F;
        this.friction = 1.0F;
        this.quadSize *= 0.80999994F;
        int baseLifetime = (int) (32.0 / (this.random.nextFloat() * 0.8 + 0.2));
        this.lifetime = (int) Math.max(baseLifetime * 0.9F, 10.0F);
        this.rotSpeed = (this.random.nextFloat() - 0.5F) * 0.1F;
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.oRoll = this.roll;
            this.roll = this.roll + (float) Math.PI * this.rotSpeed * 2.0F * (float) Math.exp(this.age / -8.0F);
            if (this.onGround && !this.landed) {
                this.lifetime = this.age + 20;
                this.landed = true;
            }
            this.move(this.xd, this.yd, this.zd);
            this.yd -= 0.003F;
            this.yd = Math.max(this.yd, -0.14F);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux) {
            FreezingParticle particle = new FreezingParticle(level, x, y, z, xAux, yAux, zAux);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
