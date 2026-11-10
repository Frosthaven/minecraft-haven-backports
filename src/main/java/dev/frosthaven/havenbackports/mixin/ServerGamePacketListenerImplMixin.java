package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.ghast.HappyGhast;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x handleMoveVehicle does not count a flying vehicle (Entity.isFlyingVehicle: an adult happy ghast) as floating, so
 * a server with allow-flight=false does not kick its rider for "flying". 1.21.1 has no such check: the vehicle's
 * isNoGravity() reads true for an adult happy ghast here, which is where 26.x adds its test.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @WrapOperation(method = "handleMoveVehicle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isNoGravity()Z"))
    private boolean haven_backports$flyingVehicle(Entity vehicle, Operation<Boolean> original) {
        return original.call(vehicle) || vehicle instanceof HappyGhast ghast && ghast.isFlyingVehicle();
    }
}
