package dev.frosthaven.havenbackports.cube;

import dev.frosthaven.havenbackports.block.Bouncy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * 26.x's new entity physics, for the entities that carry 26.x's bounciness, friction_modifier and air_drag_modifier
 * attributes (Cube.BOUNCINESS ...): only this class's subclasses have them, so no other entity moves differently.
 * 26.x puts this in Entity.move (restituteMovementAfterCollisions, getBlockBounciness), LivingEntity.travelInAir
 * (computeModifiedFriction, handleRelativeFrictionAndCalculateMovement, getFrictionInfluencedSpeed, handleOnClimbable)
 * and LivingEntity.getAirDrag / getEffectiveGravity / getEntityBounciness, line for line.
 * <p>
 * 1.21.1 forms: move() runs 1.21.1's move and then applies 26.x's restitution to the velocity the entity had before
 * it (26.x reads it at the same point: nothing changes it in between but a cobweb's stuck speed, which 26.x zeroes
 * too). The collided movement is the position change. Block bounciness is 26.x's per-block value (Blocks.java:
 * slime block 1.0, beds 0.75, the shelf mushroom 0.75 through {@link Bouncy}); 1.21.1 blocks have no such field. 26.x
 * then applies the block speed factor, as 1.21.1's move does at its end. There is no GameEvent.BOUNCE in 1.21.1.
 */
public abstract class Physics26Mob extends AgeableMob {
    /** 26.x block tag suppresses_bounce (honey block). */
    static final TagKey<Block> SUPPRESSES_BOUNCE = BlockTags.create(ResourceLocation.withDefaultNamespace("suppresses_bounce"));

    protected Physics26Mob(EntityType<? extends AgeableMob> type, Level level) {
        super(type, level);
    }

    /** 26.x Entity.omnidirectionalAirMover (false by default). */
    protected boolean omnidirectionalAirMover() {
        return false;
    }

    static float computeModifiedFriction(float friction, float modifier) {
        return Math.clamp(1.0F - (1.0F - friction) * modifier, 0.0F, 1.0F);
    }

    protected double getEntityBounciness() {
        return this.getAttributeValue(Cube.BOUNCINESS);
    }

    protected float getAirDrag26() {
        return computeModifiedFriction(this.omnidirectionalAirMover() ? 0.91F : 0.98F, (float) this.getAttributeValue(Cube.AIR_DRAG_MODIFIER));
    }

    protected double getEffectiveGravity() {
        boolean isFalling = this.getDeltaMovement().y <= 0.0;
        return isFalling && this.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(this.getGravity(), 0.01) : this.getGravity();
    }

    private double getBlockBounciness(BlockState state) {
        float b = state.getBlock() instanceof Bouncy bouncy ? bouncy.bounceRestitution()
                : state.is(Blocks.SLIME_BLOCK) ? 1.0F : state.is(BlockTags.BEDS) ? 0.75F : 0.0F;
        return b;   // a LivingEntity: 26.x scales it by 0.8 for other entities only
    }

    @Override
    public void move(MoverType type, Vec3 delta) {
        if (this.noPhysics) {
            super.move(type, delta);
            return;
        }
        boolean stuck = this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7;
        Vec3 requested = stuck ? delta.multiply(this.stuckSpeedMultiplier) : delta;
        Vec3 current = stuck ? Vec3.ZERO : this.getDeltaMovement();
        Vec3 from = this.position();
        super.move(type, delta);
        if (this.isRemoved()) {
            return;
        }
        Vec3 movement = this.position().subtract(from);
        boolean xCollision = !Mth.equal(requested.x, movement.x);
        boolean zCollision = !Mth.equal(requested.z, movement.z);
        boolean movedVertically = Math.abs(requested.y) > 0.0;
        if (movedVertically && this.verticalCollision || this.horizontalCollision) {
            this.restituteMovementAfterCollisions(this.level().getBlockState(this.getOnPosLegacy()), xCollision, zCollision, movement, current);
        }
    }

