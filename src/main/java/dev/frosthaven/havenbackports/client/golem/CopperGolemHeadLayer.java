package dev.frosthaven.havenbackports.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.golem.CopperGolem;
import java.util.Map;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;

/**
 * 1.21.1's CustomHeadLayer for the copper golem (a carved pumpkin or skull it wears), placed where 26.x's
 * CustomHeadLayer puts it: root, then the golem's translateToHead (body, head, up 0.125, scaled 1.0625). 1.21.1's layer
 * only asks the model for its head part, so it cannot reach the golem's own head transform.
 */
final class CopperGolemHeadLayer extends RenderLayer<CopperGolem, CopperGolemModel> {
    private final Map<SkullBlock.Type, SkullModelBase> skullModels;
    private final ItemInHandRenderer itemInHandRenderer;

    CopperGolemHeadLayer(RenderLayerParent<CopperGolem, CopperGolemModel> parent, EntityModelSet models, ItemInHandRenderer itemInHandRenderer) {
        super(parent);
        this.skullModels = SkullBlockRenderer.createSkullRenderers(models);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, CopperGolem golem, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = golem.getItemBySlot(EquipmentSlot.HEAD);
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        this.getParentModel().translateToHead(pose);
        if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof AbstractSkullBlock skull) {
            pose.scale(1.1875F, -1.1875F, -1.1875F);
            pose.translate(-0.5, 0.0, -0.5);
            SkullBlock.Type type = skull.getType();
            RenderType renderType = SkullBlockRenderer.getRenderType(type, stack.get(DataComponents.PROFILE));
            SkullBlockRenderer.renderSkull(null, 180.0F, golem.walkAnimation.position(partialTicks), pose, buffers, light, skullModels.get(type), renderType);
        } else if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != EquipmentSlot.HEAD) {
            CustomHeadLayer.translateToHead(pose, false);
            itemInHandRenderer.renderItem(golem, stack, ItemDisplayContext.HEAD, false, pose, buffers, light);
        }
        pose.popPose();
    }
}
