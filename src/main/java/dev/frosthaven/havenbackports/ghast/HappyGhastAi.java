package dev.frosthaven.havenbackports.ghast;

import dev.frosthaven.havenbackports.Ghasts;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.CountDownCooldownTicks;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

/**
 * Mojang's 26.x HappyGhastAi (the ghastling's brain), with the 26.x behaviours and sensors it uses that 1.21.1 has in
 * an older form: FollowTemptation with lookInTheEyes, BabyFollowAdult with a memory and eye target, AnimalPanic's fly
 * variant (AirAndWaterRandomPos, fly height 0), EntityTracker's eye-height target, TemptingSensor.forAnimal reading
 * the tempt range (16, 26.x's TEMPT_RANGE attribute) and AdultSensorAnyType (#followable_friendly_mobs; 1.21.1's
 * nearest-adult memory holds AgeableMob, and every mob in that tag is one). 26.x derives the brain's memories from
 * its parts; 1.21.1 lists them (MEMORIES).
 */
public final class HappyGhastAi {
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(3, 16);
    static final double TEMPT_RANGE = 16.0;
    static final List<MemoryModuleType<?>> MEMORIES = List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.IS_PANICKING, MemoryModuleType.HURT_BY,
            MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.BREED_TARGET,
            MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT);

    private HappyGhastAi() {}

    static Brain<?> init(Brain<HappyGhast> brain) {
        brain.addActivity(Activity.CORE, 0, ImmutableList.of(new Swim(0.8F), new FlyPanic(2.0F), new LookAtTargetSink(45, 90),
                new MoveToTargetSink(), new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS)));
        brain.addActivity(Activity.IDLE, ImmutableList.of(
                Pair.of(1, new FollowTemptation(1.25F, 3.0)),
                Pair.of(2, babyFollowAdult(MemoryModuleType.NEAREST_VISIBLE_PLAYER)),
                Pair.of(3, babyFollowAdult(MemoryModuleType.NEAREST_VISIBLE_ADULT)),
                Pair.of(4, new RunOne<>(ImmutableList.of(Pair.of(RandomStroll.fly(1.0F), 1), Pair.of(SetWalkTargetFromLookTarget.create(1.0F, 3), 1))))));
        brain.addActivityWithConditions(Activity.PANIC, ImmutableList.of(), Set.of(Pair.of(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_PRESENT)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    static void updateActivity(HappyGhast body) {
        body.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.PANIC, Activity.IDLE));
    }

    /** 26.x EntityTracker(entity, trackEyeHeight, targetEyeHeight). */
    static final class EyeTracker extends EntityTracker {
        private final Entity entity;
        private final boolean targetEye;

        EyeTracker(Entity entity, boolean trackEye, boolean targetEye) {
            super(entity, trackEye);
            this.entity = entity;
            this.targetEye = targetEye;
        }

        @Override
        public BlockPos currentBlockPosition() {
            return targetEye ? BlockPos.containing(entity.getEyePosition()) : entity.blockPosition();
        }
    }

    /** 26.x BabyFollowAdult.create(ADULT_FOLLOW_RANGE, 1.1, memory, true). */
    private static OneShot<LivingEntity> babyFollowAdult(MemoryModuleType<? extends LivingEntity> memory) {
        return BehaviorBuilder.create(i -> i.group(i.present(memory), i.registered(MemoryModuleType.LOOK_TARGET), i.absent(MemoryModuleType.WALK_TARGET))
                .apply(i, (nearest, look, walk) -> (level, body, time) -> {
                    if (!body.isBaby()) {
                        return false;
                    }
                    LivingEntity adult = i.get(nearest);
                    if (body.closerThan(adult, ADULT_FOLLOW_RANGE.getMaxValue() + 1) && !body.closerThan(adult, ADULT_FOLLOW_RANGE.getMinValue())) {
                        look.set(new EyeTracker(adult, true, true));
                        walk.set(new WalkTarget(new EyeTracker(adult, true, true), 1.1F, ADULT_FOLLOW_RANGE.getMinValue() - 1));
                        return true;
                    }
                    return false;
                }));
    }

    /** 26.x FollowTemptation(speed, closeEnough, lookInTheEyes = true). */
    static final class FollowTemptation extends Behavior<PathfinderMob> {
        private final float speed;
        private final double closeEnough;

        FollowTemptation(float speed, double closeEnough) {
            super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                    MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.TEMPTING_PLAYER, MemoryStatus.VALUE_PRESENT,
                    MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
            this.speed = speed;
            this.closeEnough = closeEnough;
        }

        @Override
        protected boolean timedOut(long time) {
            return false;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, PathfinderMob body, long time) {
            return body.getBrain().getMemory(MemoryModuleType.TEMPTING_PLAYER).isPresent()
                    && !body.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET) && !body.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
        }

        @Override
        protected void stop(ServerLevel level, PathfinderMob body, long time) {
            Brain<?> brain = body.getBrain();
            brain.setMemory(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, 100);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        }

        @Override
        protected void tick(ServerLevel level, PathfinderMob body, long time) {
            Player player = body.getBrain().getMemory(MemoryModuleType.TEMPTING_PLAYER).get();
            Brain<?> brain = body.getBrain();
            brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
            if (body.distanceToSqr(player) < Mth.square(closeEnough)) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EyeTracker(player, true, true), speed, 2));
            }
        }
    }

    /** 26.x AnimalPanic(speed, flyHeight 0): panics to an air or water position (AirAndWaterRandomPos 5, 4, 0). */
    static final class FlyPanic extends Behavior<PathfinderMob> {
        private final float speed;

        FlyPanic(float speed) {
            super(Map.of(MemoryModuleType.IS_PANICKING, MemoryStatus.REGISTERED, MemoryModuleType.HURT_BY, MemoryStatus.REGISTERED), 100, 120);
            this.speed = speed;
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob body) {
            return body.getBrain().getMemory(MemoryModuleType.HURT_BY).map(d -> d.is(DamageTypeTags.PANIC_CAUSES)).orElse(false)
                    || body.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, PathfinderMob body, long time) {
            return true;
        }

        @Override
        protected void start(ServerLevel level, PathfinderMob body, long time) {
            body.getBrain().setMemory(MemoryModuleType.IS_PANICKING, true);
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            body.getNavigation().stop();
        }

        @Override
        protected void stop(ServerLevel level, PathfinderMob body, long time) {
            body.getBrain().eraseMemory(MemoryModuleType.IS_PANICKING);
        }

        @Override
        protected void tick(ServerLevel level, PathfinderMob body, long time) {
            if (body.getNavigation().isDone()) {
                Vec3 pos = panicPos(body, level);
                if (pos != null) {
                    body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, speed, 0));
                }
            }
        }

        private static Vec3 panicPos(PathfinderMob body, ServerLevel level) {
            if (body.isOnFire()) {
                BlockPos at = body.blockPosition();
                if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                    Optional<BlockPos> water = Mth.ceil(body.getBbWidth()) == 2
                            ? BlockPos.findClosestMatch(at, 5, 1, p -> BlockPos.squareOutSouthEast(p).allMatch(q -> level.getFluidState(q).is(FluidTags.WATER)))
                            : BlockPos.findClosestMatch(at, 5, 1, p -> level.getFluidState(p).is(FluidTags.WATER));
                    if (water.isPresent()) {
                        return Vec3.atBottomCenterOf(water.get());
                    }
                }
            }
            Vec3 view = body.getViewVector(0.0F);
            return AirAndWaterRandomPos.getPos(body, 5, 4, 0, view.x, view.z, (float) (Math.PI / 2));
        }
    }

    /** 26.x TemptingSensor.forAnimal with TEMPT_RANGE 16. */
    public static final class FoodTemptations extends Sensor<PathfinderMob> {
        private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();

        @Override
        protected void doTick(ServerLevel level, PathfinderMob body) {
            TargetingConditions targeting = TEMPT_TARGETING.copy().range(TEMPT_RANGE);
            Optional<Player> player = level.players().stream().filter(EntitySelector.NO_SPECTATORS)
                    .filter(p -> targeting.test(body, p))
                    .filter(p -> body instanceof Animal a && (a.isFood(p.getMainHandItem()) || a.isFood(p.getOffhandItem())))
                    .filter(p -> !body.hasPassenger(p))
                    .sorted(Comparator.comparingDouble(body::distanceToSqr)).map(Player.class::cast).findFirst();
            if (player.isPresent()) {
                body.getBrain().setMemory(MemoryModuleType.TEMPTING_PLAYER, player.get());
            } else {
                body.getBrain().eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
            }
        }

        @Override
        public Set<MemoryModuleType<?>> requires() {
            return ImmutableSet.of(MemoryModuleType.TEMPTING_PLAYER);
        }
    }

    /** 26.x AdultSensorAnyType: the nearest visible adult in #followable_friendly_mobs. */
    public static final class AdultAnyType extends Sensor<LivingEntity> {
        @Override
        public Set<MemoryModuleType<?>> requires() {
            return ImmutableSet.of(MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        }

        @Override
        protected void doTick(ServerLevel level, LivingEntity body) {
            body.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).ifPresent(visible -> set(body, visible));
        }

        private static void set(LivingEntity body, NearestVisibleLivingEntities visible) {
            Optional<AgeableMob> adult = visible.findClosest(e -> e.getType().is(Ghasts.FOLLOWABLE_FRIENDLY_MOBS) && !e.isBaby() && e instanceof AgeableMob)
                    .map(AgeableMob.class::cast);
            body.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, adult);
        }
    }
}
