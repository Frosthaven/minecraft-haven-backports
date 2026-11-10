package dev.frosthaven.havenbackports.ghast;

import dev.frosthaven.havenbackports.Ghasts;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A player's right-click on a happy ghast, in 26.x's order: Entity.interact (sneak: the mobs on the player's leads go
 * onto the ghast; shears take the harness off: Mob.attemptToShearEquipment), then HappyGhast.mobInteract (the held
 * item first, so a harness goes on; then riding). 1.21.1's Mob.interact is final and handles a held lead and a name
 * tag before this runs, so sneaking with a lead in hand leashes the ghast itself, as in 1.21.1.
 * Returns null where 26.x falls through to Animal.mobInteract.
 */
final class HappyGhastInteraction {
    private HappyGhastInteraction() {}

    @Nullable
    static InteractionResult interact(HappyGhast ghast, Player player, InteractionHand hand) {
        if (!ghast.level().isClientSide() && player.isSecondaryUseActive() && ghast.canBeLeashed() && ghast.isAlive() && !ghast.isBaby()
                && moveLeashesOnto(ghast, player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.SHEARS) && !ghast.isVehicle() && !player.isSecondaryUseActive() && shearHarness(ghast, player, hand, held)) {
            return InteractionResult.sidedSuccess(ghast.level().isClientSide());
        }
        if (ghast.isBaby()) {
            return null;
        }
        if (!held.isEmpty()) {
            InteractionResult result = held.interactLivingEntity(player, ghast, hand);
            if (result.consumesAction()) {
                return result;
            }
        }
        if (ghast.isWearingBodyArmor() && !player.isSecondaryUseActive()) {
            if (!ghast.level().isClientSide()) {
                player.startRiding(ghast);
            }
            return InteractionResult.sidedSuccess(ghast.level().isClientSide());
        }
        return null;
    }

    /** 26.x Entity.interact's sneak branch with Leashable.leashableInArea (32 blocks) and canHaveALeashAttachedTo. */
    private static boolean moveLeashesOnto(HappyGhast ghast, Player player) {
        List<Entity> leashed = ghast.level().getEntitiesOfClass(Entity.class, AABB.ofSize(ghast.getBoundingBox().getCenter(), 32.0, 32.0, 32.0),
                e -> e instanceof Leashable l && l.getLeashHolder() == player);
        boolean any = false;
        for (Entity e : leashed) {
            Leashable l = (Leashable) e;
            double distance = ghast.getBoundingBox().getCenter().distanceTo(e.getBoundingBox().getCenter());
            if (e != ghast && distance <= snapDistance(e) && l.canBeLeashed()) {
                l.setLeashedTo(ghast, true);
                any = true;
            }
        }
        if (any) {
            ghast.level().gameEvent(GameEvent.ENTITY_ACTION, ghast.blockPosition(), GameEvent.Context.of(player));
            ghast.playSound(Ghasts.LEAD_TIED.get());
        }
        return any;
    }

    /** 26.x Leashable.leashSnapDistance: 12, 16 for a happy ghast. */
    static double snapDistance(Entity e) {
        return e instanceof HappyGhast g ? g.snapDistance() : 12.0;
    }

    /** 26.x Mob.attemptToShearEquipment and shearItem for the harness (the only shearable equipment here). */
    private static boolean shearHarness(HappyGhast ghast, Player player, InteractionHand hand, ItemStack shears) {
        ItemStack harness = ghast.getItemBySlot(EquipmentSlot.BODY);
        if (!(harness.getItem() instanceof HarnessItem)
                || EnchantmentHelper.has(harness, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) && !player.isCreative()) {
            return false;
        }
        shears.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
        Vec3 offset = Vec3.ZERO;   // 26.x EntityAttachments.getAverage(PASSENGER)
        for (int i = 0; i < 4; i++) {
            offset = offset.add(ghast.getAttachments().get(net.minecraft.world.entity.EntityAttachment.PASSENGER, i, 0.0F).scale(0.25));
        }
        ghast.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
        ghast.gameEvent(GameEvent.SHEAR, player);
        if (!ghast.level().isClientSide()) {
            ghast.spawnAtLocation(harness, (float) offset.y);   // 26.x spawns at the full offset; 1.21.1 takes a height
        }
        ghast.playSound(Ghasts.HARNESS_UNEQUIP.get());
        return true;
    }
}
