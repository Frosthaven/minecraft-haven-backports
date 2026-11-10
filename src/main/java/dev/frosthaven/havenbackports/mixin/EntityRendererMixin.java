package dev.frosthaven.havenbackports.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.client.ghast.QuadRopes;
import dev.frosthaven.havenbackports.entity.QuadLeash;
import dev.frosthaven.havenbackports.ghast.HappyGhast;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x draws four ropes for a quad leash (a horse, camel, sniffer or boat on a lead to a happy ghast); 1.21.1's
 * renderLeash draws one. For that pair only, QuadRopes draws the four (lit as 1.21.1 lights a rope) and the single
 * rope is skipped; every other leash renders as before.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin<T extends Entity> {
    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    protected abstract int getBlockLightLevel(T entity, BlockPos pos);

    @Inject(method = "renderLeash", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void haven_backports$quadLeash(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers, E holder, CallbackInfo ci) {
        if (holder instanceof HappyGhast ghast && QuadLeash.supports(entity)) {
            BlockPos entityEye = BlockPos.containing(entity.getEyePosition(partialTick));
            BlockPos holderEye = BlockPos.containing(holder.getEyePosition(partialTick));
            @SuppressWarnings("unchecked")
            EntityRenderer<Entity> holderRenderer = (EntityRenderer<Entity>) this.entityRenderDispatcher.getRenderer(holder);
            int startBlock = this.getBlockLightLevel(entity, entityEye);
            int endBlock = ((EntityRendererMixin<Entity>) (Object) holderRenderer).getBlockLightLevel(holder, holderEye);
            QuadRopes.render(entity, ghast, partialTick, pose, buffers, startBlock, endBlock,
                    entity.level().getBrightness(LightLayer.SKY, entityEye), entity.level().getBrightness(LightLayer.SKY, holderEye));
            ci.cancel();
        }
    }
}
