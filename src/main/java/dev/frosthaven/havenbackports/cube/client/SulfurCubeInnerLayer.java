package dev.frosthaven.havenbackports.cube.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.cube.SulfurCube;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.x SulfurCubeInnerLayer: the absorbed block (flashing white while lit, as primed TNT), or the inner cube. 26.x
 * resolves the block model with BlockModelResolver; 1.21.1 draws the block state with BlockRenderDispatcher
 * .renderSingleBlock, which TNT's renderer uses for the same flash.
 */
public class SulfurCubeInnerLayer extends RenderLayer<SulfurCube, SulfurCubeModel> {
    private static final ResourceLocation INNER = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/sulfur_cube/sulfur_cube_inner.png");
    private static final ResourceLocation INNER_SMALL =
            ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/sulfur_cube/sulfur_cube_inner_small.png");
    private final SulfurCubeModel normalModel;
    private final SulfurCubeModel smallModel;
    private final BlockRenderDispatcher blocks;

    public SulfurCubeInnerLayer(RenderLayerParent<SulfurCube, SulfurCubeModel> parent, EntityModelSet models, BlockRenderDispatcher blocks) {
        super(parent);
        this.normalModel = new SulfurCubeModel(models.bakeLayer(CubeClient.INNER));
        this.smallModel = new SulfurCubeModel(models.bakeLayer(CubeClient.SMALL_INNER));
        this.blocks = blocks;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, SulfurCube entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        float fuse = SulfurCubeRenderer.fuseRemaining(entity, partialTicks);
        boolean lit = fuse >= 0.0F && (int) (fuse / 5.0F) % 2 == 0;   // 26.x TntRenderer.isLit
        int overlay = fuse > 0.0F && lit ? OverlayTexture.pack(OverlayTexture.u(1.0F), 10) : LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        ItemStack contained = entity.getItemBySlot(EquipmentSlot.BODY);
        if (!contained.isEmpty()) {
            BlockState state = contained.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                    .apply(Block.byItem(contained.getItem()).defaultBlockState());
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            if (entity.isBaby()) {
                poseStack.scale(0.5F, 0.5F, 0.5F);
            }
            poseStack.translate(-0.5F, -0.518F, -0.5F);
            this.blocks.renderSingleBlock(state, poseStack, buffer, light, overlay);
            poseStack.popPose();
        } else if (!entity.isInvisible()) {
            SulfurCubeModel model = entity.isBaby() ? this.smallModel : this.normalModel;
            model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucent(entity.isBaby() ? INNER_SMALL : INNER)), light, overlay);
        }
    }
}
