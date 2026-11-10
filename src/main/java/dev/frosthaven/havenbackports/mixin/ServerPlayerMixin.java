package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Ghasts;
import dev.frosthaven.havenbackports.ghast.HappyGhast;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x ServerPlayer.checkRidingStatistics counts distance on a happy ghast (Stats.HAPPY_GHAST_ONE_CM); 1.21.1 lists its vehicles by class. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "checkRidingStatistics", at = @At("TAIL"))
    private void haven_backports$happyGhast(double dx, double dy, double dz, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isPassenger() && self.getVehicle() instanceof HappyGhast && (dx * dx + dy * dy + dz * dz) >= 1.0E-5F) {
            self.awardStat(Stats.CUSTOM.get(Ghasts.HAPPY_GHAST_ONE_CM.get()), Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F));
        }
    }
}
