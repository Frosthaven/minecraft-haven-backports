package dev.frosthaven.havenbackports.creaking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * 26.x Creaking's inner control classes: each runs only while the creaking can move (no one looks at it). 26.x
 * StandardLookControl is 1.21.1's LookControl; 26.x MoveControl is generic, 1.21.1's is not.
 */
final class CreakingControls {
    private CreakingControls() {}

    static final class Body extends BodyRotationControl {
        private final Creaking creaking;

        Body(Creaking creaking) {
            super(creaking);
            this.creaking = creaking;
        }

        @Override
        public void clientTick() {
            if (this.creaking.canMove()) {
                super.clientTick();
            }
        }
    }

    static final class Jump extends JumpControl {
        private final Creaking creaking;

        Jump(Creaking creaking) {
            super(creaking);
            this.creaking = creaking;
        }

        @Override
        public void tick() {
            if (this.creaking.canMove()) {
                super.tick();
            } else {
                this.creaking.setJumping(false);
            }
        }
    }

    static final class Look extends LookControl {
        private final Creaking creaking;

        Look(Creaking creaking) {
            super(creaking);
            this.creaking = creaking;
        }

        @Override
        public void tick() {
            if (this.creaking.canMove()) {
                super.tick();
            }
        }
    }

    static final class Move extends MoveControl {
        private final Creaking creaking;

        Move(Creaking creaking) {
            super(creaking);
            this.creaking = creaking;
        }

        @Override
        public void tick() {
            if (this.creaking.canMove()) {
                super.tick();
            }
        }
    }

    static final class Navigation extends GroundPathNavigation {
        private final Creaking creaking;

        Navigation(Creaking creaking, Level level) {
            super(creaking, level);
            this.creaking = creaking;
        }

        @Override
        public void tick() {
            if (this.creaking.canMove()) {
                super.tick();
            }
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = new HomeNodeEvaluator();
            this.nodeEvaluator.setCanPassDoors(true);
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }
    }

    /** Never paths farther than 32 blocks from the heart, unless it is already farther (then only back toward it).
     *  1.21.1's navigation builds the evaluator in its constructor, before the creaking is set: it reads the mob that
     *  prepare() gives it. */
    static final class HomeNodeEvaluator extends WalkNodeEvaluator {
        private static final int MAX_DISTANCE_TO_HOME_SQ = 1024;

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            BlockPos homePos = this.mob instanceof Creaking creaking ? creaking.getHomePos() : null;
            if (homePos == null) {
                return super.getPathType(context, x, y, z);
            }
            double homeDistance = homePos.distSqr(new Vec3i(x, y, z));
            return homeDistance > MAX_DISTANCE_TO_HOME_SQ && homeDistance >= homePos.distSqr(context.mobPosition())
                    ? PathType.BLOCKED : super.getPathType(context, x, y, z);
        }
    }
}
