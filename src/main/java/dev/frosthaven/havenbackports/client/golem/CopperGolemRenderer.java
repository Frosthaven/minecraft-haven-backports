package dev.frosthaven.havenbackports.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.havenbackports.Golems;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.golem.CopperGolem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mojang's 26.x CopperGolemRenderer: texture per stage, emissive eyes (LivingEntityEmissiveLayer with the eyes render
 * type and the stage's eye texture), the held item, a block item on the antenna (BlockDecorationLayer: the antenna
 * transform, then the block turned over about its centre) and a worn head item.
 */
public class CopperGolemRenderer extends MobRenderer<CopperGolem, CopperGolemModel> {
    public static final ModelLayerLocation LAYER = layer("copper_golem"), RUNNING = layer("copper_golem_running"),
            SITTING = layer("copper_golem_sitting"), STAR = layer("copper_golem_star");

    public CopperGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new CopperGolemModel(context.bakeLayer(LAYER)), 0.5F);
        this.addLayer(new EyesLayer(this));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new AntennaBlockLayer(this));
        this.addLayer(new CopperGolemHeadLayer(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, name), "main");
    }

    @Override
    public ResourceLocation getTextureLocation(CopperGolem golem) {
        return Golems.level(golem.getWeatherState()).texture();
    }

    static final class EyesLayer extends RenderLayer<CopperGolem, CopperGolemModel> {
        EyesLayer(RenderLayerParent<CopperGolem, CopperGolemModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CopperGolem golem, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            this.getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(Golems.level(golem.getWeatherState()).eyeTexture())),
                    15728640, OverlayTexture.NO_OVERLAY);
        }
    }

    static final class AntennaBlockLayer extends RenderLayer<CopperGolem, CopperGolemModel> {
        AntennaBlockLayer(RenderLayerParent<CopperGolem, CopperGolemModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CopperGolem golem, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack antenna = golem.getAntenna();
            if (antenna.getItem() instanceof BlockItem item) {
                BlockState state = antenna.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(item.getBlock().defaultBlockState());
                pose.pushPose();
                this.getParentModel().applyBlockOnAntennaTransform(pose);
                pose.translate(-0.5F, 0.0F, -0.5F);   // UNIT_CUBE_BOTTOM_CENTER_TO_ANTENNA_CENTER
                pose.rotateAround(Axis.ZP.rotationDegrees(180.0F), 0.5F, 0.5F, 0.5F);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
    }
}
