package dev.frosthaven.havenbackports.golem;

import com.google.common.collect.ImmutableMap;
import dev.frosthaven.havenbackports.Golems;
import dev.frosthaven.havenbackports.entity.Navigations;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x TransportItemsBetweenContainers, method for method (the copper golem's sorting): take up to 16 items
 * from the first filled slot of a source container, carry them to a destination that already holds that item or is
 * empty, remember visited (10) and unreachable (50) containers for 6000 ticks, idle 140 ticks when nothing matches,
 * queue while someone else has the container open. 1.21.1 forms: the target record is {@link TransportTarget}; the
 * three new memories are Golems'; the lock check reads BaseContainerBlockEntity.lockKey (access transformer).
 */
public class TransportItemsBetweenContainers extends Behavior<PathfinderMob> {
    public static final int TARGET_INTERACTION_TIME = 60;

    public enum ContainerInteractionState { PICKUP_ITEM, PICKUP_NO_ITEM, PLACE_ITEM, PLACE_NO_ITEM }

    public enum TransportItemState { TRAVELLING, QUEUING, INTERACTING }

    @FunctionalInterface
    public interface OnTargetReachedInteraction {
        void accept(PathfinderMob body, TransportTarget target, int ticksSinceReachingTarget);
    }

    private final float speedModifier;
    private final int horizontalSearchDistance, verticalSearchDistance;
    private final Predicate<BlockState> sourceBlockType, destinationBlockType;
    private final Predicate<TransportTarget> shouldQueueForTarget;
    private final Consumer<PathfinderMob> onStartTravelling;
    private final Map<ContainerInteractionState, OnTargetReachedInteraction> onTargetInteractionActions;
    @Nullable
    private TransportTarget target;
    private TransportItemState state = TransportItemState.TRAVELLING;
    @Nullable
    private ContainerInteractionState interactionState;
    private int ticksSinceReachingTarget;

    public TransportItemsBetweenContainers(float speedModifier, Predicate<BlockState> source, Predicate<BlockState> destination, int horizontal, int vertical,
                                           Map<ContainerInteractionState, OnTargetReachedInteraction> actions, Consumer<PathfinderMob> onStartTravelling,
                                           Predicate<TransportTarget> shouldQueueForTarget) {
        super(ImmutableMap.of(Golems.VISITED_BLOCK_POSITIONS.get(), MemoryStatus.REGISTERED, Golems.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS.get(),
                MemoryStatus.REGISTERED, Golems.TRANSPORT_ITEMS_COOLDOWN_TICKS.get(), MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.sourceBlockType = source;
        this.destinationBlockType = destination;
        this.horizontalSearchDistance = horizontal;
        this.verticalSearchDistance = vertical;
        this.onStartTravelling = onStartTravelling;
        this.shouldQueueForTarget = shouldQueueForTarget;
        this.onTargetInteractionActions = actions;
    }

    @Override
    protected void start(ServerLevel level, PathfinderMob body, long time) {
        if (body.getNavigation() instanceof Navigations.Ground nav) {
            nav.setCanPathToTargetsBelowSurface(true);
        }
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob body) {
        return !body.isLeashed();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, PathfinderMob body, long time) {
        return body.getBrain().getMemory(Golems.TRANSPORT_ITEMS_COOLDOWN_TICKS.get()).isEmpty() && !body.isPanicking() && !body.isLeashed();
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    @Override
    protected void tick(ServerLevel level, PathfinderMob body, long time) {
        boolean updatedInvalidTarget = updateInvalidTarget(level, body);
        if (target == null) {
            this.stop(level, body, time);
        } else if (!updatedInvalidTarget) {
            if (state == TransportItemState.QUEUING) {
                onQueuingForTarget(target, level, body);
            }
            if (state == TransportItemState.TRAVELLING) {
                onTravelToTarget(target, level, body);
            }
            if (state == TransportItemState.INTERACTING) {
                onReachedTarget(target, level, body);
            }
        }
    }

    private boolean updateInvalidTarget(ServerLevel level, PathfinderMob body) {
        if (hasValidTarget(level, body)) {
            return false;
        }
        stopTargetingCurrentTarget(body);
        Optional<TransportTarget> found = getTransportTarget(level, body);
        if (found.isPresent()) {
            target = found.get();
            onStartTravelling(body);
            setVisitedBlockPos(body, level, target.pos());
        } else {
            enterCooldownAfterNoMatchingTargetFound(body);
        }
        return true;
    }

    private void onQueuingForTarget(TransportTarget target, Level level, PathfinderMob body) {
        if (!isAnotherMobInteractingWithTarget(target, level)) {
            state = TransportItemState.TRAVELLING;
            walkTowardsTarget(body);
        }
    }

    protected void onTravelToTarget(TransportTarget target, Level level, PathfinderMob body) {
        if (isWithinTargetDistance(3.0, target, level, body, centerPos(body)) && isAnotherMobInteractingWithTarget(target, level)) {
            stopInPlace(body);
            state = TransportItemState.QUEUING;
        } else if (isWithinTargetDistance(interactionRange(body), target, level, body, centerPos(body))) {
            startOnReachedTargetInteraction(target, level, body);
        } else {
            walkTowardsTarget(body);
        }
    }

    private Vec3 centerPos(PathfinderMob body) {
        return middleY(body, body.position());
    }

    protected void onReachedTarget(TransportTarget target, Level level, PathfinderMob body) {
        if (!isWithinTargetDistance(2.0, target, level, body, centerPos(body))) {
            onStartTravelling(body);
            return;
        }
        ticksSinceReachingTarget++;
        onTargetInteraction(target, body);
        if (ticksSinceReachingTarget >= TARGET_INTERACTION_TIME) {
            Container container = target.container(level);
            if (container != null) {
                doReachedTargetInteraction(body, container, this::pickUpItems, (m, c) -> stopTargetingCurrentTarget(body), this::putDownItem,
                        (m, c) -> stopTargetingCurrentTarget(body));
                onStartTravelling(body);
            } else {
                stopTargetingCurrentTarget(body);
            }
        }
    }

    private void walkTowardsTarget(PathfinderMob body) {
        if (target != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(body, target.pos(), speedModifier, 0);
        }
    }

    private void startOnReachedTargetInteraction(TransportTarget target, Level level, PathfinderMob body) {
        Container container = target.container(level);
        if (container != null) {
            doReachedTargetInteraction(body, container, onReached(ContainerInteractionState.PICKUP_ITEM), onReached(ContainerInteractionState.PICKUP_NO_ITEM),
                    onReached(ContainerInteractionState.PLACE_ITEM), onReached(ContainerInteractionState.PLACE_NO_ITEM));
            state = TransportItemState.INTERACTING;
        } else {
            stopTargetingCurrentTarget(body);
        }
    }

    private void onStartTravelling(PathfinderMob body) {
        onStartTravelling.accept(body);
        state = TransportItemState.TRAVELLING;
        interactionState = null;
        ticksSinceReachingTarget = 0;
    }

    private BiConsumer<PathfinderMob, Container> onReached(ContainerInteractionState s) {
        return (mob, container) -> interactionState = s;
    }

    private void onTargetInteraction(TransportTarget target, PathfinderMob body) {
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.pos()));
        stopInPlace(body);
        if (interactionState != null) {
            Optional.ofNullable(onTargetInteractionActions.get(interactionState)).ifPresent(a -> a.accept(body, target, ticksSinceReachingTarget));
        }
    }

    private void doReachedTargetInteraction(PathfinderMob body, Container container, BiConsumer<PathfinderMob, Container> pickupOk,
                                            BiConsumer<PathfinderMob, Container> pickupFail, BiConsumer<PathfinderMob, Container> placeOk,
                                            BiConsumer<PathfinderMob, Container> placeFail) {
        if (isPickingUpItems(body)) {
            (!container.isEmpty() ? pickupOk : pickupFail).accept(body, container);
        } else {
            (container.isEmpty() || hasItemMatchingHandItem(body, container) ? placeOk : placeFail).accept(body, container);
        }
    }

    private Optional<TransportTarget> getTransportTarget(ServerLevel level, PathfinderMob body) {
        AABB area = targetSearchArea(body);
        Set<GlobalPos> visited = visitedPositions(body), unreachable = unreachablePositions(body);
        List<ChunkPos> chunks = ChunkPos.rangeClosed(new ChunkPos(body.blockPosition()), Math.floorDiv(horizontalSearchDistance(body), 16) + 1).toList();
        TransportTarget best = null;
        double closest = Float.MAX_VALUE;
        for (ChunkPos chunkPos : chunks) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk != null) {
                for (BlockEntity candidate : chunk.getBlockEntities().values()) {
                    if (candidate instanceof ChestBlockEntity chest) {
                        double distance = chest.getBlockPos().distToCenterSqr(body.position());
                        if (distance < closest) {
                            TransportTarget valid = targetValidToPick(body, level, chest, visited, unreachable, area);
                            if (valid != null) {
                                best = valid;
                                closest = distance;
                            }
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    @Nullable
    private TransportTarget targetValidToPick(PathfinderMob body, Level level, BlockEntity blockEntity, Set<GlobalPos> visited, Set<GlobalPos> unreachable, AABB area) {
        BlockPos pos = blockEntity.getBlockPos();
        if (!area.contains(pos.getX(), pos.getY(), pos.getZ())) {
            return null;
        }
        TransportTarget t = TransportTarget.tryCreate(blockEntity, level);
        if (t == null) {
            return null;
        }
        return isWantedBlock(body, t.state()) && !isPositionAlreadyVisited(visited, unreachable, t, level) && !isContainerLocked(t) ? t : null;
    }

    private static boolean isContainerLocked(TransportTarget t) {
        return t.blockEntity() instanceof BaseContainerBlockEntity be && !be.lockKey.equals(LockCode.NO_LOCK);
    }

    private boolean hasValidTarget(Level level, PathfinderMob body) {
        boolean validType = target != null && isWantedBlock(body, target.state()) && target.blockEntity().equals(level.getBlockEntity(target.pos()));
        if (validType && !ChestBlock.isChestBlockedAt(level, target.pos())) {
            if (state != TransportItemState.TRAVELLING || hasValidTravellingPath(level, target, body)) {
                return true;
            }
            markVisitedBlockPosAsUnreachable(body, level, target.pos());
        }
        return false;
    }

    private boolean hasValidTravellingPath(Level level, TransportTarget target, PathfinderMob body) {
        Path path = body.getNavigation().getPath() == null ? body.getNavigation().createPath(target.pos(), 0) : body.getNavigation().getPath();
        Vec3 from = positionToReachTargetFrom(path, body);
        boolean canReach = isWithinTargetDistance(interactionRange(body), target, level, body, from);
        boolean noPathYet = path == null && !canReach;
        return noPathYet || canReach && canSeeAnyTargetSide(target, level, body, from);
    }

    private Vec3 positionToReachTargetFrom(@Nullable Path path, PathfinderMob body) {
        boolean noPath = path == null || path.getEndNode() == null;
        return middleY(body, noPath ? body.position() : Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()));
    }

    private static Vec3 middleY(PathfinderMob body, Vec3 pos) {
        return pos.add(0.0, body.getBoundingBox().getYsize() / 2.0, 0.0);
    }

    private Stream<TransportTarget> connectedTargets(TransportTarget t, Level level) {
        BlockPos other = t.connectedPos();
        TransportTarget connected = other == null ? null : TransportTarget.tryCreate(other, level);
        return connected != null ? Stream.of(t, connected) : Stream.of(t);
    }

    private AABB targetSearchArea(PathfinderMob mob) {
        int h = horizontalSearchDistance(mob);
        return new AABB(mob.blockPosition()).inflate(h, mob.isPassenger() ? 1 : verticalSearchDistance, h);
    }

    private int horizontalSearchDistance(PathfinderMob mob) {
        return mob.isPassenger() ? 1 : horizontalSearchDistance;
    }

    private static Set<GlobalPos> visitedPositions(PathfinderMob mob) {
        return mob.getBrain().getMemory(Golems.VISITED_BLOCK_POSITIONS.get()).orElse(Set.of());
    }

    private static Set<GlobalPos> unreachablePositions(PathfinderMob mob) {
        return mob.getBrain().getMemory(Golems.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS.get()).orElse(Set.of());
    }

    private boolean isPositionAlreadyVisited(Set<GlobalPos> visited, Set<GlobalPos> unreachable, TransportTarget t, Level level) {
        return connectedTargets(t, level).map(x -> GlobalPos.of(level.dimension(), x.pos())).anyMatch(p -> visited.contains(p) || unreachable.contains(p));
    }

    protected void setVisitedBlockPos(PathfinderMob body, Level level, BlockPos pos) {
        Set<GlobalPos> visited = new HashSet<>(visitedPositions(body));
        visited.add(GlobalPos.of(level.dimension(), pos));
        if (visited.size() > 10) {
            enterCooldownAfterNoMatchingTargetFound(body);
        } else {
            body.getBrain().setMemoryWithExpiry(Golems.VISITED_BLOCK_POSITIONS.get(), visited, 6000L);
        }
    }

    protected void markVisitedBlockPosAsUnreachable(PathfinderMob body, Level level, BlockPos pos) {
        Set<GlobalPos> visited = new HashSet<>(visitedPositions(body));
        visited.remove(GlobalPos.of(level.dimension(), pos));
        Set<GlobalPos> unreachable = new HashSet<>(unreachablePositions(body));
        unreachable.add(GlobalPos.of(level.dimension(), pos));
        if (unreachable.size() > 50) {
            enterCooldownAfterNoMatchingTargetFound(body);
        } else {
            body.getBrain().setMemoryWithExpiry(Golems.VISITED_BLOCK_POSITIONS.get(), visited, 6000L);
            body.getBrain().setMemoryWithExpiry(Golems.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS.get(), unreachable, 6000L);
        }
    }

    private boolean isWantedBlock(PathfinderMob mob, BlockState block) {
        return isPickingUpItems(mob) ? sourceBlockType.test(block) : destinationBlockType.test(block);
    }

    private static double interactionRange(PathfinderMob body) {
        return body.getNavigation().getPath() != null && body.getNavigation().getPath().isDone() ? 1.0 : 0.5;
    }

    private boolean isWithinTargetDistance(double distance, TransportTarget t, Level level, PathfinderMob body, Vec3 from) {
        AABB box = body.getBoundingBox();
        AABB moved = AABB.ofSize(from, box.getXsize(), box.getYsize(), box.getZsize());
        return t.state().getCollisionShape(level, t.pos()).bounds().inflate(distance, 0.5, distance).move(t.pos()).intersects(moved);
    }

    private boolean canSeeAnyTargetSide(TransportTarget t, Level level, PathfinderMob body, Vec3 eye) {
        Vec3 center = Vec3.atCenterOf(t.pos());
        return Direction.stream().map(d -> center.add(0.5 * d.getStepX(), 0.5 * d.getStepY(), 0.5 * d.getStepZ()))
                .map(hit -> level.clip(new ClipContext(eye, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body)))
                .anyMatch(r -> r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(t.pos()));
    }

    private boolean isAnotherMobInteractingWithTarget(TransportTarget t, Level level) {
        return connectedTargets(t, level).anyMatch(shouldQueueForTarget);
    }

    private static boolean isPickingUpItems(PathfinderMob body) {
        return body.getMainHandItem().isEmpty();
    }

    private static boolean hasItemMatchingHandItem(PathfinderMob body, Container container) {
        ItemStack hand = body.getMainHandItem();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (ItemStack.isSameItem(container.getItem(i), hand)) {
                return true;
            }
        }
        return false;
    }

    private void pickUpItems(PathfinderMob body, Container container) {
        body.setItemSlot(EquipmentSlot.MAINHAND, pickupItemFromContainer(container));
        body.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        container.setChanged();
        clearMemoriesAfterMatchingTargetFound(body);
    }

    private void putDownItem(PathfinderMob body, Container container) {
        ItemStack left = addItemsToContainer(body, container);
        container.setChanged();
        body.setItemSlot(EquipmentSlot.MAINHAND, left);
        if (left.isEmpty()) {
            clearMemoriesAfterMatchingTargetFound(body);
        } else {
            stopTargetingCurrentTarget(body);
        }
    }

    private static ItemStack pickupItemFromContainer(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                return container.removeItem(slot, Math.min(stack.getCount(), 16));
            }
        }
        return ItemStack.EMPTY;
    }

    /** Mojang's, including its count arithmetic (the hand stack loses the room left, not the count moved). */
    private static ItemStack addItemsToContainer(PathfinderMob body, Container container) {
        ItemStack hand = body.getMainHandItem();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (inSlot.isEmpty()) {
                container.setItem(slot, hand);
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(inSlot, hand) && inSlot.getCount() < inSlot.getMaxStackSize()) {
                int room = inSlot.getMaxStackSize() - inSlot.getCount();
                int add = Math.min(room, hand.getCount());
                inSlot.setCount(inSlot.getCount() + add);
                hand.setCount(hand.getCount() - room);
                container.setItem(slot, inSlot);
                if (hand.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return hand;
    }

    protected void stopTargetingCurrentTarget(PathfinderMob body) {
        ticksSinceReachingTarget = 0;
        target = null;
        body.getNavigation().stop();
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    protected void clearMemoriesAfterMatchingTargetFound(PathfinderMob body) {
        stopTargetingCurrentTarget(body);
        body.getBrain().eraseMemory(Golems.VISITED_BLOCK_POSITIONS.get());
        body.getBrain().eraseMemory(Golems.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS.get());
    }

    private void enterCooldownAfterNoMatchingTargetFound(PathfinderMob body) {
        stopTargetingCurrentTarget(body);
        body.getBrain().setMemory(Golems.TRANSPORT_ITEMS_COOLDOWN_TICKS.get(), 140);
        body.getBrain().eraseMemory(Golems.VISITED_BLOCK_POSITIONS.get());
        body.getBrain().eraseMemory(Golems.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS.get());
    }

    @Override
    protected void stop(ServerLevel level, PathfinderMob body, long time) {
        onStartTravelling(body);
        if (body.getNavigation() instanceof Navigations.Ground nav) {
            nav.setCanPathToTargetsBelowSurface(false);
        }
    }

    private static void stopInPlace(PathfinderMob mob) {
        mob.getNavigation().stop();
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setSpeed(0.0F);
        mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
    }
}
