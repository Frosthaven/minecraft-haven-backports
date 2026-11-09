package dev.frosthaven.havenbackports.block;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Mojang's 26.x {@code SelectableSlotContainer}: the slot under the cursor on a block's front face, in a rows x columns grid. */
public interface SelectableSlotContainer {
    int getRows();

    int getColumns();

    default OptionalInt getHitSlot(BlockHitResult hitResult, Direction blockFacing) {
        return getRelativeHitCoordinatesForBlockFace(hitResult, blockFacing).map(hit -> {
            int row = getSection(1.0F - hit.y, getRows());
            int column = getSection(hit.x, getColumns());
            return OptionalInt.of(column + row * getColumns());
        }).orElseGet(OptionalInt::empty);
    }

    private static Optional<Vec2> getRelativeHitCoordinatesForBlockFace(BlockHitResult hitResult, Direction blockFacing) {
        Direction hitDirection = hitResult.getDirection();
        if (blockFacing != hitDirection) {
            return Optional.empty();
        }
        BlockPos hitBlockPos = hitResult.getBlockPos().relative(hitDirection);
        Vec3 relativeHit = hitResult.getLocation().subtract(hitBlockPos.getX(), hitBlockPos.getY(), hitBlockPos.getZ());
        double x = relativeHit.x(), y = relativeHit.y(), z = relativeHit.z();
        return switch (hitDirection) {
            case NORTH -> Optional.of(new Vec2((float) (1.0 - x), (float) y));
            case SOUTH -> Optional.of(new Vec2((float) x, (float) y));
            case WEST -> Optional.of(new Vec2((float) z, (float) y));
            case EAST -> Optional.of(new Vec2((float) (1.0 - z), (float) y));
            case DOWN, UP -> Optional.empty();
        };
    }

    private static int getSection(float relativeCoordinate, int maxSections) {
        float targetedPixel = relativeCoordinate * 16.0F;
        float sectionSize = 16.0F / maxSections;
        return Math.clamp((long) Mth.floor(targetedPixel / sectionSize), 0, maxSections - 1);
    }
}
