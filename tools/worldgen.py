"""26.x worldgen JSON -> 1.21.1 worldgen JSON, from Mojang's files only (called from Port.run through biomes.py).

26.x changed the worldgen data formats; this converts them key for key and stops on anything it does not know:
- `worldgen/feature/<id>` (flat: type + fields) -> `worldgen/configured_feature/<id>` ({type, config});
- block states `{"id", "properties"}` -> `{"Name", "Properties"}`; a bare id where a state provider goes ->
  `simple_state_provider`; `weighted` -> `weighted_state_provider`; named providers (worldgen/block_state_provider)
  are inlined, except `soil_beneath_tree`, which is 1.21.1's tree `dirt_provider: dirt` + `force_dirt: false` (26.x
  keeps a block of #cannot_replace_below_tree_trunk, 1.21.1 keeps a #dirt block but grass and mycelium: the same
  result for every block 1.21.1 has);
- 26.x dropped `random_patch` for placement "count + offset(trapezoid) + filters". A symmetric trapezoid with plateau
  0 samples nextInt(max + 1) - nextInt(max + 1), x then y then z, which is 1.21.1 RandomPatchFeature's offset in the
  same order: such a run becomes `random_patch` (tries = count, xz_spread = max x = max z, y_spread = max y) around
  the same inner feature and filters, with the same distribution and random order. Any other offset stops the run;
- feature, placer, decorator and predicate types 1.21.1 lacks become Haven Backports' ports of Mojang's classes;
  every other type must be one 1.21.1's own worldgen data uses;
- ids: a feature 1.21.1 has under the same id is used as 1.21.1's (the run prints any whose 26.x content differs),
  a new one becomes ours (haven_backports:<id>).
"""
import json
import sys

NS = "haven_backports"
# 26.x types 1.21.1 lacks -> our ported class's registered type (Java: worldgen/Worldgen.java)
TYPES = {
    "minecraft:weighted_random_selector": f"{NS}:weighted_random_selector",
    "minecraft:fallen_tree": f"{NS}:fallen_tree",
    "minecraft:below_heightmap": f"{NS}:below_heightmap",
    "minecraft:poplar_trunk_placer": f"{NS}:poplar_trunk_placer",
    "minecraft:poplar_foliage_placer": f"{NS}:poplar_foliage_placer",
    "minecraft:place_on_ground": f"{NS}:place_on_ground",
    "minecraft:attached_to_logs": f"{NS}:attached_to_logs",
    "minecraft:shelf_mushroom": f"{NS}:shelf_mushroom",
    # 1.21.1 has lake and root_system without 26.x's new fields; the rest are new
    "minecraft:lake": f"{NS}:lake",
    "minecraft:root_system": f"{NS}:root_system",
    "minecraft:sequence": f"{NS}:sequence",
    "minecraft:template": f"{NS}:template",
    "minecraft:speleothem": f"{NS}:speleothem",
    "minecraft:speleothem_cluster": f"{NS}:speleothem_cluster",
    "minecraft:large_speleothem": f"{NS}:large_speleothem",
    "minecraft:crevasse": f"{NS}:crevasse",
    "minecraft:matching_fluids": f"{NS}:matching_fluids",   # 26.x adds the fluid state check
    "minecraft:offset": f"{NS}:offset",
    "minecraft:trapezoid": f"{NS}:trapezoid",   # the int provider (1.21.1's trapezoid is a height provider only)
    "minecraft:pale_moss": f"{NS}:pale_moss",
    "minecraft:dark_oak_trunk_placer": f"{NS}:dark_oak_trunk_placer",   # 26.x moved the side branches' leaves
    "minecraft:creaking_heart": f"{NS}:creaking_heart",
}
# Keys whose value is a block state provider.
PROVIDER_KEYS = {"trunk_provider", "foliage_provider", "block_state_provider", "block_provider", "to_place",
                 "state_provider", "provider", "fluid", "barrier", "root_state_provider", "hanging_root_state_provider",
                 "ground_state", "surface_block", "fill_block"}
# Keys whose value is a plain block state.
STATE_KEYS = {"default_state", "result_state", "body_state", "head_state", "base_block", "pointed_block"}


