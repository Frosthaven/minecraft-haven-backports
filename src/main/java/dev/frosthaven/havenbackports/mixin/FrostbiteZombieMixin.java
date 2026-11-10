package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.frostbite.FreezingConversion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x Zombie's freezing conversion (FreezingConversion: a zombie in powder snow becomes a frostbite, a husk or a
 * drowned a zombie). 1.21.1's Zombie has no hook for a second conversion: this adds 26.x's synced flag
 * (DATA_FROSTBITE_CONVERSION_ID: the client shakes the zombie while it converts), its two timers, the tick after the
 * zombie's own, and the save keys. The logic is in FreezingConversion; nothing else in Zombie changes.
 */
@Mixin(Zombie.class)
abstract class FrostbiteZombieMixin implements FreezingConversion.Host {
    @Unique
    private static final EntityDataAccessor<Boolean> HAVEN_BACKPORTS$FROSTBITE_CONVERSION =
            SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private int haven_backports$afflictionTime;
    @Unique
    private int haven_backports$conversionTime;

    @Shadow
    protected abstract void convertToZombieType(EntityType<? extends Zombie> type);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void haven_backports$defineFrostbiteConversion(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(HAVEN_BACKPORTS$FROSTBITE_CONVERSION, false);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void haven_backports$freezingTick(CallbackInfo ci) {
        FreezingConversion.tick((Zombie) (Object) this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void haven_backports$saveFreezing(CompoundTag tag, CallbackInfo ci) {
        FreezingConversion.save((Zombie) (Object) this, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void haven_backports$loadFreezing(CompoundTag tag, CallbackInfo ci) {
        FreezingConversion.load((Zombie) (Object) this, tag);
    }

    @Override
    public int haven_backports$afflictionTime() {
        return this.haven_backports$afflictionTime;
    }

    @Override
    public void haven_backports$setAfflictionTime(int ticks) {
        this.haven_backports$afflictionTime = ticks;
    }

    @Override
    public int haven_backports$conversionTime() {
        return this.haven_backports$conversionTime;
    }

    @Override
    public void haven_backports$setConversionTime(int ticks) {
        this.haven_backports$conversionTime = ticks;
    }

    @Override
    public boolean haven_backports$isFreezingConverting() {
        return ((Zombie) (Object) this).getEntityData().get(HAVEN_BACKPORTS$FROSTBITE_CONVERSION);
    }

    @Override
    public void haven_backports$setFreezingConverting(boolean converting) {
        ((Zombie) (Object) this).getEntityData().set(HAVEN_BACKPORTS$FROSTBITE_CONVERSION, converting);
    }

    @Override
    public void haven_backports$convertTo(EntityType<? extends Zombie> type) {
        this.convertToZombieType(type);
    }
}
