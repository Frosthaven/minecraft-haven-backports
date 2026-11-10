package dev.frosthaven.havenbackports.frostbite;

import java.util.EnumSet;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;

/** The frostbite's two attack goals: 26.x RangedZombie's RangedZombieRangedAttackGoal (on 26.x's RangedAttackGoal,
 *  whose attack intervals are suppliers: they follow the difficulty) and RangedZombieMeleeAttackGoal (26.x's
 *  ZombieAttackGoal with a 30 tick attack interval; 1.21.1's MeleeAttackGoal has a fixed 20). */
final class FrostbiteGoals {
    private FrostbiteGoals() {}

    /** 26.x RangedAttackGoal(mob, speed, min, max, radius) with RangedZombieRangedAttackGoal's use checks and flags;
     *  chargeWeapon and stopWhenInRange are false for the frostbite. */
    static final class Ranged extends Goal {
        private final Frostbite mob;
        @Nullable
        private LivingEntity target;
        private int attackTime = -1;
        private final double speedModifier;
        private int seeTime;
        private final IntSupplier attackIntervalMin;
        private final IntSupplier attackIntervalMax;
        private final float attackRadius;
        private final float attackRadiusSqr;

        Ranged(Frostbite mob, double speedModifier, IntSupplier attackIntervalMin, IntSupplier attackIntervalMax, float attackRadius) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.attackIntervalMin = attackIntervalMin;
            this.attackIntervalMax = attackIntervalMax;
            this.attackRadius = attackRadius;
            this.attackRadiusSqr = attackRadius * attackRadius;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private boolean baseCanUse() {
            LivingEntity bestTarget = this.mob.getTarget();
            if (bestTarget != null && bestTarget.isAlive()) {
                this.target = bestTarget;
                return true;
            }
            return false;
        }

        @Override
        public boolean canUse() {
            return this.baseCanUse() && this.mob.shouldDoRangedAttack();
        }

        @Override
        public boolean canContinueToUse() {
            return (this.baseCanUse() || this.target.isAlive() && !this.mob.getNavigation().isDone()) && this.mob.shouldDoRangedAttack();
        }

        @Override
        public void start() {
            this.mob.setAggressive(true);
            this.mob.isRanged = true;
        }

        @Override
        public void stop() {
            this.target = null;
            this.seeTime = 0;
            this.attackTime = -1;
            this.mob.setAggressive(false);
            this.mob.isRanged = false;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            double targetDistSqr = this.mob.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ());
            boolean hasLineOfSight = this.mob.getSensing().hasLineOfSight(this.target);
            if (hasLineOfSight) {
                this.seeTime++;
            } else {
                this.seeTime = 0;
            }
            // stopWhenInRange() is false for the frostbite: it keeps walking at its target
            this.mob.getNavigation().moveTo(this.target, this.speedModifier);
            this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            if (--this.attackTime == 0) {
                if (!hasLineOfSight) {
                    return;
                }
                float dist = (float) Math.sqrt(targetDistSqr) / this.attackRadius;
                float power = Math.clamp(dist, 0.1F, 1.0F);
                this.mob.performRangedAttack(this.target, power);
                this.attackTime = Mth.floor(dist * (this.attackIntervalMax.getAsInt() - this.attackIntervalMin.getAsInt()) + this.attackIntervalMin.getAsInt());
            } else if (this.attackTime < 0) {
                this.attackTime = Mth.floor(Mth.lerp(Math.sqrt(targetDistSqr) / this.attackRadius,
                        (double) this.attackIntervalMin.getAsInt(), (double) this.attackIntervalMax.getAsInt()));
            }
        }
    }

    /** ZombieAttackGoal with 26.x MeleeAttackGoal's attackInterval: 1.21.1 keeps its cooldown private at 20 ticks, so
     *  this goal keeps its own, counted down where 26.x counts (right before the attack check). */
    static final class Melee extends ZombieAttackGoal {
        private final int attackInterval;
        private int ticksUntilNextAttack;

        Melee(Frostbite mob, double speedModifier, boolean trackTarget, int attackInterval) {
            super(mob, speedModifier, trackTarget);
            this.attackInterval = attackInterval;
        }

        @Override
        public void start() {
            super.start();
            this.ticksUntilNextAttack = 0;
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
            super.checkAndPerformAttack(target);
        }

        @Override
        protected void resetAttackCooldown() {
            this.ticksUntilNextAttack = this.adjustedTickDelay(this.attackInterval);
        }

        @Override
        protected boolean isTimeToAttack() {
            return this.ticksUntilNextAttack <= 0;
        }

        @Override
        protected int getTicksUntilNextAttack() {
            return this.ticksUntilNextAttack;
        }

        @Override
        protected int getAttackInterval() {
            return this.adjustedTickDelay(this.attackInterval);
        }
    }
}
