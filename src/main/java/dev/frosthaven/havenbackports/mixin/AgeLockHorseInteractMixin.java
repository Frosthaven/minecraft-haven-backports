package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.x Horse and AbstractChestedHorse mobInteract: a baby with a golden dandelion in either hand skips the food,
 *  temper and chest branches and goes to AbstractHorse.mobInteract (whose baby path reaches the age lock), so the
 *  dandelion does not make it mad. */
@Mixin({Horse.class, AbstractChestedHorse.class})
abstract class AgeLockHorseInteractMixin extends AbstractHorse {
    protected AgeLockHorseInteractMixin(EntityType<? extends AbstractHorse> type, Level level) {
        super(type, level);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void haven_backports$goldenDandelion(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (this.isBaby() && player.isHolding(AgeLock::isGoldenDandelion)) {
            cir.setReturnValue(super.mobInteract(player, hand));
        }
    }
}
