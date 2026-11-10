package dev.frosthaven.havenbackports.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.havenbackports.Golems;
import dev.frosthaven.havenbackports.golem.CopperGolemStatueBlock;
import dev.frosthaven.havenbackports.golem.CopperGolemStatueBlockEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Mojang's 26.x CopperGolemStatueBlockRenderer and CopperGolemStatueSpecialRenderer: one golem model per pose
 * (CopperGolemStatueModel: root at y 0, turned pi about z), the stage's golem texture, the block's facing. 26.x draws
 * the item with a special model chosen by the pose in its block_state component and the item definition's
 * transformation (translate 0.5, 1.5, 0.5; scale 1, -1, -1); 1.21.1 draws it here through a client item extension.
 */
public class CopperGolemStatueRenderer implements BlockEntityRenderer<CopperGolemStatueBlockEntity> {
    private final Map<CopperGolemStatueBlock.Pose, ModelPart> models;

    public CopperGolemStatueRenderer(BlockEntityRendererProvider.Context context) {
        this.models = bake(context.getModelSet());
    }

    static Map<CopperGolemStatueBlock.Pose, ModelPart> bake(EntityModelSet set) {
        Map<CopperGolemStatueBlock.Pose, ModelPart> m = new EnumMap<>(CopperGolemStatueBlock.Pose.class);
        m.put(CopperGolemStatueBlock.Pose.STANDING, set.bakeLayer(CopperGolemRenderer.LAYER));
        m.put(CopperGolemStatueBlock.Pose.RUNNING, set.bakeLayer(CopperGolemRenderer.RUNNING));
        m.put(CopperGolemStatueBlock.Pose.SITTING, set.bakeLayer(CopperGolemRenderer.SITTING));
        m.put(CopperGolemStatueBlock.Pose.STAR, set.bakeLayer(CopperGolemRenderer.STAR));
        return m;
    }

    @Override
    public void render(CopperGolemStatueBlockEntity statue, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = statue.getBlockState();
        Direction facing = state.getValue(CopperGolemStatueBlock.FACING);
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot()));
        draw(models.get(state.getValue(CopperGolemStatueBlock.POSE)), state.getBlock(), pose, buffers, light, overlay);
        pose.popPose();
    }

    static void draw(ModelPart model, net.minecraft.world.level.block.Block block, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        model.getAllParts().forEach(ModelPart::resetPose);
        model.y = 0.0F;
        model.zRot = (float) Math.PI;
        var stage = block instanceof CopperGolemStatueBlock s ? s.getWeatheringState() : net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED;
        model.render(pose, buffers.getBuffer(RenderType.entityCutout(Golems.level(stage).texture())), light, overlay);
    }

    public static void items(RegisterClientExtensionsEvent e) {
        Minecraft mc = Minecraft.getInstance();
        BlockEntityWithoutLevelRenderer renderer = new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()) {
            private Map<CopperGolemStatueBlock.Pose, ModelPart> models;

            @Override
            public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
                if (models == null) {
                    models = bake(mc.getEntityModels());
                }
                CopperGolemStatueBlock block = (CopperGolemStatueBlock) ((BlockItem) stack.getItem()).getBlock();
                BlockState state = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(block.defaultBlockState());
                pose.pushPose();
                pose.translate(0.5F, 1.5F, 0.5F);
                pose.scale(1.0F, -1.0F, -1.0F);
                draw(models.get(state.getValue(CopperGolemStatueBlock.POSE)), block, pose, buffers, light, overlay);
                pose.popPose();
            }

            @Override
            public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                models = null;
            }
        };
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        };
        e.registerItem(ext, Golems.STATUES.stream().map(b -> b.get().asItem()).toArray(net.minecraft.world.item.Item[]::new));
    }
}
