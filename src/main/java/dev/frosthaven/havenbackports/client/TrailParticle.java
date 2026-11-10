package dev.frosthaven.havenbackports.client;

import dev.frosthaven.havenbackports.particle.TrailParticleOption;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 26.x TrailParticle: moves towards its target so it arrives as its life ends; full brightness; color varied by
 *  up to 12.5% per channel. */
public class TrailParticle extends TextureSheetParticle {
    private final Vec3 target;

    TrailParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, Vec3 target, int color) {
        super(level, x, y, z, xa, ya, za);
        int r = Math.min(255, (int) (FastColor.ARGB32.red(color) * (0.875F + this.random.nextFloat() * 0.25F)));
        int g = Math.min(255, (int) (FastColor.ARGB32.green(color) * (0.875F + this.random.nextFloat() * 0.25F)));
        int b = Math.min(255, (int) (FastColor.ARGB32.blue(color) * (0.875F + this.random.nextFloat() * 0.25F)));
        this.rCol = r / 255.0F;
        this.gCol = g / 255.0F;
        this.bCol = b / 255.0F;
        this.quadSize = 0.26F;
        this.target = target;
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
            double alpha = 1.0 / (this.lifetime - this.age);
            this.x = Mth.lerp(alpha, this.x, this.target.x());
            this.y = Mth.lerp(alpha, this.y, this.target.y());
            this.z = Mth.lerp(alpha, this.z, this.target.z());
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<TrailParticleOption> {
        @Override
        public Particle createParticle(TrailParticleOption o, ClientLevel level, double x, double y, double z, double xa, double ya, double za) {
            TrailParticle p = new TrailParticle(level, x, y, z, xa, ya, za, o.target(), o.color());
            p.pickSprite(this.sprites);
            p.setLifetime(o.duration());
            return p;
        }
    }
}
