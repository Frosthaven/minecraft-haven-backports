package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Variants;
import dev.frosthaven.havenbackports.variant.VariantHolder;
import dev.frosthaven.havenbackports.variant.WolfSoundVariant;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x Wolf's sound variant (wolf_sound_variant: classic, puglin, sad, angry, grumpy, big, cute): synced, saved as
 * "sound_variant", random at spawn and for a bred puppy. The wolf's ambient (growl when angry, else one in three a
 * whine when hurt and tame or a pant), hurt (armor keeps its own), death and step sounds come from it, the puppy's
 * from its baby set (26.x getSoundSet). 1.21.1 hard-codes the wolf's sounds.
 */
@Mixin(Wolf.class)
abstract class WolfMixin implements VariantHolder<WolfSoundVariant> {
    @Unique
    private static final EntityDataAccessor<Holder<WolfSoundVariant>> HAVEN_BACKPORTS$SOUND_VARIANT = SynchedEntityData.defineId(Wolf.class, Variants.WOLF_SOUND_SERIALIZER);

    @Override
    public EntityDataAccessor<Holder<WolfSoundVariant>> haven_backports$accessor() {
        return HAVEN_BACKPORTS$SOUND_VARIANT;
    }

    @Override
    public ResourceKey<Registry<WolfSoundVariant>> haven_backports$registry() {
        return Variants.WOLF_SOUND;
    }

    @Override
    public String haven_backports$defaultName() {
        return "classic";
    }

    @Override
    public String haven_backports$saveKey() {
        return "sound_variant";
    }

    @Override
    public Holder<WolfSoundVariant> haven_backports$variant() {
        return self().getEntityData().get(HAVEN_BACKPORTS$SOUND_VARIANT);
    }

    @Override
    public void haven_backports$setVariant(Holder<WolfSoundVariant> variant) {
        self().getEntityData().set(HAVEN_BACKPORTS$SOUND_VARIANT, variant);
    }

    @Unique
    private Wolf self() {
        return (Wolf) (Object) this;
    }

    @Unique
    private WolfSoundVariant.SoundSet haven_backports$set() {
        return self().isBaby() ? haven_backports$variant().value().babySounds() : haven_backports$variant().value().adultSounds();
    }

    @Unique
    private static SoundEvent haven_backports$of(Optional<Holder<SoundEvent>> sound) {
        return sound.map(Holder::value).orElse(null);
    }

    @Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
    private void haven_backports$ambient(CallbackInfoReturnable<SoundEvent> cir) {
        Wolf wolf = self();
        WolfSoundVariant.SoundSet set = haven_backports$set();
        if (wolf.isAngry()) {
            cir.setReturnValue(haven_backports$of(set.growlSound()));
        } else if (wolf.getRandom().nextInt(3) == 0) {
            cir.setReturnValue(haven_backports$of(wolf.isTame() && wolf.getHealth() < 20.0F ? set.whineSound() : set.pantSound()));
        } else {
            cir.setReturnValue(haven_backports$of(set.ambientSound()));
        }
    }

    @Inject(method = "getHurtSound", at = @At("RETURN"), cancellable = true)
    private void haven_backports$hurt(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        if (cir.getReturnValue() != net.minecraft.sounds.SoundEvents.WOLF_ARMOR_DAMAGE) {
            cir.setReturnValue(haven_backports$of(haven_backports$set().hurtSound()));
        }
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void haven_backports$death(CallbackInfoReturnable<SoundEvent> cir) {
        cir.setReturnValue(haven_backports$of(haven_backports$set().deathSound()));
    }

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void haven_backports$step(BlockPos pos, BlockState state, CallbackInfo ci) {
        haven_backports$set().stepSound().ifPresent(s -> self().playSound(s.value(), 0.15F, 1.0F));
        ci.cancel();
    }

    @Inject(method = "getBreedOffspring", at = @At("RETURN"))
    @SuppressWarnings("unchecked")
    private void haven_backports$puppySound(ServerLevel level, AgeableMob partner, CallbackInfoReturnable<Wolf> cir) {
        if (cir.getReturnValue() instanceof VariantHolder<?> baby) {
            ((VariantHolder<WolfSoundVariant>) baby).haven_backports$setVariant(Variants.randomWolfSound(level.registryAccess(), self().getRandom()));
        }
    }
}
