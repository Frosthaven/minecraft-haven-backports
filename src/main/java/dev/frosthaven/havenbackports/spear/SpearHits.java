package dev.frosthaven.havenbackports.spear;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x {@code ProjectileUtil.getHitEntitiesAlong} and {@code getManyEntityHitResult}: every entity on the line from the
 * attack range's near end to its far end, not only the first. 26.x clips blocks with {@code clipIncludingBorder};
 * 1.21.1 lacks it, so this uses {@code Level.clip}: the two differ only where the line crosses the world border.
 */
public final class SpearHits {
    private SpearHits() {}

    /** 26.x {@code Entity.getHeadLookAngle}: the view vector from the pitch and the head yaw. */
    public static Vec3 headLookAngle(Entity entity) {
        return entity.calculateViewVector(entity.getXRot(), entity.getYHeadRot());
    }

    public static Either<BlockHitResult, Collection<EntityHitResult>> getHitEntitiesAlong(
            Entity attacker, AttackRange attackRange, Predicate<Entity> matching, ClipContext.Block blockClipType) {
        Vec3 look = headLookAngle(attacker);
        Vec3 eyePosition = attacker.getEyePosition();
        Vec3 from = eyePosition.add(look.scale(attackRange.effectiveMinRange(attacker)));
        double movementComponent = attacker.getKnownMovement().dot(look);
        Vec3 to = eyePosition.add(look.scale(attackRange.effectiveMaxRange(attacker) + Math.max(0.0, movementComponent)));
        return getHitEntitiesAlong(attacker, eyePosition, from, matching, to, attackRange.hitboxMargin(), blockClipType);
    }

    private static Either<BlockHitResult, Collection<EntityHitResult>> getHitEntitiesAlong(
            Entity source, Vec3 origin, Vec3 from, Predicate<Entity> matching, Vec3 to, float entityMargin, ClipContext.Block clipType) {
        Level level = source.level();
        BlockHitResult hitResult = level.clip(new ClipContext(origin, to, clipType, ClipContext.Fluid.NONE, source));
        if (hitResult.getType() != HitResult.Type.MISS) {
            to = hitResult.getLocation();
            if (origin.distanceToSqr(to) < origin.distanceToSqr(from)) {
                return Either.left(hitResult);
            }
        }
        AABB searchArea = AABB.ofSize(from, entityMargin, entityMargin, entityMargin).expandTowards(to.subtract(from)).inflate(1.0);
        Collection<EntityHitResult> entityHit = getManyEntityHitResult(level, source, from, to, searchArea, matching, entityMargin, clipType, true, true);
        return !entityHit.isEmpty() ? Either.right(entityHit) : Either.left(hitResult);
    }

    public static Collection<EntityHitResult> getManyEntityHitResult(Level level, Entity source, Vec3 from, Vec3 to, AABB targetSearchArea,
            Predicate<Entity> matching, float entityMargin, ClipContext.Block clipType, boolean includeFromEntity, boolean projectSurfaceHitLocation) {
        List<EntityHitResult> collector = new ArrayList<>();
        for (Entity entity : level.getEntities(source, targetSearchArea, matching)) {
            AABB entityBB = entity.getBoundingBox();
            if (includeFromEntity && entityBB.contains(from)) {
                collector.add(new EntityHitResult(entity, from));
            } else {
                Optional<Vec3> exactHit = entityBB.clip(from, to);
                if (exactHit.isPresent()) {
                    collector.add(new EntityHitResult(entity, exactHit.get()));
                } else if (!(entityMargin <= 0.0)) {
                    Optional<Vec3> outsideHit = entityBB.inflate(entityMargin).clip(from, to);
                    if (outsideHit.isPresent()) {
                        Vec3 outsideHitPosition = outsideHit.get();
                        Vec3 towardsTarget = entityBB.getCenter();
                        BlockHitResult hitResult = level.clip(new ClipContext(outsideHitPosition, towardsTarget, clipType, ClipContext.Fluid.NONE, source));
                        if (hitResult.getType() != HitResult.Type.MISS) {
                            towardsTarget = hitResult.getLocation();
                        }
                        Optional<Vec3> surfaceHit = entity.getBoundingBox().clip(outsideHitPosition, towardsTarget);
                        if (surfaceHit.isPresent()) {
                            collector.add(new EntityHitResult(entity, projectSurfaceHitLocation ? surfaceHit.get() : outsideHitPosition));
                        }
                    }
                }
            }
        }
        return collector;
    }
}
