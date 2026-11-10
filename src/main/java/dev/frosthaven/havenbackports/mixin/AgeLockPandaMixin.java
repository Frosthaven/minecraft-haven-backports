package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.x Panda.mobInteract: where 1.21.1 ends with PASS (not scared, not on its back, no bamboo in hand), a baby with a
 *  golden dandelion in either hand goes to Animal.mobInteract (which reaches the age lock). */
@Mixin(Panda.class)
abstract class AgeLockPandaMixin extends Animal {
    protected AgeLockPandaMixin(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    @Shadow
    public abstract boolean isScared();

    @Shadow
    public abstract boolean isOnBack();

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void haven_backports$goldenDandelion(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!this.isScared() && !this.isOnBack() && !this.isFood(player.getItemInHand(hand)) && this.isBaby()
                && player.isHolding(AgeLock::isGoldenDandelion)) {
            cir.setReturnValue(super.mobInteract(player, hand));
        }
    }

    /** 26.x Panda feeds a baby bamboo only if canAgeUp (1.21.1: isBaby; the one isBaby() call in mobInteract). */
    @WrapOperation(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Panda;isBaby()Z"))
    private boolean haven_backports$canAgeUp(Panda self, Operation<Boolean> original) {
        return original.call(self) && !AgeLock.isAgeLocked(self);
    }
}
