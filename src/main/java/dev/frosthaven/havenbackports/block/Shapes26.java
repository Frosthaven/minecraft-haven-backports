package dev.frosthaven.havenbackports.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 26.x shape helpers 1.21.1 lacks: Block.column and Shapes.rotateHorizontal (a north shape turned about the block
 * centre: east = Y 90, south = Y 180, west = Y 270, as OctahedralGroup.BLOCK_ROT_Y_*). Checked against 26.x's own
 * output in the offline port test (tools/porttest).
 */
public final class Shapes26 {
    private Shapes26() {}

    public static VoxelShape column(double sizeX, double sizeZ, double minY, double maxY) {
        return Block.box(8.0 - sizeX / 2.0, minY, 8.0 - sizeZ / 2.0, 8.0 + sizeX / 2.0, maxY, 8.0 + sizeZ / 2.0);
    }

    public static Map<Direction, VoxelShape> rotateHorizontal(VoxelShape north) {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        out.put(Direction.NORTH, north);
        out.put(Direction.EAST, turn(north, 1));
        out.put(Direction.SOUTH, turn(north, 2));
        out.put(Direction.WEST, turn(north, 3));
        return out;
    }

    /** 26.x Block.boxZ(sizeX, minY, maxY, minZ, maxZ) and boxZ(sizeXY, minZ, maxZ). */
    public static VoxelShape boxZ(double sizeX, double minY, double maxY, double minZ, double maxZ) {
        return Block.box(8.0 - sizeX / 2.0, minY, minZ, 8.0 + sizeX / 2.0, maxY, maxZ);
    }

    public static VoxelShape boxZ(double sizeXY, double minZ, double maxZ) {
        return boxZ(sizeXY, 8.0 - sizeXY / 2.0, 8.0 + sizeXY / 2.0, minZ, maxZ);
    }

    /** 26.x Shapes.rotateAll: the four horizontal turns, and up / down (the north face turned to the top / bottom). */
    public static Map<Direction, VoxelShape> rotateAll(VoxelShape north) {
        Map<Direction, VoxelShape> out = rotateHorizontal(north);
        VoxelShape up = Shapes.empty();
        VoxelShape down = Shapes.empty();
        for (AABB b : north.toAabbs()) {
            up = Shapes.or(up, Shapes.box(b.minX, 1.0 - b.maxZ, b.minY, b.maxX, 1.0 - b.minZ, b.maxY));
            down = Shapes.or(down, Shapes.box(b.minX, b.minZ, 1.0 - b.maxY, b.maxX, b.maxZ, 1.0 - b.minY));
        }
        out.put(Direction.UP, up.optimize());
        out.put(Direction.DOWN, down.optimize());
        return out;
    }

    /** Turns a shape clockwise (seen from above) by quarter turns about the block centre. */
    private static VoxelShape turn(VoxelShape shape, int quarters) {
        VoxelShape out = Shapes.empty();
        for (AABB b : shape.toAabbs()) {
            double x0 = b.minX, z0 = b.minZ, x1 = b.maxX, z1 = b.maxZ;
            for (int q = 0; q < quarters; q++) {   // (x, z) -> (1 - z, x)
                double nx0 = 1.0 - z1, nx1 = 1.0 - z0, nz0 = x0, nz1 = x1;
                x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
            }
            out = Shapes.or(out, Shapes.box(x0, b.minY, z0, x1, b.maxY, z1));
        }
        return out.optimize();
    }
}
