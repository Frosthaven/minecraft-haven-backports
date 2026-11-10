package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x AgeableMob.mobInteract (1.21.1's AgeableMob has none, so its subclasses' super calls end in Mob.mobInteract):
 * a golden dandelion on a baby toggles its age lock. Only AgeableMobs; every other mob gets 1.21.1's result.
 */
@Mixin(Mob.class)
abstract class AgeLockMobMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void haven_backports$goldenDandelion(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if ((Object) this instanceof AgeableMob mob) {
            InteractionResult result = AgeLock.interact(mob, player, hand);
            if (result != null) {
                cir.setReturnValue(result);
            }
        }
    }
}
