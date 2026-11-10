package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.material.Fluids;

/**
 * 26.x PoplarFoliagePlacer, line for line. 1.21.1 forms: TreeConfiguration in place of TreeFeature, providers'
 * getState(random, pos); 1.21.1's attachment has no foliage height offset (26.x's is 0 for this attachment).
 * tryPlaceLeaf is 26.x FoliagePlacer.tryPlaceLeaf (it skips persistent leaves, which 1.21.1's does not).
 */
public class PoplarFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<PoplarFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(i -> foliagePlacerParts(i)
            .and(i.group(
                    IntProvider.codec(5, 16).fieldOf("height").forGetter(p -> p.height),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("side_hole_chance").forGetter(p -> p.sideHoleChance)))
            .apply(i, PoplarFoliagePlacer::new));
    private final IntProvider height;
    private final float sideHoleChance;

    public PoplarFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider height, float sideHoleChance) {
        super(radius, offset);
        this.height = height;
        this.sideHoleChance = sideHoleChance;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return Worldgen.POPLAR_FOLIAGE_PLACER.get();
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliageSetter foliageSetter, RandomSource random, TreeConfiguration tree,
            int treeHeight, FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        boolean doubleTrunk = foliageAttachment.doubleTrunk();
        BlockPos foliagePos = foliageAttachment.pos().above(offset);
        int currentRadius = leafRadius + foliageAttachment.radiusOffset() - 1;
        boolean flipRhombusShape = random.nextBoolean();
        int foliageHeightWithOffset = foliageHeight;
        this.placeLeavesRow(level, foliageSetter, random, tree, foliagePos, currentRadius - 2, foliageHeightWithOffset - 1, doubleTrunk, foliageHeightWithOffset, flipRhombusShape);
        this.placeLeavesRow(level, foliageSetter, random, tree, foliagePos, currentRadius - 1, foliageHeightWithOffset - 2, doubleTrunk, foliageHeightWithOffset, flipRhombusShape);
        this.placeLeavesRow(level, foliageSetter, random, tree, foliagePos, currentRadius - 1, foliageHeightWithOffset - 3, doubleTrunk, foliageHeightWithOffset, flipRhombusShape);
        for (int y = foliageHeightWithOffset - 4; y >= 1; y--) {
            this.placeLeavesRow(level, foliageSetter, random, tree, foliagePos, currentRadius, y, doubleTrunk, foliageHeightWithOffset, flipRhombusShape);
        }
        this.replaceLeavesWithLog(level, foliageSetter, tree, random, foliagePos, currentRadius, foliageHeightWithOffset - 4, doubleTrunk, foliageHeightWithOffset, flipRhombusShape);
        this.placeLeavesRow(level, foliageSetter, random, tree, foliagePos, currentRadius - 1, 0, doubleTrunk, foliageHeightWithOffset, flipRhombusShape);
        this.placeLeavesRow(level, foliageSetter, random, tree, foliagePos, (int) Math.clamp((long) (currentRadius - 2), 1, 2), -1, doubleTrunk,
                foliageHeightWithOffset, flipRhombusShape);
    }

    private void replaceLeavesWithLog(LevelSimulatedReader level, FoliageSetter foliageSetter, TreeConfiguration tree, RandomSource random,
            BlockPos origin, int currentRadius, int y, boolean doubleTrunk, int foliageHeight, boolean flipRhombusShape) {
        int offset = doubleTrunk ? 1 : 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -currentRadius; dx <= currentRadius + offset; dx++) {
            for (int dz = -currentRadius; dz <= currentRadius + offset; dz++) {
                int absDz = Mth.abs(dz);
                int absDx = Mth.abs(dx);
                if (isWithinRhombusShape(currentRadius, absDx, absDz,
                        this.getCornerBlocksToCutForRhombusShape(dx, dz, currentRadius, this.shouldRowBePartialRhombusShape(foliageHeight, y), flipRhombusShape), 2)
                        && (absDz == 0 && currentRadius - absDx >= 4 || absDx == 0 && currentRadius - absDz >= 4)) {
                    pos.setWithOffset(origin, dx, y, dz);
                    tryPlaceLog(level, foliageSetter, random, tree, pos, PoplarTrunkPlacer.getSidewaysStateModifier(
                            Direction.fromAxisAndDirection(absDz == 0 ? Direction.Axis.X : Direction.Axis.Z, Direction.AxisDirection.POSITIVE)));
                }
            }
        }
    }

    private static void tryPlaceLog(LevelSimulatedReader level, FoliageSetter foliageSetter, RandomSource random, TreeConfiguration tree,
            BlockPos pos, Function<BlockState, BlockState> stateModifier) {
        if (level.isStateAtPosition(pos, state -> state.equals(tree.foliageProvider.getState(random, pos)))) {
            foliageSetter.set(pos, stateModifier.apply(tree.trunkProvider.getState(random, pos)));
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration tree) {
        return this.height.sample(random);
    }

    private void placeLeavesRow(LevelSimulatedReader level, FoliageSetter foliageSetter, RandomSource random, TreeConfiguration tree,
            BlockPos origin, int currentRadius, int y, boolean doubleTrunk, int foliageHeight, boolean flipRhombusShape) {
        int offset = doubleTrunk ? 1 : 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -currentRadius; dx <= currentRadius + offset; dx++) {
            for (int dz = -currentRadius; dz <= currentRadius + offset; dz++) {
                if (!this.shouldSkipLocation(random, dx, y, dz, currentRadius, doubleTrunk, foliageHeight, flipRhombusShape)) {
                    pos.setWithOffset(origin, dx, y, dz);
                    tryPlaceLeaf26(level, foliageSetter, random, tree, pos);
                }
            }
        }
    }

    /** 26.x FoliagePlacer.tryPlaceLeaf. */
    static boolean tryPlaceLeaf26(LevelSimulatedReader level, FoliageSetter foliageSetter, RandomSource random, TreeConfiguration tree, BlockPos pos) {
        boolean isPersistent = level.isStateAtPosition(pos, state -> state.hasProperty(BlockStateProperties.PERSISTENT)
                && state.getValue(BlockStateProperties.PERSISTENT));
        if (!isPersistent && TreeFeature.validTreePos(level, pos)) {
            BlockState foliageState = tree.foliageProvider.getState(random, pos);
            if (foliageState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                foliageState = foliageState.setValue(BlockStateProperties.WATERLOGGED,
                        level.isFluidAtPosition(pos, fluidState -> fluidState.isSourceOfType(Fluids.WATER)));
            }
            foliageSetter.set(pos, foliageState);
            return true;
        }
        return false;
    }

    private boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk, int foliageHeight,
            boolean flipRhombusShape) {
        boolean partial = this.shouldRowBePartialRhombusShape(foliageHeight, y);
        int cut = this.getCornerBlocksToCutForRhombusShape(dx, dz, currentRadius, partial, flipRhombusShape);
        int absDx = Mth.abs(dx);
        int absDz = Mth.abs(dz);
        boolean isRhombusEdgeBlock = absDx == currentRadius || absDz == currentRadius;
        if (partial && isRhombusEdgeBlock) {
            return true;
        }
        int additionalSideRemoval = random.nextFloat() <= this.sideHoleChance ? 1 : 0;
        return !isWithinRhombusShape(currentRadius, absDx, absDz, cut, additionalSideRemoval);
    }

    @Override
    protected boolean shouldSkipLocationSigned(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        throw new IllegalStateException("Overridden method needs more context");
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        throw new IllegalStateException("Overridden method needs more context");
    }

    private int getCornerBlocksToCutForRhombusShape(int dx, int dz, int currentRadius, boolean partial, boolean flipRhombusShape) {
        boolean small = flipRhombusShape ? isLeftTopCornerOrRightLowerCorner(dx, dz) : isLeftLowerCornerOrRightTopCorner(dx, dz);
        return small ? currentRadius - 1 : (partial ? currentRadius + 1 : currentRadius);
    }

    private static boolean isWithinRhombusShape(int currentRadius, int absDx, int absDz, int cut, int additionalSideRemoval) {
        return absDx + absDz <= currentRadius * 2 - (cut + additionalSideRemoval);
    }

    private static boolean isLeftLowerCornerOrRightTopCorner(int dx, int dz) {
        return dx > 0 && dz < 0 || dz > 0 && dx < 0;
    }

    private static boolean isLeftTopCornerOrRightLowerCorner(int dx, int dz) {
        return dx > 0 && dz > 0 || dz < 0 && dx < 0;
    }

    private boolean shouldRowBePartialRhombusShape(int foliageHeight, int y) {
        return foliageHeight - 1 == y || foliageHeight - 2 == y;
    }
}
