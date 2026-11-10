package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.x Camel.mobInteract: after the item and food branches, a baby with a golden dandelion in either hand goes to
 *  AbstractHorse.mobInteract (which reaches the age lock) instead of 1.21.1's plain success. Injected where 1.21.1
 *  starts that last branch (the ride check, which a baby never passes). */
@Mixin(Camel.class)
abstract class AgeLockCamelMixin extends AbstractHorse {
    protected AgeLockCamelMixin(EntityType<? extends AbstractHorse> type, Level level) {
        super(type, level);
    }

    @Inject(method = "mobInteract", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/camel/Camel;getPassengers()Ljava/util/List;"))
    private void haven_backports$goldenDandelion(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (this.isBaby() && player.isHolding(AgeLock::isGoldenDandelion)) {
            cir.setReturnValue(super.mobInteract(player, hand));
        }
    }

    /** 26.x Camel.handleEating ages a baby up only if canAgeUp (1.21.1: isBaby; the one isBaby() call there). */
    @WrapOperation(method = "handleEating", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/camel/Camel;isBaby()Z"))
    private boolean haven_backports$canAgeUp(Camel self, Operation<Boolean> original) {
        return original.call(self) && !AgeLock.isAgeLocked(self);
    }
}
