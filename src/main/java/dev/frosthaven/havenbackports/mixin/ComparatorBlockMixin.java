package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.frosthaven.havenbackports.block.ShelfBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x comparators tell the block they read which of its sides they touch (getAnalogOutputSignal(level, pos,
 * direction.getOpposite()), direct and through a solid block); a shelf answers only from its back. 1.21.1's call has no
 * side, so for shelves this passes the comparator's own, as 26.x does.
 */
@Mixin(ComparatorBlock.class)
abstract class ComparatorBlockMixin {
    @WrapOperation(method = "getInputSignal",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getAnalogOutputSignal(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I"))
    private int haven_backports$shelfSide(BlockState target, Level level, BlockPos pos, Operation<Integer> original,
                                         @Local(argsOnly = true) BlockState comparator) {
        if (target.getBlock() instanceof ShelfBlock shelf) {
            return shelf.getAnalogOutputSignal(target, level, pos, comparator.getValue(ComparatorBlock.FACING).getOpposite());
        }
        return original.call(target, level, pos);
    }
}
