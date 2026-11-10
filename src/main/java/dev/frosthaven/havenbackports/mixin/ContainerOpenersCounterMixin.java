package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havenbackports.golem.CopperGolem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x ContainerOpenersCounter counts every ContainerUser with the container open: players and copper golems. 1.21.1
 * counts players only. When it recounts (recheckOpeners: on a golem's open and close, and every 5 ticks while open),
 * the copper golems that have this chest open are added, found in the same box 26.x searches; one entity query.
 */
@Mixin(ContainerOpenersCounter.class)
abstract class ContainerOpenersCounterMixin {
    @Shadow
    private double maxInteractionRange;

    @ModifyExpressionValue(method = "recheckOpeners", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int haven_backports$copperGolems(int players, Level level, BlockPos pos, BlockState state) {
        List<CopperGolem> golems = level.getEntitiesOfClass(CopperGolem.class, new AABB(pos).inflate(Math.max(this.maxInteractionRange, 3.0) + 4.0),
                g -> !g.isSpectator() && g.hasContainerOpen(pos));
        return players + golems.size();
    }
}
