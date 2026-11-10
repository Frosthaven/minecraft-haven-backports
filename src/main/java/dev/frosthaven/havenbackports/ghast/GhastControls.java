package dev.frosthaven.havenbackports.ghast;

import dev.frosthaven.havenbackports.Ghasts;
import dev.frosthaven.havenbackports.entity.Traversal;
import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x ghast movement, which 1.21.1 has only as package-private ghast internals of an older form:
 * Ghast.GhastMoveControl (careful, stopped while on still timeout), Ghast.RandomFloatAroundGoal (with the
 * distance-to-blocks rule), Ghast.faceMovementDirection, and TemptGoal.ForNonPathfinders (1.21.1's TemptGoal takes a
 * PathfinderMob and has a fixed range; 26.x's reads the tempt range, here 16 as HappyGhast's TEMPT_RANGE attribute).
 */
public final class GhastControls {
    private GhastControls() {}

    /** 26.x Mth.wrapDegrees90. */
    static float wrapDegrees90(float angle) {
        float normalized = angle % 90.0F;
        if (normalized >= 45.0F) {
            normalized -= 90.0F;
        }
        if (normalized < -45.0F) {
            normalized += 90.0F;
        }
        return normalized;
    }

    public static void faceMovementDirection(Mob ghast) {
        if (ghast.getTarget() == null) {
            Vec3 movement = ghast.getDeltaMovement();
            ghast.setYRot(-((float) Mth.atan2(movement.x, movement.z)) * (180.0F / (float) Math.PI));
            ghast.yBodyRot = ghast.getYRot();
        } else {
            LivingEntity target = ghast.getTarget();
            if (target.distanceToSqr(ghast) < 4096.0) {
                ghast.setYRot(-((float) Mth.atan2(target.getX() - ghast.getX(), target.getZ() - ghast.getZ())) * (180.0F / (float) Math.PI));
                ghast.yBodyRot = ghast.getYRot();
            }
        }
    }

    public static class GhastMoveControl extends MoveControl {
        private int floatDuration;
        private final boolean careful;
        private final BooleanSupplier shouldBeStopped;

        public GhastMoveControl(Mob ghast, boolean careful, BooleanSupplier shouldBeStopped) {
            super(ghast);
            this.careful = careful;
            this.shouldBeStopped = shouldBeStopped;
        }

        /** 26.x MoveControl.setWait. */
        public void setWait() {
            this.operation = Operation.WAIT;
        }

        @Override
        public void tick() {
            if (shouldBeStopped.getAsBoolean()) {
                this.operation = Operation.WAIT;
                this.mob.stopInPlace();
            }
            if (this.operation == Operation.MOVE_TO && floatDuration-- <= 0) {
                floatDuration = floatDuration + this.mob.getRandom().nextInt(5) + 2;
                Vec3 travel = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
                if (canReach(travel)) {
                    this.mob.addDeltaMovement(travel.normalize().scale(this.mob.getAttributeValue(Attributes.FLYING_SPEED) * 5.0 / 3.0));
                } else {
                    this.operation = Operation.WAIT;
                }
            }
        }

