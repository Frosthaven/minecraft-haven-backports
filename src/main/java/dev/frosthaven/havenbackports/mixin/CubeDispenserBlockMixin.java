package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.cube.CubeDispense;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x DispenserBlock.getDefaultDispenseMethod: an item with no dispenser behaviour of its own that a Sulfur Cube can
 * swallow (#sulfur_cube_swallowable, a data tag of many blocks) is fed to a cube in front of the dispenser. 1.21.1
 * hard-codes the default (DISPENSER_REGISTRY's default value); this changes only that default, only for those items.
 */
@Mixin(DispenserBlock.class)
abstract class CubeDispenserBlockMixin {
    private static final TagKey<Item> SWALLOWABLE = ItemTags.create(ResourceLocation.withDefaultNamespace("sulfur_cube_swallowable"));

    @Inject(method = "getDispenseMethod", at = @At("RETURN"), cancellable = true)
    private void haven_backports$sulfurCube(Level level, ItemStack stack, CallbackInfoReturnable<DispenseItemBehavior> cir) {
        if (!DispenserBlock.DISPENSER_REGISTRY.containsKey(stack.getItem()) && stack.is(SWALLOWABLE) && stack.isItemEnabled(level.enabledFeatures())) {
            cir.setReturnValue(CubeDispense.INSTANCE);
        }
    }
}
