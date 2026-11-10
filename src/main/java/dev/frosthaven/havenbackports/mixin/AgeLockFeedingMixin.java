package dev.frosthaven.havenbackports.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.x Animal.mobInteract feeds a baby only if canAgeUp (1.21.1: isBaby), so food does not grow a locked baby. The
 *  one isBaby() call in the method is that check. (Panda's own feeding: AgeLockPandaMixin.) */
@Mixin(Animal.class)
abstract class AgeLockFeedingMixin {
    /** The golden dandelion is no animal's food in 26.x (its #bee_food leaves it out), but 1.21.1's #bee_food is
     *  #flowers, which holds it through #small_flowers: the dandelion goes to the age lock first, and a bee that
     *  cannot take it ignores it rather than eat it. */
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void haven_backports$dandelionFirst(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (AgeLock.isGoldenDandelion(player.getItemInHand(hand))) {
            InteractionResult result = AgeLock.interact((Animal) (Object) this, player, hand);
            if (result != null) {
                cir.setReturnValue(result);
            } else if ((Object) this instanceof Bee) {
                cir.setReturnValue(InteractionResult.PASS);
            }
        }
    }

    @WrapOperation(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Animal;isBaby()Z"))
    private boolean haven_backports$canAgeUp(Animal self, Operation<Boolean> original) {
        return original.call(self) && !AgeLock.isAgeLocked(self);
    }
}
