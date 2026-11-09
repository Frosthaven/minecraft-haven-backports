package dev.frosthaven.havenbackports.block;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;

/** Vanilla blocks whose 1.21.1 constructors are protected; Mojang's snapshot builds them directly from its package. */
public final class Access {
    private Access() {}

    public static class Bars extends IronBarsBlock {
        public Bars(Properties p) { super(p); }
    }

    public static class Torch extends TorchBlock {
        public Torch(SimpleParticleType flame, Properties p) { super(flame, p); }
    }

    public static class WallTorch extends WallTorchBlock {
        public WallTorch(SimpleParticleType flame, Properties p) { super(flame, p); }
    }
}
