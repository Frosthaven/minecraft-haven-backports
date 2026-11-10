package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.client.BundleClient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x's AbstractContainerScreen.slotClicked tells its item slot mouse actions about every click; the bundle's
 * (BundleMouseActions.onSlotClicked) clears the selected item on a shift-click or hotbar swap. 1.21.1 has no such
 * hook and no event for it.
 */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void haven_backports$bundleClick(Slot slot, int slotId, int mouseButton, ClickType type, CallbackInfo ci) {
        BundleClient.onSlotClicked(slot, type);
    }
}
