package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Biomes26;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mojang's own placement of the newer biomes, which 1.21.1 hard-codes in OverworldBiomeBuilder's tables (26.x
 * OverworldBiomeBuilder). The vanilla overworld preset and TerraBlender's vanilla region both build their parameter
 * list with this class, so the biomes appear in new chunks of every world (saved chunks keep their biomes) and
 * /locate and Nature's Compass find them (they read the biome source). Each change is one of 26.x's table cells:
 * see {@link Biomes26}. Not inside VanillaRegistries' bootstrap (RegistrySetBuilder: 1.21.1's built-in registries, used
 * for command validation and debug lists): that set has only 1.21.1's biomes, so it keeps 1.21.1's tables.
 */
@Mixin(OverworldBiomeBuilder.class)
abstract class OverworldBiomeBuilderMixin {
    @Shadow
    @Final
    private ResourceKey<Biome>[][] MIDDLE_BIOMES_VARIANT;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void haven_backports$tables(CallbackInfo ci) {
        if (!Biomes26.inVanillaBootstrap()) {
            Biomes26.tables(this.MIDDLE_BIOMES_VARIANT);
        }
    }
}
