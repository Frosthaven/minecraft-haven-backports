package dev.frosthaven.havenbackports.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.havenbackports.OnShelf;
import dev.frosthaven.havenbackports.block.ShelfBlock;
import dev.frosthaven.havenbackports.block.ShelfBlockEntity;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mojang's 26.x {@code ShelfRenderer}: each item at a quarter size, in its slot (5 pixels apart), turned to the shelf's
 * facing and drawn in the ON_SHELF display context; centred on the shelf's middle by its model box, or standing on the
 * bottom lip when the block entity says {@code align_items_to_bottom}. 26.x's extract / submit split is one render call.
 *
 * <p>26.x resolves item models with the shelf as their ItemOwner (centre of the block, facing turned around), so a
 * compass points at its target and a clock reads the time. 1.21.1's compass and clock read the item's entity
 * representation instead (as for item frames): a marker entity with that position and rotation stands in while drawing.
 */
public class ShelfRenderer implements BlockEntityRenderer<ShelfBlockEntity> {
    private static final float ITEM_SIZE = 0.25F;
    private static final float ALIGN_ITEMS_TO_BOTTOM = -0.25F;
    private final ItemRenderer itemRenderer;
    private Entity owner;

    public ShelfRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    /** 26.x ShelfBlockEntity as ItemOwner: position() = Vec3.atCenterOf(pos), getVisualRotationYInDegrees() = facing
     *  turned around. One marker per level, moved to each shelf as it is drawn. */
    private Entity owner(Level level, Vec3 position, float yRot) {
        if (owner == null || owner.level() != level) {
            owner = EntityType.MARKER.create(level);
        }
        owner.setPos(position);
        owner.setYRot(yRot);
        return owner;
    }

    @Override
    public void render(ShelfBlockEntity shelf, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = shelf.getBlockState().getValue(ShelfBlock.FACING);
        float yRot = facing.getAxis().isHorizontal() ? -facing.toYRot() : 180.0F;
        boolean alignToBottom = shelf.getAlignItemsToBottom();
        NonNullList<ItemStack> items = shelf.getItems();
        int seed = HashCommon.long2int(shelf.getBlockPos().asLong());
        ItemDisplayContext onShelf = OnShelf.context();
        Entity owner = owner(shelf.getLevel(), Vec3.atCenterOf(shelf.getBlockPos()), facing.getOpposite().toYRot());
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            stack.setEntityRepresentation(owner);
            BakedModel model = itemRenderer.getModel(stack, shelf.getLevel(), null, seed + slot);
            AABB box = ItemBounds.of(itemRenderer, stack, onShelf, model);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
            poseStack.translate((slot - 1) * 0.3125F, alignToBottom ? ALIGN_ITEMS_TO_BOTTOM : 0.0F, -0.25F);
            poseStack.scale(ITEM_SIZE, ITEM_SIZE, ITEM_SIZE);
            double offsetY = -box.minY;
            if (!alignToBottom) {
                offsetY += -(box.maxY - box.minY) / 2.0;
            }
            poseStack.translate(0.0, offsetY, 0.0);
            itemRenderer.render(stack, onShelf, false, poseStack, buffers, light, OverlayTexture.NO_OVERLAY, model);
            stack.setEntityRepresentation(null);
            poseStack.popPose();
        }
    }
}
