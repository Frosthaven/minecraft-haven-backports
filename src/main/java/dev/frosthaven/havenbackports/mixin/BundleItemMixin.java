package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.bundle.BundleLogic;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x's BundleItem on 1.21.1's (the vanilla bundle and the dyed ones, all BundleItem): each method 26.x changed runs
 * BundleLogic, Mojang's 26.x code, instead. The selected item, the insert-fail sound, dropping one item at a time
 * while use is held (onUseTick, 200 ticks) instead of all at once, 26.x's bar colors and tooltip, and no
 * "fullness" line (26.x shows the bar in the tooltip). onDestroyed is the same in both and stays 1.21.1's.
 */
@Mixin(BundleItem.class)
abstract class BundleItemMixin extends Item {
    BundleItemMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
    private void haven_backports$stackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(BundleLogic.overrideStackedOnOther(stack, slot, action, player));
    }

    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    private void haven_backports$otherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access,
                                                 CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(BundleLogic.overrideOtherStackedOnMe(stack, other, slot, action, player, access));
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void haven_backports$use(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        cir.setReturnValue(BundleLogic.use(level, player, hand));
    }

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void haven_backports$barVisible(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(BundleLogic.isBarVisible(stack));
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void haven_backports$barWidth(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(BundleLogic.getBarWidth(stack));
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    private void haven_backports$barColor(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(BundleLogic.getBarColor(stack));
    }

    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void haven_backports$tooltipImage(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        cir.setReturnValue(BundleLogic.getTooltipImage(stack));
    }

    @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
    private void haven_backports$noFullness(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag, CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        BundleLogic.onUseTick(level, entity, stack, remainingUseDuration);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return BundleLogic.useDuration();
    }
}
