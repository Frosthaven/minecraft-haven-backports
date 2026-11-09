package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.block.CopperChestBlock;
import dev.frosthaven.havenbackports.client.ChestTextures;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.x ChestRenderer.getChestMaterial: a copper chest takes its stage's copper texture, before the holiday one. */
@Mixin(Sheets.class)
abstract class SheetsMixin {
    @Inject(method = "chooseMaterial(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/level/block/state/properties/ChestType;Z)Lnet/minecraft/client/resources/model/Material;",
            at = @At("HEAD"), cancellable = true)
    private static void haven_backports$copper(BlockEntity be, ChestType type, boolean holiday, CallbackInfoReturnable<Material> cir) {
        if (be.getBlockState().getBlock() instanceof CopperChestBlock chest) {
            cir.setReturnValue(ChestTextures.of(chest.getState(), type));
        }
    }
}
