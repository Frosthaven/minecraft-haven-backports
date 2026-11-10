package dev.frosthaven.havenbackports.client.ghast;

import net.minecraft.client.model.geom.ModelPart;

/**
 * 26.x MeshTransformer.scaling(f) on a mesh root (pose scaled by f, then moved down by 24.016 * (1 - f)): 1.21.1 part
 * poses carry no scale and resetPose sets it back to 1, so a model applies the root's y and scale after resetting.
 */
public record MeshScale(float y, float scale) {
    public static final MeshScale IDENTITY = new MeshScale(0.0F, 1.0F);

    public MeshScale scaling(float factor) {
        return new MeshScale(this.y * factor + 24.016F * (1.0F - factor), this.scale * factor);
    }

    public void apply(ModelPart root) {
        root.y = this.y;
        root.xScale = this.scale;
        root.yScale = this.scale;
        root.zScale = this.scale;
    }
}
