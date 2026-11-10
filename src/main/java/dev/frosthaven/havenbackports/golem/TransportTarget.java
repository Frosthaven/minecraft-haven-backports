package dev.frosthaven.havenbackports.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

/** Mojang's 26.x TransportItemsBetweenContainers.TransportItemTarget (a chest-like container a golem can use). */
public record TransportTarget(BlockPos pos, BlockEntity blockEntity, BlockState state) {
    @Nullable
    public static TransportTarget tryCreate(BlockEntity blockEntity, Level level) {
        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = blockEntity.getBlockState();
        return container(blockEntity, state, level, pos) != null ? new TransportTarget(pos, blockEntity, state) : null;
    }

    @Nullable
    public static TransportTarget tryCreate(BlockPos pos, Level level) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null : tryCreate(blockEntity, level);
    }

    @Nullable
    private static Container container(BlockEntity blockEntity, BlockState state, Level level, BlockPos pos) {
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, false);
        }
        return blockEntity instanceof Container container ? container : null;
    }

    @Nullable
    public Container container(Level level) {
        return container(blockEntity, state, level, pos);
    }

    /** 26.x ChestBlock.getConnectedBlockPos, for a chest of a double chest; null for a single one. */
    @Nullable
    public BlockPos connectedPos() {
        if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return null;
    }
}
