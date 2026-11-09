package dev.frosthaven.havenbackports.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * 26.x {@code ItemStackRenderState.getModelBoundingBox()} on 1.21.1: the box around every vertex an item draws in a
 * display context, in the pose the item is drawn from. 26.x gathers the same vertices from the model's quads (special
 * models: their renderer's fixed extents), memoized per model; here they are recorded from one draw, kept per baked
 * model (per item for block-entity-rendered items, whose extents 26.x fixes per renderer). Cleared on resource reload.
 */
public final class ItemBounds {
    private static final Map<Object, AABB> CACHE = new IdentityHashMap<>();
    private static final AABB EMPTY = new AABB(0, 0, 0, 0, 0, 0);   // AABB.ofSize(Vec3.ZERO, 0, 0, 0)

    private ItemBounds() {}

    static void clear() {
        CACHE.clear();
    }

    static AABB of(ItemRenderer renderer, ItemStack stack, ItemDisplayContext context, BakedModel model) {
        Object key = model.isCustomRenderer() ? stack.getItem() : model;
        AABB box = CACHE.get(key);
        if (box == null) {
            box = measure(renderer, stack, context, model);
            CACHE.put(key, box);
        }
        return box;
    }

    private static AABB measure(ItemRenderer renderer, ItemStack stack, ItemDisplayContext context, BakedModel model) {
        Recorder recorder = new Recorder();
        MultiBufferSource buffers = type -> recorder;
        renderer.render(stack, context, false, new PoseStack(), buffers, 0xF000F0, OverlayTexture.NO_OVERLAY, model);
        return recorder.any ? new AABB(recorder.minX, recorder.minY, recorder.minZ, recorder.maxX, recorder.maxY, recorder.maxZ) : EMPTY;
    }

    /** Records vertex positions; every other vertex attribute is dropped. */
    private static final class Recorder implements VertexConsumer {
        boolean any;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            any = true;
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
