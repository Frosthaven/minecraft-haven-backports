package dev.frosthaven.havenbackports.nautilus;

import dev.frosthaven.havenbackports.Nautiluses;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x AbstractNautilus.mobInteract and tryToTame, after 26.x Mob.interact's equipment shearing (shears take the body
 * armor off first, then the saddle: EquipmentSlot.VALUES order, each Equippable canBeSheared, not while ridden, not
 * through Curse of Binding outside creative). 1.21.1's Mob.interact is final and runs the lead and name tag checks
 * first, so the shearing happens at the start of mobInteract. 26.x's player_sheared_equipment advancement trigger
 * does not exist in 1.21.1 and is left out.
 */
final class NautilusInteraction {
    private NautilusInteraction() {}

    static InteractionResult mobInteract(AbstractNautilus nautilus, Player player, InteractionHand hand) {
        nautilus.setPersistenceRequired();   // 26.x AbstractNautilus.interact
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.SHEARS) && !nautilus.isVehicle() && !player.isSecondaryUseActive() && shear(nautilus, player, hand, stack)) {
            return InteractionResult.sidedSuccess(nautilus.level().isClientSide());
        }
        if (nautilus.isBaby()) {
            return animalOrMob(nautilus, player, hand);
        }
        if (nautilus.isTame() && player.isSecondaryUseActive()) {
            nautilus.openCustomInventoryScreen(player);
            return InteractionResult.sidedSuccess(nautilus.level().isClientSide());
        }
        if (!stack.isEmpty()) {
            if (!nautilus.level().isClientSide() && !nautilus.isTame() && nautilus.isFood(stack)) {
                nautilus.usePlayerItemForTaming(player, hand, stack);
                tryToTame(nautilus, player);
                return InteractionResult.SUCCESS;
            }
            if (nautilus.isFood(stack) && nautilus.getHealth() > 0.0F && nautilus.getHealth() < nautilus.getMaxHealth()) {
                // 26.x TamableAnimal.feed(player, hand, stack, 2.0, 1.0): heal 2 x nutrition, or 1 without food
                var food = stack.getFoodProperties(nautilus);
                nautilus.usePlayerItemForTaming(player, hand, stack);
                nautilus.heal(food != null ? 2.0F * food.nutrition() : 1.0F);
                nautilus.playEatingSound();
                return InteractionResult.sidedSuccess(nautilus.level().isClientSide());
            }
            InteractionResult result = stack.interactLivingEntity(player, nautilus, hand);
            if (result.consumesAction()) {
                return result;
            }
        }
        if (nautilus.isTame() && !player.isSecondaryUseActive() && !nautilus.isFood(stack)) {
            nautilus.doPlayerRide(player);
            return InteractionResult.sidedSuccess(nautilus.level().isClientSide());
        }
        return animalOrMob(nautilus, player, hand);
    }

    /** super.mobInteract in 26.x: TamableAnimal (none of its own) -> 26.x Animal.mobInteract (it plays the eating
     *  sound when breeding or growing; 1.21.1's does not) -> Mob (PASS). */
    private static InteractionResult animalOrMob(AbstractNautilus nautilus, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (nautilus.isFood(stack)) {
            int age = nautilus.getAge();
            if (!nautilus.level().isClientSide() && age == 0 && nautilus.canFallInLove()) {
                nautilus.usePlayerItemForTaming(player, hand, stack);
                nautilus.setInLove(player);
                nautilus.playEatingSound();
                return InteractionResult.SUCCESS;
            }
            if (nautilus.isBaby()) {
                nautilus.usePlayerItemForTaming(player, hand, stack);
                nautilus.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-age), true);
                nautilus.playEatingSound();
                return InteractionResult.sidedSuccess(nautilus.level().isClientSide());
            }
            if (nautilus.level().isClientSide()) {
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    private static void tryToTame(AbstractNautilus nautilus, Player player) {
        if (nautilus.getRandom().nextInt(3) == 0) {
            nautilus.tame(player);
            nautilus.getNavigation().stop();
            nautilus.level().broadcastEntityEvent(nautilus, (byte) 7);
        } else {
            nautilus.level().broadcastEntityEvent(nautilus, (byte) 6);
        }
        nautilus.playEatingSound();
    }

    /** 26.x Mob.attemptToShearEquipment and shearItem over BODY (nautilus armor) and SADDLE. */
    private static boolean shear(AbstractNautilus nautilus, Player player, InteractionHand hand, ItemStack shears) {
        ItemStack armor = nautilus.getItemBySlot(EquipmentSlot.BODY);
        boolean body = armor.getItem() instanceof NautilusArmorItem && changeable(armor, player);
        ItemStack sheared = body ? armor : nautilus.getSaddle();
        if (!body && (sheared.isEmpty() || !changeable(sheared, player))) {
            return false;
        }
        shears.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        Vec3 offset = nautilus.getAttachments().get(EntityAttachment.PASSENGER, 0, 0.0F);   // 26.x getAverage(PASSENGER): one seat
        if (body) {
            nautilus.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
        } else {
            nautilus.setSaddle(ItemStack.EMPTY);
        }
        nautilus.gameEvent(GameEvent.SHEAR, player);
        if (!nautilus.level().isClientSide()) {
            nautilus.spawnAtLocation(sheared, (float) offset.y);   // 26.x spawns at the full offset; 1.21.1 takes a height
        }
        nautilus.playSound(body ? Nautiluses.ARMOR_UNEQUIP_NAUTILUS.get() : Nautiluses.SADDLE_UNEQUIP.get());
        return true;
    }

    private static boolean changeable(ItemStack stack, Player player) {
        return !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) || player.isCreative();
    }
}
