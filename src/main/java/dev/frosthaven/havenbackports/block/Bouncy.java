package dev.frosthaven.havenbackports.block;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x's block bounce (Entity.restituteMovementAfterCollisions with a block's bounceRestitution), for the blocks that
 * have one. 26.x runs it for every entity landing on any block; 1.21.1 hands a landing to the block below through
 * Block.updateEntityAfterFallOn, without the collided movement the formula needs: EntityMixin passes it in, for these
 * blocks only. Air drag is 0.98, 26.x's default for Entity and LivingEntity (26.x scales it with its air drag
 * attribute; 1.21.1 entities have none, and the Sulfur Cube's own physics carries its own).
 */
public interface Bouncy {
    float bounceRestitution();

    void bounceOn(Entity entity);

    /** 26.x restituteMovementAfterCollisions, vertical part, landing on this block. */
    default void bounce(Entity entity, Vec3 collided) {
        Vec3 current = entity.getDeltaMovement();
        double gravity = entity.getGravity();
        double restitution = 0.0;
        if (entity.verticalCollisionBelow && !(-current.y <= gravity) && !entity.isShiftKeyDown()) {
            restitution = entity instanceof LivingEntity ? this.bounceRestitution() : this.bounceRestitution() * 0.8F;
        }
        double gravityCompensation = 0.0;
        double effectiveDrag = 1.0;
        if (restitution > 0.0) {
            double portion = collided.y / current.y;
            gravityCompensation = portion * gravity;
            effectiveDrag = Mth.lerp(portion, 1.0, 0.98F);
        }
        entity.setDeltaMovement(current.x, (gravityCompensation - current.y) * effectiveDrag * restitution, current.z);
        if (restitution > 0.0) {
            this.bounceOn(entity);
            entity.hasImpulse = true;
        }
    }
}
