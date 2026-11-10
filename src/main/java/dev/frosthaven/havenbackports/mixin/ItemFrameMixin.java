package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x's ItemFrame.interact refuses a map whose saved data tracks over 256 frames for any item with map data;
 * 1.21.1 checks minecraft:filled_map only. Explorer map items get the same limit.
 */
@Mixin(ItemFrame.class)
abstract class ItemFrameMixin {
    @WrapOperation(method = "interact", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean haven_backports$anyMap(ItemStack stack, Item item, Operation<Boolean> original) {
        return item == Items.FILLED_MAP ? stack.has(DataComponents.MAP_ID) : original.call(stack, item);
    }
}
