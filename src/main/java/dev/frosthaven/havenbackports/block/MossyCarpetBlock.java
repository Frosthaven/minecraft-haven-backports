package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.PaleGarden;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x MossyCarpetBlock (the pale moss carpet), line for line: a base layer with LOW or TALL sides where a face holds
 * them, and a topper layer above (random sides on placement, all sides by bonemeal). 26.x's BOTTOM property is
 * 1.21.1's BlockStateProperties.BOTTOM ("bottom"); the side properties are the wall ones. 1.21.1's simple_block
 * feature cannot call placeAt: Haven Backports' simple_block port does (worldgen/SimpleBlockFeature).
 */
public class MossyCarpetBlock extends Block implements BonemealableBlock {
    public static final BooleanProperty BASE = BlockStateProperties.BOTTOM;
    public static final EnumProperty<WallSide> NORTH = BlockStateProperties.NORTH_WALL;
    public static final EnumProperty<WallSide> EAST = BlockStateProperties.EAST_WALL;
    public static final EnumProperty<WallSide> SOUTH = BlockStateProperties.SOUTH_WALL;
    public static final EnumProperty<WallSide> WEST = BlockStateProperties.WEST_WALL;
    public static final Map<Direction, EnumProperty<WallSide>> PROPERTY_BY_DIRECTION = Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST,
            Direction.SOUTH, SOUTH, Direction.WEST, WEST);
    private final Function<BlockState, VoxelShape> shapes;

    public MossyCarpetBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(BASE, true).setValue(NORTH, WallSide.NONE).setValue(EAST, WallSide.NONE)
                .setValue(SOUTH, WallSide.NONE).setValue(WEST, WallSide.NONE));
        this.shapes = this.makeShapes();
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(MossyCarpetBlock::new);
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> low = Shapes26.rotateHorizontal(Shapes26.boxZ(16.0, 0.0, 10.0, 0.0, 1.0));
        Map<Direction, VoxelShape> tall = Shapes26.rotateAll(Shapes26.boxZ(16.0, 0.0, 1.0));
        return this.getShapeForEachState(state -> {
            VoxelShape shape = state.getValue(BASE) ? tall.get(Direction.DOWN) : Shapes.empty();
            for (Map.Entry<Direction, EnumProperty<WallSide>> e : PROPERTY_BY_DIRECTION.entrySet()) {
                switch (state.getValue(e.getValue())) {
                    case LOW -> shape = Shapes.or(shape, low.get(e.getKey()));
                    case TALL -> shape = Shapes.or(shape, tall.get(e.getKey()));
                    default -> { }
                }
            }
            return shape.isEmpty() ? Shapes.block() : shape;
        })::get;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BASE) ? this.shapes.apply(this.defaultBlockState()) : Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return state.getValue(BASE) ? !below.isAir() : below.is(this) && below.getValue(BASE);
    }

    private static boolean hasFaces(BlockState state) {
        if (state.getValue(BASE)) {
            return true;
        }
        for (EnumProperty<WallSide> p : PROPERTY_BY_DIRECTION.values()) {
            if (state.getValue(p) != WallSide.NONE) {
                return true;
            }
        }
        return false;
    }

    private static boolean canSupportAtFace(BlockGetter level, BlockPos pos, Direction direction) {
        if (direction == Direction.UP) {
            return false;
        }
        BlockPos n = pos.relative(direction);
        return MultifaceBlock.canAttachTo(level, direction, n, level.getBlockState(n));
    }

    private static BlockState getUpdatedState(BlockState state, BlockGetter level, BlockPos pos, boolean createSides) {
        Block carpet = PaleGarden.PALE_MOSS_CARPET.get();
        BlockState above = null;
        BlockState below = null;
        createSides |= state.getValue(BASE);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            EnumProperty<WallSide> property = PROPERTY_BY_DIRECTION.get(direction);
            WallSide side = canSupportAtFace(level, pos, direction) ? (createSides ? WallSide.LOW : state.getValue(property)) : WallSide.NONE;
            if (side == WallSide.LOW) {
                if (above == null) {
                    above = level.getBlockState(pos.above());
                }
                if (above.is(carpet) && above.getValue(property) != WallSide.NONE && !above.getValue(BASE)) {
                    side = WallSide.TALL;
                }
                if (!state.getValue(BASE)) {
                    if (below == null) {
                        below = level.getBlockState(pos.below());
                    }
                    if (below.is(carpet) && below.getValue(property) == WallSide.NONE) {
                        side = WallSide.NONE;
                    }
                }
            }
            state = state.setValue(property, side);
        }
        return state;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getUpdatedState(this.defaultBlockState(), context.getLevel(), context.getClickedPos(), true);
    }

    public static void placeAt(LevelAccessor level, BlockPos pos, RandomSource random, int flags) {
        BlockState simple = PaleGarden.PALE_MOSS_CARPET.get().defaultBlockState();
        BlockState adjusted = getUpdatedState(simple, level, pos, true);
        level.setBlock(pos, adjusted, flags);
        BlockState topper = createTopperWithSideChance(level, pos, random::nextBoolean);
        if (!topper.isAir()) {
            level.setBlock(pos.above(), topper, flags);
            level.setBlock(pos, getUpdatedState(adjusted, level, pos, true), flags);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (!level.isClientSide()) {
            BlockState topper = createTopperWithSideChance(level, pos, level.getRandom()::nextBoolean);
            if (!topper.isAir()) {
                level.setBlockAndUpdate(pos.above(), topper);
            }
        }
    }

    private static BlockState createTopperWithSideChance(BlockGetter level, BlockPos pos, BooleanSupplier sideSurvivalTest) {
        Block carpet = PaleGarden.PALE_MOSS_CARPET.get();
        BlockPos above = pos.above();
        BlockState previous = level.getBlockState(above);
        boolean carpetAbove = previous.is(carpet);
        if ((!carpetAbove || !previous.getValue(BASE)) && (carpetAbove || previous.canBeReplaced())) {
            BlockState aboveState = getUpdatedState(carpet.defaultBlockState().setValue(BASE, false), level, above, true);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                EnumProperty<WallSide> property = PROPERTY_BY_DIRECTION.get(direction);
                if (aboveState.getValue(property) != WallSide.NONE && !sideSurvivalTest.getAsBoolean()) {
                    aboveState = aboveState.setValue(property, WallSide.NONE);
                }
            }
            return hasFaces(aboveState) && aboveState != previous ? aboveState : Blocks.AIR.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState updated = getUpdatedState(state, level, pos, false);
        return !hasFaces(updated) ? Blocks.AIR.defaultBlockState() : updated;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BASE, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH)).setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state.setValue(NORTH, state.getValue(EAST)).setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST)).setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> state.setValue(NORTH, state.getValue(WEST)).setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST)).setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> super.mirror(state, mirror);
        };
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(BASE) && !createTopperWithSideChance(level, pos, () -> true).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockState topper = createTopperWithSideChance(level, pos, () -> true);
        if (!topper.isAir()) {
            level.setBlockAndUpdate(pos.above(), topper);
        }
    }
}
