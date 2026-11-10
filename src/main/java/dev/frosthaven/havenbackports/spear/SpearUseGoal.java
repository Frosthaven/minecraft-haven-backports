package dev.frosthaven.havenbackports.spear;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x {@code SpearUseGoal}, line for line: approach to {@code approachDistance}, charge with the spear in use, back off
 * 6..7 blocks when within {@code targetInRangeRadius}, then stop using and flee 9..11 blocks. "Has KINETIC_WEAPON" is
 * "holds a {@link SpearItem}"; {@code LandRandomPos.getPosAway(min, max)} is {@link SpearPos}.
 */
public class SpearUseGoal<T extends Monster> extends Goal {
    private static final double MAX_FLEEING_TIME = reducedTickDelay(100);
    private final T mob;
    @Nullable
    private SpearUseState state;
    private final double speedModifierWhenCharging;
    private final double speedModifierWhenRepositioning;
    private final float approachDistanceSq;
    private final float targetInRangeRadiusSq;

    public SpearUseGoal(T mob, double speedModifierWhenCharging, double speedModifierWhenRepositioning, float approachDistance, float targetInRangeRadius) {
        this.mob = mob;
        this.speedModifierWhenCharging = speedModifierWhenCharging;
        this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
        this.approachDistanceSq = approachDistance * approachDistance;
        this.targetInRangeRadiusSq = targetInRangeRadius * targetInRangeRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** 26.x Mob.chargeSpeedModifier of the mount: 1.4 for a zombie horse (camel husks are 26.x-only), else 1.0. */
    static float chargeSpeedModifier(Entity mount) {
        return mount instanceof ZombieHorse ? 1.4F : 1.0F;
    }

    @Override
    public boolean canUse() {
        return this.ableToAttack() && !this.mob.isUsingItem();
    }

    private boolean ableToAttack() {
        return this.mob.getTarget() != null && this.mob.getMainHandItem().getItem() instanceof SpearItem;
    }

    private int getKineticWeaponUseDuration() {
        int durationTicks = this.mob.getMainHandItem().getItem() instanceof SpearItem spear ? spear.kinetic().computeDamageUseDuration() : 0;
        return reducedTickDelay(durationTicks);
    }

    @Override
    public boolean canContinueToUse() {
        return this.state != null && !this.state.done && this.ableToAttack();
    }

    @Override
    public void start() {
        super.start();
        this.mob.setAggressive(true);
        this.state = new SpearUseState();
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.getNavigation().stop();
        this.mob.setAggressive(false);
        this.state = null;
        this.mob.stopUsingItem();
    }

    @Override
    public void tick() {
        if (this.state == null) {
            return;
        }
        LivingEntity target = this.mob.getTarget();
        double targetDistSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        float speedModifier = chargeSpeedModifier(this.mob.getRootVehicle());
        int mountDistance = this.mob.isPassenger() ? 2 : 0;
        this.mob.lookAt(target, 30.0F, 30.0F);
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.state.notEngagedYet()) {
            if (targetDistSqr > this.approachDistanceSq) {
                this.mob.getNavigation().moveTo(target, speedModifier * this.speedModifierWhenRepositioning);
                return;
            }
            this.state.startEngagement(this.getKineticWeaponUseDuration());
            this.mob.startUsingItem(InteractionHand.MAIN_HAND);
        }
        if (this.state.tickAndCheckEngagement()) {
            this.mob.stopUsingItem();
            double distance = Math.sqrt(targetDistSqr);
            this.state.awayPos = SpearPos.getPosAway(this.mob, Math.max(0.0, 9 + mountDistance - distance), Math.max(1.0, 11 + mountDistance - distance),
                    7, target.position());
            this.state.fleeingTime = 1;
        }
        if (!this.state.tickAndCheckFleeing()) {
            if (this.state.awayPos != null) {
                this.mob.getNavigation().moveTo(this.state.awayPos.x, this.state.awayPos.y, this.state.awayPos.z, speedModifier * this.speedModifierWhenRepositioning);
                if (this.mob.getNavigation().isDone()) {
                    if (this.state.fleeingTime > 0) {
                        this.state.done = true;
                        return;
                    }
                    this.state.awayPos = null;
                }
            } else {
                this.mob.getNavigation().moveTo(target, speedModifier * this.speedModifierWhenCharging);
                if (targetDistSqr < this.targetInRangeRadiusSq || this.mob.getNavigation().isDone()) {
                    double distance = Math.sqrt(targetDistSqr);
                    this.state.awayPos = SpearPos.getPosAway(this.mob, 6 + mountDistance - distance, 7 + mountDistance - distance, 7, target.position());
                }
            }
        }
    }

    static class SpearUseState {
        private int engageTime = -1;
        private int fleeingTime = -1;
        @Nullable
        private Vec3 awayPos;
        private boolean done = false;

        boolean notEngagedYet() {
            return this.engageTime < 0;
        }

        void startEngagement(int spearDownTime) {
            this.engageTime = spearDownTime;
        }

        boolean tickAndCheckEngagement() {
            if (this.engageTime > 0) {
                this.engageTime--;
                if (this.engageTime == 0) {
                    return true;
                }
            }
            return false;
        }

        boolean tickAndCheckFleeing() {
            if (this.fleeingTime > 0) {
                this.fleeingTime++;
                if (this.fleeingTime > MAX_FLEEING_TIME) {
                    this.done = true;
                    return true;
                }
            }
            return false;
        }
    }
}
