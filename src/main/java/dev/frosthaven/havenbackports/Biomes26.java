package dev.frosthaven.havenbackports;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * Where 26.x's OverworldBiomeBuilder puts the backported biomes, as table cells of 1.21.1's builder (which
 * OverworldBiomeBuilderMixin edits). Mojang's indices: temperature 0..4 (frozen..hot), humidity 0..4 (arid..humid).
 */
public final class Biomes26 {
    public static final ResourceKey<Biome> DAPPLED_FOREST = key("dappled_forest");
    public static final ResourceKey<Biome> SULFUR_CAVES = key("sulfur_caves");

    private Biomes26() {}

    /** 26.x OverworldBiomeBuilder: MIDDLE_BIOMES_VARIANT[1][0] = DAPPLED_FOREST (1.21.1: null, so plains). */
    public static void tables(ResourceKey<Biome>[][] middleVariant) {
        middleVariant[1][0] = DAPPLED_FOREST;
    }

    /** True while 1.21.1's built-in registry set is being built (it holds no biome of ours). */
    public static boolean inVanillaBootstrap() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(f -> f.getClassName().equals("net.minecraft.core.RegistrySetBuilder")));
    }

    private static ResourceKey<Biome> key(String id) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, id));
    }
}
