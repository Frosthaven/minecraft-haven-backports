package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.client.spear.SpearClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x LocalPlayer.raycastHitResult (called from Minecraft.pick): with a spear the crosshair picks the closest entity
 *  along the spear's range. 1.21.1 hard-codes the interaction-range pick in GameRenderer.pick. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void haven_backports$spearPick(float partialTicks, CallbackInfo ci) {
        Entity camera = this.minecraft.getCameraEntity();
        if (camera != null && this.minecraft.level != null && this.minecraft.player != null) {
            this.minecraft.hitResult = SpearClient.pick(this.minecraft.player, camera, partialTicks, this.minecraft.hitResult);
            this.minecraft.crosshairPickEntity = SpearClient.pickedEntity(this.minecraft.hitResult);
        }
    }
}
