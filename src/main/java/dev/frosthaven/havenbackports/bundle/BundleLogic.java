package dev.frosthaven.havenbackports.bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.math.Fraction;

/**
 * Mojang's 26.x BundleItem, method for method, on 1.21.1's BundleContents. BundleItemMixin routes 1.21.1's BundleItem
 * (the vanilla bundle and our 16 dyed ones) here. 26.x's Mutable keeps a selected item index; here it is the
 * Bundles.SELECTED component, with the same rules: removeOne gives out the selected item (else the first) and clears
 * the selection; toggling to the same index, or one out of range, clears it.
 * 1.21.1 forced: the use animation stays NONE (26.x ItemUseAnimation.BUNDLE only adds an arm swing in first person);
 * InteractionResult.SUCCESS is 1.21.1's sidedSuccess.
 */
public final class BundleLogic {
    // ARGB.colorFromFloat(1, 1, 0.33, 0.33) and (1, 0.44, 0.53, 1): each channel floor(v * 255).
    private static final int FULL_BAR_COLOR = 0xFFFF5454;
    private static final int BAR_COLOR = 0xFF7087FF;
    private static final int TICKS_AFTER_FIRST_THROW = 10;
    private static final int TICKS_BETWEEN_THROWS = 2;
    private static final int TICKS_MAX_THROW_DURATION = 200;

    private BundleLogic() {}

    public static int selectedIndex(ItemStack stack) {
        return stack.getOrDefault(Bundles.SELECTED.get(), -1);
    }

    /** BundleItem.toggleSelectedItem / Mutable.toggleSelectedItem. */
    public static void toggleSelectedItem(ItemStack stack, int index) {
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) {
            return;
        }
        int next = selectedIndex(stack) != index && index >= 0 && index < contents.size() ? index : -1;
        if (next == -1) {
            stack.remove(Bundles.SELECTED.get());
        } else {
            stack.set(Bundles.SELECTED.get(), next);
        }
    }

    /** BundleContents.getNumberOfItemsToShow: at most 12 (11 and a "+N" when there are more). */
    public static int numberOfItemsToShow(BundleContents contents) {
        int stacks = contents.size();
        int available = stacks > 12 ? 11 : 12;
        int onNonFullRow = stacks % 4;
        int emptyOnNonFullRow = onNonFullRow == 0 ? 0 : 4 - onNonFullRow;
        return Math.min(stacks, available - emptyOnNonFullRow);
    }

    public static boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
        BundleContents initial = self.get(DataComponents.BUNDLE_CONTENTS);
        if (initial == null) {
            return false;
        }
        ItemStack other = slot.getItem();
        if (action == ClickAction.PRIMARY && !other.isEmpty()) {
            BundleContents.Mutable contents = new BundleContents.Mutable(initial);
            if (contents.tryTransfer(slot, player) > 0) {
                playInsertSound(player);
            } else {
                playInsertFailSound(player);
            }
            self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            broadcastChangesOnContainerMenu(player);
            return true;
        } else if (action == ClickAction.SECONDARY && other.isEmpty()) {
            ItemStack removed = removeOne(self, initial);
            if (removed != null) {
                ItemStack remainder = slot.safeInsert(removed);
                if (remainder.getCount() > 0) {
                    BundleContents.Mutable contents = new BundleContents.Mutable(self.get(DataComponents.BUNDLE_CONTENTS));
                    contents.tryInsert(remainder);
                    self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
                } else {
                    playRemoveOneSound(player);
                }
            }
            broadcastChangesOnContainerMenu(player);
            return true;
        }
        return false;
    }

    public static boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
        if (action == ClickAction.PRIMARY && other.isEmpty()) {
            toggleSelectedItem(self, -1);
            return false;
        }
        BundleContents initial = self.get(DataComponents.BUNDLE_CONTENTS);
        if (initial == null) {
            return false;
        }
        if (action == ClickAction.PRIMARY && !other.isEmpty()) {
            BundleContents.Mutable contents = new BundleContents.Mutable(initial);
            if (slot.allowModification(player) && contents.tryInsert(other) > 0) {
                playInsertSound(player);
            } else {
                playInsertFailSound(player);
            }
            self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            broadcastChangesOnContainerMenu(player);
            return true;
        } else if (action == ClickAction.SECONDARY && other.isEmpty()) {
            if (slot.allowModification(player)) {
                ItemStack removed = removeOne(self, initial);
                if (removed != null) {
                    playRemoveOneSound(player);
                    carried.set(removed);
                }
            }
            broadcastChangesOnContainerMenu(player);
            return true;
        }
        toggleSelectedItem(self, -1);
        return false;
    }

    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    /** onUseTick: the first item at once, then one every 2 ticks after 10, for up to 200 ticks. */
    public static void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (entity instanceof Player player) {
            boolean first = ticksRemaining == TICKS_MAX_THROW_DURATION;
            if (first || ticksRemaining < TICKS_MAX_THROW_DURATION - TICKS_AFTER_FIRST_THROW && ticksRemaining % TICKS_BETWEEN_THROWS == 0) {
                dropContent(level, player, stack);
            }
        }
    }

    public static int useDuration() {
        return TICKS_MAX_THROW_DURATION;
    }

    private static void dropContent(Level level, Player player, ItemStack bundle) {
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (contents != null && !contents.isEmpty()) {
            ItemStack removed = removeOne(bundle, contents);
            if (removed != null) {
                playRemoveOneSound(player);
                player.drop(removed, true);
                level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F,
                        0.8F + level.getRandom().nextFloat() * 0.4F);
                player.awardStat(Stats.ITEM_USED.get(bundle.getItem()));
            }
        }
    }

    /** Mutable.removeOne: the selected item if there is one, else the first; clears the selection. */
    private static ItemStack removeOne(ItemStack bundle, BundleContents contents) {
        if (contents.isEmpty()) {
            return null;
        }
        int selected = selectedIndex(bundle);
        int index = selected < 0 || selected >= contents.size() ? 0 : selected;
        List<ItemStack> items = new ArrayList<>(contents.size());
        contents.itemCopyStream().forEach(items::add);
        ItemStack removed = items.remove(index);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
        bundle.remove(Bundles.SELECTED.get());
        return removed;
    }

    public static boolean isBarVisible(ItemStack stack) {
        return stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).weight().compareTo(Fraction.ZERO) > 0;
    }

    public static int getBarWidth(ItemStack stack) {
        Fraction weight = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).weight();
        return Math.min(1 + Mth.mulAndTruncate(weight, 12), 13);
    }

    public static int getBarColor(ItemStack stack) {
        Fraction weight = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).weight();
        return weight.compareTo(Fraction.ONE) >= 0 ? FULL_BAR_COLOR : BAR_COLOR;
    }

    /** 1.21.1's hide checks; the image is 26.x's tooltip (client/BundleTooltipRenderer) with the selection. */
    public static Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        if (stack.has(DataComponents.HIDE_TOOLTIP) || stack.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(DataComponents.BUNDLE_CONTENTS)).map(c -> new SelectedBundleTooltip(c, selectedIndex(stack)));
    }

    /** The 26.x BundleTooltip: the contents and which of them is selected. */
    public record SelectedBundleTooltip(BundleContents contents, int selected) implements TooltipComponent {}

    private static void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsertFailSound(Entity entity) {
        entity.playSound(Bundles.INSERT_FAIL.get(), 1.0F, 1.0F);
    }

    private static void broadcastChangesOnContainerMenu(Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null) {
            menu.slotsChanged(player.getInventory());
        }
    }
}
