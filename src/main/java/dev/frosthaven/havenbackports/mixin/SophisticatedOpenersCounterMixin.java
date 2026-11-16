package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havenbackports.golem.CopperGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Not Mojang's. Sophisticated Storage's chests count their openers in their own copy of recheckOpeners, which
 * {@link ContainerOpenersCounterMixin} cannot reach; the same addition there makes their lid open for a copper golem.
 * Skipped when the mod is absent; require = 0, so a changed method costs only the lid animation.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.block.SophisticatedOpenersCounter", remap = false)
abstract class SophisticatedOpenersCounterMixin {
    @ModifyExpressionValue(method = "recheckOpeners", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"), require = 0)
    private int haven_backports$copperGolems(int players, Level level, BlockPos pos, BlockState state) {
        return players + CopperGolem.countWithContainerOpen(level, pos, 5.0);
    }
}
