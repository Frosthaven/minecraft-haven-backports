package dev.frosthaven.havenbackports.spear;

import dev.frosthaven.havenbackports.Spears;
import dev.frosthaven.havenbackports.mixin.LivingEntityInvoker;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.function.Predicate;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;

/**
 * 26.x's stab attack and the LivingEntity / Player / Entity methods spears use, on 1.21.1 APIs:
 * {@code Player.stabAttack} (with attackVisualEffects, itemAttackInteraction, damageStatsAndHearts),
 * {@code LivingEntity.stabAttack}, {@code causeExtraKnockback}, {@code postPiercingAttack}, the recent-stab memory and
 * {@code Entity.getKnownSpeed}.
 * <p>1.21.1 differences: {@code hurtOrSimulate} is {@code hurt}; 1.21.1's knockback takes no source; enchantment damage on
 * the server is {@code EnchantmentHelper.modifyDamage} (what 1.21.1's ServerPlayer.getEnchantedDamage does). A stab is not
 * {@code Player.attack}, so NeoForge's AttackEntityEvent and CriticalHitEvent do not fire for it (26.x has neither).
 * Ender dragon parts are NeoForge PartEntities in 1.21.1.
 */
public final class Stab {
    private Stab() {}

    public static SpearState.Living state(LivingEntity e) {
        return (SpearState.Living) e;
    }

    /** 26.x Entity.getKnownSpeed: a vehicle a player controls reports the player's. */
    public static Vec3 getKnownSpeed(Entity e) {
        return e.getControllingPassenger() instanceof Player controller && e.isAlive() ? getKnownSpeed(controller)
                : ((SpearState.Known) e).haven_backports$lastKnownSpeed();
    }

    public static boolean wasRecentlyStabbed(LivingEntity self, Entity target, int allowedTime) {
        Object2LongMap<Entity> map = state(self).haven_backports$recentKineticEnemies();
        if (map == null) {
            return false;
        }
        return map.containsKey(target) && self.level().getGameTime() - map.getLong(target) < allowedTime;
    }

    public static void rememberStabbedEntity(LivingEntity self, Entity target) {
        Object2LongMap<Entity> map = state(self).haven_backports$recentKineticEnemies();
        if (map != null) {
            map.put(target, self.level().getGameTime());
        }
    }

    public static int stabbedEntities(LivingEntity self, Predicate<Entity> filter) {
        Object2LongMap<Entity> map = state(self).haven_backports$recentKineticEnemies();
        return map == null ? 0 : (int) map.keySet().stream().filter(filter).count();
    }

    /** 26.x LivingEntity.postPiercingAttack: the main-hand weapon's post_piercing_attack enchantment effects. */
    public static void postPiercingAttack(LivingEntity self) {
        if (self.level() instanceof ServerLevel serverLevel) {
            Lunge.doPostPiercingAttackEffects(serverLevel, self);
        }
    }

    /** 26.x getTicksSinceLastKineticHitFeedback. */
    public static float ticksSinceLastKineticHitFeedback(LivingEntity self, float partial) {
        long last = state(self).haven_backports$lastKineticHitFeedbackTime();
        return last < 0L ? 0.0F : (float) (self.level().getGameTime() - last) + partial;
    }

    /** 26.x LivingEntity.onKineticHit (entity event 2, client): at most once per 10 ticks, the local hit sound. */
    public static void onKineticHit(LivingEntity self) {
        if (self.level().getGameTime() - state(self).haven_backports$lastKineticHitFeedbackTime() > 10L) {
            state(self).haven_backports$setLastKineticHitFeedbackTime(self.level().getGameTime());
            if (self.getUseItem().getItem() instanceof SpearItem spear) {
                spear.kinetic().makeLocalHitSound(self);
            }
        }
    }

    public static boolean stabAttack(LivingEntity self, EquipmentSlot slot, Entity target, float baseDamage, boolean dealsDamage,
            boolean dealsKnockback, boolean dismounts) {
        return self instanceof Player player ? playerStab(player, slot, target, baseDamage, dealsDamage, dealsKnockback, dismounts)
                : mobStab(self, slot, target, baseDamage, dealsDamage, dealsKnockback, dismounts);
    }

