package dev.frosthaven.havenbackports.block;

import dev.frosthaven.havenbackports.Sulfur;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x SpeleothemBlock (the shared class of pointed dripstone, icicle and sulfur spike), line for line. 1.21.1 forms:
 * 26.x's SPELEOTHEM_THICKNESS is 1.21.1's DRIPSTONE_THICKNESS (same name "thickness", same values) and TIP_DIRECTION
 * is VERTICAL_DIRECTION, so 1.21.1's pointed dripstone counts as a speleothem here as in 26.x (#speleothems);
 * updateShape's argument order; the landing level event 26.x keeps per block (1052 for the sulfur spike) has no
 * 1.21.1 id, so the server plays its sound (the same sound, volume and pitch) through {@link #landingSound}.
 */
public abstract class SpeleothemBlock extends Block implements SimpleWaterloggedBlock, Fallable {
    public static final DirectionProperty TIP_DIRECTION = BlockStateProperties.VERTICAL_DIRECTION;
    public static final EnumProperty<DripstoneThickness> THICKNESS = BlockStateProperties.DRIPSTONE_THICKNESS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_TIP_MERGE = Shapes26.column(6.0, 6.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_TIP_UP = Shapes26.column(6.0, 6.0, 0.0, 11.0);
    protected static final VoxelShape SHAPE_TIP_DOWN = Shapes26.column(6.0, 6.0, 5.0, 16.0);
    private static final VoxelShape SHAPE_FRUSTUM = Shapes26.column(8.0, 8.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_MIDDLE = Shapes26.column(10.0, 10.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_BASE = Shapes26.column(12.0, 12.0, 0.0, 16.0);
    private static final float MAX_HORIZONTAL_OFFSET = (float) SHAPE_BASE.min(Direction.Axis.X);
    protected final BlockState blockToGrowOn;

    public SpeleothemBlock(BlockState blockToGrowOn, Properties properties) {
        super(properties);
        this.blockToGrowOn = blockToGrowOn;
        this.registerDefaultState(this.stateDefinition.any().setValue(TIP_DIRECTION, Direction.UP).setValue(THICKNESS, DripstoneThickness.TIP)
                .setValue(WATERLOGGED, false));
    }

    /** The landing sound and its volume (26.x LevelEventHandler plays it at the given volume, pitch 0.9 to 1.0). */
    protected abstract void landingSound(Level level, BlockPos pos);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIP_DIRECTION, THICKNESS, WATERLOGGED);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.isValidSpeleothemPlacement(level, pos, state.getValue(TIP_DIRECTION));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction toNeighbour, BlockState neighbourState, LevelAccessor level, BlockPos pos,
            BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (toNeighbour != Direction.UP && toNeighbour != Direction.DOWN) {
            return state;
        }
        Direction tipDirection = state.getValue(TIP_DIRECTION);
        if (tipDirection == Direction.DOWN && level.getBlockTicks().hasScheduledTick(pos, this)) {
            return state;
        } else if (toNeighbour == tipDirection.getOpposite() && !this.canSurvive(state, level, pos)) {
            level.scheduleTick(pos, this, tipDirection == Direction.DOWN ? 2 : 1);
            return state;
        }
        boolean merge = state.getValue(THICKNESS) == DripstoneThickness.TIP_MERGE;
        return state.setValue(THICKNESS, this.calculateSpeleothemThickness(level, pos, tipDirection, merge));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelAccessor level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction tipDirection = this.calculateTipDirection(level, pos, context.getNearestLookingVerticalDirection().getOpposite());
        if (tipDirection == null) {
            return null;
        }
        DripstoneThickness thickness = this.calculateSpeleothemThickness(level, pos, tipDirection, !context.isSecondaryUseActive());
        return this.defaultBlockState().setValue(TIP_DIRECTION, tipDirection).setValue(THICKNESS, thickness)
                .setValue(WATERLOGGED, level.getFluidState(pos).isSourceOfType(Fluids.WATER));
    }

    @Nullable
    private Direction calculateTipDirection(LevelReader level, BlockPos pos, Direction defaultTip) {
        if (this.isValidSpeleothemPlacement(level, pos, defaultTip)) {
            return defaultTip;
        }
        return this.isValidSpeleothemPlacement(level, pos, defaultTip.getOpposite()) ? defaultTip.getOpposite() : null;
    }

    private DripstoneThickness calculateSpeleothemThickness(LevelReader level, BlockPos pos, Direction tipDirection, boolean merge) {
        Direction baseDirection = tipDirection.getOpposite();
        BlockState inFront = level.getBlockState(pos.relative(tipDirection));
        if (isSpeleothemWithDirection(inFront, baseDirection) && inFront.is(this)) {
            return !merge && inFront.getValue(THICKNESS) != DripstoneThickness.TIP_MERGE ? DripstoneThickness.TIP : DripstoneThickness.TIP_MERGE;
        } else if (!isSpeleothemWithDirection(inFront, tipDirection)) {
            return DripstoneThickness.TIP;
        }
        DripstoneThickness inFrontThickness = inFront.getValue(THICKNESS);
        if (inFrontThickness != DripstoneThickness.TIP && inFrontThickness != DripstoneThickness.TIP_MERGE) {
            BlockState behind = level.getBlockState(pos.relative(baseDirection));
            return !isSpeleothemWithDirection(behind, tipDirection) ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
        }
        return DripstoneThickness.FRUSTUM;
    }

    private boolean isValidSpeleothemPlacement(LevelReader level, BlockPos pos, Direction tipDirection) {
        BlockPos behindPos = pos.relative(tipDirection.getOpposite());
        BlockState behind = level.getBlockState(behindPos);
        return behind.isFaceSturdy(level, behindPos, tipDirection) || isSpeleothemWithDirection(behind, tipDirection) && behind.is(this);
    }

    private static boolean isSpeleothemWithDirection(BlockState state, Direction tipDirection) {
        return state.is(Sulfur.SPELEOTHEMS) && state.getValue(TIP_DIRECTION) == tipDirection;
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide()) {
            BlockPos pos = hit.getBlockPos();
            if (level instanceof ServerLevel server && projectile.mayInteract(server, pos) && projectile.mayBreak(server)
                    && projectile instanceof ThrownTrident && projectile.getDeltaMovement().length() > 0.6) {
                level.destroyBlock(pos, true);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isStalagmite(state) && !this.canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
        } else {
            spawnFallingStalactite(state, level, pos);
        }
    }

    protected static void spawnFallingStalactite(BlockState state, ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos fallPos = pos.mutable();
        BlockState fallState = state;
        while (isStalactite(fallState)) {
            FallingBlockEntity entity = FallingBlockEntity.fall(level, fallPos, fallState);
            if (isTip(fallState, true)) {
                int size = Math.max(1 + pos.getY() - fallPos.getY(), 6);
                entity.setHurtsEntities(1.0F * size, 40);
                break;
            }
            fallPos.move(Direction.DOWN);
            fallState = level.getBlockState(fallPos);
        }
    }

    protected static boolean isStalagmite(BlockState state) {
        return isSpeleothemWithDirection(state, Direction.UP);
    }

    protected static boolean isStalactite(BlockState state) {
        return isSpeleothemWithDirection(state, Direction.DOWN);
    }

    protected static boolean isTip(BlockState state, boolean includeMergedTip) {
        if (!state.is(Sulfur.SPELEOTHEMS)) {
            return false;
        }
        DripstoneThickness t = state.getValue(THICKNESS);
        return t == DripstoneThickness.TIP || includeMergedTip && t == DripstoneThickness.TIP_MERGE;
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity entity) {
        if (!entity.isSilent()) {
            this.landingSound(level, pos);
        }
    }

    @Override
    public DamageSource getFallDamageSource(Entity entity) {
        return entity.damageSources().fallingStalactite(entity);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = switch (state.getValue(THICKNESS)) {
            case TIP_MERGE -> SHAPE_TIP_MERGE;
            case TIP -> state.getValue(TIP_DIRECTION) == Direction.DOWN ? SHAPE_TIP_DOWN : SHAPE_TIP_UP;
            case FRUSTUM -> SHAPE_FRUSTUM;
            case MIDDLE -> SHAPE_MIDDLE;
            case BASE -> SHAPE_BASE;
        };
        return shape.move(state.getOffset(level, pos).x, 0.0, state.getOffset(level, pos).z);
    }

    @Override
    protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    protected float getMaxHorizontalOffset() {
        return MAX_HORIZONTAL_OFFSET;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.011377778F && isStalactiteStartPos(state, level, pos)) {
            this.growStalactiteOrStalagmiteIfPossible(state, level, pos, random);
        }
    }

    protected static boolean isStalactiteStartPos(BlockState state, LevelReader level, BlockPos pos) {
        return isStalactite(state) && !level.getBlockState(pos.above()).is(state.getBlock());
    }

    public void growStalactiteOrStalagmiteIfPossible(BlockState startState, ServerLevel level, BlockPos startPos, RandomSource random) {
        if (!this.canGrow(level, startPos)) {
            return;
        }
        BlockPos tipPos = findTip(startState, level, startPos, this.getMaxGrowthLength(), false);
        if (tipPos == null) {
            return;
        }
        BlockState tip = level.getBlockState(tipPos);
        if (isFreeHangingStalactite(tip) && this.canTipGrow(tip, level, tipPos)) {
            List<Direction> directions = this.getNaturalGrowthDirections();
            Direction growth = directions.isEmpty() ? null : directions.get(random.nextInt(directions.size()));
            if (growth == Direction.DOWN) {
                this.grow(level, tipPos, Direction.DOWN);
            } else if (growth == Direction.UP) {
                this.growStalagmiteBelow(level, tipPos);
            }
        }
    }

    protected List<Direction> getNaturalGrowthDirections() {
        return List.copyOf(TIP_DIRECTION.getPossibleValues());
    }

    protected boolean canGrow(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(this.blockToGrowOn.getBlock());
    }

    @Nullable
    protected static BlockPos findTip(BlockState state, LevelAccessor level, BlockPos pos, int maxSearchLength, boolean includeMergedTip) {
        if (isTip(state, includeMergedTip)) {
            return pos;
        }
        Direction dir = state.getValue(TIP_DIRECTION);
        BiPredicate<BlockPos, BlockState> path = (p, s) -> s.is(state.getBlock()) && s.getValue(TIP_DIRECTION) == dir;
        return findBlockVertical(level, pos, dir.getAxisDirection(), path, s -> isTip(s, includeMergedTip), maxSearchLength).orElse(null);
    }

    protected static Optional<BlockPos> findBlockVertical(LevelAccessor level, BlockPos pos, Direction.AxisDirection axisDirection,
            BiPredicate<BlockPos, BlockState> pathPredicate, Predicate<BlockState> targetPredicate, int maxSteps) {
        Direction direction = Direction.get(axisDirection, Direction.Axis.Y);
        BlockPos.MutableBlockPos m = pos.mutable();
        for (int i = 1; i < maxSteps; i++) {
            m.move(direction);
            BlockState s = level.getBlockState(m);
            if (targetPredicate.test(s)) {
                return Optional.of(m.immutable());
            }
            if (level.isOutsideBuildHeight(m.getY()) || !pathPredicate.test(m, s)) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private boolean canTipGrow(BlockState tip, ServerLevel level, BlockPos tipPos) {
        Direction dir = tip.getValue(TIP_DIRECTION);
        BlockState at = level.getBlockState(tipPos.relative(dir));
        if (!at.getFluidState().isEmpty()) {
            return false;
        }
        return at.isAir() || this.isUnmergedTipWithDirection(at, dir.getOpposite());
    }

    private boolean isUnmergedTipWithDirection(BlockState state, Direction tipDirection) {
        return isTip(state, false) && state.getValue(TIP_DIRECTION) == tipDirection && state.is(this);
    }

    private void grow(ServerLevel level, BlockPos from, Direction to) {
        BlockPos target = from.relative(to);
        BlockState existing = level.getBlockState(target);
        if (this.isUnmergedTipWithDirection(existing, to.getOpposite()) && this.shouldMergeTips()) {
            this.createMergedTips(existing, level, target);
        } else if (existing.isAir() || existing.is(Blocks.WATER)) {
            this.createSpeleothem(level, target, to, DripstoneThickness.TIP);
        }
    }

    protected boolean shouldMergeTips() {
        return true;
    }

    private void createSpeleothem(LevelAccessor level, BlockPos pos, Direction direction, DripstoneThickness thickness) {
        level.setBlock(pos, this.defaultBlockState().setValue(TIP_DIRECTION, direction).setValue(THICKNESS, thickness)
                .setValue(WATERLOGGED, level.getFluidState(pos).isSourceOfType(Fluids.WATER)), 3);
    }

    private void createMergedTips(BlockState tip, LevelAccessor level, BlockPos tipPos) {
        BlockPos stalactite = tip.getValue(TIP_DIRECTION) == Direction.UP ? tipPos.above() : tipPos;
        BlockPos stalagmite = tip.getValue(TIP_DIRECTION) == Direction.UP ? tipPos : tipPos.below();
        this.createSpeleothem(level, stalactite, Direction.DOWN, DripstoneThickness.TIP_MERGE);
        this.createSpeleothem(level, stalagmite, Direction.UP, DripstoneThickness.TIP_MERGE);
    }

    private void growStalagmiteBelow(ServerLevel level, BlockPos above) {
        BlockPos.MutableBlockPos pos = above.mutable();
        for (int i = 0; i < 10; i++) {
            pos.move(Direction.DOWN);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return;
            }
            if (this.isUnmergedTipWithDirection(state, Direction.UP) && this.canTipGrow(state, level, pos)) {
                this.grow(level, pos, Direction.UP);
                return;
            }
            if (this.isValidSpeleothemPlacement(level, pos, Direction.UP) && !level.isWaterAt(pos.below())) {
                this.grow(level, pos.below(), Direction.UP);
                return;
            }
            if (this.blocksStalagmiteScan(level, pos, state)) {
                return;
            }
        }
    }

    protected boolean blocksStalagmiteScan(LevelReader level, BlockPos pos, BlockState state) {
        return false;
    }

    protected static boolean isFreeHangingStalactite(BlockState state) {
        return isStalactite(state) && state.getValue(THICKNESS) == DripstoneThickness.TIP && !state.getValue(WATERLOGGED);
    }

    protected int getMaxGrowthLength() {
        return 7;
    }
}
