package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.frosthaven.havenbackports.spear.SpearItem;
import dev.frosthaven.havenbackports.spear.SpearState;
import dev.frosthaven.havenbackports.spear.Stab;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x LivingEntity parts spears need:
 * <ul>
 * <li>{@code recentKineticEnemies}: made when a kinetic weapon starts being used (server), dropped in stopUsingItem.</li>
 * <li>entity event 2 ({@code onKineticHit}, client: hit sound, {@code lastKineticHitFeedbackTime}); 1.21.1 uses no
 * event 2 in LivingEntity or its subclasses.</li>
 * <li>{@code getModifiedSwingDuration(ATTACK_ANIMATION)}: a spear swings for its STAB duration, changed by haste and
 * mining fatigue as 26.x does. 1.21.1 sends no swing animation per packet; the swinging hand's item gives it (if the
 * item changes mid-swing, the new one's duration applies; 26.x keeps the old swing's).</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin implements SpearState.Living {
    @Unique
    private Object2LongMap<Entity> haven_backports$recentKineticEnemies;
    @Unique
    private long haven_backports$lastKineticHitFeedbackTime = Integer.MIN_VALUE;

    @Override
    public Object2LongMap<Entity> haven_backports$recentKineticEnemies() {
        return this.haven_backports$recentKineticEnemies;
    }

    @Override
    public void haven_backports$setRecentKineticEnemies(Object2LongMap<Entity> map) {
        this.haven_backports$recentKineticEnemies = map;
    }

    @Override
    public long haven_backports$lastKineticHitFeedbackTime() {
        return this.haven_backports$lastKineticHitFeedbackTime;
    }

    @Override
    public void haven_backports$setLastKineticHitFeedbackTime(long time) {
        this.haven_backports$lastKineticHitFeedbackTime = time;
    }

    /** In the server branch of startUsingItem, after useItem is set. */
    @Inject(method = "startUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setLivingEntityFlag(IZ)V", ordinal = 0))
    private void haven_backports$startKinetic(InteractionHand hand, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).getUseItem().getItem() instanceof SpearItem) {
            this.haven_backports$recentKineticEnemies = new Object2LongOpenHashMap<>();
        }
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void haven_backports$stopKinetic(CallbackInfo ci) {
        if (!((LivingEntity) (Object) this).level().isClientSide()) {
            this.haven_backports$recentKineticEnemies = null;
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void haven_backports$kineticHit(byte id, CallbackInfo ci) {
        if (id == 2) {
            Stab.onKineticHit((LivingEntity) (Object) this);
            ci.cancel();
        }
    }

    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int haven_backports$stabDuration(int vanilla) {
        LivingEntity self = (LivingEntity) (Object) this;
        InteractionHand hand = self.swingingArm == null ? InteractionHand.MAIN_HAND : self.swingingArm;
        if (!(self.getItemInHand(hand).getItem() instanceof SpearItem spear)) {
            return vanilla;
        }
        int swingDuration = spear.swingTicks();
        if (MobEffectUtil.hasDigSpeed(self)) {
            return swingDuration - (1 + MobEffectUtil.getDigSpeedAmplification(self));
        }
        return self.hasEffect(MobEffects.DIG_SLOWDOWN) ? swingDuration + (1 + self.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) * 2 : swingDuration;
    }
}
