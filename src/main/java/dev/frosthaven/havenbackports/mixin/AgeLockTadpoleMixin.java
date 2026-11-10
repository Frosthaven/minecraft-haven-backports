package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x Tadpole's own age lock (a tadpole is no AgeableMob): AGE_LOCKED synced, "AgeLocked" in the save and the
 * bucket, aiStep ages only while unlocked, the lock particles, and mobInteract: food grows only an unlocked tadpole;
 * a golden dandelion toggles the lock (its age back to 0); otherwise the bucket and AbstractFish, as 1.21.1.
 */
@Mixin(Tadpole.class)
abstract class AgeLockTadpoleMixin extends AbstractFish implements AgeLock.Lockable {
    @Unique
    private static final EntityDataAccessor<Boolean> HAVEN_BACKPORTS$AGE_LOCKED = SynchedEntityData.defineId(Tadpole.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private int haven_backports$ageLockParticleTimer;

    protected AgeLockTadpoleMixin(EntityType<? extends AbstractFish> type, Level level) {
        super(type, level);
    }

    @Shadow
    private boolean isFood(ItemStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private void setAge(int age) {
        throw new AssertionError();
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

    /** 1.21.1's Tadpole has no defineSynchedData of its own: 26.x's, with AGE_LOCKED. */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HAVEN_BACKPORTS$AGE_LOCKED, false);
    }

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/frog/Tadpole;setAge(I)V"))
    private void haven_backports$growOnlyUnlocked(Tadpole self, int age, Operation<Void> original) {
        if (!this.haven_backports$isAgeLocked()) {
            original.call(self, age);
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void haven_backports$particles(CallbackInfo ci) {
        this.haven_backports$ageLockParticleTimer = AgeLock.makeAgeLockedParticle(this.level(), this, this.haven_backports$ageLockParticleTimer,
                this.haven_backports$isAgeLocked());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void haven_backports$save(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("AgeLocked", this.haven_backports$isAgeLocked());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void haven_backports$load(CompoundTag tag, CallbackInfo ci) {
        this.haven_backports$setAgeLocked(tag.getBoolean("AgeLocked"));
    }

    @Inject(method = "saveToBucketTag", at = @At("TAIL"))
    private void haven_backports$saveBucket(ItemStack stack, CallbackInfo ci) {
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> tag.putBoolean("AgeLocked", this.haven_backports$isAgeLocked()));
    }

    @Inject(method = "loadFromBucketTag", at = @At("TAIL"))
    private void haven_backports$loadBucket(CompoundTag tag, CallbackInfo ci) {
        this.haven_backports$setAgeLocked(tag.getBoolean("AgeLocked"));
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void haven_backports$interact(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack itemStack = player.getItemInHand(hand);
        boolean food = this.isFood(itemStack);
        if (food && !this.haven_backports$isAgeLocked()) {
            return;   // 1.21.1's feeding
        }
        if (AgeLock.canUseGoldenDandelion(itemStack, true, this.haven_backports$ageLockParticleTimer, this)) {
            AgeLock.setAgeLocked(this, this::haven_backports$isAgeLocked, player, itemStack, mob -> {
                this.haven_backports$setAgeLocked(!this.haven_backports$isAgeLocked());
                this.setAge(0);
                this.haven_backports$ageLockParticleTimer = AgeLock.COOLDOWN_TICKS;
            });
            cir.setReturnValue(InteractionResult.SUCCESS);
        } else if (food) {   // locked: 26.x skips the feeding and goes on to the bucket
            cir.setReturnValue(Bucketable.bucketMobPickup(player, hand, this).orElse(super.mobInteract(player, hand)));
        }
    }
}
