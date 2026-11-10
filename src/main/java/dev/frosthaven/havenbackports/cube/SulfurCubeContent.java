package dev.frosthaven.havenbackports.cube;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * 26.x SulfurCubeContent data component: the block a bucketed Sulfur Cube holds. 26.x keeps an ItemStackTemplate;
 * 1.21.1 has none, so the stack itself (one item, the same data). Its tooltip line is the bucket's
 * (SulfurCubeBucketItem): 1.21.1 shows tooltips only of its own components.
 */
public record SulfurCubeContent(ItemStack absorbedBlockItemStack) {
    public static final Codec<SulfurCubeContent> CODEC = ItemStack.CODEC.xmap(SulfurCubeContent::new, SulfurCubeContent::absorbedBlockItemStack);
    public static final StreamCodec<RegistryFriendlyByteBuf, SulfurCubeContent> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(SulfurCubeContent::new, SulfurCubeContent::absorbedBlockItemStack);

    public static SulfurCubeContent ofNonEmpty(ItemStack stack) {
        return new SulfurCubeContent(stack.copyWithCount(1));
    }

    public void addToTooltip(Consumer<Component> out) {
        out.accept(Component.translatable("entity.haven_backports.sulfur_cube.content", this.absorbedBlockItemStack.getHoverName())
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SulfurCubeContent c && ItemStack.matches(this.absorbedBlockItemStack, c.absorbedBlockItemStack);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(this.absorbedBlockItemStack);
    }
}
