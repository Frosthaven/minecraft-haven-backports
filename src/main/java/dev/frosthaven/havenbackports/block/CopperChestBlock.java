package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.DataMapHooks;

/**
 * Mojang's 26.x {@code CopperChestBlock} (and the parts of 26.x's {@code ChestBlock} it relies on, which 1.21.1's
 * ChestBlock does not have): any two copper chests join (#minecraft:copper_chests), a joined pair takes the less
 * oxidized look, its hinge sounds depend on its age, and a chest keeps its contents when it oxidizes, is waxed or is
 * scraped (26.x's shouldChangedStateKeepBlockEntity; here onRemove leaves the shared chest block entity in place).
 * Uses the vanilla chest block entity, as 26.x does (Content adds these blocks to its valid blocks).
 */
public class CopperChestBlock extends ChestBlock {
    public static final TagKey<Block> COPPER_CHESTS = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("copper_chests"));
    private final WeatherState weatherState;
    private final Supplier<SoundEvent> openSound, closeSound;

    public CopperChestBlock(WeatherState weatherState, Supplier<SoundEvent> openSound, Supplier<SoundEvent> closeSound, Properties properties) {
        super(properties, () -> BlockEntityType.CHEST);
        this.weatherState = weatherState;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return MapCodec.unit(this);   // registered blocks, not data-driven; the unit codec returns this block
    }

    public WeatherState getState() {
        return weatherState;
    }

    public boolean isWaxed() {
        return true;
    }

    public SoundEvent getOpenChestSound() {
        return openSound.get();
    }

    public SoundEvent getCloseChestSound() {
        return closeSound.get();
    }

    public boolean chestCanConnectTo(BlockState state) {
        return state.is(COPPER_CHESTS) && state.hasProperty(ChestBlock.TYPE);
    }

    /** 26.x ChestBlock.getStateForPlacement with chestCanConnectTo, then CopperChestBlock's least-oxidized pick. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        ChestType type = ChestType.SINGLE;
        Direction facing = context.getHorizontalDirection().getOpposite();
        FluidState replaced = context.getLevel().getFluidState(context.getClickedPos());
        boolean secondaryUse = context.isSecondaryUseActive();
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isHorizontal() && secondaryUse) {
            Direction neighbourFacing = candidatePartnerFacing(context.getLevel(), context.getClickedPos(), clickedFace.getOpposite());
            if (neighbourFacing != null && neighbourFacing.getAxis() != clickedFace.getAxis()) {
                facing = neighbourFacing;
                type = neighbourFacing.getCounterClockWise() == clickedFace.getOpposite() ? ChestType.RIGHT : ChestType.LEFT;
            }
        }
        if (type == ChestType.SINGLE && !secondaryUse) {
            type = getChestType(context.getLevel(), context.getClickedPos(), facing);
        }
        BlockState state = defaultBlockState().setValue(FACING, facing).setValue(TYPE, type)
                .setValue(WATERLOGGED, replaced.isSourceOfType(Fluids.WATER));
        return leastOxidizedOfConnected(state, context.getLevel(), context.getClickedPos());
    }

    private ChestType getChestType(Level level, BlockPos pos, Direction facing) {
        if (facing == candidatePartnerFacing(level, pos, facing.getClockWise())) {
            return ChestType.LEFT;
        }
        return facing == candidatePartnerFacing(level, pos, facing.getCounterClockWise()) ? ChestType.RIGHT : ChestType.SINGLE;
    }

    @Nullable
    private Direction candidatePartnerFacing(Level level, BlockPos pos, Direction neighbourDirection) {
        BlockState state = level.getBlockState(pos.relative(neighbourDirection));
        return chestCanConnectTo(state) && state.getValue(TYPE) == ChestType.SINGLE ? state.getValue(FACING) : null;
    }

    private static BlockState leastOxidizedOfConnected(BlockState state, Level level, BlockPos pos) {
        BlockState connected = level.getBlockState(pos.relative(getConnectedDirection(state)));
        if (state.getValue(TYPE) != ChestType.SINGLE && state.getBlock() instanceof CopperChestBlock self
                && connected.getBlock() instanceof CopperChestBlock other) {
            BlockState updated = state, predicted = connected;
            if (self.isWaxed() != other.isWaxed()) {
                updated = unwax(self, state).orElse(state);
                predicted = unwax(other, connected).orElse(connected);
            }
            Block least = self.weatherState.ordinal() <= other.weatherState.ordinal() ? updated.getBlock() : predicted.getBlock();
            return least.withPropertiesOf(updated);
        }
        return state;
    }

    private static Optional<BlockState> unwax(CopperChestBlock block, BlockState state) {
        if (!block.isWaxed()) {
            return Optional.of(state);
        }
        // 26.x HoneycombItem.WAX_OFF_BY_BLOCK; on NeoForge 1.21.1 the waxables data map, reversed
        return Optional.ofNullable(DataMapHooks.getBlockUnwaxed(state.getBlock())).map(b -> b.withPropertiesOf(state));
    }

    /** 26.x COPPER_TO_COPPER_CHEST_MAPPING: WeatheringCopperCollection.zipApply(COPPER_BLOCK, COPPER_CHEST), waxed to waxed. */
    private static final com.google.common.base.Supplier<java.util.Map<Block, Block>> COPPER_TO_COPPER_CHEST = com.google.common.base.Suppliers.memoize(() -> {
        java.util.Map<Block, Block> map = new java.util.HashMap<>();
        int i = 0;
        for (String waxed : new String[]{"", "waxed_"}) {
            for (WeatherState stage : WeatherState.values()) {
                String copper = waxed + (stage == WeatherState.UNAFFECTED ? "copper_block" : stage.getSerializedName() + "_copper");
                map.put(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(copper)),
                        dev.frosthaven.havenbackports.Chests.ALL.get(i++).get());
            }
        }
        return map;
    });

    /** 26.x CopperChestBlock.getFromCopperBlock: the chest a copper golem leaves where its copper block was. */
    public static BlockState getFromCopperBlock(Block copperBlock, Direction facing, Level level, BlockPos pos) {
        CopperChestBlock block = (CopperChestBlock) COPPER_TO_COPPER_CHEST.get().getOrDefault(copperBlock, dev.frosthaven.havenbackports.Chests.ALL.get(0).get());
        ChestType type = block.getChestType(level, pos, facing);
        BlockState state = block.defaultBlockState().setValue(FACING, facing).setValue(TYPE, type);
        return leastOxidizedOfConnected(state, level, pos);
    }

    /** 26.x ChestBlock.updateShape with chestCanConnectTo, then CopperChestBlock's: a joined pair shares one block. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        BlockState result;
        if (chestCanConnectTo(neighbour) && direction.getAxis().isHorizontal()) {
            ChestType neighbourType = neighbour.getValue(TYPE);
            if (state.getValue(TYPE) == ChestType.SINGLE && neighbourType != ChestType.SINGLE
                    && state.getValue(FACING) == neighbour.getValue(FACING) && getConnectedDirection(neighbour) == direction.getOpposite()) {
                result = state.setValue(TYPE, neighbourType.getOpposite());
            } else {
                result = state;
            }
        } else if (getConnectedDirection(state) == direction) {
            result = state.setValue(TYPE, ChestType.SINGLE);
        } else {
            result = state;
        }
        if (chestCanConnectTo(neighbour) && result.getValue(TYPE) != ChestType.SINGLE && getConnectedDirection(result) == direction) {
            return neighbour.getBlock().withPropertiesOf(result);
        }
        return result;
    }

    /** Becoming another copper chest keeps the block entity and its items (26.x shouldChangedStateKeepBlockEntity). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (newState.is(COPPER_CHESTS)) {
            return;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
