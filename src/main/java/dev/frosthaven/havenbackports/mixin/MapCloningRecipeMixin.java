package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.map.ExplorerMaps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.MapCloningRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x's map_cloning recipe copies any #minecraft:clonable_maps item (crafting_transmute, input #clonable_maps:
 * filled_map and every explorer map); 1.21.1's MapCloningRecipe hard-codes minecraft:filled_map. Only that check
 * changes; the recipe and its result (a copy of the map) stay 1.21.1's.
 */
@Mixin(MapCloningRecipe.class)
abstract class MapCloningRecipeMixin {
    @WrapOperation(method = {"matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean haven_backports$clonable(ItemStack stack, Item item, Operation<Boolean> original) {
        return item == Items.FILLED_MAP ? stack.is(ExplorerMaps.CLONABLE_MAPS) : original.call(stack, item);
    }
}
