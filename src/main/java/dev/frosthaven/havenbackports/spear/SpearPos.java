package dev.frosthaven.havenbackports.spear;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x {@code LandRandomPos.getPosAway(mob, double min, double max, int vertical, Vec3)} and the chain under it
 * (getPosInDirection, RandomPos.generateRandomDirectionWithinRadians and generateRandomPosTowardDirection with double
 * distances, GoalUtils.mobRestricted(double)): 1.21.1 has only the int-radius forms, with no minimum distance.
 * 26.x's "home" is 1.21.1's restriction (hasRestriction, getRestrictCenter, getRestrictRadius, isWithinRestriction).
 */
public final class SpearPos {
    private SpearPos() {}

    @Nullable
    public static Vec3 getPosAway(PathfinderMob mob, double minHorizontalDist, double maxHorizontalDist, int verticalDist, Vec3 avoidPos) {
        Vec3 dirAway = mob.position().subtract(avoidPos);
        if (dirAway.length() == 0.0) {
            dirAway = new Vec3(mob.getRandom().nextDouble() - 0.5, 0.0, mob.getRandom().nextDouble() - 0.5);
        }
        boolean restrict = mob.hasRestriction()
                && mob.getRestrictCenter().closerToCenterThan(mob.position(), mob.getRestrictRadius() + maxHorizontalDist + 1.0);
        Vec3 dir = dirAway;
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos direction = generateRandomDirectionWithinRadians(mob.getRandom(), minHorizontalDist, maxHorizontalDist, verticalDist, 0,
                    dir.x, dir.z, (float) (Math.PI / 2));
            if (direction == null) {
                return null;
            }
            BlockPos pos = generateRandomPosTowardDirection(mob, maxHorizontalDist, restrict, direction);
            return pos == null ? null : LandRandomPos.movePosUpOutOfSolid(mob, pos);
        });
    }

    @Nullable
    private static BlockPos generateRandomDirectionWithinRadians(RandomSource random, double minHorizontalDist, double maxHorizontalDist,
            int verticalDist, int flyingHeight, double xDir, double zDir, double maxXzRadiansFromDir) {
        double yRadiansCenter = Mth.atan2(zDir, xDir) - (float) (Math.PI / 2);
        double yRadians = yRadiansCenter + (2.0F * random.nextFloat() - 1.0F) * maxXzRadiansFromDir;
        double dist = Mth.lerp(Math.sqrt(random.nextDouble()), minHorizontalDist, maxHorizontalDist) * Mth.SQRT_OF_TWO;
        double xt = -dist * Math.sin(yRadians);
        double zt = dist * Math.cos(yRadians);
        if (!(Math.abs(xt) > maxHorizontalDist) && !(Math.abs(zt) > maxHorizontalDist)) {
            int yt = random.nextInt(2 * verticalDist + 1) - verticalDist + flyingHeight;
            return BlockPos.containing(xt, yt, zt);
        }
        return null;
    }

    @Nullable
    private static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, double horizontalDist, boolean restrict, BlockPos direction) {
        double xt = direction.getX();
        double zt = direction.getZ();
        RandomSource random = mob.getRandom();
        if (mob.hasRestriction() && horizontalDist > 1.0) {
            BlockPos center = mob.getRestrictCenter();
            xt += (mob.getX() > center.getX() ? -1 : 1) * random.nextDouble() * horizontalDist / 2.0;
            zt += (mob.getZ() > center.getZ() ? -1 : 1) * random.nextDouble() * horizontalDist / 2.0;
        }
        BlockPos pos = BlockPos.containing(xt + mob.getX(), direction.getY() + mob.getY(), zt + mob.getZ());
        return !GoalUtils.isOutsideLimits(pos, mob) && !GoalUtils.isRestricted(restrict, mob, pos) && !GoalUtils.isNotStable(mob.getNavigation(), pos)
                ? pos : null;
    }
}
