package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x's cartography table takes any item with a map id (map slot mayPlace and quickMoveStack:
 * itemStack.has(MAP_ID)); 1.21.1 hard-codes minecraft:filled_map, which would keep the explorer map items out.
 * The table's results (copies of the input) already keep the item.
 */
@Mixin(targets = {"net.minecraft.world.inventory.CartographyTableMenu", "net.minecraft.world.inventory.CartographyTableMenu$3"})
abstract class CartographyTableMenuMixin {
    @WrapOperation(method = {"quickMoveStack", "mayPlace"}, require = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean haven_backports$hasMapId(ItemStack stack, Item item, Operation<Boolean> original) {
        return item == Items.FILLED_MAP ? stack.has(DataComponents.MAP_ID) : original.call(stack, item);
    }
}
