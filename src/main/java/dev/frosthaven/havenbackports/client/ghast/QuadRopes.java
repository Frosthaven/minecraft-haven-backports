package dev.frosthaven.havenbackports.client.ghast;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.frosthaven.havenbackports.entity.QuadLeash;
import dev.frosthaven.havenbackports.ghast.HappyGhast;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The four ropes of 26.x's quad leash: EntityRenderer.extractRenderState's quad branch (offsets from
 * getQuadLeashOffsets and the ghast's getQuadLeashHolderOffsets, turned by each one's body yaw) drawn as
 * LeashFeatureRenderer draws them (24 steps, width 0.05, straight: slack false). 1.21.1's single rope stays for the rest.
 */
public final class QuadRopes {
    private QuadRopes() {}

    public static void render(Entity entity, HappyGhast holder, float partialTick, PoseStack pose, MultiBufferSource buffers,
                              int startBlock, int endBlock, int startSky, int endSky) {
        float entityYRot = entity.getPreciseBodyRotation(partialTick) * (float) (Math.PI / 180.0);
        float holderYRot = holder.getPreciseBodyRotation(partialTick) * (float) (Math.PI / 180.0);
        Vec3[] own = QuadLeash.offsets(entity), held = holder.getQuadLeashHolderOffsets();
        Vec3 entityPos = entity.getPosition(partialTick), holderPos = holder.getPosition(partialTick);
        VertexConsumer builder = buffers.getBuffer(RenderType.leash());
        for (int i = 0; i < 4; i++) {
            Vec3 offset = own[i].yRot(-entityYRot);
            Vec3 start = entityPos.add(offset), end = holderPos.add(held[i].yRot(-holderYRot));
            pose.pushPose();
            pose.translate(offset.x, offset.y, offset.z);
            Matrix4f matrix = pose.last().pose();
            float dx = (float) (end.x - start.x), dy = (float) (end.y - start.y), dz = (float) (end.z - start.z);
            float factor = Mth.invSqrt(dx * dx + dz * dz) * 0.05F / 2.0F;
            float dxOff = dz * factor, dzOff = dx * factor;
            for (int k = 0; k <= 24; k++) {
                vertexPair(builder, matrix, dx, dy, dz, 0.05F, dxOff, dzOff, k, false, startBlock, endBlock, startSky, endSky);
            }
            for (int k = 24; k >= 0; k--) {
                vertexPair(builder, matrix, dx, dy, dz, 0.0F, dxOff, dzOff, k, true, startBlock, endBlock, startSky, endSky);
            }
            pose.popPose();
        }
    }

    private static void vertexPair(VertexConsumer builder, Matrix4f pose, float dx, float dy, float dz, float fudge, float dxOff, float dzOff,
                                   int k, boolean backwards, int startBlock, int endBlock, int startSky, int endSky) {
        float progress = k / 24.0F;
        int light = LightTexture.pack((int) Mth.lerp(progress, startBlock, endBlock), (int) Mth.lerp(progress, startSky, endSky));
        float modifier = k % 2 == (backwards ? 1 : 0) ? 0.7F : 1.0F;
        float r = 0.5F * modifier, g = 0.4F * modifier, b = 0.3F * modifier;
        float x = dx * progress, y = dy * progress, z = dz * progress;
        builder.addVertex(pose, x - dxOff, y + fudge, z + dzOff).setColor(r, g, b, 1.0F).setLight(light);
        builder.addVertex(pose, x + dxOff, y + 0.05F - fudge, z - dzOff).setColor(r, g, b, 1.0F).setLight(light);
    }
}
