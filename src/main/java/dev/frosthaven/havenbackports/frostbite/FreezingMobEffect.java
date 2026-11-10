package dev.frosthaven.havenbackports.frostbite;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 26.x FreezingMobEffect: every tick freezeForTicks(1) (more frozen, up to fully frozen, unless the mob cannot freeze);
 * its particle is ParticleTypes.FREEZING. 1.21.1 builds the effect's particle when the effect is made, before the
 * particle types exist, so the particle is given per effect instance instead. The rest of 26.x's freezing lives in
 * LivingEntity.aiStep (FreezingLivingEntityMixin: no thawing while the effect lasts, fully frozen damage 1.5).
 */
public class FreezingMobEffect extends MobEffect {
    public static final float DAMAGE_VALUE = 1.5F;

    protected FreezingMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity mob, int amplification) {
        if (mob.canFreeze()) {   // Entity.freezeForTicks(1)
            mob.setTicksFrozen(Math.min(mob.getTicksRequiredToFreeze(), mob.getTicksFrozen() + 1));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }

    @Override
    public ParticleOptions createParticleOptions(MobEffectInstance effect) {
        return FrostbiteContent.FREEZING_PARTICLE.get();
    }
}
