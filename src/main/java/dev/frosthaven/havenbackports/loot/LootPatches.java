package dev.frosthaven.havenbackports.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frosthaven.havenbackports.HavenBackports;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * 26.x's changes to vanilla loot tables, applied on top of the table that wins at load (Mojang's, or another mod's or
 * datapack's version), so nothing another mod puts in the table is lost. tools/lootpatch.py writes one patch per
 * table to data/haven_backports/loot_patches and documents the format; its {@code apply} is this class's twin.
 *
 * <p>The table is encoded to JSON with its own codec, patched, and decoded again: entries are compared in the codec's
 * form on both sides, so spelled-out defaults never hide a match. A table the codec cannot encode (a mod type
 * without an encoder) gets the additions only, as pools of their own, with a warning for the rest. Runs at LOWEST
 * priority: other mods' LootTableLoadEvent changes are in the table it patches.
 */
public final class LootPatches {
    private static final String DIR = "loot_patches";
    private static final Map<ResourceLocation, JsonObject> PATCHES = new HashMap<>();

    private LootPatches() {}

    public static void register() {
        Path root = ModList.get().getModFileById(HavenBackports.ID).getFile().findResource("data", HavenBackports.ID, DIR);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String rel = root.relativize(f).toString().replace('\\', '/');
                PATCHES.put(ResourceLocation.withDefaultNamespace(rel.substring(0, rel.length() - 5)),
                        JsonParser.parseString(Files.readString(f)).getAsJsonObject());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Haven Backports: cannot read " + root, ex);
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LootTableLoadEvent.class, e -> {
            JsonObject patch = PATCHES.get(e.getName());
            if (patch != null) {
                e.setTable(apply(e.getName(), e.getTable(), patch, e.getRegistries()));
            }
        });
    }

    /** The patched tables' ids (for a dev audit; see CLAUDE.md, Loot). */
    public static Iterable<ResourceLocation> tables() {
        return PATCHES.keySet();
    }

    /** Applies the patch of table {@code id} to {@code table}; returns the table to load (new, or {@code table}). */
    public static LootTable apply(ResourceLocation id, LootTable table, HolderLookup.Provider registries) {
        JsonObject patch = PATCHES.get(id);
        return patch == null ? table : apply(id, table, patch, registries);
    }

    static LootTable apply(ResourceLocation id, LootTable table, JsonObject patch, HolderLookup.Provider registries) {
        Canon canon = Canon.of(registries);
        JsonObject json;
        try {
            json = LootTable.DIRECT_CODEC.encodeStart(canon.encodeOps(), table).getOrThrow().getAsJsonObject();
        } catch (RuntimeException ex) {
            HavenBackports.LOG.warn("Loot patch {}: the loaded table cannot be encoded ({}); only 26.x's additions are "
                    + "added, as pools of their own", id, ex.getMessage());
            addOnly(id, table, patch, canon);
            return table;
        }
        List<JsonObject> pools = new ArrayList<>();
        json.getAsJsonArray("pools").forEach(p -> pools.add(p.getAsJsonObject()));
        JsonArray wanted = patch.getAsJsonArray("pools");
        Map<Integer, Integer> where = PoolMatch.match(wanted, pools);
        List<JsonObject> extra = new ArrayList<>();   // fallback pools of unmatched patch pools
        for (int i = 0; i < wanted.size(); i++) {
            JsonObject pp = wanted.get(i).getAsJsonObject();
            Integer t = where.get(i);
            if (t == null) {
                unmatched(id, i, pp, canon, extra);
            } else {
                PoolEdit.edit(id, i, pools.get(t), pp, canon);
            }
        }
        JsonArray out = new JsonArray();
        int n = 0;
        n = addPools(out, patch, -1, canon, pools, id, n);
        for (int t = 0; t < pools.size(); t++) {
            out.add(pools.get(t));
            for (Map.Entry<Integer, Integer> w : where.entrySet()) {
                if (w.getValue() == t) {
                    n = addPools(out, patch, w.getKey(), canon, pools, id, n);
                }
            }
        }
        for (int after : PoolMatch.unplaced(patch, where)) {
            n = addPools(out, patch, after, canon, pools, id, n);
        }
        for (JsonObject fb : extra) {
            fb.addProperty("name", HavenBackports.ID + ":" + n++);
            out.add(fb);
        }
        json.add("pools", out);
        LootTable patched = LootTable.DIRECT_CODEC.parse(canon.ops(), json).getOrThrow(
                msg -> new IllegalStateException("Loot patch " + id + ": the patched table does not decode: " + msg));
        patched.setLootTableId(id);
        return patched;
    }

    /** Appends the add_pools anchored after patch pool {@code after}, skipping a pool the table already has. */
    private static int addPools(JsonArray out, JsonObject patch, int after, Canon canon, List<JsonObject> pools,
                                ResourceLocation id, int n) {
        if (!patch.has("add_pools")) {
            return n;
        }
        for (JsonElement a : patch.getAsJsonArray("add_pools")) {
            if (a.getAsJsonObject().get("after").getAsInt() != after) {
                continue;
            }
            JsonObject pool = canon.pool(a.getAsJsonObject().getAsJsonObject("pool"));
            if (pools.stream().anyMatch(p -> Canon.unnamed(p).equals(pool))) {
                HavenBackports.LOG.info("Loot patch {}: the table already has 26.x's new pool {}; not added twice", id, pool);
                continue;
            }
            pool.addProperty("name", HavenBackports.ID + ":" + n++);
            out.add(pool);
        }
        return n;
    }

    private static void unmatched(ResourceLocation id, int i, JsonObject pp, Canon canon, List<JsonObject> extra) {
        if (pp.has("fallback")) {
            HavenBackports.LOG.warn("Loot patch {}: no pool holds the entries of 1.21.1's pool {} ({}); 26.x's additions "
                    + "to it are added as a pool of their own with 26.x's rolls", id, i, pp.get("match"));
            extra.add(canon.pool(pp.getAsJsonObject("fallback")));
        }
        for (String op : new String[] {"remove", "change", "set"}) {
            if (pp.has(op)) {
                HavenBackports.LOG.warn("Loot patch {}: 1.21.1's pool {} is gone, so 26.x's '{}' on it is not applied: {}",
                        id, i, op, pp.get(op));
            }
        }
    }

    private static void addOnly(ResourceLocation id, LootTable table, JsonObject patch, Canon canon) {
        List<JsonObject> pools = new ArrayList<>();
        for (JsonElement p : patch.getAsJsonArray("pools")) {
            unmatched(id, pools.size(), p.getAsJsonObject(), canon, pools);
        }
        if (patch.has("add_pools")) {
            patch.getAsJsonArray("add_pools").forEach(a -> pools.add(canon.pool(a.getAsJsonObject().getAsJsonObject("pool"))));
        }
        int n = 0;
        for (JsonObject p : pools) {
            p.addProperty("name", HavenBackports.ID + ":" + n++);
            table.addPool(LootPool.CODEC.parse(canon.ops(), p).getOrThrow());
        }
    }
}
