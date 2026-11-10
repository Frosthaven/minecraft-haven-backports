package dev.frosthaven.havenbackports.husk;

import dev.frosthaven.havenbackports.Husks;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x Parched, method for method, on 1.21.1's AbstractSkeleton: arrows of weakness (600 ticks), 16 health,
 * attack intervals 50 (hard) and 70. Two 26.x engine tags 1.21.1 lacks are read here for this mob: it is not in
 * #burn_in_daylight (1.21.1's AbstractSkeleton always burns: isSunBurnTick is false) and it is in #immune_to_weakness
 * (26.x LivingEntity.canBeAffected; the tag is ours to read, written by the generator).
 */
public class Parched extends AbstractSkeleton {
    public Parched(EntityType<? extends AbstractSkeleton> type, Level level) {
        super(type, level);
    }

    @Override
    protected AbstractArrow getArrow(ItemStack projectile, float power, @Nullable ItemStack weapon) {
        AbstractArrow arrow = super.getArrow(projectile, power, weapon);
        if (arrow instanceof Arrow a) {
            a.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600));
        }
        return arrow;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes().add(Attributes.MAX_HEALTH, 16.0);
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return (!effect.is(MobEffects.WEAKNESS) || !this.getType().is(Husks.IMMUNE_TO_WEAKNESS)) && super.canBeAffected(effect);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Husks.PARCHED_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Husks.PARCHED_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Husks.PARCHED_DEATH.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return Husks.PARCHED_STEP.get();
    }

    @Override
    protected int getHardAttackInterval() {
        return 50;
    }

    @Override
    protected int getAttackInterval() {
        return 70;
    }
}
