package dev.frosthaven.havenbackports.client;

import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.bundle.BundleLogic;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;

/**
 * Mojang's 26.x ClientBundleTooltip: a 4-wide grid of up to 12 slots (the last a "+N" when more are inside), the
 * selected item highlighted and named above, and a fullness bar reading "Empty" / "Full". Sprites are Mojang's
 * (gui/sprites/container/bundle, ours where 1.21.1 lacks them). 1.21.1 forced: renderImage gets no tooltip width, so
 * the grid sits at the tooltip's left edge where 26.x centres it in a tooltip wider than 96.
 */
final class BundleTooltipRenderer implements ClientTooltipComponent {
    private static final ResourceLocation PROGRESSBAR_BORDER = sprite("bundle_progressbar_border");
    private static final ResourceLocation PROGRESSBAR_FILL = sprite("bundle_progressbar_fill");
    private static final ResourceLocation PROGRESSBAR_FULL = sprite("bundle_progressbar_full");
    private static final ResourceLocation SLOT_HIGHLIGHT_BACK = sprite("slot_highlight_back");
    private static final ResourceLocation SLOT_HIGHLIGHT_FRONT = sprite("slot_highlight_front");
    private static final ResourceLocation SLOT_BACKGROUND = sprite("slot_background");
    private static final int SLOT_SIZE = 24;
    private static final int GRID_WIDTH = 96;
    private static final int PROGRESSBAR_HEIGHT = 13;
    private static final int PROGRESSBAR_FILL_MAX = 94;
    private static final int PROGRESSBAR_MARGIN_Y = 4;
    private static final Component BUNDLE_FULL_TEXT = Component.translatable("item.minecraft.bundle.full");
    private static final Component BUNDLE_EMPTY_TEXT = Component.translatable("item.minecraft.bundle.empty");
    private static final Component BUNDLE_EMPTY_DESCRIPTION = Component.translatable("item.minecraft.bundle.empty.description");
    private final BundleContents contents;
    private final int selected;

    BundleTooltipRenderer(BundleLogic.SelectedBundleTooltip tooltip) {
        this.contents = tooltip.contents();
        this.selected = tooltip.selected();
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "container/bundle/" + name);
    }

    @Override
    public int getHeight() {
        Font font = Minecraft.getInstance().font;
        return this.contents.isEmpty() ? emptyDescriptionHeight(font) + PROGRESSBAR_HEIGHT + 8 : this.itemGridHeight() + PROGRESSBAR_HEIGHT + 8;
    }

    @Override
    public int getWidth(Font font) {
        return GRID_WIDTH;
    }

    private int itemGridHeight() {
        return this.gridSizeY() * SLOT_SIZE;
    }

    private int gridSizeY() {
        return Mth.positiveCeilDiv(Math.min(12, this.contents.size()), 4);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        Fraction weight = this.contents.weight();
        if (this.contents.isEmpty()) {
            graphics.drawWordWrap(font, BUNDLE_EMPTY_DESCRIPTION, x, y, GRID_WIDTH, -5592406);
            progressbar(x, y + emptyDescriptionHeight(font) + PROGRESSBAR_MARGIN_Y, font, graphics, Fraction.ZERO);
            return;
        }
        boolean overflowing = this.contents.size() > 12;
        List<ItemStack> shown = new ArrayList<>();
        int toShow = BundleLogic.numberOfItemsToShow(this.contents);
        for (int i = 0; i < Math.min(this.contents.size(), toShow); i++) {
            shown.add(this.contents.getItemUnsafe(i));
        }
        int xStart = x + GRID_WIDTH;
        int yStart = y + this.gridSizeY() * SLOT_SIZE;
        int slotNumber = 1;
        for (int row = 1; row <= this.gridSizeY(); row++) {
            for (int column = 1; column <= 4; column++) {
                int drawX = xStart - column * SLOT_SIZE;
                int drawY = yStart - row * SLOT_SIZE;
                if (overflowing && column * row == 1) {
                    int hidden = 0;
                    for (int i = shown.size(); i < this.contents.size(); i++) {
                        hidden += this.contents.getItemUnsafe(i).getCount();
                    }
                    graphics.drawCenteredString(font, "+" + hidden, drawX + 12, drawY + 10, -1);
                } else if (shown.size() >= slotNumber) {
                    this.slot(slotNumber, drawX, drawY, shown, font, graphics);
                    slotNumber++;
                }
            }
        }
        this.selectedItemName(font, graphics, x, y);
        progressbar(x, y + this.itemGridHeight() + PROGRESSBAR_MARGIN_Y, font, graphics, weight);
    }

    private void slot(int slotNumber, int drawX, int drawY, List<ItemStack> shown, Font font, GuiGraphics graphics) {
        int index = shown.size() - slotNumber;
        boolean highlight = index == this.selected;
        ItemStack item = shown.get(index);
        graphics.blitSprite(highlight ? SLOT_HIGHLIGHT_BACK : SLOT_BACKGROUND, drawX, drawY, SLOT_SIZE, SLOT_SIZE);
        graphics.renderItem(item, drawX + 4, drawY + 4, slotNumber);
        graphics.renderItemDecorations(font, item, drawX + 4, drawY + 4);
        if (highlight) {
            graphics.blitSprite(SLOT_HIGHLIGHT_FRONT, drawX, drawY, SLOT_SIZE, SLOT_SIZE);
        }
    }

    /** 26.x extractSelectedItemTooltip: the selected item's name in a tooltip of its own, centred over the grid. */
    private void selectedItemName(Font font, GuiGraphics graphics, int x, int y) {
        if (this.selected < 0 || this.selected >= this.contents.size()) {
            return;
        }
        ItemStack item = this.contents.getItemUnsafe(this.selected);
        // 26.x getStyledHoverName: the name in its rarity's style, italic when renamed (1.21.1's tooltip title).
        net.minecraft.network.chat.MutableComponent name = Component.empty().append(item.getHoverName()).withStyle(item.getRarity().getStyleModifier());
        if (item.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            name.withStyle(net.minecraft.ChatFormatting.ITALIC);
        }
        int textWidth = font.width(name.getVisualOrderText());
        int centre = x + GRID_WIDTH / 2 - 12;
        graphics.renderTooltip(font, List.of(name.getVisualOrderText()), centre - textWidth / 2, y - 15);
    }

    private static void progressbar(int x, int y, Font font, GuiGraphics graphics, Fraction weight) {
        boolean full = weight.compareTo(Fraction.ONE) >= 0;
        graphics.blitSprite(full ? PROGRESSBAR_FULL : PROGRESSBAR_FILL, x + 1, y, Math.clamp(Mth.mulAndTruncate(weight, PROGRESSBAR_FILL_MAX), 0, PROGRESSBAR_FILL_MAX), PROGRESSBAR_HEIGHT);
        graphics.blitSprite(PROGRESSBAR_BORDER, x, y, GRID_WIDTH, PROGRESSBAR_HEIGHT);
        Component text = weight.compareTo(Fraction.ZERO) == 0 ? BUNDLE_EMPTY_TEXT : full ? BUNDLE_FULL_TEXT : null;
        if (text != null) {
            graphics.drawCenteredString(font, text, x + 48, y + 3, -1);
        }
    }

    private static int emptyDescriptionHeight(Font font) {
        return font.split(BUNDLE_EMPTY_DESCRIPTION, GRID_WIDTH).size() * 9;
    }
}
