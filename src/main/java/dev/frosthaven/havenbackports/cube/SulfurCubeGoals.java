package dev.frosthaven.havenbackports.cube;

import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x SulfurCube's inner classes: SulfurCubeLookControl, SulfurCubeMobMoveControl, SulfurCubeSearchForItemsGoal and
 * SulfurCubeTemptGoal (26.x TemptGoal.ForNonPathfinders, which 1.21.1 lacks: 1.21.1's TemptGoal steers by path
 * navigation only, so the 26.x goal is ported whole). 26.x reads the tempt range from its tempt_range attribute;
 * 1.21.1 has none, so it is the Sulfur Cube's value, 8.
 */
final class SulfurCubeGoals {
    static final double TEMPT_RANGE = 8.0;

    private SulfurCubeGoals() {}

    static class Look extends LookControl {
        private final SulfurCube cube;

        Look(SulfurCube cube) {
            super(cube);
            this.cube = cube;
        }

        @Override
        public void tick() {
            if (!this.cube.hasBodyItem()) {
                super.tick();
            } else {
                float closeAngle = this.cube.getYRot() % 90.0F;   // 26.x Mth.wrapDegrees90
                if (closeAngle >= 45.0F) {
                    closeAngle -= 90.0F;
                }
                if (closeAngle < -45.0F) {
                    closeAngle += 90.0F;
                }
                this.cube.setYRot(this.cube.getYRot() - closeAngle);
                this.cube.setYHeadRot(this.cube.getYRot());
            }
        }
    }

    static class Move extends CubeGoals.MoveControl {
        private final SulfurCube cube;

        Move(SulfurCube cube) {
            super(cube);
            this.cube = cube;
        }

        @Override
        public void tick() {
            if (!this.cube.hasBodyItem()) {
                super.tick();
            }
        }
    }

    static class SearchForItems extends Goal {
        private final SulfurCube cube;
        @Nullable
        private ItemEntity targetItem;

        SearchForItems(SulfurCube cube) {
            this.cube = cube;
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.cube.isBaby() && this.cube.pickupTimer <= 0 && this.cube.level() instanceof ServerLevel level) {
                this.targetItem = null;   // 26.x ServerLevel.getNearestEntity: the closest by distance
                double best = -1.0;
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.cube.getBoundingBox().inflate(8.0, 8.0, 8.0),
                        SulfurCube.ALLOWED_ITEMS)) {
                    double d = item.distanceToSqr(this.cube.getX(), this.cube.getY(), this.cube.getZ());
                    if (best == -1.0 || d < best) {
                        best = d;
                        this.targetItem = item;
                    }
                }
                return this.targetItem != null;
            }
            return false;
        }

        @Override
        public void tick() {
            this.cube.lookAt(this.targetItem, 10.0F, 10.0F);
            if (this.cube.getMoveControl() instanceof CubeGoals.MoveControl control) {
                control.setDirection(this.cube.getYRot(), true);
            }
        }
    }

    /** 26.x TemptGoal + ForNonPathfinders + SulfurCubeTemptGoal (look only; the cube steers by its move control). */
    static class Tempt extends Goal {
        private final Mob mob;
        private final Predicate<ItemStack> items;
        private final TargetingConditions targetingConditions;
        private final double stopDistance;
        @Nullable
        private Player player;
        private int calmDown;

        Tempt(Mob mob, Predicate<ItemStack> items, double stopDistance) {
            this.mob = mob;
            this.items = items;
            this.stopDistance = stopDistance;
            this.setFlags(EnumSet.of(Flag.LOOK));
            this.targetingConditions = TargetingConditions.forNonCombat().ignoreLineOfSight().range(TEMPT_RANGE).selector(this::shouldFollow);
        }

        private boolean shouldFollow(LivingEntity player) {
            return this.items.test(player.getMainHandItem()) || this.items.test(player.getOffhandItem());
        }

        @Override
        public boolean canUse() {
            if (this.calmDown > 0) {
                this.calmDown--;
                return false;
            }
            this.player = this.mob.level().getNearestPlayer(this.targetingConditions, this.mob);
            return this.player != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void stop() {
            this.player = null;
            this.stopNavigation();
            this.calmDown = reducedTickDelay(100);
        }

        @Override
        public void tick() {
            this.mob.getLookControl().setLookAt(this.player, this.mob.getMaxHeadYRot() + 20, this.mob.getMaxHeadXRot());
            if (this.mob.distanceToSqr(this.player) < this.stopDistance * this.stopDistance) {
                this.stopNavigation();
            } else {
                this.mob.lookAt(this.player, 10.0F, 10.0F);
                if (this.mob.getMoveControl() instanceof CubeGoals.MoveControl control) {
                    control.setDirection(this.mob.getYRot(), true);
                }
            }
        }

        private void stopNavigation() {
            if (this.mob.getMoveControl() instanceof CubeGoals.MoveControl control) {
                control.setWantedMovement(0.0);
            }
        }
    }
}
