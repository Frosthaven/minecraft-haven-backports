package dev.frosthaven.havenbackports.spear;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x {@code AttackRange} (the ATTACK_RANGE item component) as a plain record: 1.21.1 has no such component, so
 * {@link SpearItem} holds it. {@code getClosesetHit} keeps Mojang's name.
 */
public record AttackRange(float minReach, float maxReach, float minCreativeReach, float maxCreativeReach, float hitboxMargin, float mobFactor) {
    public HitResult getClosesetHit(Entity attacker, float partial, Predicate<Entity> matching) {
        var result = SpearHits.getHitEntitiesAlong(attacker, this, matching, ClipContext.Block.OUTLINE);
        if (result.left().isPresent()) {
            return result.left().get();
        }
        Collection<EntityHitResult> targets = result.right().get();
        EntityHitResult entity = null;
        Vec3 attackerPos = attacker.getEyePosition(partial);
        double closestDistance = Double.MAX_VALUE;
        for (EntityHitResult target : targets) {
            double distance = attackerPos.distanceToSqr(target.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                entity = target;
            }
        }
        if (entity != null) {
            return entity;
        }
        Vec3 eyeGaze = SpearHits.headLookAngle(attacker);
        Vec3 missPosition = attacker.getEyePosition(partial).add(eyeGaze);
        // 26.x Direction.getApproximateNearest is 1.21.1's Direction.getNearest
        return BlockHitResult.miss(missPosition, Direction.getNearest(eyeGaze.x, eyeGaze.y, eyeGaze.z), BlockPos.containing(missPosition));
    }

    public float effectiveMinRange(Entity entity) {
        if (entity instanceof Player player) {
            return player.isCreative() ? this.minCreativeReach : this.minReach;
        }
        return this.minReach * this.mobFactor;
    }

    public float effectiveMaxRange(Entity entity) {
        if (entity instanceof Player player) {
            return player.isCreative() ? this.maxCreativeReach : this.maxReach;
        }
        return this.maxReach * this.mobFactor;
    }

    public boolean isInRange(LivingEntity attacker, Vec3 location) {
        return this.isInRange(attacker, location::distanceToSqr, 0.0);
    }

    public boolean isInRange(LivingEntity attacker, AABB boundingBox, double extraBuffer) {
        return this.isInRange(attacker, boundingBox::distanceToSqr, extraBuffer);
    }

    private boolean isInRange(LivingEntity attacker, ToDoubleFunction<Vec3> distanceFunction, double extraBuffer) {
        double distance = Math.sqrt(distanceFunction.applyAsDouble(attacker.getEyePosition()));
        double minReach = this.effectiveMinRange(attacker) - this.hitboxMargin - extraBuffer;
        double maxReach = this.effectiveMaxRange(attacker) + this.hitboxMargin + extraBuffer;
        return distance >= minReach && distance <= maxReach;
    }
}
