package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.frosthaven.havenbackports.frostbite.FreezingMobEffect;
import dev.frosthaven.havenbackports.frostbite.FrostbiteContent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 26.x LivingEntity.aiStep "freezing" for the Freezing effect: a mob that can freeze does not thaw while it has the
 * effect (1.21.1 thaws 2 ticks a tick outside powder snow, faster than the effect's 1 tick of frost), and fully frozen
 * damage is 1.5 instead of 1 while it has the effect. Without the effect, 1.21.1's freezing is unchanged.
 */
@Mixin(LivingEntity.class)
abstract class FreezingLivingEntityMixin {
    @WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setTicksFrozen(I)V", ordinal = 1))
    private boolean haven_backports$noThawWhileFreezing(LivingEntity self, int ticks) {
        return !(self.canFreeze() && self.hasEffect(FrostbiteContent.FREEZING));
    }

    @ModifyArg(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
    private float haven_backports$freezingDamage(DamageSource source, float amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        return source.is(DamageTypes.FREEZE) && self.hasEffect(FrostbiteContent.FREEZING) ? FreezingMobEffect.DAMAGE_VALUE : amount;
    }
}
