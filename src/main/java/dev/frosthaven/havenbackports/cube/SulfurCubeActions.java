package dev.frosthaven.havenbackports.cube;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.item.PrimedTnt;

/**
 * 26.x SulfurCube's interaction, fuse, push, contact damage and knockback methods, line for line (split out of
 * SulfurCube: each file stays under 500 lines). 1.21.1 forms: no tnt_explodes game rule (1.21.1 TNT always
 * explodes), so that check and its "TNT is disabled" message are gone; no PLAYER_SHEARED_EQUIPMENT criterion;
 * 1.21.1's knockback(power, x, z) without the damage source, so SulfurCube keeps the source of the hurt it is in
 * (26.x passes it); knockback from an effect (26.x comesFromEffect) does not pass through knockback() in 1.21.1.
 */
final class SulfurCubeActions {
    private SulfurCubeActions() {}

    /** 26.x SulfurCube.hurtServer with a block inside: fire primes it, an explosion primes it short; the damage types
     *  of #sulfur_cube_with_block_immune_to only push it. True: handled (no damage). */
    static boolean hurtWithBlock(SulfurCube cube, DamageSource source, float damage) {
        if (!(cube.level() instanceof ServerLevel) || !cube.hasBodyItem()) {
            return false;
        }
        if (cube.canExplode() && !cube.isPrimed()) {
            Entity direct = source.getDirectEntity();
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || direct instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow && arrow.isOnFire()) {
                primeTime(cube, false);
            } else if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
                primeTime(cube, true);
            }
        }
        if (source.is(SulfurCube.IMMUNE_WITH_BLOCK)) {
            if (!source.is(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK)) {
                dealDefaultKnockback(cube, source, damage);
            }
            return true;
        }
        return false;
    }

    /** 26.x SulfurCube.travelInFluid: after the fluid move, a held block floats if its archetype is buoyant. */
    static void floatInFluid(SulfurCube cube) {
        if (cube.hasBodyItem() && cube.floatsInLiquids) {
            float vibe = 0.2F * Mth.sin(cube.tickCount * 0.4F);
            double immersion = cube.getFluidHeight(cube.isInWater() ? net.minecraft.tags.FluidTags.WATER : net.minecraft.tags.FluidTags.LAVA)
                    - cube.getFluidJumpThreshold() + vibe;
            if (immersion > 0.0) {
                cube.addDeltaMovement(new Vec3(0.0, Math.min(1.0, immersion) * 0.04F, 0.0));
            }
        }
    }

    /** 26.x SulfurCube.pickUpItem: one item of the stack, unless it just ejected one (pickup timer). */
    static void pickUp(SulfurCube cube, ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (cube.canHoldItem(stack) && cube.pickupTimer <= 0) {
            cube.onItemPickup(entity);
            cube.setItemSlot(EquipmentSlot.BODY, stack.split(1));
            cube.playSound(CubeSounds.ABSORB.get());
            cube.setGuaranteedDrop(EquipmentSlot.BODY);
            cube.take(entity, 1);
            if (stack.isEmpty()) {
                entity.discard();
            }
        }
    }

    static void tickFuse(SulfurCube cube) {
        if (cube.fuse > 0) {
            cube.fuse--;
        }
        if (cube.explosionData.isPresent() && cube.fuse == 0) {
            cube.dropLeash(true, false);
            cube.markDead();
            if (cube.level() instanceof ServerLevel level) {
                Level.ExplosionInteraction interaction = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                        ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
                var data = cube.explosionData.get();
                level.explode(cube, Explosion.getDefaultDamageSource(level, cube),
                        cube.getPortalCooldown() > 0 ? PrimedTnt.USED_PORTAL_DAMAGE_CALCULATOR : null,
                        cube.getX(), cube.getY(0.0625), cube.getZ(), data.power(), data.causesFire(), interaction);
                cube.deathEffects();
            }
            cube.discard();
        }
    }

    static void primeWhenOnPoweredPosition(SulfurCube cube) {
        if (cube.level() instanceof ServerLevel level && cube.canExplode()) {
            BlockPos here = BlockPos.containing(cube.position());
            BlockState state = level.getBlockState(here);
            // 26.x SignalGetter.getBestOwnOrNeighbourSignal; 26.x ownSignal (a block's signal, any side) is 1.21.1's
            // strongest getSignal over the six sides
            int own = 0;
            if (state.isSignalSource()) {
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    own = Math.max(own, state.getSignal(level, here, d));
                }
            }
            int signal = Math.max(level.getBestNeighborSignal(here), own);
            if (signal != 0) {
                primeTime(cube, false);
            }
        }
    }

    static boolean primeTime(SulfurCube cube, boolean imminent) {
        if (cube.explosionData.isPresent() && cube.isAlive() && cube.level() instanceof ServerLevel && !cube.isPrimed()) {
            int fuse = cube.explosionData.get().fuse();
            // 26.x PrimedTnt.getRandomShortFuse
            int fuseTime = imminent ? cube.getRandom().nextInt(Math.max(1, fuse / 4)) + fuse / 8 : fuse;
            cube.setInvulnerable(true);   // 26.x setPermanentlyInvulnerable: the same Invulnerable flag
            cube.syncFuse(fuseTime);
            cube.makeSound(SoundEvents.TNT_PRIMED);
            cube.gameEvent(GameEvent.PRIME_FUSE);
            return true;
        }
        return false;
    }

    static InteractionResult mobInteract(SulfurCube cube, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Level level = cube.level();
        if (cube.isBaby()) {
            if (held.is(SulfurCube.FOOD) && cube.canAgeUp()) {
                int age = cube.getAge();
                held.consume(1, player);
                cube.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-age), true);
                cube.makeSound(CubeSounds.SMALL_EAT.get());
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return cube.superMobInteract(player, hand);
        } else if (cube.isPrimed()) {
            return InteractionResult.PASS;
        } else if (!cube.canExplode() || !held.is(Items.FLINT_AND_STEEL) && !held.is(Items.FIRE_CHARGE)) {
            if (held.is(Items.SHEARS) && cube.readyForShearing()) {
                if (level instanceof ServerLevel) {
                    shear(cube);
                    cube.gameEvent(GameEvent.SHEAR, player);
                    held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else if (SulfurCube.isSwallowableItem(held)) {
                boolean worked = equipItem(cube, held);
                if (worked) {
                    held.consume(1, player);
                    cube.gameEvent(GameEvent.ENTITY_INTERACT);
                }
                return worked ? (level.isClientSide ? InteractionResult.CONSUME : InteractionResult.SUCCESS) : InteractionResult.PASS;
            }
            return bucketPickup(cube, player, hand).orElseGet(() -> cube.superMobInteract(player, hand));
        }
        primeTime(cube, false);
        if (held.is(Items.FLINT_AND_STEEL)) {
            level.playSound(null, cube.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
            held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        } else {
            net.minecraft.util.RandomSource random = level.getRandom();
            level.playSound(null, cube.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F,
                    (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
            held.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(held.getItem()));
        return level.isClientSide ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
    }

    /** 26.x Bucketable.bucketMobPickup with the cube's canBePickedUpWithBucket (an empty bucket, not a water one). */
    static java.util.Optional<InteractionResult> bucketPickup(SulfurCube cube, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() == Items.BUCKET && cube.isAlive()) {
            cube.playSound(cube.getPickupSound(), 1.0F, 1.0F);
            ItemStack bucket = cube.getBucketItemStack();
            cube.saveToBucketTag(bucket);
            player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(stack, player, bucket, false));
            if (!cube.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.minecraft.advancements.CriteriaTriggers.FILLED_BUCKET.trigger(sp, bucket);
            }
            cube.dropLeash(true, false);
            cube.discard();
            return java.util.Optional.of(InteractionResult.sidedSuccess(cube.level().isClientSide));
        }
        return java.util.Optional.empty();
    }

    /** 26.x SulfurCube.equipItem (the dispenser path calls it too). */
    static boolean equipItem(SulfurCube cube, ItemStack held) {
        if (cube.isBaby()) {
            return false;
        }
        if (cube.hasBodyItem()) {
            if (held.is(cube.getItemBySlot(EquipmentSlot.BODY).getItem())) {
                return false;
            }
            if (cube.level() instanceof ServerLevel level) {
                spawnAt(level, cube, cube.getItemBySlot(EquipmentSlot.BODY), cube.equipmentSpawnOffset());
            }
        }
        if (!cube.level().isClientSide) {
            // 26.x also runs collectEquipmentChanges for the swallowed stack here; 1.21.1's equipment check (and the
            // LivingEquipmentChangeEvent the archetype change runs from) sees the same before and after next tick
            cube.setBodyItem(held.copyWithCount(1));
        }
        cube.playSound(CubeSounds.ABSORB.get());
        return true;
    }

    static void shear(SulfurCube cube) {
        if (cube.level() instanceof ServerLevel level) {
            ItemStack toShear = cube.getItemBySlot(EquipmentSlot.BODY);
            cube.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
            if (spawnAt(level, cube, toShear, cube.equipmentSpawnOffset()) != null) {
                cube.playSound(CubeSounds.EJECT.get());
                cube.pickupTimer = SulfurCube.PICKUP_TIMER_DURATION;
            }
        }
    }

    /** 26.x Entity.spawnAtLocation(level, stack, Vec3 offset). */
    static ItemEntity spawnAt(ServerLevel level, Entity at, ItemStack stack, Vec3 offset) {
        if (stack.isEmpty()) {
            return null;
        }
        ItemEntity item = new ItemEntity(level, at.getX() + offset.x, at.getY() + offset.y, at.getZ() + offset.z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        return item;
    }

    static void applyContactDamage(SulfurCube cube, Entity entity) {
        if (cube.level() instanceof ServerLevel) {
            for (SulfurCubeArchetype.ContactDamage damage : cube.contactDamages) {
                Entity source = !damage.attributeToSource() && !(entity instanceof Player) ? null : cube;
                entity.hurt(new DamageSource(damage.damageType(), source), damage.amount().sample(cube.getRandom()));
            }
        }
    }

    static void playerPush(SulfurCube cube, Player player) {
        if (!cube.hasBodyItem()) {
            return;
        }
        Entity pusher = player.isPassenger() ? player.getRootVehicle() : player;
        Vec3 cubeToPusher = cube.position().subtract(pusher.position());
        double feet = pusher.getY();
        double bottom = cube.getY();
        double top = bottom + cube.getBbHeight();
        double pusherTop = feet + pusher.getBbHeight();
        if (cubeToPusher.horizontalDistance() < 1.3F && feet <= top && pusherTop > bottom) {
            double knockback = Math.max(0.0, 1.0 - cube.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            Vec3 direction = new Vec3(cubeToPusher.x, 0.0, cubeToPusher.z).normalize().scale(knockback);
            float scale = player.isPassenger() ? 0.16F : 0.3F;
            double speed = Math.clamp(player.getKnownMovement().length() * 2.0 * scale, 0.0, 0.5);   // 26.x getKnownSpeed
            Vec3 velocity = new Vec3(direction.x, cube.onGround() ? knockback * 0.3F : 0.0, direction.z).scale(speed);
            cube.hasImpulse = true;
            cube.soundSettings.pushSound().ifPresent(sound -> {
                float threshold = cube.soundSettings.pushSoundImpulseThreshold();
                if (velocity.lengthSqr() > threshold * threshold && cube.pushSoundCooldown <= 0) {
                    cube.pushSoundCooldown = (int) (cube.soundSettings.pushSoundCooldown() * 20.0F);
                    cube.playSound(sound.value());
                }
            });
            cube.addDeltaMovement(velocity);
            applyContactDamage(cube, player);
        }
    }

    /** 26.x LivingEntity.dealDefaultKnockback (blocked: no damage indication). */
    static void dealDefaultKnockback(SulfurCube cube, DamageSource source, float damage) {
        double xd = 0.0;
        double zd = 0.0;
        if (source.getDirectEntity() instanceof Projectile projectile) {
            var dir = projectile.calculateHorizontalHurtKnockbackDirection(cube, source);
            xd = -dir.leftDouble();
            zd = -dir.rightDouble();
        } else if (source.getSourcePosition() != null) {
            xd = source.getSourcePosition().x() - cube.getX();
            zd = source.getSourcePosition().z() - cube.getZ();
        }
        cube.knockbackSource = source;
        cube.knockbackDamage = damage;
        try {
            cube.knockback(0.4F, xd, zd);
        } finally {
            cube.knockbackSource = null;
        }
    }

    /** 26.x SulfurCube.knockback, with a block inside and an attacker; false: the default knockback applies. */
    static boolean knockback(SulfurCube cube, double xd, double zd) {
        DamageSource source = cube.knockbackSource;
        if (source == null || source.getEntity() == null || !cube.hasBodyItem()) {
            return false;
        }
        Entity attacker = source.getEntity();
        float horizontalPower = cube.knockbackModifier.horizontalPower();
        float verticalPower = cube.knockbackModifier.verticalPower();
        Vec2 newAngle = CubeKnockback.applyHorizontalHitAngleScale(1.6F, new Vec2((float) xd, (float) zd), attacker.getEyePosition(),
                attacker.getLookAngle().normalize(), cube.getBoundingBox().getCenter());
        Vec2 power = CubeKnockback.applyVerticalHitAnglePowerTransfer(0.5F, horizontalPower, verticalPower, attacker.getEyePosition(),
                attacker.getLookAngle().normalize(), cube.getBoundingBox().getCenter(), cube.getBbHeight());
        power = CubeKnockback.applyVerticalPositionAnglePowerRotation(0.8F, power.x, power.y, horizontalPower, verticalPower,
                attacker.position(), cube.position());
        horizontalPower = power.x;
        verticalPower = power.y;
        float multiplier = Mth.sqrt(cube.knockbackDamage);   // 26.x: times power * 0.25 when it comes from an effect
        horizontalPower *= multiplier;
        verticalPower *= multiplier;
        double resistance = cube.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        horizontalPower *= (float) (1.0 - resistance);
        verticalPower *= (float) (1.0 - resistance);
        cube.hasImpulse = true;
        horizontalPower *= 0.4F;
        horizontalPower = Math.clamp(horizontalPower, -128.0F, 128.0F);
        verticalPower = Math.clamp(verticalPower, -128.0F, 128.0F);
        Vec3 horizontal = new Vec3(newAngle.x, 0.0, newAngle.y).normalize().scale(horizontalPower);
        cube.addDeltaMovement(new Vec3(-horizontal.x, verticalPower * 1.2, -horizontal.z));
        cube.soundSettings.hitSound().ifPresent(s -> cube.playSound(s.value()));
        return true;
    }
}
