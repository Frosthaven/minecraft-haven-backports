package dev.frosthaven.havenbackports.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.bundle.BundleLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * 26.x's bundle item definition in the GUI with a selected item: a composite of <bundle>_open_back, the selected item
 * (26.x BundleSelectedItemSpecialRenderer: its own GUI layers) and <bundle>_open_front; the open models' GUI
 * transforms move them 16 back and 16 forward, so the item sits between. 1.21.1 has no item definitions, so
 * ItemRendererBundleMixin calls this for a bundle drawn in the GUI. Each layer is drawn with its own lighting (flat
 * for the bundle, 3D for a block item), as 26.x's item layers carry their own.
 */
public final class BundleRenderer {
    private static boolean drawing;

    private BundleRenderer() {}

    /** True when it drew the open bundle (the caller then skips the closed one). */
    public static boolean render(ItemRenderer renderer, ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (drawing || context != ItemDisplayContext.GUI) {
            return false;
        }
        int selected = BundleLogic.selectedIndex(stack);
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        ModelResourceLocation backId = BundleClient.openModel(stack.getItem(), "back");
        if (selected < 0 || contents == null || selected >= contents.size() || backId == null) {
            return false;
        }
        ModelManager models = Minecraft.getInstance().getModelManager();
        BakedModel back = models.getModel(backId);
        BakedModel front = models.getModel(BundleClient.openModel(stack.getItem(), "front"));
        if (back == models.getMissingModel() || front == models.getMissingModel()) {
            return false;
        }
        ItemStack item = contents.getItemUnsafe(selected);
        BakedModel itemModel = renderer.getModel(item, Minecraft.getInstance().level, null, 0);
        drawing = true;
        try {
            renderer.render(stack, context, leftHand, pose, buffers, light, overlay, back);
            flush(buffers);
            if (itemModel.usesBlockLight()) {
                Lighting.setupFor3DItems();
            }
            renderer.render(item, context, leftHand, pose, buffers, light, overlay, itemModel);
            flush(buffers);
            if (itemModel.usesBlockLight()) {
                Lighting.setupForFlatItems();
            }
            renderer.render(stack, context, leftHand, pose, buffers, light, overlay, front);
        } finally {
            drawing = false;
        }
        return true;
    }

    private static void flush(MultiBufferSource buffers) {
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
    }
}
