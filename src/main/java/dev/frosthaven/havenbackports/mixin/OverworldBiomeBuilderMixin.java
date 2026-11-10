package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Biomes26;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Climate;
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

    @Shadow
    @Final
    private Climate.Parameter FULL_RANGE;
    @Shadow
    @Final
    private Climate.Parameter[] erosions;
    @Shadow
    @Final
    private Climate.Parameter coastContinentalness;
    @Shadow
    @Final
    private Climate.Parameter inlandContinentalness;

    @Shadow
    private void addUndergroundBiome(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, Climate.Parameter temperature,
            Climate.Parameter humidity, Climate.Parameter continentalness, Climate.Parameter erosion, Climate.Parameter weirdness, float offset,
            ResourceKey<Biome> biome) {
        throw new AssertionError();
    }

    /** 26.x addUndergroundBiomes: the Sulfur Caves point comes after lush caves and before the deep dark. */
    @Inject(method = "addUndergroundBiomes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/OverworldBiomeBuilder;addBottomBiome(Ljava/util/function/Consumer;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;FLnet/minecraft/resources/ResourceKey;)V"))
    private void haven_backports$underground(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, CallbackInfo ci) {
        if (!Biomes26.inVanillaBootstrap()) {
            // 26.x: temperature full; humidity -1..0.7; continentalness coast..inland; erosion[5]..[6]; weirdness -1.1..-0.85
            this.addUndergroundBiome(consumer, this.FULL_RANGE, Climate.Parameter.span(-1.0F, 0.7F),
                    Climate.Parameter.span(this.coastContinentalness, this.inlandContinentalness),
                    Climate.Parameter.span(this.erosions[5], this.erosions[6]), Climate.Parameter.span(-1.1F, -0.85F), 0.0F, Biomes26.SULFUR_CAVES);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void haven_backports$tables(CallbackInfo ci) {
        if (!Biomes26.inVanillaBootstrap()) {
            Biomes26.tables(this.MIDDLE_BIOMES_VARIANT);
        }
    }
}
