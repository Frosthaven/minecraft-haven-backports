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

import biome_mods
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
    "sulfur_caves": {
        "c": ["is_cave"],   # as minecraft:dripstone_caves (c:is_underground follows from c:is_cave)
        # the shared surface-and-under rules (floor and under floor) after dripstone caves; the underground bands
        # before the deepslate gradient (26.x material_rule/overworld/underground)
        "surface": [("overworld/biome_surface", "after", "minecraft:dripstone_caves"),
                    ("overworld/underground", "before_deepslate", None)],
        "noises": ["sulfur_cave_gradient"],
        # 26.x glow lichen grows on sulfur and cinnabar too (its only change): 1.21.1's feature with ours added
        "additions": [("configured_feature/glow_lichen", ["config", "can_be_placed_on"], ["can_be_placed_on"])],
    },
    "pale_garden": {
        "c": ["is_dense_vegetation/overworld", "is_spooky", "is_temperate/overworld", "is_tree/deciduous"],   # as dark_forest
        "configured": ["pale_oak_bonemeal", "pale_moss_patch_bonemeal", "pale_moss_patch"],   # TreeGrower.PALE_OAK, the
        # pale moss block's bonemeal, PaleMossDecorator's ground patch
    },
    "ice_caves": {
        "c": ["is_cave", "is_cold/overworld", "is_icy"],   # as dripstone_caves (cave) and ice_spikes (cold, icy)
        # after sulfur caves in the floor and under-floor rules; its bands after sulfur's, before the deepslate
        # gradient (26.x material_rule/overworld/biome_surface, under_biome_surface, underground)
        "surface": [("overworld/biome_surface", "after", f"{NS}:sulfur_caves"),
                    ("overworld/underground", "before_deepslate", None)],
        "noises": ["ice_cave_gradient"],
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
        modified = biome_mods.features(self.port, self.res, self.wg, patched={f"minecraft:{a[0].split('/')[-1]}"
                                       for spec in BIOMES.values() for a in spec.get("additions", [])})
        self.check_order(modified)
        self.surface_rules()
        biome_mods.spawns(self.port, self.res)
        self.additions()
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
            effects["music"] = {"sound": self.port.sound_id(m["sound"]), "min_delay": m["min_delay"], "max_delay": m["max_delay"],
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
                if not self.port.exists_1211_id(self.port.id(e["type"]), "data/minecraft/tags/entity_type/") \
                        and not self.port.id(e["type"]).startswith(f"{NS}:"):
                    print(f"biome {name}: spawner {e['type']} left out (not ported)")
                    continue
                c = e["count"]
                lo, hi = (c, c) if isinstance(c, int) else (c["min_inclusive"], c["max_inclusive"])
                out.append({"type": self.port.id(e["type"]), "weight": e["weight"], "minCount": lo, "maxCount": hi})
            spawners[cat] = out
        if attrs:
            sys.exit(f"biome {name}: attributes with no 1.21.1 form: {sorted(attrs)}")
        skip = BIOMES[name].get("skip", [])
        features = [self.vanilla_order(name, i, [self.wg.placed_ref(f) for f in step if f not in skip])
                    for i, step in enumerate(raw["features"])]
        out = {"has_precipitation": raw["has_precipitation"], "temperature": raw["temperature"],
               "downfall": raw["downfall"]}
        if "temperature_modifier" in raw:
            out["temperature_modifier"] = raw["temperature_modifier"]
        out.update({"effects": dict(sorted(effects.items())), "spawners": spawners,
                    "spawn_costs": spawns.get("spawn_costs", {}), "carvers": {"air": raw["carvers"]},
                    "features": features})
        self.wg.write(f"biome/{name}", out)

    def vanilla_order(self, name, step, feats):
        """The vanilla features of one step in an order 1.21.1's biomes agree with. 1.21.1 sorts every biome's
        features into one order per step and stops chunk generation on a cycle ("Feature order cycle found"); 26.x
        moved some (patch_pumpkin before patch_sugar_cane), so a 26.x list can contradict 1.21.1's biomes. Ours keep
        their slots; the vanilla slots take the vanilla features, 26.x order where 1.21.1 has no rule. Each feature
        seeds its own random from its index, so a swap changes only which feature sees the other's blocks."""
        if not hasattr(self, "_before"):
            self._before = set()
            for n in self.port.old_names:
                if n.startswith("data/minecraft/worldgen/biome/") and n.endswith(".json"):
                    for i, st in enumerate(json.loads(self.port.old.read(n))["features"]):
                        self._before.update((i, a, b) for x, a in enumerate(st) for b in st[x + 1:])
        vanilla = [f for f in feats if f.startswith("minecraft:")]
        done = []
        rest = list(vanilla)
        while rest:
            f = next(f for f in rest if not any((step, g, f) in self._before for g in rest if g != f))
            rest.remove(f)
            done.append(f)
        if done != vanilla:
            print(f"biome {name}: step {step} vanilla features in 1.21.1's order {done} (26.x: {vanilla})")
        it = iter(done)
        return [next(it) if f.startswith("minecraft:") else f for f in feats]

    def check_order(self, modified):
        """1.21.1's FeatureSorter test on 1.21.1's biomes (as our biome modifiers leave them) plus ours: consecutive
        features (step, id) must form no cycle, or chunk generation stops at the first chunk."""
        lists = [modified.get(n.split("/")[-1][:-5]) or json.loads(self.port.old.read(n))["features"] for n in self.port.old_names
                 if n.startswith("data/minecraft/worldgen/biome/") and n.endswith(".json")]
        lists += [json.loads((self.res / "data" / NS / "worldgen" / "biome" / f"{b}.json").read_text())["features"] for b in BIOMES]
        edges = {}
        for steps in lists:
            flat = [(i, f) for i, st in enumerate(steps) for f in st]
            for a, b in zip(flat, flat[1:]):
                edges.setdefault(a, set()).add(b)
        state = {}
        for root in list(edges):
            stack = [(root, iter(edges.get(root, ())))] if root not in state else []
            state.setdefault(root, 1)
            while stack:
                node, it = stack[-1]
                m = next(it, None)
                if m is None:
                    state[node] = 2
                    stack.pop()
                elif state.get(m) == 1:
                    sys.exit(f"feature order cycle at {node} -> {m}")
                elif m not in state:
                    state[m] = 1
                    stack.append((m, iter(edges.get(m, ()))))

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

    def additions(self):
        """1.21.1 worldgen files whose 26.x version only adds our blocks to a list: 1.21.1's file with ours added."""
        for spec in BIOMES.values():
            for path, old_keys, new_keys in spec.get("additions", []):
                data = json.loads(self.port.old.read(f"data/minecraft/worldgen/{path}.json"))
                new = self.port.read(f"data/minecraft/worldgen/{path.replace('configured_feature', 'feature')}.json")
                target = data
                for k in old_keys[:-1]:
                    target = target[k]
                src = new
                for k in new_keys:
                    src = src[k]
                ours = [self.port.id(v) for v in src if self.port.id(v).startswith(f"{NS}:")]
                rest = [v for v in src if not self.port.id(v).startswith(f"{NS}:")]
                if rest != target[old_keys[-1]]:
                    sys.exit(f"{path}: 26.x changes more than adding our blocks; port it by hand")
                target[old_keys[-1]] = target[old_keys[-1]] + ours
                self.port.write(self.res / f"data/minecraft/worldgen/{path}.json", data)

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