    private static DamageSource damageSource(LivingEntity attacker) {
        return new DamageSource(attacker.level().registryAccess().holderOrThrow(Spears.SPEAR_DAMAGE), attacker);
    }

    /** 26.x LivingEntity.stabAttack. */
    private static boolean mobStab(LivingEntity self, EquipmentSlot weaponSlot, Entity target, float baseDamage, boolean dealsDamage,
            boolean dealsKnockback, boolean dismounts) {
        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ItemStack weaponItem = self.getItemBySlot(weaponSlot);
        DamageSource damageSource = damageSource(self);
        float postEnchantmentDamage = EnchantmentHelper.modifyDamage(serverLevel, weaponItem, target, damageSource, baseDamage);
        Vec3 oldMovement = target.getDeltaMovement();
        boolean dealtDamage = dealsDamage && target.hurt(damageSource, postEnchantmentDamage);
        boolean affected = dealsKnockback | dealtDamage;
        if (dealsKnockback) {
            causeExtraKnockback(self, target, 0.4F, oldMovement);
            causeExtraKnockback(self, target, ((LivingEntityInvoker) self).haven_backports$getKnockback(target, damageSource), oldMovement);
        }
        if (dismounts && target.isPassenger() && !target.getType().is(Spears.CANNOT_BE_DISMOUNTED_BY_ITEM_USAGE)) {
            affected = true;
            target.stopRiding();
        }
        if (target instanceof LivingEntity livingTarget) {
            weaponItem.getItem().hurtEnemy(weaponItem, livingTarget, self);   // 1.21.1's ItemStack.hurtEnemy takes a Player
        }
        if (dealtDamage) {
            EnchantmentHelper.doPostAttackEffects(serverLevel, target, damageSource);
        }
        if (!affected) {
            return false;
        }
        self.setLastHurtMob(target);
        return true;   // 26.x playAttackSound(): empty for every mob that can hold a spear (zombies, piglins)
    }

