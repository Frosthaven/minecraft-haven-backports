package dev.frosthaven.havenbackports.spear;

import dev.frosthaven.havenbackports.Spears;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x {@code KineticWeapon} (the KINETIC_WEAPON item component): the charge while the use key is held. A plain record
 * on {@link SpearItem} (1.21.1 has no such component); sounds are suppliers, as registered sounds are.
 */
public record KineticWeapon(int contactCooldownTicks, int delayTicks, Optional<Condition> dismountConditions,
        Optional<Condition> knockbackConditions, Optional<Condition> damageConditions, float forwardMovement, float damageMultiplier,
        Optional<Supplier<SoundEvent>> sound, Optional<Supplier<SoundEvent>> hitSound) {
    public static final int HIT_FEEDBACK_TICKS = 10;

    public static Vec3 getMotion(Entity livingEntity) {
        if (!(livingEntity instanceof Player) && livingEntity.isPassenger()) {
            livingEntity = livingEntity.getRootVehicle();
        }
        return Stab.getKnownSpeed(livingEntity).scale(20.0);
    }

    public void makeSound(Entity causer) {
        this.sound.ifPresent(s -> causer.level().playSound(causer instanceof Player p ? p : null, causer.getX(), causer.getY(), causer.getZ(), s.get(), causer.getSoundSource(), 1.0F, 1.0F));
    }

    /** 26.x playLocalSound(entity, sound, source, volume, pitch): at the entity, not delayed. */
    public void makeLocalHitSound(Entity causer) {
        this.hitSound.ifPresent(s -> causer.level().playLocalSound(causer.getX(), causer.getY(), causer.getZ(), s.get(), causer.getSoundSource(), 1.0F, 1.0F, false));
    }

    public int computeDamageUseDuration() {
        return this.delayTicks + this.damageConditions.map(Condition::maxDurationTicks).orElse(0);
    }

    public void damageEntities(ItemStack stack, int ticksRemaining, LivingEntity livingEntity, EquipmentSlot equipmentSlot) {
        int ticksUsed = stack.getUseDuration(livingEntity) - ticksRemaining;
        if (ticksUsed >= this.delayTicks) {
            ticksUsed -= this.delayTicks;
            Vec3 attackerLookVector = livingEntity.getLookAngle();
            double attackerSpeedProjection = attackerLookVector.dot(getMotion(livingEntity));
            float actionFactor = livingEntity instanceof Player ? 1.0F : 0.2F;
            AttackRange attackRange = SpearItem.attackRange(livingEntity, stack);
            double baseMobDamage = livingEntity.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            boolean affected = false;
            Collection<EntityHitResult> hits = SpearHits.getHitEntitiesAlong(livingEntity, attackRange,
                    e -> PiercingWeapon.canHitEntity(livingEntity, e), ClipContext.Block.COLLIDER).map(a -> List.of(), e -> e);
            for (EntityHitResult hitResult : hits) {
                Entity otherEntity = hitResult.getEntity();
                if (otherEntity instanceof EnderDragonPart dragonPart) {
                    otherEntity = dragonPart.parentMob;
                }
                boolean wasStabbed = Stab.wasRecentlyStabbed(livingEntity, otherEntity, this.contactCooldownTicks);
                if (!wasStabbed) {
                    Stab.rememberStabbedEntity(livingEntity, otherEntity);
                    double targetSpeedProjection = attackerLookVector.dot(getMotion(otherEntity));
                    double relativeSpeed = Math.max(0.0, attackerSpeedProjection - targetSpeedProjection);
                    boolean dealsDismount = this.dismountConditions.isPresent()
                            && this.dismountConditions.get().test(ticksUsed, attackerSpeedProjection, relativeSpeed, actionFactor);
                    boolean dealsKnockback = this.knockbackConditions.isPresent()
                            && this.knockbackConditions.get().test(ticksUsed, attackerSpeedProjection, relativeSpeed, actionFactor);
                    boolean dealsDamage = this.damageConditions.isPresent()
                            && this.damageConditions.get().test(ticksUsed, attackerSpeedProjection, relativeSpeed, actionFactor);
                    if (dealsDismount || dealsKnockback || dealsDamage) {
                        float damageDealt = (float) baseMobDamage + Mth.floor(relativeSpeed * this.damageMultiplier);
                        affected |= Stab.stabAttack(livingEntity, equipmentSlot, otherEntity, damageDealt, dealsDamage, dealsKnockback, dealsDismount);
                    }
                }
            }
            if (affected) {
                livingEntity.level().broadcastEntityEvent(livingEntity, (byte) 2);
                if (livingEntity instanceof ServerPlayer player) {
                    Spears.SPEAR_MOBS_TRIGGER.get().trigger(player, Stab.stabbedEntities(livingEntity, e -> e instanceof LivingEntity));
                }
            }
        }
    }

    public record Condition(int maxDurationTicks, float minSpeed, float minRelativeSpeed) {
        public boolean test(int ticksUsed, double attackerSpeed, double relativeSpeed, double entityFactor) {
            return ticksUsed <= this.maxDurationTicks && attackerSpeed >= this.minSpeed * entityFactor && relativeSpeed >= this.minRelativeSpeed * entityFactor;
        }

        public static Optional<Condition> ofAttackerSpeed(int untilTicks, float minAttackerSpeed) {
            return Optional.of(new Condition(untilTicks, minAttackerSpeed, 0.0F));
        }

        public static Optional<Condition> ofRelativeSpeed(int untilTicks, float minRelativeSpeed) {
            return Optional.of(new Condition(untilTicks, 0.0F, minRelativeSpeed));
        }
    }
}
