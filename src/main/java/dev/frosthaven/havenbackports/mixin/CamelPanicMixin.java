package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.husk.CamelHusk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.AnimalPanic;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.camel.CamelAi;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.x CamelAi.CamelPanic.checkExtraStartConditions: a camel does not panic while a mob rides in front of it
 * (isMobControlled; in 26.x only the camel husk can be). 1.21.1's CamelPanic has no such check, so this adds it.
 */
@Mixin(CamelAi.CamelPanic.class)
abstract class CamelPanicMixin extends AnimalPanic<Camel> {
    private CamelPanicMixin(float speedMultiplier) {
        super(speedMultiplier);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Camel camel) {
        return super.checkExtraStartConditions(level, camel) && !(camel instanceof CamelHusk husk && husk.isMobControlled());
    }
}
