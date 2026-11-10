package dev.frosthaven.havenbackports.spear;

import dev.frosthaven.havenbackports.Spears;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x {@code SpearApproach}, {@code SpearAttack} (with {@code SpearStatus}) and {@code SpearRetreat}: the piglin's
 * spear state machine (brain form of {@link SpearUseGoal}). The memories are Haven Backports' (Spears.SPEAR_*).
 */
public final class SpearBehaviors {
    private SpearBehaviors() {}

    public enum SpearStatus { APPROACH, CHARGING, RETREAT }

    @Nullable
    private static LivingEntity target(PathfinderMob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private static boolean ableToAttack(PathfinderMob mob) {
        return target(mob) != null && mob.getMainHandItem().getItem() instanceof SpearItem;
    }

    public static class Approach extends Behavior<PathfinderMob> {
        private final double speedModifierWhenRepositioning;
        private final float approachDistanceSq;

        public Approach(double speedModifierWhenRepositioning, float approachDistance) {
            super(Map.of(Spears.SPEAR_STATUS.get(), MemoryStatus.VALUE_ABSENT));
            this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
            this.approachDistanceSq = approachDistance * approachDistance;
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob body) {
            return ableToAttack(body) && !body.isUsingItem();
        }

        @Override
        protected void start(ServerLevel level, PathfinderMob body, long timestamp) {
            body.setAggressive(true);
            body.getBrain().setMemory(Spears.SPEAR_STATUS.get(), SpearStatus.APPROACH);
            super.start(level, body, timestamp);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, PathfinderMob body, long timestamp) {
            return ableToAttack(body) && this.farEnough(body);
        }

        private boolean farEnough(PathfinderMob mob) {
            LivingEntity target = target(mob);
            return mob.distanceToSqr(target.getX(), target.getY(), target.getZ()) > this.approachDistanceSq;
        }

        @Override
        protected void tick(ServerLevel level, PathfinderMob mob, long timestamp) {
            LivingEntity target = target(mob);
            float speedModifier = SpearUseGoal.chargeSpeedModifier(mob.getRootVehicle());
            mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
            mob.getNavigation().moveTo(target, speedModifier * this.speedModifierWhenRepositioning);
        }

        @Override
        protected void stop(ServerLevel level, PathfinderMob body, long timestamp) {
            body.getNavigation().stop();
            body.getBrain().setMemory(Spears.SPEAR_STATUS.get(), SpearStatus.CHARGING);
        }

        @Override
        protected boolean timedOut(long timestamp) {
            return false;
        }
    }

    public static class Attack extends Behavior<PathfinderMob> {
        private final double speedModifierWhenCharging;
        private final double speedModifierWhenRepositioning;
        private final float targetInRangeRadiusSq;

        public Attack(double speedModifierWhenCharging, double speedModifierWhenRepositioning, float targetInRangeRadius) {
            super(Map.of(Spears.SPEAR_STATUS.get(), MemoryStatus.VALUE_PRESENT));
            this.speedModifierWhenCharging = speedModifierWhenCharging;
            this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
            this.targetInRangeRadiusSq = targetInRangeRadius * targetInRangeRadius;
        }

        private static int useDuration(PathfinderMob mob) {
            return mob.getMainHandItem().getItem() instanceof SpearItem spear ? spear.kinetic().computeDamageUseDuration() : 0;
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob body) {
            return body.getBrain().getMemory(Spears.SPEAR_STATUS.get()).orElse(SpearStatus.APPROACH) == SpearStatus.CHARGING
                    && ableToAttack(body) && !body.isUsingItem();
        }

        @Override
        protected void start(ServerLevel level, PathfinderMob body, long timestamp) {
            body.setAggressive(true);
            body.getBrain().setMemory(Spears.SPEAR_ENGAGE_TIME.get(), useDuration(body));
            body.getBrain().eraseMemory(Spears.SPEAR_CHARGE_POSITION.get());
            body.startUsingItem(InteractionHand.MAIN_HAND);
            super.start(level, body, timestamp);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, PathfinderMob body, long timestamp) {
            return body.getBrain().getMemory(Spears.SPEAR_ENGAGE_TIME.get()).orElse(0) > 0 && ableToAttack(body);
        }

        @Override
        protected void tick(ServerLevel level, PathfinderMob mob, long timestamp) {
            LivingEntity target = target(mob);
            double targetDistSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            float speedModifier = SpearUseGoal.chargeSpeedModifier(mob.getRootVehicle());
            int mountDistance = mob.isPassenger() ? 2 : 0;
            mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
            mob.getBrain().setMemory(Spears.SPEAR_ENGAGE_TIME.get(), mob.getBrain().getMemory(Spears.SPEAR_ENGAGE_TIME.get()).orElse(0) - 1);
            Vec3 awayPos = mob.getBrain().getMemory(Spears.SPEAR_CHARGE_POSITION.get()).orElse(null);
            if (awayPos != null) {
                mob.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, speedModifier * this.speedModifierWhenRepositioning);
                if (mob.getNavigation().isDone()) {
                    mob.getBrain().eraseMemory(Spears.SPEAR_CHARGE_POSITION.get());
                }
            } else {
                mob.getNavigation().moveTo(target, speedModifier * this.speedModifierWhenCharging);
                if (targetDistSqr < this.targetInRangeRadiusSq || mob.getNavigation().isDone()) {
                    double distance = Math.sqrt(targetDistSqr);
                    Vec3 newAwayPos = SpearPos.getPosAway(mob, 6 + mountDistance - distance, 7 + mountDistance - distance, 7, target.position());
                    mob.getBrain().setMemory(Spears.SPEAR_CHARGE_POSITION.get(), newAwayPos);
                }
            }
        }

        @Override
        protected void stop(ServerLevel level, PathfinderMob body, long timestamp) {
            body.getNavigation().stop();
            body.stopUsingItem();
            body.getBrain().eraseMemory(Spears.SPEAR_CHARGE_POSITION.get());
            body.getBrain().eraseMemory(Spears.SPEAR_ENGAGE_TIME.get());
            body.getBrain().setMemory(Spears.SPEAR_STATUS.get(), SpearStatus.RETREAT);
        }

        @Override
        protected boolean timedOut(long timestamp) {
            return false;
        }
    }

