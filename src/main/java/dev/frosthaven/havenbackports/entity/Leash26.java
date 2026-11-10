package dev.frosthaven.havenbackports.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Mojang's 26.x leash tick (Leashable.tickLeash, checkElasticInteractions, computeElasticInteraction,
 * computeDampenedSpringInteraction, Wrench, angularFriction), for the leashes 26.x behaviour applies to here: a happy
 * ghast on a lead, and (with the quad leash) mobs on a lead to a happy ghast. Other leashes keep 1.21.1's physics.
 *
 * <p>1.21.1 forms: 26.x's per-class overrides that this tick calls are dispatched here for the vanilla classes
 * (PathfinderMob.whenLeashedTo = restrictTo(holder, elastic - 1); AbstractHorse and Camel onElasticLeashPull stop
 * eating and stand up); checkFallDistanceAccumulation is 1.21.1's checkSlowFallDistance; the angular momentum 26.x
 * keeps in LeashData is kept per entity in a weak map (LeashData is final in 1.21.1); canInteractWithLevel is isAlive.
 */
public final class Leash26 {
    public static final Vec3 AXIS_SPECIFIC_ELASTICITY = new Vec3(0.8, 0.2, 0.8);
    public static final List<Vec3> ENTITY_ATTACHMENT_POINT = List.of(new Vec3(0.0, 0.5, 0.5));
    public static final List<Vec3> LEASHER_ATTACHMENT_POINT = List.of(new Vec3(0.0, 0.5, 0.0));
    public static final List<Vec3> SHARED_QUAD_ATTACHMENT_POINTS = List.of(
            new Vec3(-0.5, 0.5, 0.5), new Vec3(-0.5, 0.5, -0.5), new Vec3(0.5, 0.5, -0.5), new Vec3(0.5, 0.5, 0.5));
    private static final Map<Entity, double[]> ANGULAR_MOMENTUM = new WeakHashMap<>();

    /** What 26.x's Leashable defaults and overrides answer for one leashed entity. */
    public interface Rules {
        default double elasticDistance() {
            return 6.0;
        }

        default double snapDistance() {
            return 12.0;
        }

        default boolean quad() {
            return false;
        }

        default void onElasticPull() {}
    }

    /** A holder that 26.x tells about its leashees (HappyGhast.notifyLeashHolder). */
    public interface Holder {
        boolean supportQuadLeashAsHolder();

        void notifyLeashHolder(Entity leashee);
    }

    private Leash26() {}

