package dev.frosthaven.havenbackports.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frosthaven.havenbackports.HavenBackports;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * One patch pool applied to the pool it matched (tools/lootpatch.py apply): `remove` and `change` touch only an entry
 * equal to 1.21.1's, `set` only a field that still has 1.21.1's value, and `add` never removes anything. Each change
 * that cannot apply is logged with the table and pool; another mod's entries always stay.
 */
final class PoolEdit {
    private PoolEdit() {}

    static void edit(ResourceLocation id, int i, JsonObject pool, JsonObject pp, Canon canon) {
        List<JsonElement> entries = new ArrayList<>();
        pool.getAsJsonArray("entries").forEach(entries::add);
        for (JsonElement e : list(pp, "remove")) {
            if (!entries.remove(canon.entry(e))) {
                skip(id, i, "removal", e);
            }
        }
        for (JsonElement c : list(pp, "change")) {
            int at = entries.indexOf(canon.entry(c.getAsJsonObject().get("from")));
            if (at < 0) {
                skip(id, i, "change", c);
            } else {
                entries.set(at, canon.entry(c.getAsJsonObject().get("to")));
            }
        }
        for (JsonElement a : list(pp, "add")) {
            JsonElement entry = canon.entry(a.getAsJsonObject().get("entry"));
            if (entries.contains(entry)) {
                continue;   // the table already holds this exact entry (a mod with the same idea): not added twice
            }
            JsonElement after = a.getAsJsonObject().get("after");
            int found = after.isJsonNull() ? -1 : entries.indexOf(canon.entry(after));
            entries.add(after.isJsonNull() ? 0 : found < 0 ? entries.size() : found + 1, entry);   // absent: at the end
        }
        JsonArray out = new JsonArray();
        entries.forEach(out::add);
        pool.add("entries", out);
        if (pp.has("set")) {
            for (Map.Entry<String, JsonElement> s : pp.getAsJsonObject("set").entrySet()) {
                JsonObject fromTo = s.getValue().getAsJsonObject();
                JsonElement from = canon.field(s.getKey(), fromTo.get("from"));
                JsonElement now = pool.get(s.getKey());
                if (from == null ? now != null : !from.equals(now)) {
                    skip(id, i, "'" + s.getKey() + "' change", fromTo);
                    continue;
                }
                JsonElement to = canon.field(s.getKey(), fromTo.get("to"));
                if (to == null) {
                    pool.remove(s.getKey());
                } else {
                    pool.add(s.getKey(), to);
                }
            }
        }
    }

    private static List<JsonElement> list(JsonObject pp, String key) {
        List<JsonElement> out = new ArrayList<>();
        if (pp.has(key)) {
            pp.getAsJsonArray(key).forEach(out::add);
        }
        return out;
    }

    private static void skip(ResourceLocation id, int i, String what, JsonElement change) {
        HavenBackports.LOG.warn("Loot patch {}: pool {} no longer holds 1.21.1's entry or value, so 26.x's {} is not "
                + "applied (the loaded table's content stays): {}", id, i, what, change);
    }
}
