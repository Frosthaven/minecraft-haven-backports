package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Content;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mojang's 26.x {@code ShelfBlock}, method for method, on 1.21.1's APIs. Unpowered: right-clicking a slot swaps the held
 * item with it. Powered: powered shelves side by side join (up to 3) and right-clicking swaps the whole row with the
 * hotbar. Comparators read 3 bits (one per filled slot) from the back only.
 *
 * <p>1.21.1 forms: {@code useItemOn} returns ItemInteractionResult (26.x PASS = SKIP_DEFAULT_BLOCK_INTERACTION, the
 * item's own use goes on; SUCCESS_SERVER = SUCCESS on the server); 1.21.1 items have no USE_EFFECTS component, so a
 * single swap always sends ITEM_INTERACT_FINISH; the comparator's side reaches {@link #getAnalogOutputSignal(BlockState,
 * Level, BlockPos, Direction)} through ComparatorBlockMixin; BaseEntityBlock needs RenderShape.MODEL set.
 */
public class ShelfBlock extends BaseEntityBlock implements SelectableSlotContainer, SideChainPartBlock, SimpleWaterloggedBlock {
    public static final MapCodec<ShelfBlock> CODEC = simpleCodec(ShelfBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<SideChainPart> SIDE_CHAIN_PART = SideChainPart.PROPERTY;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final TagKey<Block> WOODEN_SHELVES = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("wooden_shelves"));
    /** Shapes.rotateHorizontal(top lip, back, bottom lip): the north shape turned to each facing. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        double[][] boxes = {{0, 12, 11, 16, 16, 13}, {0, 0, 13, 16, 16, 16}, {0, 0, 11, 16, 4, 13}};
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                shape = Shapes.or(shape, turned(b, d));
            }
            SHAPES.put(d, shape);
        }
    }

    /** A north-facing box (pixels) turned clockwise from north to {@code d}, about the block's vertical axis. */
    private static VoxelShape turned(double[] b, Direction d) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (d) {
            case EAST -> Block.box(16 - z1, b[1], x0, 16 - z0, b[4], x1);
            case SOUTH -> Block.box(16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0);
            case WEST -> Block.box(z0, b[1], 16 - x1, z1, b[4], 16 - x0);
            default -> Block.box(x0, b[1], z0, x1, b[4], z1);
        };
    }

    public ShelfBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false)
                .setValue(SIDE_CHAIN_PART, SideChainPart.UNCONNECTED).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.WATER && state.getFluidState().is(FluidTags.WATER);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShelfBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, SIDE_CHAIN_PART, WATERLOGGED);
    }

    /** 26.x preRemoveSideEffects (drop the items) + affectNeighborsAfterRemoval. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean removed = !state.is(newState.getBlock());
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (removed && !level.isClientSide()) {
            updateNeighborsAfterPoweringDown(level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        boolean signal = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != signal) {
            BlockState newState = state.setValue(POWERED, signal);
            if (!signal) {
                newState = newState.setValue(SIDE_CHAIN_PART, SideChainPart.UNCONNECTED);
            }
            level.setBlockAndUpdate(pos, newState);
            playSound(level, pos, signal ? Content.SHELF_ACTIVATE.get() : Content.SHELF_DEACTIVATE.get());
            level.gameEvent(signal ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(newState));
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState replaced = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()))
                .setValue(WATERLOGGED, replaced.isSourceOfType(Fluids.WATER));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public int getRows() {
        return 1;
    }

    @Override
    public int getColumns() {
        return 3;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ShelfBlockEntity shelf) || hand.equals(InteractionHand.OFF_HAND)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        OptionalInt hitSlot = getHitSlot(hitResult, state.getValue(FACING));
        if (hitSlot.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        Inventory inventory = player.getInventory();
        if (level.isClientSide()) {
            return inventory.getSelected().isEmpty() ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.CONSUME;
        }
        if (!state.getValue(POWERED)) {
            boolean itemRemoved = swapSingleItem(itemStack, player, shelf, hitSlot.getAsInt(), inventory);
            if (itemRemoved) {
                playSound(level, pos, itemStack.isEmpty() ? Content.SHELF_TAKE_ITEM.get() : Content.SHELF_SINGLE_SWAP.get());
            } else {
                if (itemStack.isEmpty()) {
                    return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
                }
                playSound(level, pos, Content.SHELF_PLACE_ITEM.get());
            }
            return ItemInteractionResult.SUCCESS;
        }
        boolean anySwapped = swapHotbar(level, pos, inventory);
        if (!anySwapped) {
            return ItemInteractionResult.CONSUME;
        }
        playSound(level, pos, Content.SHELF_MULTI_SWAP.get());
        return ItemInteractionResult.SUCCESS;
    }

    private static boolean swapSingleItem(ItemStack itemStack, Player player, ShelfBlockEntity shelf, int hitSlot, Inventory inventory) {
        ItemStack removedItem = shelf.swapItemNoUpdate(hitSlot, itemStack);
        ItemStack newInventoryItem = player.hasInfiniteMaterials() && removedItem.isEmpty() ? itemStack.copy() : removedItem;
        inventory.setItem(inventory.selected, newInventoryItem);
        inventory.setChanged();
        shelf.setChanged(GameEvent.ITEM_INTERACT_FINISH);
        return !removedItem.isEmpty();
    }

    private boolean swapHotbar(Level level, BlockPos pos, Inventory inventory) {
        List<BlockPos> connected = getAllBlocksConnectedTo(level, pos);
        if (connected.isEmpty()) {
            return false;
        }
        boolean anySwapped = false;
        for (int part = 0; part < connected.size(); part++) {
            if (!(level.getBlockEntity(connected.get(part)) instanceof ShelfBlockEntity shelf)) {
                continue;
            }
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                int inventorySlot = 9 - (connected.size() - part) * shelf.getContainerSize() + slot;
                if (inventorySlot >= 0 && inventorySlot <= inventory.getContainerSize()) {
                    ItemStack placed = inventory.removeItemNoUpdate(inventorySlot);
                    ItemStack removed = shelf.swapItemNoUpdate(slot, placed);
                    if (!placed.isEmpty() || !removed.isEmpty()) {
                        inventory.setItem(inventorySlot, removed);
                        anySwapped = true;
                    }
                }
            }
            inventory.setChanged();
            shelf.setChanged(GameEvent.ENTITY_INTERACT);
        }
        return anySwapped;
    }

    @Override
    public SideChainPart getSideChainPart(BlockState state) {
        return state.getValue(SIDE_CHAIN_PART);
    }

    @Override
    public BlockState setSideChainPart(BlockState state, SideChainPart newPart) {
        return state.setValue(SIDE_CHAIN_PART, newPart);
    }

    @Override
    public Direction getFacing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public boolean isConnectable(BlockState state) {
        return state.is(WOODEN_SHELVES) && state.getOptionalValue(POWERED).orElse(false);
    }

    @Override
    public int getMaxChainLength() {
        return 3;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (state.getValue(POWERED)) {
            updateSelfAndNeighborsOnPoweringUp(level, pos, state, oldState);
        } else {
            updateNeighborsAfterPoweringDown(level, pos, state);
        }
    }

    private void playSound(LevelAccessor level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** 1.21.1 callers that give no side (other mods' readers) read the shelf as from its back, the side 26.x reads. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getAnalogOutputSignal(state, level, pos, state.getValue(FACING).getOpposite());
    }

    /** 26.x's comparator form: {@code direction} is the shelf's side the comparator touches. */
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.isClientSide() || direction != state.getValue(FACING).getOpposite()) {
            return 0;
        }
        if (level.getBlockEntity(pos) instanceof ShelfBlockEntity shelf) {
            int a = shelf.getItem(0).isEmpty() ? 0 : 1;
            int b = shelf.getItem(1).isEmpty() ? 0 : 1;
            int c = shelf.getItem(2).isEmpty() ? 0 : 1;
            return a | b << 1 | c << 2;
        }
        return 0;
    }
}
