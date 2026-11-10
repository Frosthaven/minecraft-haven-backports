"""Natural spawns of our mobs (26.x biomes' natural_mob_spawns attribute) as NeoForge biome modifiers (called from mobs.run).

26.x keeps a biome's spawners under attributes/minecraft:gameplay/natural_mob_spawns (spawns_by_category); 1.21.1
under spawners. For every 26.x biome 1.21.1 has, the two lists are compared per category:
- an entry of one of our entities becomes a neoforge:add_spawns modifier (the category comes from the entity type,
  as in 26.x's list);
- a 1.21.1 entry whose weight 26.x changed in the same biome and category as one of ours (the desert skeleton,
  100 -> 50 with the parched at 50) becomes a haven_backports:spawn_weight modifier (Java: SpawnWeightModifier), which
  only touches an entry still at 1.21.1's weight, so a pack's own change wins;
- other 26.x changes (mobs not backported, 26.x-only biomes) are printed, not ported.
"""
import json

NS = "haven_backports"


def counts(entry):
    c = entry.get("count", 1)
    if isinstance(c, int):
        return c, c
    if isinstance(c, dict) and c.get("type") == "minecraft:uniform":
        return c["min_inclusive"], c["max_inclusive"]
    raise SystemExit(f"spawns: count {c} has no 1.21.1 form")


def run(port, res, entities):
    ours = {f"minecraft:{e}" for e in entities}
    adds, weights, other = {}, {}, set()
    for n in sorted(port.new_names):
        if not (n.startswith("data/minecraft/worldgen/biome/") and n.endswith(".json")) or n not in port.old_names:
            continue
        biome = "minecraft:" + n.rsplit("/", 1)[1][:-5]
        new = port.read(n).get("attributes", {}).get("minecraft:gameplay/natural_mob_spawns", {}).get("argument", {})
        new = new.get("spawns_by_category", {})
        old = json.loads(port.old.read(n)).get("spawners", {})
        for cat in sorted(set(new) | set(old)):
            o = {s["type"]: s for s in old.get(cat, [])}
            added = [s for s in new.get(cat, []) if s["type"] in ours]
            for s in added:
                lo, hi = counts(s)
                key = (port.id(s["type"]), s["weight"], lo, hi)
                adds.setdefault(key, []).append(biome)
            for s in new.get(cat, []):
                if s["type"] in ours:
                    continue
                before = o.get(s["type"])
                if before is None or before["weight"] != s["weight"]:
                    if added and before is not None:
                        weights.setdefault((s["type"], before["weight"], s["weight"]), []).append(biome)
                    else:
                        other.add(f"{biome} {cat} {s['type']}")
    out = res / "data" / NS / "neoforge/biome_modifier"
    for (etype, weight, lo, hi), biomes in sorted(adds.items()):
        port.write(out / f"spawn_{etype.split(':')[1]}_{weight}.json", {
            "type": "neoforge:add_spawns", "biomes": biomes if len(biomes) > 1 else biomes[0],
            "spawners": {"type": etype, "weight": weight, "minCount": lo, "maxCount": hi}})
    for (etype, before, after), biomes in sorted(weights.items()):
        port.write(out / f"weight_{etype.split(':')[1]}_{after}.json", {
            "type": f"{NS}:spawn_weight", "biomes": biomes if len(biomes) > 1 else biomes[0],
            "entity": etype, "old_weight": before, "weight": after})
    print(f"spawns: {[(k[0], k[1], v) for k, v in sorted(adds.items())]}; weights: {sorted(weights.items())}")
    print(f"spawns 26.x changes not ported (not ours): {sorted(other)}")
