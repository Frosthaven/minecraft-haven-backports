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
