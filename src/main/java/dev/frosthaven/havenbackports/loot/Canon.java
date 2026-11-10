package dev.frosthaven.havenbackports.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;

/**
 * Loot JSON in the codec's own form: decoded and encoded again through LootPool.CODEC, so a patch's entry and a
 * loaded table's entry compare equal when the game reads them the same (defaults, 1 vs 1.0, constant providers).
 *
 * <p>{@code ops} decodes, with the lookup the loot load uses (1.21.1's ReloadableServerRegistries wraps each registry
 * in a tag-adding {@link HolderLookup.RegistryLookup.Delegate}). {@code encodeOps} encodes: holders belong to the
 * registry itself, and a holder only serializes with its own owner, so it uses each Delegate's parent.
 */
record Canon(RegistryOps<JsonElement> ops, RegistryOps<JsonElement> encodeOps) {
    static Canon of(HolderLookup.Provider registries) {
        HolderLookup.Provider owners = new HolderLookup.Provider() {
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
                return registries.listRegistries();
            }

            @Override
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return registries.lookup(key).map(l -> l instanceof HolderLookup.RegistryLookup.Delegate<T> d ? d.parent() : l);
            }
        };
        return new Canon(registries.createSerializationContext(JsonOps.INSTANCE), owners.createSerializationContext(JsonOps.INSTANCE));
    }

    JsonObject pool(JsonObject pool) {
        LootPool decoded = LootPool.CODEC.parse(ops, pool.deepCopy()).getOrThrow(
                msg -> new IllegalStateException("Loot patch pool does not decode: " + msg + " in " + pool));
        return LootPool.CODEC.encodeStart(encodeOps, decoded).getOrThrow().getAsJsonObject();
    }

    JsonElement entry(JsonElement entry) {
        JsonObject p = new JsonObject();
        p.addProperty("rolls", 1);
        JsonArray entries = new JsonArray();
        entries.add(entry.deepCopy());
        p.add("entries", entries);
        return pool(p).getAsJsonArray("entries").get(0);
    }

    /** A pool field's value in the codec's form; null when it is absent or the default (the codec leaves it out). */
    JsonElement field(String key, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        JsonObject p = new JsonObject();
        p.addProperty("rolls", 1);
        p.add("entries", new JsonArray());
        p.add(key, value.deepCopy());
        return pool(p).get(key);
    }

    static JsonObject unnamed(JsonObject pool) {
        JsonObject copy = pool.deepCopy();
        copy.remove("name");
        return copy;
    }
}