        private boolean canReach(Vec3 travel) {
            AABB box = this.mob.getBoundingBox();
            AABB atDestination = box.move(travel);
            if (careful) {
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(atDestination.minX - 1, atDestination.minY - 1, atDestination.minZ - 1),
                        BlockPos.containing(atDestination.maxX + 1, atDestination.maxY + 1, atDestination.maxZ + 1))) {
                    if (!traversable(this.mob.level(), null, null, pos, false, false)) {
                        return false;
                    }
                }
            }
            boolean inWater = this.mob.isInWater();
            boolean inLava = this.mob.isInLava();
            Vec3 start = this.mob.position();
            Vec3 end = start.add(travel);
            return Traversal.forEachBlockIntersectedBetween(start, end, atDestination,
                    (pos, i) -> box.intersects(new AABB(pos)) || traversable(this.mob.level(), start, end, pos, inWater, inLava));
        }

        private boolean traversable(BlockGetter level, @Nullable Vec3 start, @Nullable Vec3 end, BlockPos pos, boolean throughWater, boolean throughLava) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return true;
            }
            boolean precise = start != null && end != null;
            boolean noCollisions = precise
                    ? !Traversal.collidedWithShapeMovingFrom(this.mob, start, end, state.getCollisionShape(level, pos).move(pos.getX(), pos.getY(), pos.getZ()).toAabbs())
                    : state.getCollisionShape(level, pos).isEmpty();
            if (!careful) {
                return noCollisions;
            }
            if (state.is(Ghasts.HAPPY_GHAST_AVOIDS)) {
                return false;
            }
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty() && (!precise || Traversal.collidedWithFluid(this.mob, fluid, pos, start, end))) {
                if (fluid.is(FluidTags.WATER)) {
                    return throughWater;
                }
                if (fluid.is(FluidTags.LAVA)) {
                    return throughLava;
                }
            }
            return noCollisions;
        }
    }

    public static class RandomFloatAroundGoal extends Goal {
        private final Mob ghast;
        private final int distanceToBlocks;

        public RandomFloatAroundGoal(Mob ghast, int distanceToBlocks) {
            this.ghast = ghast;
            this.distanceToBlocks = distanceToBlocks;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl control = ghast.getMoveControl();
            if (!control.hasWanted()) {
                return true;
            }
            double xd = control.getWantedX() - ghast.getX(), yd = control.getWantedY() - ghast.getY(), zd = control.getWantedZ() - ghast.getZ();
            double dd = xd * xd + yd * yd + zd * zd;
            return dd < 1.0 || dd > 3600.0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Vec3 result = getSuitableFlyToPosition(ghast, distanceToBlocks);
            ghast.getMoveControl().setWantedPosition(result.x(), result.y(), result.z(), 1.0);
        }

        public static Vec3 getSuitableFlyToPosition(Mob mob, int distanceToBlocks) {
            Level level = mob.level();
            RandomSource random = mob.getRandom();
            Vec3 center = mob.position();
            Vec3 result = null;
            for (int i = 0; i < 64; i++) {
                result = chooseRandomPositionWithRestriction(mob, center, random);
                if (result != null && isGoodTarget(level, result, distanceToBlocks)) {
                    return result;
                }
            }
            if (result == null) {
                result = chooseRandomPosition(center, random);
            }
            BlockPos pos = BlockPos.containing(result);
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
            if (height < pos.getY() && height > level.getMinBuildHeight()) {
                result = new Vec3(result.x(), mob.getY() - Math.abs(mob.getY() - result.y()), result.z());
            }
            return result;
        }

        private static boolean isGoodTarget(Level level, Vec3 target, int distanceToBlocks) {
            if (distanceToBlocks <= 0) {
                return true;
            }
            BlockPos pos = BlockPos.containing(target);
            if (!level.getBlockState(pos).isAir()) {
                return false;
            }
            for (Direction dir : Direction.values()) {
                for (int i = 1; i < distanceToBlocks; i++) {
                    if (!level.getBlockState(pos.relative(dir, i)).isAir()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static Vec3 chooseRandomPosition(Vec3 center, RandomSource random) {
            return new Vec3(center.x() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F, center.y() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F,
                    center.z() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F);
        }

        @Nullable
        private static Vec3 chooseRandomPositionWithRestriction(Mob mob, Vec3 center, RandomSource random) {
            Vec3 target = chooseRandomPosition(center, random);
            return mob.hasRestriction() && !mob.isWithinRestriction(BlockPos.containing(target)) ? null : target;
        }
    }

    /** 26.x TemptGoal with ForNonPathfinders' movement (canScare false, as the happy ghast uses it). */
    public static class TemptForNonPathfinders extends Goal {
        private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();
        private final Mob mob;
        private final double speedModifier, stopDistance, range;
        private final Predicate<ItemStack> items;
        private final TargetingConditions targeting;
        @Nullable
        private Player player;
        private int calmDown;

        public TemptForNonPathfinders(Mob mob, double speedModifier, Predicate<ItemStack> items, double stopDistance, double range) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.items = items;
            this.stopDistance = stopDistance;
            this.range = range;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
            this.targeting = TEMPT_TARGETING.copy().selector(p -> items.test(p.getMainHandItem()) || items.test(p.getOffhandItem()));
        }

        @Override
        public boolean canUse() {
            if (calmDown > 0) {
                calmDown--;
                return false;
            }
            player = ((ServerLevel) mob.level()).getNearestPlayer(targeting.range(range), mob);
            return player != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            player = null;
            ((GhastMoveControl) mob.getMoveControl()).setWait();
            calmDown = reducedTickDelay(100);
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(player, mob.getMaxHeadYRot() + 20, mob.getMaxHeadXRot());
            if (mob.distanceToSqr(player) < stopDistance * stopDistance) {
                ((GhastMoveControl) mob.getMoveControl()).setWait();
            } else {
                Vec3 target = player.getEyePosition().subtract(mob.position()).scale(mob.getRandom().nextDouble()).add(mob.position());
                mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, speedModifier);
            }
        }
    }

    /** HappyGhast.HappyGhastFloatGoal: floats unless holding still. */
    public static class HappyGhastFloatGoal extends net.minecraft.world.entity.ai.goal.FloatGoal {
        private final HappyGhast ghast;

        public HappyGhastFloatGoal(HappyGhast ghast) {
            super(ghast);
            this.ghast = ghast;
        }

        @Override
        public boolean canUse() {
            return !ghast.isOnStillTimeout() && super.canUse();
        }
    }

    /** HappyGhast.HappyGhastLookControl: snaps to a quarter turn while holding still. */
    public static class HappyGhastLookControl extends net.minecraft.world.entity.ai.control.LookControl {
        private final HappyGhast ghast;

        public HappyGhastLookControl(HappyGhast ghast) {
            super(ghast);
            this.ghast = ghast;
        }

        @Override
        public void tick() {
            if (ghast.isOnStillTimeout()) {
                float closeAngle = wrapDegrees90(ghast.getYRot());
                ghast.setYRot(ghast.getYRot() - closeAngle);
                ghast.setYHeadRot(ghast.getYRot());
            } else if (this.lookAtCooldown > 0) {
                this.lookAtCooldown--;
                ghast.setYRot(-((float) Mth.atan2(this.wantedX - ghast.getX(), this.wantedZ - ghast.getZ())) * (180.0F / (float) Math.PI));
                ghast.yBodyRot = ghast.getYRot();
                ghast.yHeadRot = ghast.yBodyRot;
            } else {
                faceMovementDirection(this.mob);
            }
        }
    }

    /** HappyGhast.HappyGhastBodyRotationControl: body and head follow the ghast's yaw while ridden. */
    public static class HappyGhastBodyRotationControl extends net.minecraft.world.entity.ai.control.BodyRotationControl {
        private final HappyGhast ghast;

        public HappyGhastBodyRotationControl(HappyGhast ghast) {
            super(ghast);
            this.ghast = ghast;
        }

        @Override
        public void clientTick() {
            if (ghast.isVehicle()) {
                ghast.yHeadRot = ghast.getYRot();
                ghast.yBodyRot = ghast.yHeadRot;
            }
            super.clientTick();
        }
    }
}
