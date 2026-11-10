"""Explorer maps (26.x Items: one MapItem per destination) and the cartographer that sells them (split out of
backport.py; called through extras.run).

- The map items with their Mojang sources: the cartographer's trades, the shipwreck and underwater ruin chests (buried
  treasure map), and the abandoned camp's chests (tools/camp.py: the abandoned camp maps, and the buried ancient city,
  buried mineshaft, desert pyramid and warm ocean ruins maps, whose only source they are).
- 26.x's structure tags those maps search (on_*_maps) that 1.21.1 lacks, with our ids.
- 26.x map decoration types 1.21.1 lacks (DECORATIONS: no texture of that name in 1.21.1): checked against 26.x's
  MapDecorationTypes, their textures copied for Java (map/ExplorerMaps registers them under our ids).
- The cartographer's 26.x trade set (data/minecraft/trade_set/cartographer, its #villager_trade tags and the
  villager_trade files), written as one table that Java (CartographerTrades) turns into 1.21.1 ItemListings. Only
  what 1.21.1's MerchantOffer can hold is accepted; any other field stops the run.
"""
import re
import sys

import lootfmt
import sources

NS = "haven_backports"
MAPS = ["ocean_monument_map", "woodland_mansion_map", "buried_trial_chambers_map", "jungle_pyramid_map", "swamp_hut_map",
        "desert_village_map", "plains_village_map", "savanna_village_map", "snowy_village_map", "taiga_village_map",
        "buried_treasure_map", "abandoned_camp_map", "buried_ancient_city_map", "buried_mineshaft_map",
        "desert_pyramid_map", "warm_ocean_ruins_map"]
# 1.21.1 tables that make a buried treasure map from minecraft:map; 26.x makes the buried_treasure_map item.
TREASURE = {"minecraft:map+exploration_map": f"{NS}:buried_treasure_map"}
LOOT = ["chests/shipwreck_map", "chests/underwater_ruin_big", "chests/underwater_ruin_small"]
PROFESSIONS = ["cartographer"]
VILLAGER_KEYS = {"wants", "additional_wants", "gives", "max_uses", "xp", "reputation_discount", "merchant_predicate",
                 "given_item_modifier"}


def run(port, res, conv):
    decorations(port, res)
    structure_tags(port, res, LOOT)
    import extras
    for table in LOOT:
        extras.take_loot(port, res, conv, table, patch=TREASURE)
    for prof in PROFESSIONS:
        trades(port, res, conv, prof)


def decorations(port, res):
    """26.x's map decoration types whose texture 1.21.1 lacks must be lootfmt.DECORATIONS (ours); copies them."""
    src = sources.decompile(port.jar.filename, "net/minecraft/world/level/saveddata/maps/MapDecorationTypes")
    new = {n: a for n, a in re.findall(r'register\("(\w+)", "(\w+)"', src)
           if f"assets/minecraft/textures/map/decorations/{a}.png" not in port.old_names}
    if {f"minecraft:{n}" for n in new} != set(lootfmt.DECORATIONS):
        sys.exit(f"map decorations 1.21.1 lacks: 26.x {sorted(new)}, lootfmt.DECORATIONS {sorted(lootfmt.DECORATIONS)}")
    for n, asset in sorted(new.items()):
        port.copy_texture(f"assets/minecraft/textures/map/decorations/{asset}.png",
                          res / f"assets/{NS}/textures/map/decorations/{asset}.png")


def structure_tags(port, res, tables):
    """The on_*_maps tags our maps and trades search, written as 26.x has them (our ids) when 1.21.1 lacks them."""
    names = set()
    for prof in PROFESSIONS:
        for n in port.new_names:
            if n.startswith(f"data/minecraft/villager_trade/{prof}/") and n.endswith(".json"):
                for f in port.read(n).get("given_item_modifier", []):
                    if f.get("type") == "minecraft:exploration_map":
                        names.add(f["destination"].lstrip("#"))
    for table in tables:
        for f in functions_in(port.read(f"data/minecraft/loot_table/{table}.json")):
            if f.get("type") == "minecraft:exploration_map":
                names.add(f["destination"].lstrip("#"))
    for tag in sorted(names):
        path = f"data/minecraft/tags/worldgen/structure/{tag.split(':')[1]}.json"
        if path not in port.old_names:
            port.write(res / path, {"replace": False, "values": [port.id(v) for v in port.read(path)["values"]]})


def functions_in(value):
    if isinstance(value, list):
        return [f for v in value for f in functions_in(v)]
    if isinstance(value, dict):
        own = value.get("modifier", [])
        own = own if isinstance(own, list) else [own]
        return own + [f for v in value.values() for f in functions_in(v)]
    return []


def trades(port, res, conv, prof):
    """26.x trade_set/<prof>/level_N -> #villager_trade tag -> trades, as one table for Java."""
    levels = {}
    for level in range(1, 6):
        trade_set = port.read(f"data/minecraft/trade_set/{prof}/level_{level}.json")
        if trade_set.get("amount") != 2 or trade_set.get("allow_duplicates", False):
            sys.exit(f"trade_set {prof}/level_{level}: 1.21.1 villagers pick 2 distinct trades per level")
        tag = trade_set["trades"].lstrip("#").split(":")[1]
        ids = port.read(f"data/minecraft/tags/villager_trade/{tag}.json")["values"]
        levels[str(level)] = [trade(port, conv, i, port.read(f"data/minecraft/villager_trade/{i.split(':')[1]}.json")) for i in ids]
    port.write(res / f"data/{NS}/villager_trades/{prof}.json", {"source": f"26.x trade_set/{prof}", "levels": levels})


def trade(port, conv, tid, t):
    unknown = set(t) - VILLAGER_KEYS
    if unknown:
        sys.exit(f"villager_trade {tid}: fields {sorted(unknown)} have no 1.21.1 MerchantOffer counterpart")
    out = {"id": tid}
    for key in ("wants", "additional_wants"):
        if key in t:
            cost = t[key]
            if set(cost) - {"id", "count"} or not isinstance(cost.get("count", 1), int):
                sys.exit(f"villager_trade {tid}: {key} {cost} is more than an item and a fixed count")
            out[key] = {"id": cost["id"], "count": cost.get("count", 1)}
    gives = port.ids(t["gives"])
    if set(gives) - {"id", "count"} or not port.exists_1211_item(gives["id"]):
        sys.exit(f"villager_trade {tid}: gives {gives} cannot be made on 1.21.1")
    out["gives"] = {"id": gives["id"], "count": gives.get("count", 1)}
    for key, default in (("max_uses", 4), ("xp", 1), ("reputation_discount", 0.0)):
        value = t.get(key, default)
        if not isinstance(value, (int, float)):
            sys.exit(f"villager_trade {tid}: {key} {value} is not a fixed number")
        out[key] = value
    if "merchant_predicate" in t:
        p = t["merchant_predicate"]
        variants = p.get("predicate", {}).get("minecraft:predicates", {}).get("minecraft:villager/variant")
        if p.get("type") != "minecraft:entity_properties" or p.get("entity") != "this" or variants is None or \
                len(p["predicate"]) != 1 or len(p["predicate"]["minecraft:predicates"]) != 1:
            sys.exit(f"villager_trade {tid}: merchant_predicate {p} is more than the villager's variant")
        out["villager_types"] = variants if isinstance(variants, list) else [variants]
    if "given_item_modifier" in t:   # loot functions, applied by Java with the villager as the loot origin
        out["given_item_modifier"] = conv.functions(t["given_item_modifier"])
    return out

