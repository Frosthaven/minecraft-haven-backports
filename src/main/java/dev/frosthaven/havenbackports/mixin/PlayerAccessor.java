package dev.frosthaven.havenbackports.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.1's private Player.currentImpulseContextResetGraceTime: 26.x applyPostImpulseGraceTime (Lunge's impulse). */
@Mixin(Player.class)
public interface PlayerAccessor {
    @Accessor("currentImpulseContextResetGraceTime")
    int haven_backports$graceTime();

    @Accessor("currentImpulseContextResetGraceTime")
    void haven_backports$setGraceTime(int ticks);
}
