package dev.frosthaven.havenbackports.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mojang's 26.x {@code SideChainPartBlock}, line for line: side by side blocks facing the same way join into a chain
 * (left, center, right) of at most {@link #getMaxChainLength()}. Mojang's ArrayListDeque is Java 21's ArrayList here
 * (addFirst / addLast).
 */
public interface SideChainPartBlock {
    SideChainPart getSideChainPart(BlockState state);

    BlockState setSideChainPart(BlockState state, SideChainPart newPart);

    Direction getFacing(BlockState state);

    boolean isConnectable(BlockState state);

    int getMaxChainLength();

    default List<BlockPos> getAllBlocksConnectedTo(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isConnectable(state)) {
            return List.of();
        }
        Neighbors neighbors = getNeighbors(level, pos, getFacing(state));
        List<BlockPos> results = new ArrayList<>();
        results.add(pos);
        addBlocksConnectingTowards(neighbors::left, SideChainPart.LEFT, results::addFirst);
        addBlocksConnectingTowards(neighbors::right, SideChainPart.RIGHT, results::addLast);
        return results;
    }

    private void addBlocksConnectingTowards(IntFunction<Neighbor> getNeighbor, SideChainPart endPart, Consumer<BlockPos> accumulator) {
        for (int steps = 1; steps < getMaxChainLength(); steps++) {
            Neighbor neighbor = getNeighbor.apply(steps);
            if (neighbor.connectsTowards(endPart)) {
                accumulator.accept(neighbor.pos());
            }
            if (neighbor.isUnconnectableOrChainEnd()) {
                break;
            }
        }
    }

    default void updateNeighborsAfterPoweringDown(LevelAccessor level, BlockPos pos, BlockState state) {
        Neighbors neighbors = getNeighbors(level, pos, getFacing(state));
        neighbors.left().disconnectFromRight();
        neighbors.right().disconnectFromLeft();
    }

    default void updateSelfAndNeighborsOnPoweringUp(LevelAccessor level, BlockPos pos, BlockState state, BlockState oldState) {
        if (!isConnectable(state) || isBeingUpdatedByNeighbor(state, oldState)) {
            return;
        }
        Neighbors neighbors = getNeighbors(level, pos, getFacing(state));
        SideChainPart newPartForSelf = SideChainPart.UNCONNECTED;
        int existingChainOnTheLeft = neighbors.left().isConnectable() ? getAllBlocksConnectedTo(level, neighbors.left().pos()).size() : 0;
        int existingChainOnTheRight = neighbors.right().isConnectable() ? getAllBlocksConnectedTo(level, neighbors.right().pos()).size() : 0;
        int currentChainLength = 1;
        if (canConnect(existingChainOnTheLeft, currentChainLength)) {
            newPartForSelf = newPartForSelf.whenConnectedToTheLeft();
            neighbors.left().connectToTheRight();
            currentChainLength += existingChainOnTheLeft;
        }
        if (canConnect(existingChainOnTheRight, currentChainLength)) {
            newPartForSelf = newPartForSelf.whenConnectedToTheRight();
            neighbors.right().connectToTheLeft();
        }
        setPart(level, pos, newPartForSelf);
    }

    private boolean canConnect(int newBlocksToConnectTo, int currentChainLength) {
        return newBlocksToConnectTo > 0 && currentChainLength + newBlocksToConnectTo <= getMaxChainLength();
    }

    private boolean isBeingUpdatedByNeighbor(BlockState state, BlockState oldState) {
        boolean isGettingConnected = getSideChainPart(state).isConnected();
        boolean hasBeenConnectedBefore = isConnectable(oldState) && getSideChainPart(oldState).isConnected();
        return isGettingConnected || hasBeenConnectedBefore;
    }

    private Neighbors getNeighbors(LevelAccessor level, BlockPos center, Direction facing) {
        return new Neighbors(this, level, facing, center, new HashMap<>());
    }

    private void setPart(LevelAccessor level, BlockPos pos, SideChainPart newPart) {
        BlockState state = level.getBlockState(pos);
        if (getSideChainPart(state) != newPart) {
            level.setBlock(pos, setSideChainPart(state, newPart), 3);   // Level.setBlockAndUpdate
        }
    }

    record EmptyNeighbor(BlockPos pos) implements Neighbor {
        @Override public boolean isConnectable() { return false; }
        @Override public boolean isUnconnectableOrChainEnd() { return true; }
        @Override public boolean connectsTowards(SideChainPart endPart) { return false; }
    }

    sealed interface Neighbor permits EmptyNeighbor, SideChainNeighbor {
        BlockPos pos();
        boolean isConnectable();
        boolean isUnconnectableOrChainEnd();
        boolean connectsTowards(SideChainPart endPart);
        default void connectToTheRight() {}
        default void connectToTheLeft() {}
        default void disconnectFromRight() {}
        default void disconnectFromLeft() {}
    }

    record Neighbors(SideChainPartBlock block, LevelAccessor level, Direction facing, BlockPos center, Map<BlockPos, Neighbor> cache) {
        private boolean isConnectableToThisBlock(BlockState neighbor) {
            return block.isConnectable(neighbor) && block.getFacing(neighbor) == facing;
        }

        private Neighbor createNewNeighbor(BlockPos pos) {
            BlockState neighbor = level.getBlockState(pos);
            SideChainPart part = isConnectableToThisBlock(neighbor) ? block.getSideChainPart(neighbor) : null;
            return part == null ? new EmptyNeighbor(pos) : new SideChainNeighbor(level, block, pos, part);
        }

        private Neighbor getOrCreateNeighbor(Direction dir, int steps) {
            return cache.computeIfAbsent(center.relative(dir, steps), this::createNewNeighbor);
        }

        public Neighbor left(int steps) { return getOrCreateNeighbor(facing.getClockWise(), steps); }
        public Neighbor right(int steps) { return getOrCreateNeighbor(facing.getCounterClockWise(), steps); }
        public Neighbor left() { return left(1); }
        public Neighbor right() { return right(1); }
    }

    record SideChainNeighbor(LevelAccessor level, SideChainPartBlock block, BlockPos pos, SideChainPart part) implements Neighbor {
        @Override public boolean isConnectable() { return true; }
        @Override public boolean isUnconnectableOrChainEnd() { return part.isChainEnd(); }
        @Override public boolean connectsTowards(SideChainPart endPart) { return part.isConnectionTowards(endPart); }
        @Override public void connectToTheRight() { block.setPart(level, pos, part.whenConnectedToTheRight()); }
        @Override public void connectToTheLeft() { block.setPart(level, pos, part.whenConnectedToTheLeft()); }
        @Override public void disconnectFromRight() { block.setPart(level, pos, part.whenDisconnectedFromTheRight()); }
        @Override public void disconnectFromLeft() { block.setPart(level, pos, part.whenDisconnectedFromTheLeft()); }
    }
}
