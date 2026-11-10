package dev.frosthaven.havenbackports.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 26.x FallingParticle / FallingLeavesParticle on 1.21.1's TextureSheetParticle (26.x SingleQuadParticle with the
 * OPAQUE layer = PARTICLE_SHEET_OPAQUE). 1.21.1 has only CherryParticle, with the cherry values fixed in code; this
 * takes 26.x's parameters per provider (Poplar, PaleOak: FallingLeavesParticle's own values).
 */
public class FallingLeavesParticle extends TextureSheetParticle {
    private float rotSpeed = (float) Math.toRadians(this.random.nextBoolean() ? -30.0 : 30.0);
    private final float spinAcceleration = (float) Math.toRadians(this.random.nextBoolean() ? -5.0 : 5.0);
    private final float windBig;
    private final boolean swirl;
    private final boolean flowAway;
    private final double xaFlowScale;
    private final double zaFlowScale;
    private final double swirlPeriod;

    protected FallingLeavesParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, float fallAcceleration,
            float sideAcceleration, boolean swirl, boolean flowAway, float scale, float startVelocity) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.windBig = sideAcceleration;
        this.swirl = swirl;
        this.flowAway = flowAway;
        this.lifetime = 300;
        this.gravity = fallAcceleration * 1.2F * 0.0025F;
        float size = scale * (this.random.nextBoolean() ? 0.05F : 0.075F);
        this.quadSize = size;
        this.setSize(size, size);
        this.friction = 1.0F;
        this.yd = -startVelocity;
        float particleRandom = this.random.nextFloat();
        this.xaFlowScale = Math.cos(Math.toRadians(particleRandom * 60.0F)) * this.windBig;
        this.zaFlowScale = Math.sin(Math.toRadians(particleRandom * 60.0F)) * this.windBig;
        this.swirlPeriod = Math.toRadians(1000.0F + particleRandom * 3000.0F);
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
        if (this.lifetime-- <= 0) {
            this.remove();
        }
        if (this.removed) {
            return;
        }
        float aliveTicks = 300 - this.lifetime;
        float relativeAge = Math.min(aliveTicks / 300.0F, 1.0F);
        double xa = 0.0;
        double za = 0.0;
        if (this.flowAway) {
            xa += this.xaFlowScale * Math.pow(relativeAge, 1.25);
            za += this.zaFlowScale * Math.pow(relativeAge, 1.25);
        }
        if (this.swirl) {
            xa += relativeAge * Math.cos(relativeAge * this.swirlPeriod) * this.windBig;
            za += relativeAge * Math.sin(relativeAge * this.swirlPeriod) * this.windBig;
        }
        this.xd += xa * 0.0025F;
        this.zd += za * 0.0025F;
        this.yd = this.yd - this.gravity;
        this.rotSpeed = this.rotSpeed + this.spinAcceleration / 20.0F;
        this.oRoll = this.roll;
        this.roll = this.roll + this.rotSpeed / 20.0F;
        this.move(this.xd, this.yd, this.zd);
        if (this.onGround || this.lifetime < 299 && (this.xd == 0.0 || this.zd == 0.0)) {
            this.remove();
        }
        if (!this.removed) {
            this.xd = this.xd * this.friction;
            this.yd = this.yd * this.friction;
            this.zd = this.zd * this.friction;
            this.age++;
        }
    }

    /** FallingLeavesParticle.PaleOakProvider: (0.25, 2.0, no swirl, flow away, scale 1.0, start velocity 0). */
    public record PaleOakProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new FallingLeavesParticle(level, x, y, z, this.sprites, 0.25F, 2.0F, false, true, 1.0F, 0.0F);
        }
    }

    /** FallingLeavesParticle.PoplarProvider: (0.07, 10.0, swirl, no flow away, scale 2.0, start velocity 0.021). */
    public record PoplarProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new FallingLeavesParticle(level, x, y, z, this.sprites, 0.07F, 10.0F, true, false, 2.0F, 0.021F);
        }
    }
}
