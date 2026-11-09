package dev.frosthaven.havenbackports.block;

import dev.frosthaven.havenbackports.Content;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Mojang's 26.x {@code ShelfBlockEntity} with its {@code ListBackedContainer} defaults written out (1.21.1 has no such
 * interface). 26.x drops a container's items when the block goes (BlockEntity.preRemoveSideEffects); ShelfBlock does
 * that in 1.21.1's onRemove. {@code align_items_to_bottom} is only ever read from saved data, as in 26.x.
 */
public class ShelfBlockEntity extends BlockEntity implements Container {
    public static final int MAX_ITEMS = 3;
    private static final String ALIGN_ITEMS_TO_BOTTOM_TAG = "align_items_to_bottom";
    private final NonNullList<ItemStack> items = NonNullList.withSize(MAX_ITEMS, ItemStack.EMPTY);
    private boolean alignItemsToBottom;

    public ShelfBlockEntity(BlockPos pos, BlockState state) {
        super(Content.SHELF_ENTITY.get(), pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        alignItemsToBottom = tag.getBoolean(ALIGN_ITEMS_TO_BOTTOM_TAG);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, true, registries);
        tag.putBoolean(ALIGN_ITEMS_TO_BOTTOM_TAG, alignItemsToBottom);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items, true, registries);
        tag.putBoolean(ALIGN_ITEMS_TO_BOTTOM_TAG, alignItemsToBottom);
        return tag;
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public ItemStack swapItemNoUpdate(int slot, ItemStack heldItemStack) {
        ItemStack retrievedItem = removeItemNoUpdate(slot);
        setItemNoUpdate(slot, heldItemStack);
        return retrievedItem;
    }

    public void setChanged(@Nullable Holder<GameEvent> event) {
        super.setChanged();
        if (level != null) {
            if (event != null) {
                level.gameEvent(event, worldPosition, GameEvent.Context.of(getBlockState()));
            }
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void setChanged() {
        setChanged(GameEvent.BLOCK_ACTIVATE);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
    }

    public boolean getAlignItemsToBottom() {
        return alignItemsToBottom;
    }

    // ---- ListBackedContainer (26.x) ----

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.removeItem(items, slot, getMaxStackSize());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return getItem(slot).isEmpty() || getItem(slot).getCount() < getMaxStackSize(stack);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        setItemNoUpdate(slot, stack);
        setChanged();
    }

    public void setItemNoUpdate(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }
}
