package dev.frosthaven.havenbackports.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.DataMapHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x's LightningBolt.clearCopperOnLightningStrike: lightning de-ages the block it strikes when that block is
 * weathering copper (copper lightning rods are, in 26.x) and spreads from there when it is weathering or waxed
 * copper. 1.21.1 instead reset the block a rod stands on and never the rod. "Weathering" here also counts a block
 * with a next stage in NeoForge's oxidizables data map, which is how the vanilla rod ages (LightningRodBlockMixin);
 * "waxed" is NeoForge's waxables data map (26.x: HoneycombItem.WAX_OFF_BY_BLOCK). The random walk is 1.21.1's own,
 * which 26.x kept.
 */
@Mixin(LightningBolt.class)
abstract class LightningBoltMixin {
    @Shadow
    private static void randomWalkCleaningCopper(Level level, BlockPos pos, BlockPos.MutableBlockPos mutable, int steps) {
        throw new AssertionError();
    }

    @Inject(method = "clearCopperOnLightningStrike", at = @At("HEAD"), cancellable = true)
    private static void haven_backports$clearCopper(Level level, BlockPos struckPos, CallbackInfo ci) {
        ci.cancel();
        BlockState struck = level.getBlockState(struckPos);
        Block block = struck.getBlock();
        boolean waxed = DataMapHooks.getBlockUnwaxed(block) != null;
        boolean weathering = block instanceof WeatheringCopper || WeatheringCopper.getNext(block).isPresent();
        if (weathering || waxed) {
            if (weathering) {
                level.setBlockAndUpdate(struckPos, WeatheringCopper.getFirst(level.getBlockState(struckPos)));
            }
            BlockPos.MutableBlockPos work = struckPos.mutable();
            int strikes = level.random.nextInt(3) + 3;
            for (int i = 0; i < strikes; i++) {
                randomWalkCleaningCopper(level, struckPos, work, level.random.nextInt(8) + 1);
            }
        }
    }
}