    /** 26.x Entity.restituteMovementAfterCollisions. */
    private void restituteMovementAfterCollisions(BlockState effectState, boolean xCollision, boolean zCollision, Vec3 movement, Vec3 current) {
        double restitution = this.isShiftKeyDown() ? 0.0 : this.getEntityBounciness();
        Vec3 after = current;
        if (xCollision) {
            after = current.with(Direction.Axis.X, -current.x * restitution);
        }
        if (zCollision) {
            after = after.with(Direction.Axis.Z, -current.z * restitution);
        }
        boolean bounced = restitution > 0.0 && (xCollision || zCollision);
        if (this.verticalCollision) {
            if (this.verticalCollisionBelow) {
                restitution = !(-current.y <= this.getEffectiveGravity()) && !this.isShiftKeyDown() && !effectState.is(SUPPRESSES_BOUNCE)
                        ? Math.max(restitution, this.getBlockBounciness(effectState)) : 0.0;
            }
            double gravityCompensation = 0.0;
            double effectiveDrag = 1.0;
            if (restitution > 0.0) {
                double portionWithMovement = movement.y / current.y;
                gravityCompensation = portionWithMovement * this.getEffectiveGravity();
                effectiveDrag = Mth.lerp(portionWithMovement, 1.0, this.getAirDrag26());
                bounced = true;
            }
            after = after.with(Direction.Axis.Y, (gravityCompensation - current.y) * effectiveDrag * restitution);
        }
        if (bounced) {
            // 26.x effectState.getBlock().bounceOn: only the shelf mushroom does anything, and its Bouncy hook (EntityMixin)
            // has already played it for this landing
            this.hasImpulse = true;
        }
        float speedFactor = this.getBlockSpeedFactor();
        this.setDeltaMovement(after.multiply(speedFactor, 1.0, speedFactor));
    }

    @Override
    public void travel(Vec3 input) {
        if (!this.isControlledByLocalInstance() || this.isFallFlying() || this.travelsInFluid()) {
            super.travel(input);
            return;
        }
        this.travelInAir(input);
        this.calculateEntityAnimation(false);
    }

    /** The fluid branches of 1.21.1's LivingEntity.travel (water and NeoForge fluid types, lava). */
    protected boolean travelsInFluid() {
        FluidState fluid = this.level().getFluidState(this.blockPosition());
        boolean water = (this.isInWater() || this.isInFluidType(fluid) && fluid.getFluidType() != NeoForgeMod.LAVA_TYPE.value())
                && this.isAffectedByFluids() && !this.canStandOnFluid(fluid);
        boolean lava = this.isInLava() && this.isAffectedByFluids() && !this.canStandOnFluid(fluid);
        return water || lava;
    }

    /** 26.x LivingEntity.travelInAir. */
    private void travelInAir(Vec3 input) {
        BlockPos posBelow = this.getBlockPosBelowThatAffectsMyMovement();
        float blockFriction = this.onGround()
                ? computeModifiedFriction(this.level().getBlockState(posBelow).getFriction(this.level(), posBelow, this),
                (float) this.getAttributeValue(Cube.FRICTION_MODIFIER))
                : 1.0F;
        Vec3 movement = this.handleRelativeFrictionAndCalculateMovement26(input, blockFriction);
        double movementY = movement.y;
        MobEffectInstance levitation = this.getEffect(MobEffects.LEVITATION);
        if (levitation != null) {
            movementY += (0.05 * (levitation.getAmplifier() + 1) - movement.y) * 0.2;
        } else if (!this.level().isClientSide() || this.level().hasChunkAt(posBelow)) {
            movementY -= this.getEffectiveGravity();
        } else if (this.getY() > this.level().getMinBuildHeight()) {
            movementY = -0.1;
        } else {
            movementY = 0.0;
        }
        if (this.shouldDiscardFriction()) {
            this.setDeltaMovement(movement.x, movementY, movement.z);
        } else {
            float modifier = (float) this.getAttributeValue(Cube.AIR_DRAG_MODIFIER);
            float airDrag = computeModifiedFriction(0.91F, modifier);
            float friction = blockFriction * airDrag;
            float verticalFriction = this.omnidirectionalAirMover() ? airDrag : computeModifiedFriction(0.98F, modifier);
            this.setDeltaMovement(movement.x * friction, movementY * verticalFriction, movement.z * friction);
        }
    }

    private Vec3 handleRelativeFrictionAndCalculateMovement26(Vec3 input, float friction) {
        this.moveRelative(this.getFrictionInfluencedSpeed26(friction), input);
        this.setDeltaMovement(this.handleOnClimbable26(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 movement = this.getDeltaMovement();
        if ((this.horizontalCollision || this.jumping) && (this.onClimbable() || this.wasInPowderSnow && PowderSnowBlock.canEntityWalkOnPowderSnow(this))) {
            movement = new Vec3(movement.x, 0.2, movement.z);
        }
        return movement;
    }

    private float getFrictionInfluencedSpeed26(float blockFriction) {
        if (this.onGround()) {
            return blockFriction > 0.6 ? this.getSpeed() * (0.21600002F / (blockFriction * blockFriction * blockFriction)) : this.getSpeed();
        }
        return this.getFlyingSpeed();
    }

    private Vec3 handleOnClimbable26(Vec3 delta) {
        if (this.onClimbable()) {
            this.resetFallDistance();
            double xd = Math.clamp(delta.x, -0.15F, 0.15F);
            double zd = Math.clamp(delta.z, -0.15F, 0.15F);
            double yd = Math.max(delta.y, -0.15F);   // 26.x's ladder hold applies to players only
            delta = new Vec3(xd, yd, zd);
        }
        return delta;
    }
}
