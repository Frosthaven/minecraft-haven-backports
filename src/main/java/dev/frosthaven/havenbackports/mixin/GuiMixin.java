package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havenbackports.client.spear.SpearClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 26.x Hud.extractCrosshair: the full attack indicator also needs the target inside the held item's attack range. */
@Mixin(Gui.class)
abstract class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isAlive()Z"))
    private boolean haven_backports$inSpearRange(boolean alive) {
        return alive && SpearClient.inAttackRange(this.minecraft.player, this.minecraft.hitResult);
    }
}
