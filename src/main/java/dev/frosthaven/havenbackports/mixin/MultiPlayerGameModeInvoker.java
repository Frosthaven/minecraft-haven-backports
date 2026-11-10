package dev.frosthaven.havenbackports.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21.1's private ensureHasSentCarriedItem: 26.x piercingAttack sends the selected slot before the jab. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeInvoker {
    @Invoker("ensureHasSentCarriedItem")
    void haven_backports$ensureHasSentCarriedItem();
}
