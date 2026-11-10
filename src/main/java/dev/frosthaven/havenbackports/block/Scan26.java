package dev.frosthaven.havenbackports.block;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** 26.x BlockScanUtils.findBlocks order (LevelReader.findBlocksIn): chunk sections by x, then z, then y; inside a
 *  section by y, then z, then x. Code that draws random numbers per match keeps Mojang's order this way. */
public final class Scan26 {
    private Scan26() {}

    public static void forEach(LevelReader level, BlockPos from, BlockPos to, Predicate<BlockState> predicate, BiConsumer<BlockPos, BlockState> consumer) {
        int minX = Math.min(from.getX(), to.getX()), minY = Math.min(from.getY(), to.getY()), minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX()), maxY = Math.max(from.getY(), to.getY()), maxZ = Math.max(from.getZ(), to.getZ());
        int minSY = Math.max(level.getMinSection(), SectionPos.blockToSectionCoord(minY));
        int maxSY = Math.min(level.getMaxSection() - 1, SectionPos.blockToSectionCoord(maxY));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int sx = SectionPos.blockToSectionCoord(minX); sx <= SectionPos.blockToSectionCoord(maxX); sx++) {
            for (int sz = SectionPos.blockToSectionCoord(minZ); sz <= SectionPos.blockToSectionCoord(maxZ); sz++) {
                for (int sy = minSY; sy <= maxSY; sy++) {
                    int x0 = Math.max(SectionPos.sectionToBlockCoord(sx), minX), x1 = Math.min(SectionPos.sectionToBlockCoord(sx, 15), maxX);
                    int y0 = Math.max(SectionPos.sectionToBlockCoord(sy), minY), y1 = Math.min(SectionPos.sectionToBlockCoord(sy, 15), maxY);
                    int z0 = Math.max(SectionPos.sectionToBlockCoord(sz), minZ), z1 = Math.min(SectionPos.sectionToBlockCoord(sz, 15), maxZ);
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                BlockState state = level.getBlockState(pos.set(x, y, z));
                                if (predicate.test(state)) {
                                    consumer.accept(pos.immutable(), state);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