    /** 26.x Player.stabAttack. */
    private static boolean playerStab(Player self, EquipmentSlot slot, Entity target, float baseDamage, boolean dealsDamage,
            boolean dealsKnockback, boolean dismounts) {
        if (!target.isAttackable() || target.skipAttackInteraction(self)) {
            return false;
        }
        ItemStack weaponItem = self.getItemBySlot(slot);
        DamageSource damageSource = damageSource(self);
        float enchanted = self.level() instanceof ServerLevel serverLevel
                ? EnchantmentHelper.modifyDamage(serverLevel, weaponItem, target, damageSource, baseDamage) : baseDamage;
        float magicBoost = enchanted - baseDamage;
        if (!self.isUsingItem() || (self.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND) != slot) {
            float attackStrengthScale = self.getAttackStrengthScale(0.5F);
            magicBoost *= attackStrengthScale;
            baseDamage *= 0.2F + attackStrengthScale * attackStrengthScale * 0.8F;
        }
        if (dealsKnockback && deflectProjectile(self, target)) {
            return true;
        }
        float totalDamage = dealsDamage ? baseDamage + magicBoost : 0.0F;
        float oldLivingEntityHealth = target instanceof LivingEntity livingTarget ? livingTarget.getHealth() : 0.0F;
        Vec3 oldMovement = target.getDeltaMovement();
        boolean wasHurt = dealsDamage && target.hurt(damageSource, totalDamage);
        if (dealsKnockback) {
            playerKnockback(self, target, 0.4F, oldMovement);
            playerKnockback(self, target, ((LivingEntityInvoker) self).haven_backports$getKnockback(target, damageSource), oldMovement);
        }
        boolean dismounted = false;
        if (dismounts && target.isPassenger() && !target.getType().is(Spears.CANNOT_BE_DISMOUNTED_BY_ITEM_USAGE)) {
            dismounted = true;
            target.stopRiding();
        }
        if (!wasHurt && !dealsKnockback && !dismounted) {
            return false;
        }
        // attackVisualEffects(target, crit false, sweep false, full strength, stab true, magicBoost): a stab plays no
        // strong/weak/crit sound
        if (magicBoost > 0.0F) {
            self.magicCrit(target);
        }
        self.setLastHurtMob(target);
        itemAttackInteraction(self, target, weaponItem, damageSource, wasHurt);
        if (target instanceof LivingEntity livingEntity) {   // damageStatsAndHearts
            float actualDamage = oldLivingEntityHealth - livingEntity.getHealth();
            self.awardStat(Stats.DAMAGE_DEALT, Math.round(actualDamage * 10.0F));
            if (self.level() instanceof ServerLevel serverLevel && actualDamage > 2.0F) {
                int count = (int) (actualDamage * 0.5);
                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.5), target.getZ(), count, 0.1, 0.0, 0.1, 0.2);
            }
        }
        self.causeFoodExhaustion(0.1F);
        return true;
    }

    private static boolean deflectProjectile(Player self, Entity entity) {
        if (entity.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile projectile
                && projectile.deflect(ProjectileDeflection.AIM_DEFLECT, self, self, true)) {
            self.level().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, self.getSoundSource());
            return true;
        }
        return false;
    }

    /** 26.x Player.itemAttackInteraction (1.21.1's Player.attack does the same after a hit). */
    private static void itemAttackInteraction(Player self, Entity entity, ItemStack stack, DamageSource damageSource, boolean applyToTarget) {
        Entity hurtTarget = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        boolean itemHurtEnemy = false;
        ItemStack copy = stack.copy();
        if (self.level() instanceof ServerLevel serverLevel) {
            if (hurtTarget instanceof LivingEntity livingTarget) {
                itemHurtEnemy = stack.hurtEnemy(livingTarget, self);
            }
            if (applyToTarget) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, entity, damageSource);
            }
        }
        if (!self.level().isClientSide && !stack.isEmpty() && hurtTarget instanceof LivingEntity living) {
            if (itemHurtEnemy) {
                stack.postHurtEnemy(living, self);
            }
            if (stack.isEmpty()) {
                InteractionHand hand = stack == self.getMainHandItem() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                EventHooks.onPlayerDestroyItem(self, copy, hand);
                self.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
    }

    /** 26.x LivingEntity.causeExtraKnockback. */
    private static void causeExtraKnockback(LivingEntity self, Entity target, float knockback, Vec3 oldMovement) {
        if (knockback > 0.0F && target instanceof LivingEntity livingTarget) {
            livingTarget.knockback(knockback, Mth.sin(self.getYRot() * (float) (Math.PI / 180.0)), -Mth.cos(self.getYRot() * (float) (Math.PI / 180.0)));
            self.setDeltaMovement(self.getDeltaMovement().multiply(0.6, 1.0, 0.6));
        }
    }

    /** 26.x Player.causeExtraKnockback ({@code syncVelocity} is 1.21.1's {@code hurtMarked}). */
    private static void playerKnockback(Player self, Entity entity, float knockbackAmount, Vec3 oldMovement) {
        if (knockbackAmount > 0.0F) {
            if (entity instanceof LivingEntity livingTarget) {
                livingTarget.knockback(knockbackAmount, Mth.sin(self.getYRot() * (float) (Math.PI / 180.0)),
                        -Mth.cos(self.getYRot() * (float) (Math.PI / 180.0)));
            } else {
                entity.push(-Mth.sin(self.getYRot() * (float) (Math.PI / 180.0)) * knockbackAmount, 0.1,
                        Mth.cos(self.getYRot() * (float) (Math.PI / 180.0)) * knockbackAmount);
            }
            self.setDeltaMovement(self.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            self.setSprinting(false);
        }
        if (entity instanceof ServerPlayer serverPlayer && entity.hurtMarked) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(entity));
            entity.hurtMarked = false;
            entity.setDeltaMovement(oldMovement);
        }
    }
}
