package dev.frosthaven.havenbackports.entity;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x movement traversal that 1.21.1 lacks, copied from Mojang: BlockGetter.forEachBlockIntersectedBetween (with
 * addCollisionsAlongTravel and getFurthestCorner), BlockPos.betweenCornersInDirection, Direction.axisStepOrder,
 * AABB.collidedAlongVector and Entity.collidedWithShapeMovingFrom / collidedWithFluid. 26.x's static
 * AABB.clip(min..max, from, to) is 1.21.1's new AABB(min..max).clip(from, to).
 */
public final class Traversal {
    private static final ImmutableList<Direction.Axis> YXZ = ImmutableList.of(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z);
    private static final ImmutableList<Direction.Axis> YZX = ImmutableList.of(Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X);

    @FunctionalInterface
    public interface BlockStepVisitor {
        boolean visit(BlockPos pos, int iteration);
    }

    private Traversal() {}

    public static boolean collidedWithFluid(Entity entity, FluidState fluid, BlockPos pos, Vec3 from, Vec3 to) {
        if (fluid.isEmpty()) {
            return false;
        }
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + fluid.getHeight(entity.level(), pos), pos.getZ() + 1.0);
        return collidedWithShapeMovingFrom(entity, from, to, List.of(box));   // 26.x Fluid.getAABB
    }

    public static boolean collidedWithShapeMovingFrom(Entity entity, Vec3 from, Vec3 to, List<AABB> boxes) {
        AABB atFrom = entity.getDimensions(entity.getPose()).makeBoundingBox(from);
        return collidedAlongVector(atFrom, to.subtract(from), boxes);
    }

    static boolean collidedAlongVector(AABB self, Vec3 vector, List<AABB> boxes) {
        Vec3 from = self.getCenter();
        Vec3 to = from.add(vector);
        for (AABB part : boxes) {
            AABB inflated = part.inflate(self.getXsize() * 0.5 - 1.0E-7, self.getYsize() * 0.5 - 1.0E-7, self.getZsize() * 0.5 - 1.0E-7);
            if (inflated.contains(to) || inflated.contains(from) || inflated.clip(from, to).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public static boolean forEachBlockIntersectedBetween(Vec3 from, Vec3 to, AABB aabbAtTarget, BlockStepVisitor visitor) {
        Vec3 travel = to.subtract(from);
        if (travel.lengthSqr() < Mth.square(1.0E-5F)) {
            for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(aabbAtTarget.minX), Mth.floor(aabbAtTarget.minY), Mth.floor(aabbAtTarget.minZ),
                    Mth.floor(aabbAtTarget.maxX), Mth.floor(aabbAtTarget.maxY), Mth.floor(aabbAtTarget.maxZ))) {
                if (!visitor.visit(pos, 0)) {
                    return false;
                }
            }
            return true;
        }
        LongSet visited = new LongOpenHashSet();
        for (BlockPos pos : betweenCornersInDirection(aabbAtTarget.move(travel.scale(-1.0)), travel)) {
            if (!visitor.visit(pos, 0)) {
                return false;
            }
            visited.add(pos.asLong());
        }
        int iterations = addCollisionsAlongTravel(visited, travel, aabbAtTarget, visitor);
        if (iterations < 0) {
            return false;
        }
        for (BlockPos pos : betweenCornersInDirection(aabbAtTarget, travel)) {
            if (visited.add(pos.asLong()) && !visitor.visit(pos, iterations + 1)) {
                return false;
            }
        }
        return true;
    }

    private static int addCollisionsAlongTravel(LongSet visited, Vec3 deltaMove, AABB aabbAtTarget, BlockStepVisitor visitor) {
        double sizeX = aabbAtTarget.getXsize(), sizeY = aabbAtTarget.getYsize(), sizeZ = aabbAtTarget.getZsize();
        Vec3i cornerDir = getFurthestCorner(deltaMove);
        Vec3 toCenter = aabbAtTarget.getCenter();
        Vec3 toCorner = new Vec3(toCenter.x() + sizeX * 0.5 * cornerDir.getX(), toCenter.y() + sizeY * 0.5 * cornerDir.getY(),
                toCenter.z() + sizeZ * 0.5 * cornerDir.getZ());
        Vec3 fromCorner = toCorner.subtract(deltaMove);
        int bx = Mth.floor(fromCorner.x), by = Mth.floor(fromCorner.y), bz = Mth.floor(fromCorner.z);
        int signX = Mth.sign(deltaMove.x), signY = Mth.sign(deltaMove.y), signZ = Mth.sign(deltaMove.z);
        double tDeltaX = signX == 0 ? Double.MAX_VALUE : signX / deltaMove.x;
        double tDeltaY = signY == 0 ? Double.MAX_VALUE : signY / deltaMove.y;
        double tDeltaZ = signZ == 0 ? Double.MAX_VALUE : signZ / deltaMove.z;
        double tX = tDeltaX * (signX > 0 ? 1.0 - Mth.frac(fromCorner.x) : Mth.frac(fromCorner.x));
        double tY = tDeltaY * (signY > 0 ? 1.0 - Mth.frac(fromCorner.y) : Mth.frac(fromCorner.y));
        double tZ = tDeltaZ * (signZ > 0 ? 1.0 - Mth.frac(fromCorner.z) : Mth.frac(fromCorner.z));
        int iterations = 0;
        while (tX <= 1.0 || tY <= 1.0 || tZ <= 1.0) {
            if (tX < tY) {
                if (tX < tZ) {
                    bx += signX;
                    tX += tDeltaX;
                } else {
                    bz += signZ;
                    tZ += tDeltaZ;
                }
            } else if (tY < tZ) {
                by += signY;
                tY += tDeltaY;
            } else {
                bz += signZ;
                tZ += tDeltaZ;
            }
            Optional<Vec3> hit = new AABB(bx, by, bz, bx + 1, by + 1, bz + 1).clip(fromCorner, toCorner);
            if (hit.isPresent()) {
                iterations++;
                Vec3 h = hit.get();
                double cx = Math.clamp(h.x, bx + 1.0E-5F, bx + 1.0 - 1.0E-5F);
                double cy = Math.clamp(h.y, by + 1.0E-5F, by + 1.0 - 1.0E-5F);
                double cz = Math.clamp(h.z, bz + 1.0E-5F, bz + 1.0 - 1.0E-5F);
                int ox = Mth.floor(cx - sizeX * cornerDir.getX());
                int oy = Mth.floor(cy - sizeY * cornerDir.getY());
                int oz = Mth.floor(cz - sizeZ * cornerDir.getZ());
                int current = iterations;
                for (BlockPos pos : betweenCornersInDirection(bx, by, bz, ox, oy, oz, deltaMove)) {
                    if (visited.add(pos.asLong()) && !visitor.visit(pos, current)) {
                        return -1;
                    }
                }
            }
        }
        return iterations;
    }

    private static Vec3i getFurthestCorner(Vec3 direction) {
        double xDot = Math.abs(direction.x), yDot = Math.abs(direction.y), zDot = Math.abs(direction.z);
        int xSign = direction.x >= 0.0 ? 1 : -1;
        int ySign = direction.y >= 0.0 ? 1 : -1;
        int zSign = direction.z >= 0.0 ? 1 : -1;
        if (xDot <= yDot && xDot <= zDot) {
            return new Vec3i(-xSign, -zSign, ySign);
        }
        return yDot <= zDot ? new Vec3i(zSign, -ySign, -xSign) : new Vec3i(-ySign, xSign, -zSign);
    }

    static Iterable<BlockPos> betweenCornersInDirection(AABB aabb, Vec3 direction) {
        return betweenCornersInDirection(Mth.floor(aabb.minX), Mth.floor(aabb.minY), Mth.floor(aabb.minZ),
                Mth.floor(aabb.maxX), Mth.floor(aabb.maxY), Mth.floor(aabb.maxZ), direction);
    }

    static Iterable<BlockPos> betweenCornersInDirection(int x1, int y1, int z1, int x2, int y2, int z2, Vec3 direction) {
        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
        int diffX = maxX - minX, diffY = maxY - minY, diffZ = maxZ - minZ;
        int startX = direction.x >= 0.0 ? minX : maxX;
        int startY = direction.y >= 0.0 ? minY : maxY;
        int startZ = direction.z >= 0.0 ? minZ : maxZ;
        List<Direction.Axis> axes = Math.abs(direction.x) < Math.abs(direction.z) ? YZX : YXZ;
        Direction first = dir(axes.get(0), direction), second = dir(axes.get(1), direction), third = dir(axes.get(2), direction);
        int firstMax = axes.get(0).choose(diffX, diffY, diffZ);
        int secondMax = axes.get(1).choose(diffX, diffY, diffZ);
        int thirdMax = axes.get(2).choose(diffX, diffY, diffZ);
        return () -> new AbstractIterator<>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private int i1, i2, i3;
            private boolean end;

            @Override
            protected BlockPos computeNext() {
                if (end) {
                    return endOfData();
                }
                cursor.set(startX + first.getStepX() * i1 + second.getStepX() * i2 + third.getStepX() * i3,
                        startY + first.getStepY() * i1 + second.getStepY() * i2 + third.getStepY() * i3,
                        startZ + first.getStepZ() * i1 + second.getStepZ() * i2 + third.getStepZ() * i3);
                if (i3 < thirdMax) {
                    i3++;
                } else if (i2 < secondMax) {
                    i2++;
                    i3 = 0;
                } else if (i1 < firstMax) {
                    i1++;
                    i3 = 0;
                    i2 = 0;
                } else {
                    end = true;
                }
                return cursor;
            }
        };
    }

    private static Direction dir(Direction.Axis axis, Vec3 v) {
        double c = axis.choose(v.x, v.y, v.z);
        return c >= 0.0 ? Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)
                : Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
    }
}
