package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.frosthaven.havenbackports.spear.SpearItem;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.monster.piglin.Piglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.x MeleeAttack skips melee while the mob holds a weapon it can use otherwise (canUseNonMeleeWeapon); 26.x Piglin
 *  counts any KINETIC_WEAPON, so a spear piglin only charges. */
@Mixin(MeleeAttack.class)
abstract class MeleeAttackMixin {
    @ModifyReturnValue(method = "isHoldingUsableProjectileWeapon", at = @At("RETURN"))
    private static boolean haven_backports$spear(boolean holding, Mob mob) {
        return holding || mob instanceof Piglin && mob.isHolding(stack -> stack.getItem() instanceof SpearItem);
    }
}
