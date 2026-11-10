package dev.frosthaven.havenbackports.nautilus;

import com.google.common.collect.ImmutableMap;
import dev.frosthaven.havenbackports.Nautiluses;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * Mojang's 26.x ChargeAttack behavior: rush the attack target in a straight line, hit and knock back the first entity
 * the charge touches, then wait. 26.x LivingEntity.causeExtraKnockback is written out (1.21.1's knockback takes only
 * strength and direction).
 */
public class ChargeAttack extends Behavior<Animal> {
    private final int timeBetweenAttacks;
    private final TargetingConditions chargeTargeting;
    private final float speed;
    private final float knockbackForce;
    private final double maxTargetDetectionDistance;
    private final double maxChargeDistance;
    private final SoundEvent chargeSound;
    private Vec3 chargeVelocityVector = Vec3.ZERO;
    private Vec3 startPosition = Vec3.ZERO;

    public ChargeAttack(int timeBetweenAttacks, TargetingConditions chargeTargeting, float speed, float knockbackForce, double maxChargeDistance,
                        double maxTargetDetectionDistance, SoundEvent chargeSound) {
        super(ImmutableMap.of(Nautiluses.CHARGE_COOLDOWN_TICKS.get(), MemoryStatus.VALUE_ABSENT, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
        this.timeBetweenAttacks = timeBetweenAttacks;
        this.chargeTargeting = chargeTargeting;
        this.speed = speed;
        this.knockbackForce = knockbackForce;
        this.maxChargeDistance = maxChargeDistance;
        this.maxTargetDetectionDistance = maxTargetDetectionDistance;
        this.chargeSound = chargeSound;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Animal body) {
        return body.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Animal body, long timestamp) {
        Brain<?> brain = body.getBrain();
        Optional<LivingEntity> candidate = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
        if (candidate.isEmpty()) {
            return false;
        }
        LivingEntity target = candidate.get();
        if (body instanceof TamableAnimal tamed && tamed.isTame()) {
            return false;
        } else if (body.position().subtract(this.startPosition).lengthSqr() >= this.maxChargeDistance * this.maxChargeDistance) {
            return false;
        } else if (target.position().subtract(body.position()).lengthSqr() >= this.maxTargetDetectionDistance * this.maxTargetDetectionDistance) {
            return false;
        }
        return body.hasLineOfSight(target) && !brain.hasMemoryValue(Nautiluses.CHARGE_COOLDOWN_TICKS.get());
    }

    @Override
    protected void start(ServerLevel level, Animal body, long timestamp) {
        this.startPosition = body.position();
        LivingEntity target = body.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        this.chargeVelocityVector = target.position().subtract(body.position()).normalize().scale(this.speed);
        if (this.canStillUse(level, body, timestamp)) {
            body.playSound(this.chargeSound);
        }
    }

    @Override
    protected void tick(ServerLevel level, Animal body, long timestamp) {
        LivingEntity target = body.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElseThrow();
        body.lookAt(target, 360.0F, 360.0F);
        body.setDeltaMovement(this.chargeVelocityVector);
        List<LivingEntity> colliding = new ArrayList<>(1);
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class), body.getBoundingBox(), e -> this.chargeTargeting.test(body, e), colliding, 1);
        if (!colliding.isEmpty()) {
            LivingEntity closest = colliding.get(0);
            if (body.hasPassenger(closest)) {
                return;
            }
            this.dealDamageToTarget(level, body, closest);
            this.dealKnockBack(body, closest);
            this.stop(level, body, timestamp);
        }
    }

    private void dealDamageToTarget(ServerLevel level, Animal body, LivingEntity target) {
        DamageSource source = level.damageSources().mobAttack(body);
        if (target.hurt(source, (float) body.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
            EnchantmentHelper.doPostAttackEffects(level, target, source);
        }
    }

    private void dealKnockBack(Animal body, LivingEntity target) {
        int speedLevel = body.hasEffect(MobEffects.MOVEMENT_SPEED) ? body.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() + 1 : 0;
        int slowLevel = body.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) ? body.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() + 1 : 0;
        float speedBoostPower = 0.25F * (speedLevel - slowLevel);
        float speedFactor = Math.clamp(this.speed * (float) body.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.2F, 2.0F) + speedBoostPower;
        float knockback = speedFactor * this.knockbackForce;
        if (knockback > 0.0F) {   // 26.x causeExtraKnockback
            target.knockback(knockback, Mth.sin(body.getYRot() * (float) (Math.PI / 180.0)), -Mth.cos(body.getYRot() * (float) (Math.PI / 180.0)));
            body.setDeltaMovement(body.getDeltaMovement().multiply(0.6, 1.0, 0.6));
        }
    }

    @Override
    protected void stop(ServerLevel level, Animal body, long timestamp) {
        body.getBrain().setMemory(Nautiluses.CHARGE_COOLDOWN_TICKS.get(), this.timeBetweenAttacks);
        body.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }
}
