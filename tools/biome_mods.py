"""NeoForge biome modifiers: what 26.x's vanilla biomes have of ours that 1.21.1's do not (tools/biomes.py runs it).

1.21.1 keeps its own vanilla biome files (other mods and the pack's data packs edit them); our content goes in through
our PlaceFeaturesModifier, as 26.x lists it (spawns: tools/spawns.py). Only entries of ours count: other newer content in
the same biomes (strays in frozen oceans, zombie horses, nautiluses) is not backported here.
"""
import json

NS = "haven_backports"
DIR = "data/minecraft/worldgen/biome/"


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
    # Replacements stay per biome group, in place. Every new feature is appended by one modifier of its own that names
    # every biome it goes in, ours too: one modifier runs at one point among all mods' modifiers, so the feature has the
    # same order against other mods' features in every biome (a different order is a feature order cycle).
    appends = {}
    for (step, entries), biomes in sorted(groups.items(), key=lambda g: (g[0][0], g[1][0])):
        entries = json.loads(entries)
        for e in entries:
            if "replaces" not in e:
                appends.setdefault((step, e["feature"]), set()).update(biomes)
        swaps = [e for e in entries if "replaces" in e]
        if swaps:
            name = f"place_{step}_{biomes[0].split(':')[1]}"
            port.write(res / "data" / NS / "neoforge" / "biome_modifier" / f"{name}.json",
                       {"type": f"{NS}:place_features", "biomes": sorted(biomes), "step": step, "features": swaps})
            print(f"biome modifier {name}: {[e['feature'] for e in swaps]} in place, {len(biomes)} vanilla biomes")
    for path in sorted((res / "data" / NS / "worldgen" / "biome").glob("*.json")):
        biome = json.loads(path.read_text())
        moved = []
        for i, step in enumerate(biome["features"]):
            for f in [f for f in step if (STEPS[i], f) in appends]:
                step.remove(f)
                appends[(STEPS[i], f)].add(f"{NS}:{path.stem}")
                moved.append(f)
        if moved:
            port.write(path, biome)
            print(f"biome {path.stem}: {moved} appended by their modifier, as in the vanilla biomes")
    for (step, f), biomes in sorted(appends.items()):
        name = f"append_{step}_{f.split(':')[1]}"
        port.write(res / "data" / NS / "neoforge" / "biome_modifier" / f"{name}.json",
                   {"type": f"{NS}:place_features", "biomes": sorted(biomes), "step": step, "features": [{"feature": f}]})
        print(f"biome modifier {name}: in {len(biomes)} biomes")
    return result


def apply(step, e):
    """PlaceFeaturesModifier's rule on a list of ids (kept in step with the Java): replace in place or skip, else append."""
    f = e["feature"]
    if "replaces" in e:
        if e["replaces"] in step:
            step[step.index(e["replaces"])] = f
    else:
        step.append(f)
