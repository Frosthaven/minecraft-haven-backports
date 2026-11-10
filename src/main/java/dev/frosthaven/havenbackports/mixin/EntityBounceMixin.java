package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.frosthaven.havenbackports.block.Bouncy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x block bounce (Entity.restituteMovementAfterCollisions with Block.getBounceRestitution): 1.21.1 hands a
 * vertical collision to the block below (updateEntityAfterFallOn) without the collided movement the formula needs.
 * For blocks that bounce ({@link Bouncy}: the shelf mushroom) the call goes to the block's bounce with that movement;
 * every other block keeps 1.21.1's call.
 */
@Mixin(Entity.class)
abstract class EntityBounceMixin {
    @WrapOperation(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;updateEntityAfterFallOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V"))
    private void haven_backports$bounce(Block block, BlockGetter level, Entity entity, Operation<Void> original,
            @Local(ordinal = 1) Vec3 collided) {
        if (block instanceof Bouncy bouncy) {
            bouncy.bounce(entity, collided);
        } else {
            original.call(block, level, entity);
        }
    }
}
