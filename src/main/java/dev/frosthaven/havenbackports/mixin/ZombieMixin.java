package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.spear.SpearUseGoal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x Zombie (husks and zombie villagers inherit it; drowned replace addBehaviourGoals, as in 26.x):
 * <ul>
 * <li>addBehaviourGoals: SpearUseGoal(this, 1.0, 1.0, 10, 2) at priority 2, ZombieAttackGoal moved from 2 to 3.</li>
 * <li>populateDefaultEquipmentSlots: the weapon roll is nextInt(6) (1.21.1: nextInt(3)): 0 iron sword, 1 iron spear,
 * else iron shovel. One nextInt call, as in 26.x.</li>
 * </ul>
 */
@Mixin(Zombie.class)
abstract class ZombieMixin extends Monster {
    @Unique
    private int haven_backports$weaponRoll;

    private ZombieMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Inject(method = "addBehaviourGoals", at = @At("HEAD"))
    private void haven_backports$spearGoal(CallbackInfo ci) {
        this.goalSelector.addGoal(2, new SpearUseGoal<>((Zombie) (Object) this, 1.0, 1.0, 10.0F, 2.0F));
    }

    @ModifyArg(method = "addBehaviourGoals", index = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V"))
    private int haven_backports$attackGoalPriority(int priority) {
        return 3;
    }

    @WrapOperation(method = "populateDefaultEquipmentSlots", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int haven_backports$weaponRoll(RandomSource random, int bound, Operation<Integer> original) {
        this.haven_backports$weaponRoll = original.call(random, 6);
        return this.haven_backports$weaponRoll == 0 ? 0 : 1;
    }

    @ModifyArg(method = "populateDefaultEquipmentSlots", index = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/entity/monster/Zombie;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"))
    private ItemStack haven_backports$spear(ItemStack shovel) {
        return this.haven_backports$weaponRoll == 1
                ? new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("haven_backports", "iron_spear"))) : shovel;
    }
}
