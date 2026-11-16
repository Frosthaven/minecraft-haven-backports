package dev.frosthaven.havenbackports.golem;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Not Mojang's: a view of another mod's chest for the copper golem. Such a chest keeps its items in a NeoForge item
 * handler, not a vanilla Container. Items go in and out through the handler, so the chest's own rules (stack limits,
 * filters) apply; there is no direct slot write.
 */
public record ItemHandlerContainer(IItemHandler handler, BlockEntity blockEntity) implements Container {
    /** Puts the stack in; returns what did not fit. */
    public ItemStack insert(ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(handler, stack, false);
    }

    @Override
    public int getContainerSize() {
        return handler.getSlots();
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return handler.getStackInSlot(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return handler.extractItem(slot, count, false);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return handler.extractItem(slot, handler.getStackInSlot(slot).getCount(), false);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        throw new UnsupportedOperationException("use insert: an item handler has no direct slot write");
    }

    @Override
    public void setChanged() {
        blockEntity.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return !blockEntity.isRemoved();
    }

    @Override
    public void clearContent() {
        throw new UnsupportedOperationException("an item handler view cannot be cleared");
    }
}
