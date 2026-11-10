package dev.frosthaven.havenbackports.mixin;

import com.google.common.collect.ImmutableList;
import dev.frosthaven.havenbackports.spear.SpearBehaviors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 26.x PiglinAi.initFightActivity: SpearApproach(1.0, 10), SpearAttack(1.0, 1.0, 2), SpearRetreat(1.0) right after
 *  SetWalkTargetFromAttackTargetIfTargetOutOfReach (the third behavior). */
@Mixin(PiglinAi.class)
abstract class PiglinAiMixin {
    @ModifyArg(method = "initFightActivity", index = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;addActivityAndRemoveMemoryWhenStopped(Lnet/minecraft/world/entity/schedule/Activity;ILcom/google/common/collect/ImmutableList;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)V"))
    private static ImmutableList<? extends BehaviorControl<? super Piglin>> haven_backports$spearBehaviors(
            ImmutableList<? extends BehaviorControl<? super Piglin>> behaviors) {
        List<BehaviorControl<? super Piglin>> out = new ArrayList<>(behaviors);
        out.addAll(3, List.of(new SpearBehaviors.Approach(1.0, 10.0F), new SpearBehaviors.Attack(1.0, 1.0, 2.0F), new SpearBehaviors.Retreat(1.0)));
        return ImmutableList.copyOf(out);
    }
}
