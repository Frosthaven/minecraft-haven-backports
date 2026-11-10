package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.frosthaven.havenbackports.frostbite.FreezingConversion;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.x AbstractZombieRenderer: a zombie shakes while it converts in powder snow as while it converts under water
 *  (ZombieRenderState.isConverting = isUnderWaterConverting() || isFreezingConverting()). */
@Mixin(AbstractZombieRenderer.class)
abstract class FrostbiteZombieRendererMixin {
    @ModifyReturnValue(method = "isShaking(Lnet/minecraft/world/entity/monster/Zombie;)Z", at = @At("RETURN"))
    private boolean haven_backports$shakeWhileFreezing(boolean shaking, Zombie zombie) {
        return shaking || ((FreezingConversion.Host) zombie).haven_backports$isFreezingConverting();
    }
}
