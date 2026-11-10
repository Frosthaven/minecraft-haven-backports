package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x StrawBedBlock with the 26.x AbstractBedBlock it extends (1.21.1 has only BedBlock, with a block
 * entity, a bounce and an explosion): a two-part bed from block models. Its rule (dimension straw_bed_rule): in the
 * overworld you sleep when it is dark, it never sets your spawn and it breaks when you get up; in the nether and the
 * end it breaks when used. 1.21.1 has no bed rules: the overworld rule is vanilla sleeping plus StrawBedEvents (spawn
 * kept, bed broken on waking), "breaks when used" is a dimension where beds do not work (bedWorks, which is 26.x's
 * bed_rule destroy_on_use there). It is a bed to NeoForge (isBed), so sleeping, occupancy and waking work.
 * Shapes: Block.column(16, 0, 4) and the pillow box, turned per facing as 26.x's Shapes.rotateHorizontal.
 */
public class StrawBedBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<StrawBedBlock> CODEC = simpleCodec(StrawBedBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    private static final VoxelShape BASE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);
    private static final Map<Direction, VoxelShape> FOOT_SHAPES = new EnumMap<>(Direction.class), HEAD_SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            FOOT_SHAPES.put(d, BASE_SHAPE);
            HEAD_SHAPES.put(d, Shapes.or(BASE_SHAPE, rotatedBox(0.0, 0.0, 0.0, 16.0, 5.0, 8.0, d)));
        }
    }

    public StrawBedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT).setValue(OCCUPIED, false));
    }

    /** A box given for north, turned clockwise about the block centre to face d (26.x OctahedralGroup BLOCK_ROT_Y_*). */
    private static VoxelShape rotatedBox(double x1, double y1, double z1, double x2, double y2, double z2, Direction d) {
        return switch (d) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OCCUPIED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(PART) == BedPart.HEAD ? HEAD_SHAPES : FOOT_SHAPES).get(getConnectedDirection(state).getOpposite());
    }

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, @Nullable LivingEntity sleeper) {
        return true;
    }

    /** 26.x getSleepHeight: the foot's top, read from the head through its foot (for LivingEntityMixin). */
    public static double sleepHeight(BlockState state, BlockGetter level, BlockPos pos) {
        BlockPos on = pos;
        BlockState onState = state;
        if (state.getValue(PART) == BedPart.HEAD) {
            on = pos.relative(getConnectedDirection(state));
            onState = level.getBlockState(on);
            if (!(onState.getBlock() instanceof StrawBedBlock) || onState.getValue(PART) != BedPart.FOOT) {
                return 0.5625;
            }
        }
        VoxelShape shape = onState.getShape(level, on);
        return shape.isEmpty() ? 0.5625 : shape.max(Direction.Axis.Y);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BedPart part = state.getValue(PART);
        BlockState other = level.getBlockState(pos.relative(neighbourDirection(part, state.getValue(FACING))));
        if (!other.is(this) || other.getValue(PART) == part) {
            return InteractionResult.CONSUME;
        }
        if (part != BedPart.HEAD) {
            pos = pos.relative(state.getValue(FACING));
            state = level.getBlockState(pos);
        }
        if (!level.dimensionType().bedWorks()) {   // straw_bed_rule destroy_on_use (the nether and the end)
            destroyBed(level, pos);
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(OCCUPIED)) {
            if (!kickVillagerOutOfBed(level, pos)) {
                player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
            }
            return InteractionResult.SUCCESS;
        }
        player.startSleepInBed(pos).ifLeft(problem -> {
            if (problem.getMessage() != null) {
                player.displayClientMessage(problem.getMessage(), true);
            }
        });
        return InteractionResult.SUCCESS;
    }

    /** 26.x StrawBedBlock.destroyBed: the leave sound, then gone (no drop). */
    public void destroyBed(Level level, BlockPos pos) {
        level.playSound(null, pos, dev.frosthaven.havenbackports.StrawBeds.BREAK_LEAVE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private static boolean kickVillagerOutOfBed(Level level, BlockPos pos) {
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(pos), LivingEntity::isSleeping);
        if (villagers.isEmpty()) {
            return false;
        }
        villagers.get(0).stopSleeping();
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == neighbourDirection(state.getValue(PART), state.getValue(FACING))) {
            return neighbour.is(this) && neighbour.getValue(PART) != state.getValue(PART) ? state.setValue(OCCUPIED, neighbour.getValue(OCCUPIED))
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    private static Direction neighbourDirection(BedPart part, Direction facing) {
        return part == BedPart.FOOT ? facing : facing.getOpposite();
    }

    public static Direction getConnectedDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(PART) == BedPart.HEAD ? facing.getOpposite() : facing;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {   // 26.x preventsBlockDrops
            BedPart part = state.getValue(PART);
            if (part == BedPart.FOOT) {
                BlockPos headPos = pos.relative(neighbourDirection(part, state.getValue(FACING)));
                BlockState head = level.getBlockState(headPos);
                if (head.is(this) && head.getValue(PART) == BedPart.HEAD) {
                    level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, headPos, Block.getId(head));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos other = context.getClickedPos().relative(facing);
        Level level = context.getLevel();
        return level.getBlockState(other).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(other)
                ? this.defaultBlockState().setValue(FACING, facing) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        level.setBlockAndUpdate(pos.relative(state.getValue(FACING)), state.setValue(PART, BedPart.HEAD));
    }

    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        BlockPos source = pos.relative(state.getValue(FACING), state.getValue(PART) == BedPart.HEAD ? 0 : 1);
        return Mth.getSeed(source.getX(), pos.getY(), source.getZ());
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
