package dev.frosthaven.havenbackports.creaking;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/**
 * 26.x PlayerSensor, for the creaking only. Two 26.x changes 1.21.1 lacks: the sensor and its targeting checks reach
 * the mob's follow range (1.21.1: always 16; the creaking's is 32), and it also writes the list
 * NEAREST_VISIBLE_ATTACKABLE_PLAYERS (CreakingAi reads it). 1.21.1's PlayerSensor stays as it is for every other mob.
 */
final class CreakingPlayerSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER,
                MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, CreakingContent.NEAREST_VISIBLE_ATTACKABLE_PLAYERS.get());
    }

    @Override
    protected void doTick(ServerLevel level, LivingEntity body) {
        double range = body.getAttributeValue(Attributes.FOLLOW_RANGE);
        List<Player> players = level.players().stream().filter(EntitySelector.NO_SPECTATORS).filter(player -> body.closerThan(player, range))
                .sorted(Comparator.comparingDouble(body::distanceToSqr)).collect(Collectors.toList());
        Brain<?> brain = body.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, players);
        List<Player> visiblePlayers = players.stream().filter(player -> targetable(body, player, range)).collect(Collectors.toList());
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, visiblePlayers.isEmpty() ? null : visiblePlayers.get(0));
        List<Player> visibleAttackablePlayers = visiblePlayers.stream().filter(player -> attackable(body, player, range)).toList();
        brain.setMemory(CreakingContent.NEAREST_VISIBLE_ATTACKABLE_PLAYERS.get(), visibleAttackablePlayers);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, visibleAttackablePlayers.isEmpty() ? null : visibleAttackablePlayers.get(0));
    }

    /** 26.x Sensor.isEntityTargetable with its range set to the follow range. */
    private static boolean targetable(LivingEntity body, LivingEntity entity, double range) {
        TargetingConditions c = TargetingConditions.forNonCombat().range(range);
        return (body.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, entity) ? c.ignoreInvisibilityTesting() : c).test(body, entity);
    }

    /** 26.x Sensor.isEntityAttackable with its range set to the follow range. */
    private static boolean attackable(LivingEntity body, LivingEntity target, double range) {
        TargetingConditions c = TargetingConditions.forCombat().range(range);
        return (body.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target) ? c.ignoreInvisibilityTesting() : c).test(body, target);
    }
}
