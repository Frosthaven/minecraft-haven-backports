package dev.frosthaven.havenbackports.creaking;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTargetSometimes;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;

/**
 * 26.x CreakingAi on 1.21.1's Brain API. 26.x builds the brain from ActivityData and collects the memories from the
 * behaviors and sensors; 1.21.1 needs the memories listed and each activity added (priorities run up from the start
 * value, as ActivityData.create does). The player sensor is CreakingPlayerSensor (26.x PlayerSensor). 26.x
 * MeleeAttack.create(predicate, cooldown) is 1.21.1's MeleeAttack behind BehaviorBuilder.triggerIf(predicate).
 */
final class CreakingAi {
    private CreakingAi() {}

    static Brain.Provider<Creaking> provider() {
        return Brain.provider(ImmutableList.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                        MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
                        CreakingContent.NEAREST_VISIBLE_ATTACKABLE_PLAYERS.get(), MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET,
                        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.ATTACK_TARGET,
                        MemoryModuleType.ATTACK_COOLING_DOWN),
                ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, CreakingContent.CREAKING_PLAYER_SENSOR.get()));
    }

    static Brain<Creaking> makeBrain(Creaking body, Brain<Creaking> brain) {
        brain.addActivity(Activity.CORE, 0, ImmutableList.of(new Swim(0.8F) {
            @Override
            protected boolean checkExtraStartConditions(ServerLevel level, Mob owner) {
                return body.canMove() && super.checkExtraStartConditions(level, owner);
            }
        }, new LookAtTargetSink(45, 90), new MoveToTargetSink()));
        brain.addActivity(Activity.IDLE, 10, ImmutableList.of(
                StartAttacking.<Creaking>create(Creaking::isActive, mob -> mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER)),
                SetEntityLookTargetSometimes.create(8.0F, UniformInt.of(30, 60)),
                new RunOne<>(ImmutableList.of(Pair.of(RandomStroll.stroll(0.3F), 2), Pair.of(SetWalkTargetFromLookTarget.create(0.3F, 3), 2),
                        Pair.of(new DoNothing(30, 60), 1)))));
        brain.addActivityWithConditions(Activity.FIGHT, ImmutableList.of(
                        Pair.of(10, SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.0F)),
                        Pair.of(11, BehaviorBuilder.<Creaking>triggerIf(Creaking::canMove, MeleeAttack.create(Creaking.ATTACK_INTERVAL))),
                        Pair.of(12, StopAttackingIfTargetInvalid.<Creaking>create(target -> !isAttackTargetStillValid(body, target)))),
                ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static boolean isAttackTargetStillValid(Creaking creaking, LivingEntity target) {
        Optional<List<Player>> players = creaking.getBrain().getMemory(CreakingContent.NEAREST_VISIBLE_ATTACKABLE_PLAYERS.get());
        return players.map(list -> target instanceof Player player && list.contains(player) && creaking.canAttack(target)).orElse(false);
    }

    static void updateActivity(Creaking creaking) {
        if (!creaking.canMove()) {
            creaking.getBrain().useDefaultActivity();
        } else {
            creaking.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.FIGHT, Activity.IDLE));
        }
    }
}
