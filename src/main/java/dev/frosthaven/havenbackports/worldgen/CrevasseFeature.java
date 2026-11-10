package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/** 26.x CrevasseFeature (the Ice Caves' water-filled cracks under brittle ice), line for line. 26.x providers may
 *  return no state (getOptionalState); 1.21.1's always return one, so every column fills and gets its surface block.
 *  ChunkPos.pack is 1.21.1's ChunkPos.asLong (the same packing). */
public class CrevasseFeature extends Feature<CrevasseFeature.Config> {
    public record Config(int maxWidth, int maxLength, int minFootprint, int cursorSurfaceSearchRange, int columnSurfaceSearchRange,
                         float twistiness, float tapering, float turnFactor, IntProvider carveDepth, IntProvider fillDepth,
                         BlockStateProvider surfaceBlock, BlockStateProvider fillBlock, BlockPredicate carvableBlocks,
                         BlockPredicate acceptableBarrier) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.intRange(2, 8).fieldOf("max_width").forGetter(Config::maxWidth),
                        Codec.intRange(4, 16).fieldOf("max_length").forGetter(Config::maxLength),
                        Codec.intRange(1, 32).fieldOf("min_footprint").forGetter(Config::minFootprint),
                        ExtraCodecs.POSITIVE_INT.fieldOf("cursor_surface_search_range").forGetter(Config::cursorSurfaceSearchRange),
                        ExtraCodecs.POSITIVE_INT.fieldOf("column_surface_search_range").forGetter(Config::columnSurfaceSearchRange),
                        Codec.floatRange(0.0F, 16.0F).fieldOf("twistiness").forGetter(Config::twistiness),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("tapering").forGetter(Config::tapering),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("turn_factor").forGetter(Config::turnFactor),
                        IntProvider.POSITIVE_CODEC.fieldOf("carve_depth").forGetter(Config::carveDepth),
                        IntProvider.POSITIVE_CODEC.fieldOf("fill_depth").forGetter(Config::fillDepth),
                        BlockStateProvider.CODEC.fieldOf("surface_block").forGetter(Config::surfaceBlock),
                        BlockStateProvider.CODEC.fieldOf("fill_block").forGetter(Config::fillBlock),
                        BlockPredicate.CODEC.fieldOf("carvable_blocks").forGetter(Config::carvableBlocks),
                        BlockPredicate.CODEC.fieldOf("acceptable_barrier").forGetter(Config::acceptableBarrier))
                .apply(i, Config::new));
    }

    public CrevasseFeature() {
        super(Config.CODEC);
    }

    private static boolean wouldExposeSideLiquid(WorldGenLevel region, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!region.getFluidState(pos.relative(dir)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Surface> findCarvableSurface(Config c, WorldGenLevel region, BlockPos centerPos, int searchRange) {
        BlockPos pos = centerPos.above(searchRange / 2);
        boolean isBlockAboveEmpty = region.getBlockState(pos.above()).isAir();
        int minY = Math.max(region.getMinBuildHeight(), pos.getY() - searchRange);
        BlockPos.MutableBlockPos blockPos = pos.mutable();
        while (blockPos.getY() >= minY) {
            BlockState block = region.getBlockState(blockPos);
            if (c.carvableBlocks().test(region, blockPos) && isBlockAboveEmpty) {
                return Optional.of(new Surface(blockPos.immutable(), block));
            }
            isBlockAboveEmpty = block.isAir();
            blockPos.move(Direction.DOWN);
        }
        return Optional.empty();
    }

    private static CarveColumn findColumn(Long2ObjectOpenHashMap<CarveColumn> columns, int x, int z) {
        return columns.getOrDefault(ChunkPos.asLong(x, z), null);
    }

    private static int getFillLevel(Long2ObjectOpenHashMap<CarveColumn> columns, int originSurfaceY, int fillDepth) {
        CarveColumn lowest = columns.values().stream().min(Comparator.comparingInt(a -> a.surfacePos.getY())).get();
        return Math.min(originSurfaceY - fillDepth, lowest.surfacePos.getY() - 1);
    }

    private static boolean hasSteepNeighbor(Long2ObjectOpenHashMap<CarveColumn> columns, CarveColumn column) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos n = column.surfacePos.relative(facing);
            CarveColumn neighbor = findColumn(columns, n.getX(), n.getZ());
            if (neighbor != null && Math.abs(column.surfacePos.getY() - neighbor.surfacePos.getY()) > 1) {
                return true;
            }
        }
        return false;
    }

    private static void removeDisconnectedColumns(Long2ObjectOpenHashMap<CarveColumn> columns, BlockPos origin) {
        CarveColumn originColumn = findColumn(columns, origin.getX(), origin.getZ());
        if (originColumn == null || hasSteepNeighbor(columns, originColumn)) {
            columns.clear();
            return;
        }
        List<CarveColumn> connected = new ArrayList<>();
        LongSet processed = new LongOpenHashSet();
        connected.add(originColumn);
        processed.add(ChunkPos.asLong(origin.getX(), origin.getZ()));
        for (int index = 0; index < connected.size(); index++) {
            CarveColumn column = connected.get(index);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos n = column.surfacePos().relative(facing);
                CarveColumn neighbor = findColumn(columns, n.getX(), n.getZ());
                if (neighbor != null) {
                    long key = ChunkPos.asLong(n.getX(), n.getZ());
                    if (!processed.contains(key) && !hasSteepNeighbor(columns, neighbor)) {
                        connected.add(neighbor);
                        processed.add(key);
                    }
                }
            }
        }
        columns.clear();
        for (CarveColumn column : connected) {
            columns.put(ChunkPos.asLong(column.surfacePos.getX(), column.surfacePos.getZ()), column);
        }
    }

    private static void appendBranchColumns(Config c, Long2ObjectOpenHashMap<CarveColumn> columns, WorldGenLevel region, BlockPos origin,
                                            int branchLength, int carveDepth, float yaw, RandomSource random) {
        double cursorX = origin.getX();
        double cursorZ = origin.getZ();
        int previousSurfaceY = origin.getY();
        float yawAcceleration = 0.0F;
        for (int step = 0; step < branchLength; step++) {
            cursorX += Math.cos(yaw);
            cursorZ += Math.sin(yaw);
            float turnDirection = (float) random.nextGaussian();
            float turnMagnitude = random.nextFloat();
            yawAcceleration = yawAcceleration * 0.5F + turnDirection * turnMagnitude * c.twistiness();
            yaw += yawAcceleration * c.turnFactor();
            int blockX = Mth.floor(cursorX);
            int blockZ = Mth.floor(cursorZ);
            Optional<Surface> surface = findCarvableSurface(c, region, new BlockPos(blockX, previousSurfaceY, blockZ), c.cursorSurfaceSearchRange());
            if (surface.isEmpty()) {
                continue;
            }
            float taper = 1.0F - (float) step / branchLength * c.tapering();
            float radius = Math.max(c.maxWidth() * 0.5F * taper, 0.5F);
            float radiusSquared = Mth.square(radius);
            int maxDepth = Math.max((int) (carveDepth * taper), 1);
            int expandedRadius = Mth.ceil(radius);
            for (int x = blockX - expandedRadius; x <= blockX + expandedRadius; x++) {
                for (int z = blockZ - expandedRadius; z <= blockZ + expandedRadius; z++) {
                    float dx = x - blockX;
                    float dz = z - blockZ;
                    if (dx * dx + dz * dz > radiusSquared) {
                        continue;
                    }
                    long key = ChunkPos.asLong(x, z);
                    if (columns.containsKey(key)) {
                        continue;
                    }
                    Optional<Surface> columnSurface = findCarvableSurface(c, region, new BlockPos(x, surface.get().pos.getY(), z),
                            c.columnSurfaceSearchRange());
                    if (columnSurface.isEmpty()) {
                        continue;
                    }
                    int depth = 1;
                    for (BlockPos.MutableBlockPos test = columnSurface.get().pos.mutable().move(Direction.DOWN);
                         depth < maxDepth && c.carvableBlocks().test(region, test); depth++) {
                        test.move(Direction.DOWN);
                    }
                    columns.put(key, new CarveColumn(columnSurface.get().pos, columnSurface.get().block, depth - 1));
                }
            }
            previousSurfaceY = surface.get().pos.getY();
        }
    }

    private static void sealExternalSides(Config c, WorldGenLevel region, Long2ObjectOpenHashMap<CarveColumn> columns, CarveColumn column,
                                          BlockPos pos, Block placementBlock) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(facing);
            if (c.acceptableBarrier().test(region, n)) {
                continue;
            }
            CarveColumn neighbor = findColumn(columns, n.getX(), n.getZ());
            if ((neighbor == null || !neighbor.containsY(pos.getY())) && !region.getBlockState(n).is(placementBlock)) {
                region.setBlock(n, column.originalSurfaceBlock, 2);
            }
        }
    }

    private static void sealFilledBottom(Config c, WorldGenLevel region, CarveColumn column, BlockPos pos, Block fillBlock) {
        BlockPos below = pos.below();
        if (!c.acceptableBarrier().test(region, below) && !region.getBlockState(below).is(fillBlock)) {
            region.setBlock(below, column.originalSurfaceBlock, 2);
        }
    }

    private static void carveColumn(Config c, WorldGenLevel region, Long2ObjectOpenHashMap<CarveColumn> columns, CarveColumn column,
                                    int fillLevel, RandomSource random) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int carveMinY = column.getBottomY();
        boolean carvedAnyBlock = false;
        BlockPos.MutableBlockPos carvePos = column.surfacePos.mutable();
        while (carvePos.getY() >= carveMinY) {
            BlockState fill = c.fillBlock().getState(random, carvePos);
            boolean shouldFill = carvePos.getY() <= fillLevel;
            if (!shouldFill && wouldExposeSideLiquid(region, carvePos)) {
                break;
            }
            Block placementBlock = shouldFill ? fill.getBlock() : air.getBlock();
            sealExternalSides(c, region, columns, column, carvePos, placementBlock);
            region.setBlock(carvePos, shouldFill ? fill : air, 2);
            if (shouldFill && carvePos.getY() == carveMinY) {
                sealFilledBottom(c, region, column, carvePos, placementBlock);
            }
            carvedAnyBlock = true;
            carvePos.move(Direction.DOWN);
        }
        if (carvedAnyBlock) {
            region.setBlock(column.surfacePos, c.surfaceBlock().getState(random, column.surfacePos), 2);
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config c = context.config();
        WorldGenLevel region = context.level();
        RandomSource random = context.random();
        BlockPos pos = context.origin();
        int carveDepth = c.carveDepth().sample(random);
        if (pos.getY() <= region.getMinBuildHeight() + carveDepth) {
            return false;
        }
        int branchLength = c.maxLength() / 2;
        float yaw = random.nextFloat() * (float) (Math.PI * 2);
        int fillDepth = c.fillDepth().sample(random);
        Long2ObjectOpenHashMap<CarveColumn> columns = new Long2ObjectOpenHashMap<>(50);
        for (int direction = 0; direction < 2; direction++) {
            appendBranchColumns(c, columns, region, pos, branchLength, carveDepth, yaw + direction * (float) Math.PI, random);
        }
        removeDisconnectedColumns(columns, pos);
        if (columns.size() < c.minFootprint()) {
            return false;
        }
        int fillLevel = getFillLevel(columns, pos.getY(), fillDepth);
        for (CarveColumn column : columns.values().stream().sorted(CarveColumn::compareTo).toList()) {
            carveColumn(c, region, columns, column, fillLevel, random);
        }
        return true;
    }

    private record CarveColumn(BlockPos surfacePos, BlockState originalSurfaceBlock, int depth) implements Comparable<CarveColumn> {
        int getBottomY() {
            return this.surfacePos.getY() - this.depth;
        }

        boolean containsY(int y) {
            return y >= this.getBottomY() && y <= this.surfacePos.getY();
        }

        @Override
        public int compareTo(CarveColumn other) {
            int xDiff = this.surfacePos.getX() - other.surfacePos.getX();
            return xDiff == 0 ? this.surfacePos.getZ() - other.surfacePos.getZ() : xDiff;
        }
    }

    private record Surface(BlockPos pos, BlockState block) {}
}
