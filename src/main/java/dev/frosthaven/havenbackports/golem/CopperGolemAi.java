package dev.frosthaven.havenbackports.golem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import dev.frosthaven.havenbackports.Golems;
import dev.frosthaven.havenbackports.block.CopperChestBlock;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalPanic;
import net.minecraft.world.entity.ai.behavior.CountDownCooldownTicks;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTargetSometimes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Mojang's 26.x CopperGolemAi. Opening and closing a chest: 26.x calls Container.startOpen / stopOpen with the golem
 * (ContainerUser); 1.21.1 counts only players, so the golem marks the chest it has open (openedChestPos) and asks the
 * chest to recount (ChestBlockEntity.recheckOpen), which ContainerOpenersCounterMixin extends to golems; the lid, its
 * sounds and the block event then follow from vanilla. Queueing reads the chest's opener count the same way.
 */
public final class CopperGolemAi {
    static final List<MemoryModuleType<?>> MEMORIES = List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.IS_PANICKING, MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.GAZE_COOLDOWN_TICKS,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_LIVING_ENTITIES);

    private CopperGolemAi() {}

    static List<MemoryModuleType<?>> memories() {
        return ImmutableList.<MemoryModuleType<?>>builder().addAll(MEMORIES).add(Golems.TRANSPORT_ITEMS_COOLDOWN_TICKS.get(),
                Golems.VISITED_BLOCK_POSITIONS.get(), Golems.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS.get()).build();
    }

    static Brain<?> init(Brain<CopperGolem> brain) {
        brain.addActivity(Activity.CORE, 0, ImmutableList.of(new AnimalPanic<>(1.5F), new LookAtTargetSink(45, 90), new MoveToTargetSink(),
                InteractWithDoor.create(), new CountDownCooldownTicks(MemoryModuleType.GAZE_COOLDOWN_TICKS),
                new CountDownCooldownTicks(Golems.TRANSPORT_ITEMS_COOLDOWN_TICKS.get())));
        brain.addActivity(Activity.IDLE, ImmutableList.of(
                Pair.of(0, new TransportItemsBetweenContainers(1.0F, state -> state.is(CopperChestBlock.COPPER_CHESTS),
                        state -> state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST), 32, 8, interactions(), CopperGolemAi::onTravelling,
                        CopperGolemAi::shouldQueueForTarget)),
                Pair.of(1, SetEntityLookTargetSometimes.create(EntityType.PLAYER, 6.0F, UniformInt.of(40, 80))),
                Pair.of(2, new RunOne<>(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                        Golems.TRANSPORT_ITEMS_COOLDOWN_TICKS.get(), MemoryStatus.VALUE_PRESENT),
                        ImmutableList.of(Pair.of(RandomStroll.stroll(1.0F, 2, 2), 1), Pair.of(new DoNothing(30, 60), 1))))));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    static void updateActivity(CopperGolem body) {
        body.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.IDLE));
    }

    private static Map<TransportItemsBetweenContainers.ContainerInteractionState, TransportItemsBetweenContainers.OnTargetReachedInteraction> interactions() {
        return Map.of(
                TransportItemsBetweenContainers.ContainerInteractionState.PICKUP_ITEM, onReached(CopperGolemState.GETTING_ITEM, Golems.ITEM_GET),
                TransportItemsBetweenContainers.ContainerInteractionState.PICKUP_NO_ITEM, onReached(CopperGolemState.GETTING_NO_ITEM, Golems.ITEM_NO_GET),
                TransportItemsBetweenContainers.ContainerInteractionState.PLACE_ITEM, onReached(CopperGolemState.DROPPING_ITEM, Golems.ITEM_DROP),
                TransportItemsBetweenContainers.ContainerInteractionState.PLACE_NO_ITEM, onReached(CopperGolemState.DROPPING_NO_ITEM, Golems.ITEM_NO_DROP));
    }

    private static TransportItemsBetweenContainers.OnTargetReachedInteraction onReached(CopperGolemState state, Supplier<SoundEvent> sound) {
        return (body, target, ticks) -> {
            if (body instanceof CopperGolem golem && target.container(golem.level()) != null) {
                if (ticks == 1) {
                    golem.setOpenedChestPos(target.pos());   // Container.startOpen(golem)
                    recheck(golem.level(), target);
                    golem.setState(state);
                }
                if (ticks == 9) {
                    golem.playSound(sound.get());
                }
                if (ticks == TransportItemsBetweenContainers.TARGET_INTERACTION_TIME) {
                    golem.clearOpenedChestPos();   // Container.stopOpen(golem)
                    recheck(golem.level(), target);
                }
            }
        };
    }

    /** The chest (both halves of a double one) recounts its openers. */
    private static void recheck(Level level, TransportTarget target) {
        if (target.blockEntity() instanceof ChestBlockEntity chest) {
            chest.recheckOpen();
        }
        BlockPos other = target.connectedPos();
        if (other != null && level.getBlockEntity(other) instanceof ChestBlockEntity chest) {
            chest.recheckOpen();
        }
    }

    private static void onTravelling(net.minecraft.world.entity.PathfinderMob body) {
        if (body instanceof CopperGolem golem) {
            golem.clearOpenedChestPos();
            golem.setState(CopperGolemState.IDLE);
        }
    }

    /** 26.x: !chest.getEntitiesWithContainerOpen().isEmpty(). */
    private static boolean shouldQueueForTarget(TransportTarget target) {
        return target.blockEntity() instanceof ChestBlockEntity chest && ChestBlockEntity.getOpenCount(chest.getLevel(), target.pos()) > 0;
    }
}
