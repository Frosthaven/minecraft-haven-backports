package dev.frosthaven.havenbackports.client;

import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.bundle.BundleLogic;
import dev.frosthaven.havenbackports.bundle.Bundles;
import dev.frosthaven.havenbackports.bundle.SelectBundleItemPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client half of 26.x's bundle: BundleMouseActions (scrolling over a bundle in a container selects an item and
 * tells the server; leaving the slot, shift-click and hotbar swap clear it), ScrollWheelHandler's accumulation,
 * SlotWidget's showTooltipWithItemInHand (a bundle's tooltip still shows while an item is carried over it), the
 * tooltip factory, and the open bundle models BundleRenderer draws.
 */
public final class BundleClient {
    private static double accumulatedX;
    private static double accumulatedY;
    private static Slot lastHovered;

    private BundleClient() {}

    /** 26.x item definitions: in the GUI with a selected item, <bundle>_open_back, the item, <bundle>_open_front. */
    static ModelResourceLocation openModel(Item item, String part) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (item != Items.BUNDLE && !(id.getNamespace().equals(HavenBackports.ID) && Bundles.DYED.contains(id.getPath()))) {
            return null;
        }
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "item/" + id.getPath() + "_open_" + part));
    }

    @EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    static final class Setup {
        private Setup() {}

        @SubscribeEvent
        static void models(ModelEvent.RegisterAdditional e) {
            for (String bundle : java.util.stream.Stream.concat(java.util.stream.Stream.of("bundle"), Bundles.DYED.stream()).toList()) {
                for (String part : new String[]{"back", "front"}) {
                    e.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "item/" + bundle + "_open_" + part)));
                }
            }
        }

        @SubscribeEvent
        static void tooltips(RegisterClientTooltipComponentFactoriesEvent e) {
            e.register(BundleLogic.SelectedBundleTooltip.class, BundleTooltipRenderer::new);
        }
    }

    @EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT)
    static final class Game {
        private Game() {}

        @SubscribeEvent
        static void scrolled(ScreenEvent.MouseScrolled.Pre e) {
            if (!(e.getScreen() instanceof AbstractContainerScreen<?> screen)) {
                return;
            }
            Slot slot = screen.getSlotUnderMouse();
            if (slot != null && slot.getItem().is(Bundles.BUNDLES) && onMouseScrolled(e.getScrollDeltaX(), e.getScrollDeltaY(), slot.index, slot.getItem())) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        static void rendered(ScreenEvent.Render.Post e) {
            if (!(e.getScreen() instanceof AbstractContainerScreen<?> screen)) {
                return;
            }
            Slot hovered = screen.getSlotUnderMouse();
            if (lastHovered != null && lastHovered != hovered) {
                onStopHovering(lastHovered);
            }
            lastHovered = hovered;
            ItemStack carried = screen.getMenu().getCarried();
            if (!carried.isEmpty() && hovered != null && hovered.hasItem() && hovered.getItem().getItem() instanceof net.minecraft.world.item.BundleItem) {
                ItemStack stack = hovered.getItem();
                e.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, Screen.getTooltipFromItem(Minecraft.getInstance(), stack),
                        stack.getTooltipImage(), stack, e.getMouseX(), e.getMouseY());
            }
        }

        @SubscribeEvent
        static void closing(ScreenEvent.Closing e) {
            if (lastHovered != null) {
                onStopHovering(lastHovered);
                lastHovered = null;
            }
        }
    }

    /** BundleMouseActions.onSlotClicked (AbstractContainerScreenMixin): shift-click and hotbar swaps clear the selection. */
    public static void onSlotClicked(Slot slot, net.minecraft.world.inventory.ClickType type) {
        if (slot != null && slot.getItem().is(Bundles.BUNDLES) && (type == net.minecraft.world.inventory.ClickType.QUICK_MOVE
                || type == net.minecraft.world.inventory.ClickType.SWAP)) {
            toggle(slot.getItem(), slot.index, -1);
        }
    }

    private static void onStopHovering(Slot slot) {
        if (slot.getItem().is(Bundles.BUNDLES) && BundleLogic.selectedIndex(slot.getItem()) != -1) {
            toggle(slot.getItem(), slot.index, -1);
        }
    }

    /** BundleMouseActions.onMouseScrolled with ScrollWheelHandler.onMouseScroll / getNextScrollWheelSelection. */
    private static boolean onMouseScrolled(double scrollX, double scrollY, int slotIndex, ItemStack stack) {
        int shown = BundleLogic.numberOfItemsToShow(stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
        if (shown == 0) {
            return false;
        }
        if (accumulatedX != 0.0 && Math.signum(scrollX) != Math.signum(accumulatedX)) {
            accumulatedX = 0.0;
        }
        if (accumulatedY != 0.0 && Math.signum(scrollY) != Math.signum(accumulatedY)) {
            accumulatedY = 0.0;
        }
        accumulatedX += scrollX;
        accumulatedY += scrollY;
        int wheelX = (int) accumulatedX;
        int wheelY = (int) accumulatedY;
        accumulatedX -= wheelX;
        accumulatedY -= wheelY;
        int wheel = wheelY == 0 ? -wheelX : wheelY;
        if (wheel != 0) {
            int selected = BundleLogic.selectedIndex(stack);
            int next = selected - (int) Math.signum(wheel);
            next = Math.max(-1, next);
            while (next < 0) {
                next += shown;
            }
            while (next >= shown) {
                next -= shown;
            }
            if (selected != next) {
                toggle(stack, slotIndex, next);
            }
        }
        return true;
    }

    private static void toggle(ItemStack stack, int slotIndex, int selected) {
        if (Minecraft.getInstance().getConnection() != null
                && selected < BundleLogic.numberOfItemsToShow(stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY))) {
            BundleLogic.toggleSelectedItem(stack, selected);
            PacketDistributor.sendToServer(new SelectBundleItemPayload(slotIndex, selected));
        }
    }
}
