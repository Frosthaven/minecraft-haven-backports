"""Biomes from 26.x in 1.21.1's format: biome JSON, their features (worldgen.py), surface rules, noises, tags, lang and
the client's dry foliage tints. Called from Port.run.

- Biome JSON: 26.x environment attributes -> 1.21.1 `effects` (fog, sky and water fog colors; music; the mood sound,
  which 26.x moved to the dimension type, from 26.x's overworld dimension type), `natural_mob_spawns` -> `spawners`
  (a count or uniform count -> minCount / maxCount), carvers -> {"air": [...]}. A 26.x attribute with no 1.21.1 form
  stops the run. `dry_foliage_color` has no 1.21.1 field: it goes to the client's dry foliage tint table.
- Surface rules: 1.21.1 keeps them inside noise_settings; 26.x moved them to worldgen/material_rule. Each biome's rule
  is taken from 26.x's tree (named rules inlined) and put in 1.21.1's own overworld, large_biomes and amplified noise
  settings at the place 26.x has it (next to the same vanilla neighbour). 26.x's 3D noise_threshold (`is_3d`) is
  Haven Backports' noise_threshold_3d: 1.21.1's noise_threshold samples at y = 0.
- Tags: Mojang's biome tags come through data.py's tag pass (1.21.1's tags only); the common `c:` tags are those
  NeoForge gives each biome's vanilla analog (ANALOG_C), so other mods' spawns and features treat them alike.
"""
import json
import sys

from worldgen import Worldgen

NS = "haven_backports"
# Biomes ported, each with: the vanilla biome whose NeoForge `c:` tags it shares (direct entries; c:is_forest,
# c:is_overworld and c:is_underground follow from the Mojang tags they reference), and where its surface rules go.
BIOMES = {
    "dappled_forest": {
        "c": ["is_temperate/overworld", "is_tree/deciduous"],   # as minecraft:forest
        # (26.x file, the biome's entry, 1.21.1 anchor: insert after the entry for that vanilla biome)
        "surface": [("overworld/biome_surface", "after", "minecraft:mushroom_fields")],
        "noises": ["small_patch"],
        "configured": ["red_poplar", "orange_poplar", "yellow_poplar"],   # TreeGrower.POPLAR (the sapling)
    },
}
NOISE_SETTINGS = ["overworld", "large_biomes", "amplified"]   # the overworld presets sharing 1.21.1's surface rule


def colour(v):
    return int(v[1:], 16) if isinstance(v, str) else v


