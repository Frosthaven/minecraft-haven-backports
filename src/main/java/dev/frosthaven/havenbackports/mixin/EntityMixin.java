package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.spear.SpearState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x Entity.computeSpeed (start of baseTick) and its reset in reapplyPosition: {@code lastKnownSpeed}, the position
 * change over the last tick, which spear charges read (KineticWeapon.getMotion). 1.21.1 has no such field.
 */
@Mixin(Entity.class)
abstract class EntityMixin implements SpearState.Known {
    @Unique
    private Vec3 haven_backports$lastKnownSpeed = Vec3.ZERO;
    @Unique
    private Vec3 haven_backports$lastKnownPosition;

    @Override
    public Vec3 haven_backports$lastKnownSpeed() {
        return this.haven_backports$lastKnownSpeed;
    }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void haven_backports$computeSpeed(CallbackInfo ci) {
        haven_backports$computeSpeed();
    }

    @Inject(method = "reapplyPosition", at = @At("HEAD"))
    private void haven_backports$forgetPosition(CallbackInfo ci) {
        this.haven_backports$lastKnownPosition = null;
    }

    /** 26.x computeSpeed; AbstractMinecartMixin calls it too (minecarts tick without baseTick, as in 26.x). */
    @Override
    public void haven_backports$computeSpeed() {
        Entity self = (Entity) (Object) this;
        if (this.haven_backports$lastKnownPosition == null) {
            this.haven_backports$lastKnownPosition = self.position();
        }
        this.haven_backports$lastKnownSpeed = self.position().subtract(this.haven_backports$lastKnownPosition);
        this.haven_backports$lastKnownPosition = self.position();
    }
}