    public static <E extends Entity & Leashable> void tick(ServerLevel level, E entity, Rules rules) {
        Entity holder = entity.getLeashHolder();
        if (holder == null) {
            return;
        }
        if (!entity.isAlive() || !holder.isAlive()) {
            entity.dropLeash(true, level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS));
            holder = entity.getLeashHolder();
        }
        if (holder == null || holder.level() != entity.level()) {
            return;
        }
        double distance = holder.getBoundingBox().getCenter().distanceTo(entity.getBoundingBox().getCenter());
        whenLeashedTo(entity, holder, rules);
        double[] momentum = ANGULAR_MOMENTUM.computeIfAbsent(entity, e -> new double[1]);
        if (distance > rules.snapDistance()) {
            level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), dev.frosthaven.havenbackports.Ghasts.LEAD_BREAK.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            entity.leashTooFarBehaviour();
        } else if (distance > rules.elasticDistance() - holder.getBbWidth() - entity.getBbWidth()
                && checkElasticInteractions(entity, holder, rules, momentum)) {
            entity.checkSlowFallDistance();
            onElasticLeashPull(entity);
            rules.onElasticPull();
        } else {
            entity.closeRangeLeashBehaviour(holder);
        }
        entity.setYRot((float) (entity.getYRot() - momentum[0]));
        momentum[0] *= angularFriction(entity);
    }

    private static void whenLeashedTo(Entity entity, Entity holder, Rules rules) {
        if (entity instanceof PathfinderMob mob) {
            mob.restrictTo(holder.blockPosition(), (int) rules.elasticDistance() - 1);
        }
        if (holder instanceof Holder h) {
            h.notifyLeashHolder(entity);
        }
    }

    private static void onElasticLeashPull(Entity entity) {
        if (entity instanceof Camel camel) {
            if (camel.isCamelSitting() && !camel.isInPoseTransition() && camel.canCamelChangePose()) {
                camel.standUp();
            }
        } else if (entity instanceof AbstractHorse horse && horse.isEating()) {
            horse.setEating(false);
        }
    }

    private static float angularFriction(Entity entity) {
        if (entity.onGround()) {
            return entity.level().getBlockState(entity.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91F;
        }
        return entity.isInLiquid() ? 0.8F : 0.91F;
    }

    private static boolean checkElasticInteractions(Entity entity, Entity holder, Rules rules, double[] momentum) {
        boolean quad = rules.quad() && holder instanceof Holder h && h.supportQuadLeashAsHolder();
        List<Wrench> wrenches = computeElasticInteraction(entity, holder, rules.elasticDistance(),
                quad ? SHARED_QUAD_ATTACHMENT_POINTS : ENTITY_ATTACHMENT_POINT, quad ? SHARED_QUAD_ATTACHMENT_POINTS : LEASHER_ATTACHMENT_POINT);
        if (wrenches.isEmpty()) {
            return false;
        }
        Wrench result = Wrench.accumulate(wrenches).scale(quad ? 0.25 : 1.0);
        momentum[0] += 10.0 * result.torque();
        Vec3 relativeVelocity = holderMovement(holder).subtract(entity.getKnownMovement());
        entity.addDeltaMovement(result.force().multiply(AXIS_SPECIFIC_ELASTICITY).add(relativeVelocity.scale(0.11)));
        return true;
    }

    private static Vec3 holderMovement(Entity holder) {
        return holder instanceof Mob mob && mob.isNoAi() ? Vec3.ZERO : holder.getKnownMovement();
    }

    private static List<Wrench> computeElasticInteraction(Entity entity, Entity holder, double slack, List<Vec3> entityPoints, List<Vec3> holderPoints) {
        Vec3 movement = holderMovement(entity);
        float entityYRot = entity.getYRot() * (float) (Math.PI / 180.0);
        Vec3 entityDimensions = new Vec3(entity.getBbWidth(), entity.getBbHeight(), entity.getBbWidth());
        float holderYRot = holder.getYRot() * (float) (Math.PI / 180.0);
        Vec3 holderDimensions = new Vec3(holder.getBbWidth(), holder.getBbHeight(), holder.getBbWidth());
        List<Wrench> wrenches = new ArrayList<>();
        for (int i = 0; i < entityPoints.size(); i++) {
            Vec3 entityAttach = entityPoints.get(i).multiply(entityDimensions).yRot(-entityYRot);
            Vec3 entityPos = entity.position().add(entityAttach);
            Vec3 holderAttach = holderPoints.get(i).multiply(holderDimensions).yRot(-holderYRot);
            Vec3 holderPos = holder.position().add(holderAttach);
            spring(holderPos, entityPos, slack, movement, entityAttach).ifPresent(wrenches::add);
        }
        return wrenches;
    }

    private static Optional<Wrench> spring(Vec3 pivot, Vec3 object, double slack, Vec3 motion, Vec3 leverArm) {
        double distance = object.distanceTo(pivot);
        if (distance < slack) {
            return Optional.empty();
        }
        Vec3 displacement = pivot.subtract(object).normalize().scale(distance - slack);
        double torque = leverArm.z * displacement.x - leverArm.x * displacement.z;
        if (motion.dot(displacement) >= 0.0) {
            displacement = displacement.scale(0.3F);
        }
        return Optional.of(new Wrench(displacement, torque));
    }

    record Wrench(Vec3 force, double torque) {
        static Wrench accumulate(List<Wrench> wrenches) {
            double x = 0, y = 0, z = 0, t = 0;
            for (Wrench w : wrenches) {
                x += w.force.x;
                y += w.force.y;
                z += w.force.z;
                t += w.torque;
            }
            return new Wrench(new Vec3(x, y, z), t);
        }

        Wrench scale(double s) {
            return new Wrench(force.scale(s), torque * s);
        }
    }
}
