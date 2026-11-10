package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Ghasts;
import dev.frosthaven.havenbackports.ghast.HappyGhast;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import dev.frosthaven.havenbackports.StrawBeds;
import dev.frosthaven.havenbackports.block.StrawBedBlock;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x ServerPlayer.checkRidingStatistics counts distance on a happy ghast (Stats.HAPPY_GHAST_ONE_CM); 1.21.1 lists its
 * vehicles by class. 26.x startSleepInBed counts the bed's own stat (a straw bed: sleep_in_straw_bed, not
 * sleep_in_bed) and fires the slept-in-bed trigger only for a bed that sets the spawn (not a straw bed).
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "checkRidingStatistics", at = @At("TAIL"))
    private void haven_backports$happyGhast(double dx, double dy, double dz, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isPassenger() && self.getVehicle() instanceof HappyGhast && (dx * dx + dy * dy + dz * dz) >= 1.0E-5F) {
            self.awardStat(Stats.CUSTOM.get(Ghasts.HAPPY_GHAST_ONE_CM.get()), Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F));
        }
    }

    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lcom/mojang/datafixers/util/Either;ifRight(Ljava/util/function/Consumer;)Lcom/mojang/datafixers/util/Either;"))
    private <L, R> Either<L, R> haven_backports$strawBedStat(Either<L, R> result, Consumer<? super R> awards, Operation<Either<L, R>> original, BlockPos at) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.level().getBlockState(at).getBlock() instanceof StrawBedBlock) {
            return result.ifRight(unit -> self.awardStat(StrawBeds.SLEEP_IN_STRAW_BED.get()));
        }
        return original.call(result, awards);
    }
}
