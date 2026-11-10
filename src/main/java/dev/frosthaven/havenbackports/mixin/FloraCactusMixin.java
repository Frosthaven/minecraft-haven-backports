package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Flora;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x CactusBlock.randomTick, which 1.21.1 hard-codes without the cactus flower: at age 8 a cactus with air above may
 * grow a cactus flower on top (10%, 25% when it is 3 or more tall); a cactus 3 tall keeps aging to 15 (1.21.1 stops it
 * at once) and then stops. Growth of the column itself is 1.21.1's. NeoForge's crop growth hooks stay around it, as in
 * 1.21.1, so mods that gate crop growth (seasons) still gate cacti. 26.x sets its blocks with flag 260 (4 plus a flag
 * 1.21.1 lacks); 1.21.1's 4 is kept.
 */
@Mixin(CactusBlock.class)
abstract class FloraCactusMixin extends Block {
    @Shadow
    @Final
    public static IntegerProperty AGE;

    FloraCactusMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void haven_backports$randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        ci.cancel();
        BlockPos above = pos.above();
        if (!level.isEmptyBlock(above)) {
            return;
        }
        int height = 1;
        int age = state.getValue(AGE);
        while (level.getBlockState(pos.below(height)).is(this)) {
            if (++height == 3 && age == 15) {
                return;
            }
        }
        if (!CommonHooks.canCropGrow(level, above, state, true)) {
            return;
        }
        if (age == 8 && this.defaultBlockState().canSurvive(level, above)) {
            if (random.nextDouble() <= (height >= 3 ? 0.25 : 0.1)) {
                level.setBlockAndUpdate(above, Flora.CACTUS_FLOWER.get().defaultBlockState());
            }
        } else if (age == 15 && height < 3) {
            level.setBlockAndUpdate(above, this.defaultBlockState());
            BlockState reset = state.setValue(AGE, 0);
            level.setBlock(pos, reset, 4);
            level.neighborChanged(reset, above, this, pos, false);
        }
        if (age < 15) {
            level.setBlock(pos, state.setValue(AGE, age + 1), 4);
        }
        CommonHooks.fireCropGrowPost(level, pos, state);
    }
}
