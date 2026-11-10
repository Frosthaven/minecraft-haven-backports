"""NeoForge biome modifiers: what 26.x's vanilla biomes have of ours that 1.21.1's do not (tools/biomes.py runs it).

1.21.1 keeps its own vanilla biome files (other mods and the pack's data packs edit them); our content goes in through
`neoforge:add_spawns` and `neoforge:add_features`, as 26.x lists it. Only entries of ours count: other newer content in
the same biomes (strays in frozen oceans, zombie horses, nautiluses) is not backported here.
"""
import json

NS = "haven_backports"
DIR = "data/minecraft/worldgen/biome/"


def count(c):
    return (c, c) if isinstance(c, int) else (c["min_inclusive"], c["max_inclusive"])


def spawns(port, res):
    """One add_spawns modifier per entity of ours: biomes grouped by (category, weight, counts) as 26.x has them."""
    groups = {}
    for n in sorted(port.new_names):
        if not (n.startswith(DIR) and n.endswith(".json")) or n not in port.old_names:
            continue
        biome = "minecraft:" + n[len(DIR):-5]
        cats = json.loads(port.jar.read(n))["attributes"]["minecraft:gameplay/natural_mob_spawns"]["argument"]["spawns_by_category"]
        for cat, entries in cats.items():
            for e in entries:
                eid = port.id(e["type"])
                if eid.startswith(f"{NS}:"):
                    lo, hi = count(e["count"])
                    groups.setdefault((eid, cat, e["weight"], lo, hi), []).append(biome)
    names = {}
    for (eid, cat, weight, lo, hi), biomes in sorted(groups.items()):
        base = f"spawn_{eid.split(':')[1]}_{weight}"
        names[base] = names.get(base, 0) + 1
        name = base if names[base] == 1 else f"{base}_{names[base]}"
        # NeoForge's add_spawns takes the category from the entity type's MobCategory, as 26.x's list does
        port.write(res / "data" / NS / "neoforge" / "biome_modifier" / f"{name}.json",
                   {"type": "neoforge:add_spawns", "biomes": sorted(biomes),
                    "spawners": [{"type": eid, "weight": weight, "minCount": lo, "maxCount": hi}]})
        print(f"biome modifier {name}: {eid} ({cat}) weight {weight} in {len(biomes)} vanilla biomes")


STEPS = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures", "strongholds",
         "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration", "top_layer_modification"]


CONDITION_KEYS = {"replaceable_blocks", "can_be_placed_on", "can_place_on", "blocks", "predicate", "target_condition",
                  "allowed_search_condition", "target", "replaceable", "valid_base_block", "rules", "tag"}


def has_ours(port, fid, seen=None):
    """True when a 26.x placed or configured feature (by id, through its references) places a block of ours."""
    seen = set() if seen is None else seen

    def walk(v):
        if isinstance(v, dict):   # what a feature places, not what it may replace or stand on
            return any(walk(x) for k, x in v.items() if k not in CONDITION_KEYS)
        if isinstance(v, list):
            return any(walk(x) for x in v)
        if isinstance(v, str) and v.startswith("minecraft:"):
            if port.id(v).startswith(f"{NS}:"):
                return True
            for kind in ("placed_feature", "feature"):
                n = f"data/minecraft/worldgen/{kind}/{v[10:]}.json"
                if n in port.new_names and n not in seen:
                    seen.add(n)
                    if walk(json.loads(port.jar.read(n))):
                        return True
        return False
    return walk(fid)


def features(port, res, wg, patched=()):
    """26.x's flora in vanilla biomes: each step's new features of ours where 26.x lists them (PlaceFeaturesModifier).
    A new feature in the place of a 1.21.1 one that 26.x dropped (same feature before it) takes that place: forest's
    trees with leaf litter, meadow's grass beside its wildflowers. A vanilla feature 26.x changed to place ours (the
    cactus with its flower) is our copy in its place. `patched`: vanilla features the generator already gives ours in
    place (glow lichen). Returns each changed biome's steps as the modifiers leave them, for the feature order test."""
    groups, result = {}, {}
    for n in sorted(port.new_names):
        if not (n.startswith(DIR) and n.endswith(".json")) or n not in port.old_names:
            continue
        biome = n[len(DIR):-5]
        new = json.loads(port.jar.read(n))["features"]
        old = json.loads(port.old.read(n))["features"]
        steps = [list(s) for s in old]
        for i, (n26, o) in enumerate(zip(new, old)):
            ours = {f for f in n26 if f not in patched and has_ours(port, f)}
            if not ours:
                continue
            cur = {f: f for f in o}   # 26.x id -> the id in the step now
            entries = []
            for j, f in enumerate(n26):
                before = n26[j - 1] if j else None
                gone = [g for g in o if g not in n26 and (o.index(g) == 0 and j == 0 or o.index(g) > 0 and o[o.index(g) - 1] == before)]
                if f in o:
                    if f not in ours:
                        continue
                    e = {"feature": wg.placed_ref(f, ours=True), "replaces": f}   # 26.x made it place ours
                elif gone and (f in ours and f.startswith(gone[0] + "_") or f not in ours and ours):
                    # ours: only its own newer form (trees_birch_and_oak -> _leaf_litter); vanilla: beside ours
                    e = {"feature": wg.placed_ref(f), "replaces": gone[0]}
                    print(f"biome modifier: {biome} {STEPS[i]}: {f} takes {gone[0]}'s place, as in 26.x")
                elif f in ours:
                    # just before the first 26.x successor still in the step (none: at the end). Features 1.21.1 has
                    # that 26.x dropped or moved (pumpkin, sugar cane) then stay before ours in every biome alike.
                    nxt = [steps[i].index(cur[g]) for g in n26[j + 1:] if g in cur and cur[g] in steps[i]]
                    at = min(nxt) if nxt else len(steps[i])
                    e = {"feature": wg.placed_ref(f), **({"after": steps[i][at - 1]} if at else {})}
                else:
                    continue
                cur[f] = e["feature"]
                entries.append(e)
                apply(steps[i], e)
            groups.setdefault((STEPS[i], json.dumps(entries)), []).append(f"minecraft:{biome}")
        result[biome] = steps
    for (step, entries), biomes in sorted(groups.items(), key=lambda g: (g[0][0], g[1][0])):
        name = f"place_{step}_{biomes[0].split(':')[1]}"
        port.write(res / "data" / NS / "neoforge" / "biome_modifier" / f"{name}.json",
                   {"type": f"{NS}:place_features", "biomes": sorted(biomes), "step": step, "features": json.loads(entries)})
        print(f"biome modifier {name}: {[e['feature'] for e in json.loads(entries)]} in {len(biomes)} vanilla biomes")
    return result


def apply(step, e):
    """PlaceFeaturesModifier's rule on a list of ids (kept in step with the Java)."""
    f = e["feature"]
    if "replaces" in e and e["replaces"] in step:
        step[step.index(e["replaces"])] = f
    elif "after" not in e:
        step.insert(0, f)
    elif e["after"] in step:
        step.insert(step.index(e["after"]) + 1, f)
    else:
        step.append(f)
