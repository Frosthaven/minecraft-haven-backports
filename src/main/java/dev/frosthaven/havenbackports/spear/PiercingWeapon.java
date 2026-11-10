package dev.frosthaven.havenbackports.spear;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 26.x {@code PiercingWeapon} (the PIERCING_WEAPON item component): the jab on the attack key. A plain record on
 * {@link SpearItem}. 26.x's {@code isInvulnerableToPiercingWeapon} is {@code isInvulnerable} for every 1.21.1 entity
 * (its only override is the 26.x sulfur cube). {@code onAttack} is 1.21.1's {@code resetAttackStrengthTicker} (both
 * zero the attack strength ticker; 26.x's item-swap ticker is client-side here, see client/spear/SpearClient).
 */
public record PiercingWeapon(boolean dealsKnockback, boolean dismounts, Optional<Supplier<SoundEvent>> sound, Optional<Supplier<SoundEvent>> hitSound) {
    public void makeSound(Entity causer) {
        this.sound.ifPresent(s -> causer.level().playSound(causer instanceof Player p ? p : null, causer.getX(), causer.getY(), causer.getZ(), s.get(), causer.getSoundSource(), 1.0F, 1.0F));
    }

    public void makeHitSound(Entity causer) {
        this.hitSound.ifPresent(s -> causer.level().playSound(null, causer.getX(), causer.getY(), causer.getZ(), s.get(), causer.getSoundSource(), 1.0F, 1.0F));
    }

    public static boolean canHitEntity(Entity jabber, Entity target) {
        if (target.isInvulnerable() || !target.isAlive()) {
            return false;
        } else if (target instanceof Interaction) {
            return true;
        } else if (!target.canBeHitByProjectile()) {
            return false;
        } else {
            return target instanceof Player targetPlayer && jabber instanceof Player jabbingPlayer && !jabbingPlayer.canHarmPlayer(targetPlayer)
                    ? false : !jabber.isPassengerOfSameVehicle(target);
        }
    }

    /** Server side. The swing goes to the trackers only (26.x swing(MAIN_HAND, STAB, false)): 1.21.1 swing(hand, false). */
    public void attack(LivingEntity attacker, EquipmentSlot hand) {
        float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack weaponItem = attacker.getItemBySlot(hand);
        AttackRange attackRange = SpearItem.attackRange(attacker, weaponItem);
        boolean hitSomething = false;
        Collection<EntityHitResult> hits = SpearHits.getHitEntitiesAlong(attacker, attackRange, e1 -> canHitEntity(attacker, e1),
                ClipContext.Block.COLLIDER).map(a -> List.of(), e -> e);
        for (EntityHitResult hitResult : hits) {
            hitSomething |= Stab.stabAttack(attacker, hand, hitResult.getEntity(), damage, true, this.dealsKnockback, this.dismounts);
        }
        if (attacker instanceof Player player) {
            player.resetAttackStrengthTicker();
        }
        Stab.postPiercingAttack(attacker);
        if (hitSomething) {
            this.makeHitSound(attacker);
        }
        this.makeSound(attacker);
        attacker.swing(InteractionHand.MAIN_HAND, false);
    }
}
