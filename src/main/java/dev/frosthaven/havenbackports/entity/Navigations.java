package dev.frosthaven.havenbackports.entity;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x PathNavigation.setRequiredPathLength(48), which both the ghastling and the copper golem set: paths reach
 * max(follow range, 48) and the path finder visits up to that times 16 nodes (26.x getMaxPathLength). 1.21.1 has no
 * such setting, so these navigations carry it (the path finder is made in the super constructor, hence the constant).
 */
public final class Navigations {
    static final float REQUIRED_PATH_LENGTH = 48.0F;

    private Navigations() {}

    static float range(Mob mob) {
        return Math.max((float) mob.getAttributeValue(Attributes.FOLLOW_RANGE), REQUIRED_PATH_LENGTH);
    }

    /** HappyGhast.BabyFlyingPathNavigation: no doors, floats, direct moves without swimming. */
    public static class Flying extends FlyingPathNavigation {
        public Flying(Mob mob, Level level) {
            super(mob, level);
            this.setCanOpenDoors(false);
            this.setCanFloat(true);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            return super.createPathFinder(Math.max(maxVisitedNodes, Mth.floor(REQUIRED_PATH_LENGTH * 16.0F)));
        }

        @Override
        @Nullable
        protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy) {
            return this.createPath(targets, regionOffset, offsetUpward, accuracy, range(this.mob));
        }

        @Override
        protected boolean canMoveDirectly(Vec3 start, Vec3 end) {
            return isClearForMovementBetween(this.mob, start, end, false);
        }
    }

    /** CopperGolem's ground navigation with the required path length, and 26.x's setCanPathToTargetsBelowSurface
     *  (a target block is not moved up to the surface above it; 1.21.1's GroundPathNavigation always moves it). */
    public static class Ground extends GroundPathNavigation {
        private boolean canPathToTargetsBelowSurface;

        public Ground(Mob mob, Level level) {
            super(mob, level);
        }

        public void setCanPathToTargetsBelowSurface(boolean value) {
            this.canPathToTargetsBelowSurface = value;
        }

        @Override
        @Nullable
        public Path createPath(BlockPos pos, int accuracy) {
            if (canPathToTargetsBelowSurface && this.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null) {
                return this.createPath(com.google.common.collect.ImmutableSet.of(pos), 8, false, accuracy);
            }
            return super.createPath(pos, accuracy);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            return super.createPathFinder(Math.max(maxVisitedNodes, Mth.floor(REQUIRED_PATH_LENGTH * 16.0F)));
        }

        @Override
        @Nullable
        protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy) {
            return this.createPath(targets, regionOffset, offsetUpward, accuracy, range(this.mob));
        }
    }
}
