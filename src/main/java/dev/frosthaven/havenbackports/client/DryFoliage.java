package dev.frosthaven.havenbackports.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frosthaven.havenbackports.HavenBackports;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.LegacyStuffWrapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;

/**
 * 26.x's dry foliage tint (BlockTintSources.dryFoliage, Biome.getDryFoliageColor, DryFoliageColor), which 1.21.1
 * lacks: a biome's dry_foliage_color, else Mojang's dry_foliage.png colormap at the biome's clamped temperature and
 * downfall (26.x default -10732494 off the map). The overrides are 26.x's biome files' (tools/biomes.py writes
 * dry_foliage_colors.json); 1.21.1 biomes carry no such field. A ColorResolver, so biome blending applies as for grass.
 */
public final class DryFoliage implements ResourceManagerReloadListener {
    static final int DEFAULT = -10732494;
    private static final ResourceLocation MAP = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/colormap/dry_foliage.png");
    private static final ResourceLocation TABLE = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "dry_foliage_colors.json");
    private static int[] pixels = new int[65536];
    private static final Map<ResourceLocation, Integer> OVERRIDES = new HashMap<>();

    public static final ColorResolver RESOLVER = (biome, x, z) -> color(biome);

    static int color(Biome biome) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            ResourceLocation id = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
            Integer o = id == null ? null : OVERRIDES.get(id);
            if (o != null) {
                return o;
            }
        }
        var climate = biome.getModifiedClimateSettings();
        double temp = Mth.clamp(climate.temperature(), 0.0F, 1.0F);
        double rain = Mth.clamp(climate.downfall(), 0.0F, 1.0F);
        return get(temp, rain);
    }

    /** 26.x ColorMapColorUtil.get (1.21.1 FoliageColor.get's arithmetic) over the dry foliage map. */
    static int get(double temp, double rain) {
        rain *= temp;
        int x = (int) ((1.0 - temp) * 255.0);
        int y = (int) ((1.0 - rain) * 255.0);
        int index = y << 8 | x;
        return index >= pixels.length ? DEFAULT : pixels[index];
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        try {
            pixels = LegacyStuffWrapper.getPixels(manager, MAP);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load dry foliage color texture", e);
        }
        OVERRIDES.clear();
        manager.getResource(TABLE).ifPresent(r -> {
            try (Reader in = new InputStreamReader(r.open())) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                o.entrySet().forEach(e -> OVERRIDES.put(ResourceLocation.parse(e.getKey()), e.getValue().getAsInt() | 0xFF000000));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + TABLE, e);
            }
        });
    }
}
