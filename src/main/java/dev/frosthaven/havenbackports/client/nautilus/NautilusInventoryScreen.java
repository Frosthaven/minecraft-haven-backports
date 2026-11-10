package dev.frosthaven.havenbackports.client.nautilus;

import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.nautilus.NautilusInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Mojang's 26.x NautilusInventoryScreen with AbstractMountInventoryScreen: gui/container/nautilus.png, the slot sprite
 * under the saddle and armor slots while the nautilus can use them, the nautilus following the mouse. 26.x slots carry
 * their empty-slot icons as GUI sprites (container/slot/saddle, nautilus_armor_inventory); 1.21.1 slots take a block
 * atlas icon, so the screen draws those sprites under an empty slot.
 */
public class NautilusInventoryScreen extends AbstractContainerScreen<NautilusInventoryMenu> {
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private static final ResourceLocation BACKGROUND = id("textures/gui/container/nautilus.png");
    private static final ResourceLocation SADDLE_ICON = id("container/slot/saddle"), ARMOR_ICON = id("container/slot/nautilus_armor_inventory");
    private float xMouse;
    private float yMouse;

    public NautilusInventoryScreen(NautilusInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, path);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(BACKGROUND, xo, yo, 0, 0, this.imageWidth, this.imageHeight);
        if (this.menu.mount.canUseMountSlots()) {
            graphics.blitSprite(SLOT, xo + 7, yo + 35 - 18, 18, 18);
            graphics.blitSprite(SLOT, xo + 7, yo + 35, 18, 18);
            if (!this.menu.getSlot(0).hasItem()) {
                graphics.blitSprite(SADDLE_ICON, xo + 8, yo + 18, 16, 16);
            }
            if (!this.menu.getSlot(1).hasItem()) {
                graphics.blitSprite(ARMOR_ICON, xo + 8, yo + 36, 16, 16);
            }
        }
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, xo + 26, yo + 18, xo + 78, yo + 70, 17, 0.25F, this.xMouse, this.yMouse, this.menu.mount);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.xMouse = mouseX;
        this.yMouse = mouseY;
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
