package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Golems;
import dev.frosthaven.havenbackports.golem.CopperGolem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.OfferFlowerGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x OfferFlowerGoal: an iron golem offers its poppy to the nearest #candidate_for_iron_golem_gift (villagers and
 * copper golems), not only villagers, and a mob in #accepts_iron_golem_gift still in reach when the offer ends keeps
 * the poppy on its antenna (a guaranteed drop). 1.21.1 holds the target as a Villager, so the target is kept here.
 */
@Mixin(OfferFlowerGoal.class)
abstract class OfferFlowerGoalMixin {
    @Unique
    private static final TargetingConditions HAVEN_BACKPORTS$OFFER = TargetingConditions.forNonCombat().range(6.0);

    @Shadow
    @Final
    private IronGolem golem;
    @Shadow
    private Villager villager;
    @Shadow
    private int tick;
    @Unique
    private LivingEntity haven_backports$entity;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void haven_backports$canUse(CallbackInfoReturnable<Boolean> cir) {
        if (!golem.level().isDay() || golem.getRandom().nextInt(8000) != 0) {
            cir.setReturnValue(false);
            return;
        }
        AABB box = golem.getBoundingBox().inflate(6.0, 2.0, 6.0);
        haven_backports$entity = ((ServerLevel) golem.level()).getNearestEntity(golem.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.getType().is(Golems.CANDIDATE_FOR_IRON_GOLEM_GIFT)), HAVEN_BACKPORTS$OFFER, golem, golem.getX(), golem.getY(), golem.getZ());
        villager = haven_backports$entity instanceof Villager v ? v : null;
        cir.setReturnValue(haven_backports$entity != null);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void haven_backports$tick(CallbackInfo ci) {
        if (haven_backports$entity != null) {
            golem.getLookControl().setLookAt(haven_backports$entity, 30.0F, 30.0F);
        }
        tick--;
        ci.cancel();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void haven_backports$gift(CallbackInfo ci) {
        if (tick == 0 && haven_backports$entity instanceof CopperGolem mob && mob.getType().is(Golems.ACCEPTS_IRON_GOLEM_GIFT) && mob.getAntenna().isEmpty()
                && golem.getBoundingBox().inflate(6.0, 2.0, 6.0).intersects(mob.getBoundingBox())) {
            mob.setAntenna(new ItemStack(Items.POPPY));
        }
        haven_backports$entity = null;
    }
}
