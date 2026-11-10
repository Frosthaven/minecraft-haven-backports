package dev.frosthaven.havenbackports.nautilus;

import dev.frosthaven.havenbackports.Nautiluses;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Mojang's 26.x NautilusInventoryMenu with its AbstractMountInventoryMenu: a saddle slot (8, 18) and a body armor slot
 * (8, 36), both active only while the nautilus can use them (adult and tame), then the player's inventory at (8, 84).
 * 1.21.1 opens mount screens through its own horse packet, so this is a NeoForge menu type (the nautilus's entity id in
 * the open buffer). The equipment containers are 26.x createEquipmentSlotContainer: one slot over the saddle (ours)
 * and over the BODY slot.
 */
public class NautilusInventoryMenu extends AbstractContainerMenu {
    private final Container mountContainer;
    public final AbstractNautilus mount;

    public NautilusInventoryMenu(int containerId, Inventory playerInventory, AbstractNautilus nautilus) {
        super(Nautiluses.MENU.get(), containerId);
        this.mount = nautilus;
        this.mountContainer = nautilus.inventory();
        this.mountContainer.startOpen(playerInventory.player);
        this.addSlot(new Slot(new EquipmentContainer(nautilus, null), 0, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.SADDLE) && !this.hasItem() && nautilus.isSaddleable();
            }

            @Override
            public boolean isActive() {
                return nautilus.canUseMountSlots();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(new EquipmentContainer(nautilus, EquipmentSlot.BODY), 0, 8, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return nautilus.isBodyArmorItem(stack) && NautilusArmorItem.equippableBy(nautilus);
            }

            @Override
            public boolean isActive() {
                return nautilus.canUseMountSlots();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public static void open(Player player, AbstractNautilus nautilus) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inv, p) -> new NautilusInventoryMenu(id, inv, nautilus), nautilus.getDisplayName()),
                    buf -> buf.writeVarInt(nautilus.getId()));
        }
    }

    public Component title() {
        return this.mount.getDisplayName();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.mount.inventory() == this.mountContainer && this.mountContainer.stillValid(player) && this.mount.isAlive()
                && player.canInteractWithEntity(this.mount, 4.0);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.mountContainer.stopOpen(player);
    }

    /** 26.x AbstractMountInventoryMenu.quickMoveStack (slot 0 saddle, 1 body armor, then the mount's own slots: none). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            clicked = stack.copy();
            int playerStart = 2 + this.mountContainer.getContainerSize();
            if (index < playerStart) {
                if (!this.moveItemStackTo(stack, playerStart, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(1).mayPlace(stack) && !this.getSlot(1).hasItem()) {
                if (!this.moveItemStackTo(stack, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(stack) && !this.getSlot(0).hasItem()) {
                if (!this.moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.mountContainer.getContainerSize() == 0 || !this.moveItemStackTo(stack, 2, playerStart, false)) {
                int playerEnd = playerStart + 27;
                int hotbarEnd = playerEnd + 9;
                if (index >= playerEnd && index < hotbarEnd) {
                    if (!this.moveItemStackTo(stack, playerStart, playerEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= playerStart && index < playerEnd) {
                    if (!this.moveItemStackTo(stack, playerEnd, hotbarEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, playerEnd, playerEnd, false)) {
                    return ItemStack.EMPTY;
                }
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return clicked;
    }

    /** 26.x LivingEntity.createEquipmentSlotContainer: one slot over a piece of the nautilus's equipment (null = saddle). */
    private record EquipmentContainer(AbstractNautilus nautilus, EquipmentSlot slot) implements Container {
        private ItemStack get() {
            return this.slot == null ? this.nautilus.getSaddle() : this.nautilus.getItemBySlot(this.slot);
        }

        private void set(ItemStack stack) {
            if (this.slot == null) {
                if (!stack.isEmpty() && !this.nautilus.isSaddled()) {
                    this.nautilus.equipSaddle(stack, net.minecraft.sounds.SoundSource.NEUTRAL);
                } else {
                    this.nautilus.setSaddle(stack);
                }
            } else {
                this.nautilus.setItemSlot(this.slot, stack);
                if (!stack.isEmpty()) {
                    this.nautilus.setGuaranteedDrop(this.slot);
                }
            }
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return this.get().isEmpty();
        }

        @Override
        public ItemStack getItem(int index) {
            return index == 0 ? this.get() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack current = this.get();
            if (index != 0 || current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = current.copy().split(count);
            ItemStack rest = current.copy();
            rest.shrink(count);
            this.set(rest);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack current = this.get();
            this.set(ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            if (index == 0) {
                this.set(stack);
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return player.getVehicle() == this.nautilus || player.canInteractWithEntity(this.nautilus, 4.0);
        }

        @Override
        public void clearContent() {
            this.set(ItemStack.EMPTY);
        }
    }
}
