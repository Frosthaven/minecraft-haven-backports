package dev.frosthaven.havenbackports.cube;

import java.util.EnumSet;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 26.x AbstractCubeMob's inner classes (CubeMobMoveControl, CubeMobFloatGoal, CubeMobRandomDirectionGoal,
 * CubeMobKeepOnJumpingGoal), line for line on 1.21.1's MoveControl and Goal. 26.x's CubeMobAttackGoal belongs to
 * the hostile cubes (slime, magma cube); the Sulfur Cube has no targeting goals, so it is not here.
 */
final class CubeGoals {
    private CubeGoals() {}

    static class MoveControl extends net.minecraft.world.entity.ai.control.MoveControl {
        private final AbstractCubeMob cube;
        private float yRot;
        private int jumpDelay;
        private boolean isAggressive;

        MoveControl(AbstractCubeMob cube) {
            super(cube);
            this.cube = cube;
            this.yRot = 180.0F * cube.getYRot() / (float) Math.PI;
        }

        void setDirection(float yRot, boolean isAggressive) {
            this.yRot = yRot;
            this.isAggressive = isAggressive;
        }

        void setWantedMovement(double speedModifier) {
            this.speedModifier = speedModifier;
            this.operation = Operation.MOVE_TO;
        }

        @Override
        public void tick() {
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), this.yRot, 90.0F));
            this.mob.yHeadRot = this.mob.getYRot();
            this.mob.yBodyRot = this.mob.getYRot();
            if (this.operation != Operation.MOVE_TO) {
                this.mob.setZza(0.0F);
            } else {
                this.operation = Operation.WAIT;
                if (this.mob.onGround()) {
                    this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                    if (this.jumpDelay-- <= 0) {
                        this.jumpDelay = this.cube.getJumpDelay();
                        if (this.isAggressive) {
                            this.jumpDelay /= 3;
                        }
                        this.cube.getJumpControl().jump();
                        if (this.cube.doPlayJumpSound()) {
                            this.cube.playSound(this.cube.getJumpSound(), this.cube.getSoundVolume(), this.cube.getSoundPitch());
                        }
                    } else {
                        this.mob.xxa = 0.0F;
                        this.mob.zza = 0.0F;
                        this.mob.setSpeed(0.0F);
                    }
                } else {
                    this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                }
            }
        }
    }

    static class FloatGoal extends Goal {
        private final AbstractCubeMob cube;

        FloatGoal(AbstractCubeMob cube) {
            this.cube = cube;
            this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
            cube.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
            return this.cube.isInLiquid() && this.cube.getMoveControl() instanceof MoveControl;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.cube.getRandom().nextFloat() < 0.8F) {
                this.cube.getJumpControl().jump();
            }
            if (this.cube.getMoveControl() instanceof MoveControl control) {
                control.setWantedMovement(1.2);
            }
        }
    }

    static class KeepOnJumpingGoal extends Goal {
        private final AbstractCubeMob cube;

        KeepOnJumpingGoal(AbstractCubeMob cube) {
            this.cube = cube;
            this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.cube.isPassenger();
        }

        @Override
        public void tick() {
            if (this.cube.getMoveControl() instanceof MoveControl control) {
                control.setWantedMovement(1.0);
            }
        }
    }

    static class RandomDirectionGoal extends Goal {
        private final AbstractCubeMob cube;
        private float chosenDegrees;
        private int nextRandomizeTime;

        RandomDirectionGoal(AbstractCubeMob cube) {
            this.cube = cube;
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.cube.getTarget() == null && (this.cube.onGround() || this.cube.isInLiquid() || this.cube.hasEffect(MobEffects.LEVITATION))
                    && this.cube.getMoveControl() instanceof MoveControl;
        }

        @Override
        public void tick() {
            if (--this.nextRandomizeTime <= 0) {
                this.nextRandomizeTime = this.adjustedTickDelay(40 + this.cube.getRandom().nextInt(60));
                this.chosenDegrees = this.cube.getRandom().nextInt(360);
            }
            if (this.cube.getMoveControl() instanceof MoveControl control) {
                control.setDirection(this.chosenDegrees, false);
            }
        }
    }
}
