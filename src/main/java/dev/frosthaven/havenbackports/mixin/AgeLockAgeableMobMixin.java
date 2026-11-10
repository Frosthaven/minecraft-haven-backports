package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x AgeableMob's age lock state, which 1.21.1's AgeableMob lacks: the AGE_LOCKED synced flag, the "AgeLocked"
 * save key, the particle timer, aiStep's growth step only when canAgeUp (a locked baby stays at its age), and
 * makeAgeLockedParticle each tick. The golden dandelion use itself is AgeLockMobMixin (26.x AgeableMob.mobInteract).
 */
@Mixin(AgeableMob.class)
abstract class AgeLockAgeableMobMixin extends PathfinderMob implements AgeLock.Lockable {
    @Unique
    private static final EntityDataAccessor<Boolean> HAVEN_BACKPORTS$AGE_LOCKED = SynchedEntityData.defineId(AgeableMob.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private int haven_backports$ageLockParticleTimer;

    protected AgeLockAgeableMobMixin(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean haven_backports$isAgeLocked() {
        return this.entityData.get(HAVEN_BACKPORTS$AGE_LOCKED);
    }

    @Override
    public void haven_backports$setAgeLocked(boolean locked) {
        this.entityData.set(HAVEN_BACKPORTS$AGE_LOCKED, locked);
    }

    @Override
    public int haven_backports$lockTimer() {
        return this.haven_backports$ageLockParticleTimer;
    }

    @Override
    public void haven_backports$setLockTimer(int ticks) {
        this.haven_backports$ageLockParticleTimer = ticks;
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void haven_backports$define(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(HAVEN_BACKPORTS$AGE_LOCKED, false);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void haven_backports$save(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("AgeLocked", this.haven_backports$isAgeLocked());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void haven_backports$load(CompoundTag tag, CallbackInfo ci) {
        this.haven_backports$setAgeLocked(tag.getBoolean("AgeLocked"));
    }

    /** 26.x aiStep: `if (canAgeUp()) setAge(++age); else if (age > 0) setAge(--age);`: a locked baby does not age. */
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/AgeableMob;setAge(I)V"))
    private void haven_backports$growOnlyUnlocked(AgeableMob self, int age, Operation<Void> original) {
        if (!(this.haven_backports$isAgeLocked() && self.getAge() < 0)) {
            original.call(self, age);
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void haven_backports$particles(CallbackInfo ci) {
        this.haven_backports$ageLockParticleTimer = AgeLock.makeAgeLockedParticle(this.level(), this, this.haven_backports$ageLockParticleTimer,
                this.haven_backports$isAgeLocked());
    }
}