class Biomes:
    def __init__(self, port, res, lang_new, lang):
        self.port, self.res, self.lang_new, self.lang = port, res, lang_new, lang
        self.wg = Worldgen(port, res)
        self.dimension = port.read("data/minecraft/dimension_type/overworld.json")["attributes"]

    def run(self):
        for b in BIOMES:
            self.biome(b)
            self.lang[f"biome.{NS}.{b}"] = self.lang_new[f"biome.minecraft.{b}"]
            for f in BIOMES[b].get("configured", []):
                self.wg.configured(f"minecraft:{f}")
            for n in BIOMES[b].get("noises", []):
                self.wg.noise(n, "minecraft")   # Mojang's key: the noise is seeded from it
        self.surface_rules()
        self.c_tags()
        self.dry_foliage()
        if self.wg.differs:
            print(f"worldgen kept at 1.21.1 where 26.x differs: {sorted(self.wg.differs)}")

    # ---------- biome ----------
    def biome(self, name):
        raw = self.port.read(f"data/minecraft/worldgen/biome/{name}.json")
        attrs = dict(raw.get("attributes", {}))
        effects = {}
        for k, v in raw.get("effects", {}).items():
            if k == "dry_foliage_color":
                continue
            effects[k] = colour(v)
        dim = self.dimension
        effects["fog_color"] = colour(attrs.pop("minecraft:visual/fog_color", dim["minecraft:visual/fog_color"]))
        effects["sky_color"] = colour(attrs.pop("minecraft:visual/sky_color", dim["minecraft:visual/sky_color"]))
        if "minecraft:visual/water_fog_color" not in attrs:
            sys.exit(f"biome {name}: no water fog color; find 26.x's default first")
        effects["water_fog_color"] = colour(attrs.pop("minecraft:visual/water_fog_color"))
        mood = dim["minecraft:audio/ambient_sounds"]["mood"]
        effects["mood_sound"] = {"sound": mood["sound"], "tick_delay": mood["tick_delay"],
                                 "block_search_extent": mood["block_search_extent"], "offset": mood["offset"]}
        music = attrs.pop("minecraft:audio/background_music", None)
        volume = attrs.pop("minecraft:audio/music_volume", None)
        if music and "default" in music:
            m = music["default"]
            effects["music"] = {"sound": m["sound"], "min_delay": m["min_delay"], "max_delay": m["max_delay"],
                                "replace_current_music": False}
        elif music == {} and volume == 0.0:
            # 26.x: no biome music and music volume 0 (silence). 1.21.1 has no music volume: the nearest form is
            # music that replaces the current track with nothing (minecraft:intentionally_empty plays no sound).
            # 26.x fades the track out; 1.21.1 stops it.
            effects["music"] = {"sound": {"sound_id": "minecraft:intentionally_empty"}, "min_delay": 12000,
                                "max_delay": 24000, "replace_current_music": True}
        elif music is not None or volume is not None:
            sys.exit(f"biome {name}: music {music} / volume {volume} needs a 1.21.1 form")
        spawns = attrs.pop("minecraft:gameplay/natural_mob_spawns")["argument"]
        spawners = {}
        for cat, entries in spawns["spawns_by_category"].items():
            out = []
            for e in entries:
                c = e["count"]
                lo, hi = (c, c) if isinstance(c, int) else (c["min_inclusive"], c["max_inclusive"])
                out.append({"type": self.port.id(e["type"]), "weight": e["weight"], "minCount": lo, "maxCount": hi})
            spawners[cat] = out
        if attrs:
            sys.exit(f"biome {name}: attributes with no 1.21.1 form: {sorted(attrs)}")
        features = [[self.wg.placed_ref(f) for f in step] for step in raw["features"]]
        out = {"has_precipitation": raw["has_precipitation"], "temperature": raw["temperature"],
               "downfall": raw["downfall"]}
        if "temperature_modifier" in raw:
            out["temperature_modifier"] = raw["temperature_modifier"]
        out.update({"effects": dict(sorted(effects.items())), "spawners": spawners,
                    "spawn_costs": spawns.get("spawn_costs", {}), "carvers": {"air": raw["carvers"]},
                    "features": features})
        self.wg.write(f"biome/{name}", out)

    # ---------- surface rules ----------
    def rule(self, v):
        """A 26.x material rule or condition, named references inlined, in 1.21.1's surface rule format."""
        if isinstance(v, str):
            for kind in ("material_rule", "material_condition"):
                n = f"data/minecraft/worldgen/{kind}/{v.split(':')[-1]}.json"
                if n in self.port.new_names:
                    return self.rule(json.loads(self.port.jar.read(n)))
            sys.exit(f"surface rule reference {v} not found")
        if isinstance(v, list):
            return [self.rule(x) for x in v]
        if not isinstance(v, dict):
            return v
        v = dict(v)
        t = v.get("type")
        if t == "minecraft:biome":
            ids = v["biome_is"] if isinstance(v["biome_is"], list) else [v["biome_is"]]
            return {"type": t, "biome_is": [self.biome_id(b) for b in ids]}
        if t == "minecraft:block":
            state = v["result_state"]
            return {"type": t, "result_state": self.wg.state(state, "surface rule")}
        if t == "minecraft:noise_threshold":
            three_d = v.pop("is_3d", False)
            v["type"] = f"{NS}:noise_threshold_3d" if three_d else t
            return v
        return {k: (self.rule(x) if k in ("if_true", "then_run", "sequence", "invert") else x) for k, x in v.items()}

    @staticmethod
    def biome_id(b):
        return f"{NS}:{b.split(':')[-1]}" if b.split(":")[-1] in BIOMES else b

    def entry_for(self, file, biome):
        """The 26.x sequence entry `if biome is <biome>` in a material rule file."""
        seq = json.loads(self.port.jar.read(f"data/minecraft/worldgen/material_rule/{file}.json"))

        def find(v):
            if isinstance(v, dict):
                c = v.get("if_true")
                if v.get("type") == "minecraft:condition" and isinstance(c, dict) and c.get("type") == "minecraft:biome" \
                        and (c["biome_is"] == f"minecraft:{biome}" or c["biome_is"] == [f"minecraft:{biome}"]):
                    return v
                for x in v.values():
                    r = find(x)
                    if r:
                        return r
            elif isinstance(v, list):
                for x in v:
                    r = find(x)
                    if r:
                        return r
            return None
        e = find(seq)
        if e is None:
            sys.exit(f"surface rule for {biome} not in material_rule/{file}")
        return self.rule(e)

    def surface_rules(self):
        inserts = []   # (how, anchor, rule)
        for b, spec in BIOMES.items():
            for file, how, anchor in spec.get("surface", []):
                inserts.append((how, anchor, self.entry_for(file, b)))
        for preset in NOISE_SETTINGS:
            data = json.loads(self.port.old.read(f"data/minecraft/worldgen/noise_settings/{preset}.json"))
            for how, anchor, rule in inserts:
                if how == "before_deepslate":
                    top = data["surface_rule"]["sequence"]
                    i = next(i for i, r in enumerate(top) if r.get("if_true", {}).get("random_name") == "minecraft:deepslate")
                    top.insert(i, rule)
                    continue
                n = self.insert_after(data["surface_rule"], anchor, rule)
                if n == 0:
                    sys.exit(f"noise_settings/{preset}: no rule for {anchor} to put ours after")
            self.port.write(self.res / f"data/minecraft/worldgen/noise_settings/{preset}.json", data)

    def insert_after(self, v, anchor, rule) -> int:
        n = 0
        if isinstance(v, dict):
            if v.get("type") == "minecraft:sequence":
                seq = v["sequence"]
                for i in range(len(seq) - 1, -1, -1):
                    c = seq[i].get("if_true", {}) if isinstance(seq[i], dict) else {}
                    if c.get("type") == "minecraft:biome" and anchor in c.get("biome_is", []):
                        seq.insert(i + 1, json.loads(json.dumps(rule)))
                        n += 1
            for x in list(v.values()):
                n += self.insert_after(x, anchor, rule)
        elif isinstance(v, list):
            for x in list(v):
                n += self.insert_after(x, anchor, rule)
        return n

    # ---------- tags and tints ----------
    def c_tags(self):
        by_tag = {}
        for b, spec in BIOMES.items():
            for t in spec["c"]:
                by_tag.setdefault(t, []).append(f"{NS}:{b}")
        for t, ids in by_tag.items():
            self.port.write(self.res / f"data/c/tags/worldgen/biome/{t}.json", {"replace": False, "values": ids})

    def dry_foliage(self):
        """26.x biomes' dry_foliage_color overrides, for the biomes 1.21.1 has and ours (client tint table), and
        Mojang's dry foliage colormap (1.21.1 has none)."""
        self.port.texture("minecraft:colormap/dry_foliage")
        table = {}
        for n in sorted(self.port.new_names):
            if n.startswith("data/minecraft/worldgen/biome/") and n.endswith(".json"):
                name = n.split("/")[-1][:-5]
                color = json.loads(self.port.jar.read(n)).get("effects", {}).get("dry_foliage_color")
                if color is None:
                    continue
                if name in BIOMES:
                    table[f"{NS}:{name}"] = colour(color)
                elif n in self.port.old_names:
                    table[f"minecraft:{name}"] = colour(color)
        self.port.write(self.res / "assets" / NS / "dry_foliage_colors.json", table)
