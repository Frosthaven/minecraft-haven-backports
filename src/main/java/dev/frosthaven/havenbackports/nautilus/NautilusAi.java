package dev.frosthaven.havenbackports.nautilus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import dev.frosthaven.havenbackports.Nautiluses;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.behavior.AnimalPanic;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.CountDownCooldownTicks;
import net.minecraft.world.entity.ai.behavior.FollowTemptation;
import net.minecraft.world.entity.ai.behavior.GateBehavior;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

/**
 * Mojang's 26.x NautilusAi and ZombieNautilusAi in one class (they share the attack target search, the charge attack
 * and the anger): core (the nautilus also panics), idle (the nautilus also breeds; temptation at 1.3 or 0.9, then
 * attacking, then swimming or walking to a look target) and fight (ChargeAttack at 0.6 or 0.5, knockback 2, 80 ticks
 * apart). 26.x derives a brain's memories from its parts; 1.21.1 lists them (MEMORIES). 26.x StartAttacking takes the
 * level as well; 1.21.1's takes the body only, and the level is the body's.
 */
public final class NautilusAi {
    private static final UniformInt TIME_BETWEEN_NON_PLAYER_ATTACKS = UniformInt.of(2400, 3600);
    static final TargetingConditions ATTACK_TARGET_CONDITIONS = TargetingConditions.forCombat().selector(target ->
            (target.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || target.getType() != EntityType.ARMOR_STAND)
                    && target.level().getWorldBorder().isWithinBounds(target.getBoundingBox()));
    static final List<MemoryModuleType<?>> MEMORIES = List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.IS_PANICKING, MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryModuleType.TEMPTING_PLAYER,
            MemoryModuleType.IS_TEMPTED, MemoryModuleType.BREED_TARGET, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.ANGRY_AT, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
            Nautiluses.CHARGE_COOLDOWN_TICKS.get(), Nautiluses.ATTACK_TARGET_COOLDOWN.get());
    static final List<SensorType<? extends Sensor<? super AbstractNautilus>>> SENSORS = List.of(SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_ADULT, SensorType.NEAREST_PLAYERS, SensorType.HURT_BY, Nautiluses.NAUTILUS_TEMPTATIONS.get());

    private NautilusAi() {}

    static void initMemories(AbstractNautilus body, RandomSource random) {
        body.getBrain().setMemory(Nautiluses.ATTACK_TARGET_COOLDOWN.get(), TIME_BETWEEN_NON_PLAYER_ATTACKS.sample(random));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<?> makeBrain(Brain<? extends AbstractNautilus> b, boolean zombie) {
        Brain<AbstractNautilus> brain = (Brain<AbstractNautilus>) b;
        ImmutableList.Builder core = ImmutableList.builder();
        if (!zombie) {
            core.add(new AnimalPanic<>(1.6F));
        }
        core.add(new LookAtTargetSink(45, 90), new MoveToTargetSink(), new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS),
                new CountDownCooldownTicks(Nautiluses.CHARGE_COOLDOWN_TICKS.get()), new CountDownCooldownTicks(Nautiluses.ATTACK_TARGET_COOLDOWN.get()));
        brain.addActivity(Activity.CORE, 0, core.build());
        float tempted = zombie ? 0.9F : 1.3F;
        int p = zombie ? 1 : 2;
        ImmutableList.Builder idle = ImmutableList.builder();
        if (!zombie) {
            idle.add(Pair.of(1, new AnimalMakeLove(Nautiluses.NAUTILUS.get(), 0.4F, 2)));
        }
        idle.add(Pair.of(p, new FollowTemptation(mob -> tempted, mob -> mob.isBaby() ? 2.5 : 3.5)));
        idle.add(Pair.of(p + 1, StartAttacking.<AbstractNautilus>create(NautilusAi::findNearestValidAttackTarget)));
        idle.add(Pair.of(p + 2, new GateBehavior<>(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), ImmutableSet.of(),
                GateBehavior.OrderPolicy.ORDERED, GateBehavior.RunningPolicy.TRY_ALL,
                ImmutableList.of(Pair.of(RandomStroll.swim(1.0F), 2), Pair.of(SetWalkTargetFromLookTarget.create(1.0F, 3), 3)))));
        brain.addActivity(Activity.IDLE, idle.build());
        brain.addActivityWithConditions(Activity.FIGHT,
                ImmutableList.of(Pair.of(0, new ChargeAttack(80, ATTACK_TARGET_CONDITIONS, zombie ? 0.5F : 0.6F, 2.0F, 12.0, 11.0,
                        zombie ? Nautiluses.ZOMBIE_DASH.get() : Nautiluses.NAUTILUS_DASH.get()))),
                ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT),
                        Pair.of(MemoryModuleType.TEMPTING_PLAYER, MemoryStatus.VALUE_ABSENT),
                        Pair.of(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT),
                        Pair.of(Nautiluses.CHARGE_COOLDOWN_TICKS.get(), MemoryStatus.VALUE_ABSENT)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    static Optional<? extends LivingEntity> findNearestValidAttackTarget(AbstractNautilus body) {
        if (!BehaviorUtils.isBreeding(body) && body.isInWater() && !body.isBaby() && !body.isTame()) {
            Optional<LivingEntity> angryAt = BehaviorUtils.getLivingEntityFromUUIDMemory(body, MemoryModuleType.ANGRY_AT)
                    .filter(e -> e.isInWater() && Sensor.isEntityAttackableIgnoringLineOfSight(body, e));
            if (angryAt.isPresent()) {
                return angryAt;
            } else if (body.getBrain().hasMemoryValue(Nautiluses.ATTACK_TARGET_COOLDOWN.get())) {
                return Optional.empty();
            }
            RandomSource random = body.getRandom();
            body.getBrain().setMemory(Nautiluses.ATTACK_TARGET_COOLDOWN.get(), TIME_BETWEEN_NON_PLAYER_ATTACKS.sample(random));
            return random.nextFloat() < 0.5F ? Optional.empty() : body.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                    .orElse(NearestVisibleLivingEntities.empty()).findClosest(NautilusAi::isHostileTarget);
        }
        return Optional.empty();
    }

    static void setAngerTarget(AbstractNautilus body, LivingEntity target) {
        if (Sensor.isEntityAttackableIgnoringLineOfSight(body, target)) {
            body.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            body.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), 400L);
        }
    }

    private static boolean isHostileTarget(LivingEntity mob) {
        return mob.isInWater() && mob.getType().is(Nautiluses.NAUTILUS_HOSTILES);
    }

    static void updateActivity(AbstractNautilus body) {
        body.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.FIGHT, Activity.IDLE));
    }

    public static Predicate<ItemStack> getTemptations() {
        return i -> i.is(Nautiluses.NAUTILUS_FOOD);
    }
}
