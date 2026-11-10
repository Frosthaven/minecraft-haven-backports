package dev.frosthaven.havenbackports.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 26.x PositionCollisionContext (CollisionContext.positionContext(y)): collision as seen from height y. */
public record PositionCollisionContext(double y) implements CollisionContext {
    @Override
    public boolean isDescending() {
        return false;
    }

    @Override
    public boolean isAbove(VoxelShape shape, BlockPos pos, boolean defaultValue) {
        return this.y > pos.getY() + shape.max(Direction.Axis.Y) - 1.0E-5F;
    }

    @Override
    public boolean isHoldingItem(Item item) {
        return false;
    }

    @Override
    public boolean canStandOnFluid(FluidState above, FluidState fluid) {
        return false;
    }
}
