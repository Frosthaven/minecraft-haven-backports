package dev.frosthaven.havenbackports.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21.1's protected LivingEntity.getKnockback, which 26.x's stab attacks call (spear/Stab). */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Invoker("getKnockback")
    float haven_backports$getKnockback(Entity attacker, DamageSource damageSource);
}