    public static class Retreat extends Behavior<PathfinderMob> {
        private final double speedModifierWhenRepositioning;

        public Retreat(double speedModifierWhenRepositioning) {
            super(Map.of(Spears.SPEAR_STATUS.get(), MemoryStatus.VALUE_PRESENT), 100);
            this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob body) {
            if (!ableToAttack(body) || body.isUsingItem()
                    || body.getBrain().getMemory(Spears.SPEAR_STATUS.get()).orElse(SpearStatus.APPROACH) != SpearStatus.RETREAT) {
                return false;
            }
            LivingEntity target = target(body);
            double targetDistSqr = body.distanceToSqr(target.getX(), target.getY(), target.getZ());
            int mountDistance = body.isPassenger() ? 2 : 0;
            double distance = Math.sqrt(targetDistSqr);
            Vec3 awayPos = SpearPos.getPosAway(body, Math.max(0.0, 9 + mountDistance - distance), Math.max(1.0, 11 + mountDistance - distance), 7,
                    target.position());
            if (awayPos == null) {
                return false;
            }
            body.getBrain().setMemory(Spears.SPEAR_FLEEING_POSITION.get(), awayPos);
            return true;
        }

        @Override
        protected void start(ServerLevel level, PathfinderMob body, long timestamp) {
            body.setAggressive(true);
            body.getBrain().setMemory(Spears.SPEAR_FLEEING_TIME.get(), 0);
            super.start(level, body, timestamp);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, PathfinderMob body, long timestamp) {
            return body.getBrain().getMemory(Spears.SPEAR_FLEEING_TIME.get()).orElse(100) < 100
                    && body.getBrain().getMemory(Spears.SPEAR_FLEEING_POSITION.get()).isPresent()
                    && !body.getNavigation().isDone() && ableToAttack(body);
        }

        @Override
        protected void tick(ServerLevel level, PathfinderMob mob, long timestamp) {
            LivingEntity target = target(mob);
            float speedModifier = SpearUseGoal.chargeSpeedModifier(mob.getRootVehicle());
            mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
            mob.getBrain().setMemory(Spears.SPEAR_FLEEING_TIME.get(), mob.getBrain().getMemory(Spears.SPEAR_FLEEING_TIME.get()).orElse(0) + 1);
            mob.getBrain().getMemory(Spears.SPEAR_FLEEING_POSITION.get())
                    .ifPresent(fleePos -> mob.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, speedModifier * this.speedModifierWhenRepositioning));
        }

        @Override
        protected void stop(ServerLevel level, PathfinderMob body, long timestamp) {
            body.getNavigation().stop();
            body.setAggressive(false);
            body.stopUsingItem();
            body.getBrain().eraseMemory(Spears.SPEAR_FLEEING_TIME.get());
            body.getBrain().eraseMemory(Spears.SPEAR_FLEEING_POSITION.get());
            body.getBrain().eraseMemory(Spears.SPEAR_STATUS.get());
        }
    }
}