class Worldgen:
    def __init__(self, port, res):
        self.port, self.res = port, res
        self.jar, self.old = port.jar, port.old
        self.new_names, self.old_names = port.new_names, port.old_names
        self.done: dict[str, str] = {}      # "<kind>/<id>" -> id written or reused
        self.differs: list[str] = []
        self.known = self.known_types()

    def known_types(self) -> set[str]:
        """Every "type" value 1.21.1's own worldgen data uses: what 1.21.1 can read."""
        seen = set()

        def walk(v):
            if isinstance(v, dict):
                if isinstance(v.get("type"), str):
                    seen.add(v["type"])
                for x in v.values():
                    walk(x)
            elif isinstance(v, list):
                for x in v:
                    walk(x)
        for n in self.old_names:
            if n.startswith("data/minecraft/worldgen/") and n.endswith(".json"):
                walk(json.loads(self.old.read(n)))
        return seen

    def read26(self, path):
        return json.loads(self.jar.read(f"data/minecraft/worldgen/{path}.json"))

    def write(self, path, data):
        self.port.write(self.res / "data" / NS / "worldgen" / f"{path}.json", data)

    # ---------- types and ids ----------
    def typ(self, t: str, where: str) -> str:
        if t in TYPES:
            return TYPES[t]
        if t not in self.known:
            sys.exit(f"{where}: type {t} is new in 26.x and has no port; port Mojang's class first")
        return t

    def block_id(self, v: str) -> str:
        return self.port.id(v if ":" in v else f"minecraft:{v}")

    # ---------- block states and providers ----------
    def state(self, v, where):
        if isinstance(v, str):
            return {"Name": self.block_id(v)}
        if isinstance(v, dict) and "id" in v:
            out = {"Name": self.block_id(v["id"])}
            if v.get("properties"):
                out["Properties"] = v["properties"]
            extra = set(v) - {"id", "properties"}
            if extra:
                sys.exit(f"{where}: block state keys {extra}")
            return out
        if isinstance(v, dict) and "Name" in v:
            return v
        sys.exit(f"{where}: not a block state: {v}")

    def provider(self, v, where):
        if isinstance(v, str):
            name = f"data/minecraft/worldgen/block_state_provider/{v.split(':')[-1]}.json"
            if name in self.new_names:
                return self.provider(json.loads(self.jar.read(name)), where)
            return {"type": "minecraft:simple_state_provider", "state": self.state(v, where)}
        if isinstance(v, dict) and "type" not in v:
            return {"type": "minecraft:simple_state_provider", "state": self.state(v, where)}
        t = v["type"]
        if t == "minecraft:weighted":
            return {"type": "minecraft:weighted_state_provider",
                    "entries": [{"data": self.state(e["data"], where), "weight": e["weight"]} for e in v["entries"]]}
        if t == "minecraft:rule_based":
            out = {"type": "minecraft:rule_based_state_provider",
                   "rules": [{"if_true": self.value(r["if_true"], where), "then": self.provider(r["then"], where)}
                             for r in v["rules"]]}
            if "fallback" in v:
                out["fallback"] = self.provider(v["fallback"], where)
            return out
        if t == "minecraft:randomized_int":
            return {"type": "minecraft:randomized_int_state_provider", "property": v["property"],
                    "values": v["values"], "source": self.provider(v["source"], where)}
        sys.exit(f"{where}: state provider type {t} needs a 1.21.1 form")

    # ---------- generic values ----------
    def value(self, v, where, key=None):
        if key in PROVIDER_KEYS:
            return self.provider(v, where)
        if key in STATE_KEYS or key == "state" and (isinstance(v, str) or isinstance(v, dict) and "id" in v):
            return self.state(v, where)   # a block state ("state" of matching_fluids is a property map: kept)
        if isinstance(v, dict):
            out = {}
            for k, x in v.items():
                if k == "type" and x == "minecraft:trapezoid" and "min_inclusive" in v:
                    out[k] = x   # the height provider (vertical anchors): 1.21.1 has it
                else:
                    out[k] = self.typ(x, where) if k == "type" and isinstance(x, str) else self.value(x, where, k)
            return out
        if isinstance(v, list):
            return [self.value(x, where, key) for x in v]
        if isinstance(v, str):
            return self.port.id(v)
        return v

    # ---------- configured and placed features ----------
    def configured_body(self, fid: str):
        """A 26.x feature (by id or inline) as 1.21.1 {type, config}."""
        where = f"feature {fid}"
        raw = self.read26(f"feature/{fid.split(':')[-1]}") if isinstance(fid, str) else fid
        raw = dict(raw)
        t = raw.pop("type")
        if t == "minecraft:tree":
            below = raw.pop("below_trunk_provider", None)
            if below not in (None, "minecraft:soil_beneath_tree"):
                sys.exit(f"{where}: below_trunk_provider {below} has no 1.21.1 form")
            raw.setdefault("dirt_provider", "minecraft:dirt")
            raw.setdefault("force_dirt", False)
            raw["dirt_provider"] = self.provider(raw["dirt_provider"], where)
        config = {}
        for k, x in raw.items():
            if t == "minecraft:weighted_random_selector" and k == "features":
                config[k] = [{"data": self.placed_any(e["data"]), "weight": e["weight"]} for e in x]
            elif k == "features" and t in ("minecraft:sequence", "minecraft:simple_random_selector"):
                config[k] = [self.placed_any(e) for e in x]
            elif k == "feature" and t == "minecraft:root_system" or k == "vegetation_feature" or k == "default" and t == "minecraft:random_selector":
                config[k] = self.placed_any(x)
            elif k == "features" and t == "minecraft:random_selector":
                config[k] = [{"chance": e["chance"], "feature": self.placed_any(e["feature"])} for e in x]
            elif k == "templates" and t == "minecraft:template":
                config[k] = [{"data": {**e["data"], "id": self.template(e["data"]["id"])}, "weight": e["weight"]} for e in x]
            elif k == "dirt_provider":
                config[k] = x
            else:
                config[k] = self.value(x, where, k)
        rule_tests = ("minecraft:all_of", "minecraft:any_of", "minecraft:height_match")
        if t == "minecraft:ore" and any(f'"predicate_type": "{r}"' in json.dumps(raw) for r in rule_tests):
            # 26.x position-aware rule tests (1.21.1's RuleTest has no position): our port of 26.x's ore feature
            text = json.dumps(config)
            for r in rule_tests:
                text = text.replace(f'"predicate_type": "{r}"', f'"predicate_type": "{NS}:{r[10:]}"')
            return {"type": f"{NS}:ore", "config": json.loads(text)}
        if t == "minecraft:simple_block" and (raw.get("schedule_tick") or "pale_moss_carpet" in json.dumps(raw)):
            # 26.x's simple_block with a scheduled tick or the mossy carpet branch: our port (1.21.1's lacks both)
            return {"type": f"{NS}:simple_block", "config": config}
        if t == "minecraft:simple_block" and "schedule_tick" in config:
            config.pop("schedule_tick")
        return {"type": self.typ(t, where), "config": config}

    def configured(self, fid: str) -> str:
        """Id of the configured feature to reference: 1.21.1's own, or ours (written)."""
        key = f"configured_feature/{fid}"
        if key in self.done:
            return self.done[key]
        name = fid.split(":")[-1]
        old = f"data/minecraft/worldgen/configured_feature/{name}.json"
        if old in self.old_names:
            self.compare(key, lambda: self.configured_body(fid), json.loads(self.old.read(old)))
            self.done[key] = f"minecraft:{name}"
        else:
            self.write(f"configured_feature/{name}", self.configured_body(fid))
            self.done[key] = f"{NS}:{name}"
        return self.done[key]

    def configured_any(self, v):
        """A feature reference (id) or inline 26.x feature, as 1.21.1 takes it in a placed feature."""
        return self.configured(v) if isinstance(v, str) else self.configured_body(v)

    def placed_any(self, v):
        """A placed feature reference (id) or inline placed feature, as 1.21.1 takes it."""
        if isinstance(v, str):
            return self.placed_ref(v)
        mods, patch = self.placement(v.get("placement", []), v["feature"], "inline placed feature")
        return {"feature": patch if patch is not None else self.configured_any(v["feature"]), "placement": mods}

    def template(self, tid: str) -> str:
        """A 26.x structure template a feature places: converted to 1.21.1's format, under our namespace."""
        import nbt
        name = tid.split(":")[-1]
        data = nbt.convert_template(self.jar.read(f"data/minecraft/structure/{name}.nbt"), self.block_id, f"structure {tid}")
        out = self.res / "data" / NS / "structure" / f"{name}.nbt"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(data)
        return f"{NS}:{name}"

    def offset(self, m, where):
        """26.x offset placement: a constant offset with x == z is 1.21.1's random_offset; any other is our port."""
        x, y, z = m["x"], m["y"], m["z"]
        if all(isinstance(p, int) for p in (x, y, z)) and x == z:
            return {"type": "minecraft:random_offset", "xz_spread": x, "y_spread": y}
        return {"type": f"{NS}:offset", "x": self.value(x, where), "y": self.value(y, where), "z": self.value(z, where)}

    def placement(self, mods: list, inner_fid, where: str):
        """Placement modifiers and the feature they place; a count + trapezoid offset run becomes random_patch."""
        for i, m in enumerate(mods):
            if m["type"] == "minecraft:offset":
                rest = mods[i + 1:]
                patch = (i > 0 and mods[i - 1]["type"] == "minecraft:count" and isinstance(mods[i - 1]["count"], int)
                         and all(r["type"] == "minecraft:block_predicate_filter" for r in rest)
                         and all(isinstance(p, dict) for p in (m["x"], m["y"], m["z"])))
                if not patch:
                    continue   # converted in order below (value() -> offset())
                x, y, z = m["x"], m["y"], m["z"]
                for p in (x, y, z):
                    if p.get("type") != "minecraft:trapezoid" or p.get("plateau", 0) != 0 or p["min"] != -p["max"]:
                        sys.exit(f"{where}: offset {p} is not a symmetric trapezoid with plateau 0")
                if x != z:
                    sys.exit(f"{where}: offset x {x} != z {z}: 1.21.1 random_patch spreads x and z alike")
                rest = mods[i + 1:]
                if any(r["type"] != "minecraft:block_predicate_filter" for r in rest):
                    sys.exit(f"{where}: after the offset only filters convert exactly; got {[r['type'] for r in rest]}")
                inner = {"feature": self.configured_any(inner_fid), "placement": [self.value(r, where) for r in rest]}
                patch = {"type": "minecraft:random_patch", "config": {
                    "feature": inner, "tries": mods[i - 1]["count"], "xz_spread": x["max"], "y_spread": y["max"]}}
                return [self.value(r, where) for r in mods[:i - 1]], patch
        return [self.offset(m, where) if m["type"] == "minecraft:offset" else self.value(m, where) for m in mods], None

    def placed_body(self, pid: str):
        where = f"placed feature {pid}"
        raw = self.read26(f"placed_feature/{pid.split(':')[-1]}")
        mods, patch = self.placement(raw["placement"], raw["feature"], where)
        if patch is None:
            return {"feature": self.configured(raw["feature"]), "placement": mods}, None
        return {"placement": mods}, patch

    def placed_ref(self, pid: str) -> str:
        key = f"placed_feature/{pid}"
        if key in self.done:
            return self.done[key]
        name = pid.split(":")[-1]
        old = f"data/minecraft/worldgen/placed_feature/{name}.json"
        if old in self.old_names:
            self.compare(key, lambda: self.placed_body(pid)[0].get("placement"), json.loads(self.old.read(old))["placement"])
            self.done[key] = f"minecraft:{name}"
            return self.done[key]
        body, patch = self.placed_body(pid)
        if patch is not None:   # the patch's own configured feature, named after the placed feature (1.21.1's pattern)
            self.write(f"configured_feature/{name}", patch)
            body["feature"] = f"{NS}:{name}"
        self.write(f"placed_feature/{name}", {"feature": body["feature"], "placement": body["placement"]})
        self.done[key] = f"{NS}:{name}"
        return self.done[key]

    def compare(self, key, convert, old):
        """A feature 1.21.1 has under the same id stays 1.21.1's; note it when 26.x's converted form differs."""
        try:
            if convert() != old:
                self.differs.append(key)
        except SystemExit as e:
            self.differs.append(f"{key} ({e})")

    # ---------- noise ----------
    def noise(self, nid: str, namespace: str):
        """26.x noise -> 1.21.1 {firstOctave, amplitudes}, only when every octave's value factor is 1.21.1's for the
        same amplitudes (noise_math: both versions' NormalNoise math). The key keeps Mojang's name: NormalNoise is
        seeded from it (RandomState.getOrCreateNoise), so only the same key gives Mojang's pattern for a seed."""
        import noise_math
        raw = self.read26(f"noise/{nid}")
        mods = raw.get("amplitude_modifiers", [])
        count = raw.get("octave_count", 1)
        if mods and len(mods) != count:
            sys.exit(f"noise {nid}: {len(mods)} amplitude modifiers for {count} octaves")
        normalize = raw.get("normalize", "enabled")
        normalize = {True: "enabled", False: "disabled"}.get(normalize, normalize)
        amps = mods or [1.0] * count
        new = noise_math.factors_26x(raw.get("base_amplitude", 1.0), count, normalize, mods)
        old = noise_math.factors_1211(amps)
        if any(abs(a - b) > 1e-9 * max(1.0, abs(b)) for a, b in zip(new, old)):
            sys.exit(f"noise {nid}: 26.x octave factors {new} differ from 1.21.1's {old}; no 1.21.1 form")
        self.port.write(self.res / "data" / namespace / "worldgen/noise" / f"{nid}.json",
                        {"firstOctave": raw["base_octave"], "amplitudes": amps})
        return new
