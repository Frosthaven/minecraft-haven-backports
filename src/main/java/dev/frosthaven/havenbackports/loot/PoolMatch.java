package dev.frosthaven.havenbackports.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds each 1.21.1 pool of a patch in the loaded table by content, not index (tools/lootpatch.py match and key): the
 * pool sharing the most entry keys (type and name) wins, ties go to the nearest index, and a pool is claimed once.
 */
final class PoolMatch {
    private PoolMatch() {}

    static Map<Integer, Integer> match(JsonArray wanted, List<JsonObject> pools) {
        List<int[]> pairs = new ArrayList<>();   // {-shared, distance, i, t}
        for (int i = 0; i < wanted.size(); i++) {
            Set<String> w = new HashSet<>();
            wanted.get(i).getAsJsonObject().getAsJsonArray("match").forEach(k -> w.add(k.getAsString()));
            for (int t = 0; t < pools.size(); t++) {
                Set<String> shared = keys(pools.get(t));
                shared.retainAll(w);
                if (!shared.isEmpty()) {
                    pairs.add(new int[] {-shared.size(), Math.abs(i - t), i, t});
                }
            }
        }
        pairs.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0])
                : a[1] != b[1] ? Integer.compare(a[1], b[1]) : a[2] != b[2] ? Integer.compare(a[2], b[2]) : Integer.compare(a[3], b[3]));
        Map<Integer, Integer> out = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        for (int[] p : pairs) {
            if (!out.containsKey(p[2]) && !used.contains(p[3])) {
                out.put(p[2], p[3]);
                used.add(p[3]);
            }
        }
        return out;
    }

    /** Anchors of add_pools whose patch pool matched nothing: those pools go at the end. */
    static Set<Integer> unplaced(JsonObject patch, Map<Integer, Integer> where) {
        Set<Integer> out = new LinkedHashSet<>();
        if (patch.has("add_pools")) {
            for (JsonElement a : patch.getAsJsonArray("add_pools")) {
                int after = a.getAsJsonObject().get("after").getAsInt();
                if (after != -1 && !where.containsKey(after)) {
                    out.add(after);
                }
            }
        }
        return out;
    }

    static Set<String> keys(JsonObject pool) {
        Set<String> out = new HashSet<>();
        pool.getAsJsonArray("entries").forEach(e -> out.add(key(e.getAsJsonObject())));
        return out;
    }

    static String key(JsonObject e) {
        JsonElement name = e.has("name") ? e.get("name") : e.get("value");
        StringBuilder b = new StringBuilder(e.has("type") ? e.get("type").getAsString() : "").append(' ');
        if (name != null && name.isJsonPrimitive()) {
            b.append(name.getAsString());
        }
        if (e.has("children")) {
            e.getAsJsonArray("children").forEach(c -> b.append(" (").append(key(c.getAsJsonObject())).append(')'));
        }
        return b.toString();
    }
}
