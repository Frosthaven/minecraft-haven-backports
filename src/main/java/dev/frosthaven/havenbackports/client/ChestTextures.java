package dev.frosthaven.havenbackports.client;

import dev.frosthaven.havenbackports.Chests;
import dev.frosthaven.havenbackports.HavenBackports;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Copper chest textures (26.x Sheets.CHEST_COPPER: entity/chest/copper[_exposed|_weathered|_oxidized][_left|_right], in
 * the chest atlas) and the item renderer: 26.x draws the item with its chest special model; 1.21.1 draws chest items
 * through a block entity renderer, so each copper chest item draws a chest block entity of its own block.
 */
public final class ChestTextures {
    private ChestTextures() {}

    public static Material of(WeatherState state, ChestType type) {
        String name = "entity/chest/copper" + (state == WeatherState.UNAFFECTED ? "" : "_" + state.getSerializedName())
                + (type == ChestType.LEFT ? "_left" : type == ChestType.RIGHT ? "_right" : "");
        return new Material(Sheets.CHEST_SHEET, ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, name));
    }

    static void items(RegisterClientExtensionsEvent e) {
        Minecraft mc = Minecraft.getInstance();
        BlockEntityWithoutLevelRenderer renderer = new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()) {
            @Override
            public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
                ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, ((BlockItem) stack.getItem()).getBlock().defaultBlockState());
                mc.getBlockEntityRenderDispatcher().renderItem(chest, pose, buffers, light, overlay);
            }
        };
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        };
        e.registerItem(ext, Chests.ALL.stream().map(b -> b.get().asItem()).toArray(net.minecraft.world.item.Item[]::new));
    }
}
