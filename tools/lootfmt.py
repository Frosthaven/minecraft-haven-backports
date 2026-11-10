"""26.x loot, predicate and condition JSON -> 1.21.1's format, key for key (split out of backport.py's simple loot()).

26.x writes `type` where 1.21.1 writes `condition` / `function`, `modifier` for `functions`, one `condition` for a
`conditions` list, entity predicates keyed by component (`minecraft:entity_type` -> `type`), and tag predicates with
a `#`. Mojang functions 1.21.1 lacks or runs differently are ported in Java under haven_backports ids
(Loot.java): exploration_map (any map item, 26.x), filtered (on_pass / on_fail), discard, and the map_id item
predicate. Anything this file does not know stops the run: a silent half-conversion would load as a broken table.
"""
import sys

NS = "haven_backports"
# 26.x functions with a 1.21.1 port of their own (1.21.1's exploration_map only turns minecraft:map into filled_map).
FUNCTIONS = {"minecraft:exploration_map": f"{NS}:exploration_map", "minecraft:discard": f"{NS}:discard"}
SAME_FUNCTIONS = {"minecraft:set_count", "minecraft:enchanted_count_increase", "minecraft:furnace_smelt",
                  "minecraft:set_name", "minecraft:enchant_randomly", "minecraft:enchant_with_levels",
                  "minecraft:set_damage", "minecraft:set_potion", "minecraft:set_stew_effect",
                  "minecraft:set_components", "minecraft:set_instrument", "minecraft:set_ominous_bottle_amplifier",
                  "minecraft:explosion_decay", "minecraft:apply_bonus", "minecraft:limit_count",
                  "minecraft:set_enchantments", "minecraft:copy_components", "minecraft:set_custom_data"}
SAME_CONDITIONS = {"minecraft:killed_by_player", "minecraft:random_chance", "minecraft:random_chance_with_enchanted_bonus",
                   "minecraft:survives_explosion", "minecraft:table_bonus", "minecraft:weather_check",
                   "minecraft:match_tool", "minecraft:block_state_property"}
ENTITY_KEYS = {"minecraft:entity_type": "type", "minecraft:vehicle": "vehicle", "minecraft:passenger": "passenger",
               "minecraft:flags": "flags", "minecraft:equipment": "equipment", "minecraft:location": "location",
               "minecraft:stepping_on": "stepping_on", "minecraft:effects": "effects", "minecraft:nbt": "nbt",
               "minecraft:distance": "distance", "minecraft:movement": "movement", "minecraft:team": "team",
               "minecraft:targeted_entity": "targeted_entity", "minecraft:slots": "slots"}
ITEM_PREDICATES = {"minecraft:map_id": f"{NS}:map_id"}
# 26.x map decoration types 1.21.1 lacks (tools/maps.py checks the list against 26.x's MapDecorationTypes; Java:
# map/ExplorerMaps registers them under our ids)
DECORATIONS = {f"minecraft:{d}": f"{NS}:{d}" for d in ("abandoned_camp", "ancient_city", "desert_pyramid", "mineshaft",
                                                       "ocean_ruin_warm")}


class Missing(Exception):
    """A reference 1.21.1 cannot resolve (a biome not backported yet): the entry carrying it is left out."""


class Converter:
    def __init__(self, ids, biomes=None):
        self.ids = ids                      # minecraft:<ours> -> haven_backports:<ours>
        self.biomes = biomes or {}          # 26.x biome id -> ours, or None while it is not backported
        self.dropped = []

    def table(self, t):
        out = {k: v for k, v in t.items() if k not in ("pools", "modifier", "functions")}
        out["pools"] = [self.pool(p) for p in t.get("pools", [])]
        if "modifier" in t or "functions" in t:
            out["functions"] = self.functions(t.get("modifier", t.get("functions")))
        return self.ids(out)

    def pool(self, p):
        out = {}
        for k, v in p.items():
            if k == "entries":
                out[k] = [e for e in (self.entry(x) for x in v) if e is not None]
            elif k in ("modifier", "functions"):
                out["functions"] = self.functions(v)
            elif k in ("condition", "conditions"):
                out["conditions"] = self.conditions(v)
            else:
                out[k] = v
        return out

    def entry(self, e):
        try:
            out = {}
            for k, v in e.items():
                if k == "children":
                    out[k] = [c for c in (self.entry(x) for x in v) if c is not None]
                elif k in ("modifier", "functions"):
                    out["functions"] = self.functions(v)
                elif k in ("condition", "conditions"):
                    out["conditions"] = self.conditions(v)
                else:
                    out[k] = v
            return out
        except Missing as m:
            self.dropped.append(f"{e.get('name', e.get('type'))}: {m}")
            return None

    def functions(self, v):
        return [self.function(f) for f in (v if isinstance(v, list) else [v])]

    def function(self, f):
        f = dict(f)
        kind = f.pop("type", None) or f.pop("function")
        out = {}
        for k, v in f.items():
            if k in ("condition", "conditions"):
                out["conditions"] = self.conditions(v)
            elif k in ("on_pass", "on_fail", "modifier"):
                out[k] = self.functions(v)[0] if not isinstance(v, list) else self.functions(v)
            elif k == "item_filter":
                out[k] = self.item(v)
            else:
                out[k] = v
        if kind == "minecraft:filtered" and ("on_pass" in out or "on_fail" in out):
            kind = f"{NS}:filtered"
        elif kind in FUNCTIONS:
            kind = FUNCTIONS[kind]
            if out.get("decoration") in DECORATIONS:
                out["decoration"] = DECORATIONS[out["decoration"]]
        elif kind not in SAME_FUNCTIONS:
            sys.exit(f"lootfmt: loot function {kind} has no 1.21.1 conversion")
        out["function"] = kind
        return out

    def conditions(self, v):
        return [self.condition(c) for c in (v if isinstance(v, list) else [v])]

    def condition(self, c):
        c = dict(c)
        kind = c.pop("type", None) or c.pop("condition")
        if kind == "minecraft:match_block":
            c = {"block": c["blocks"], "properties": c.get("state", {})}
            kind = "minecraft:block_state_property"
        out = {"condition": kind}
        for k, v in c.items():
            if k == "terms":
                out[k] = self.conditions(v)
            elif k == "term":
                out[k] = self.condition(v)
            elif k == "predicate" and kind == "minecraft:entity_properties":
                out[k] = self.entity(v)
            elif k == "predicate" and kind == "minecraft:damage_source_properties":
                out[k] = self.damage(v)
            elif k == "predicate" and kind == "minecraft:location_check":
                out[k] = self.location(v)
            elif k == "predicate" and kind == "minecraft:match_tool":
                out[k] = self.item(v)
            else:
                out[k] = v
        if kind not in SAME_CONDITIONS | {"minecraft:all_of", "minecraft:any_of", "minecraft:inverted",
                                          "minecraft:entity_properties", "minecraft:damage_source_properties",
                                          "minecraft:location_check"}:
            sys.exit(f"lootfmt: loot condition {kind} has no 1.21.1 conversion")
        return out

    def entity(self, p):
        out = {}
        for k, v in p.items():
            if k not in ENTITY_KEYS:
                sys.exit(f"lootfmt: entity predicate key {k} has no 1.21.1 conversion")
            key = ENTITY_KEYS[k]
            if key in ("vehicle", "passenger", "targeted_entity"):
                v = self.entity(v)
            elif key in ("location", "stepping_on"):
                v = self.location(v)
            elif key == "equipment":
                v = {slot: self.item(i) for slot, i in v.items()}
            out[key] = v
        return out

    def damage(self, p):
        out = {}
        for k, v in p.items():
            if k in ("direct_entity", "source_entity"):
                out[k] = self.entity(v)
            elif k == "tags":
                out[k] = [{"id": t["id"].lstrip("#"), "expected": t["expected"]} for t in v]
            elif k == "is_direct":
                out[k] = v
            else:
                sys.exit(f"lootfmt: damage source predicate key {k} has no 1.21.1 conversion")
        return out

    def location(self, p):
        out = dict(p)
        if "biomes" in out:
            ids = out["biomes"] if isinstance(out["biomes"], list) else [out["biomes"]]
            mapped = []
            for b in ids:
                if b in self.biomes:
                    if self.biomes[b] is None:
                        raise Missing(f"biome {b} is not backported yet")
                    mapped.append(self.biomes[b])
                else:
                    mapped.append(b)
            out["biomes"] = mapped[0] if len(mapped) == 1 else mapped
        return out

    def item(self, p):
        out = dict(p)
        if "predicates" in out:
            preds = {}
            for k, v in out["predicates"].items():
                preds[ITEM_PREDICATES.get(k, k)] = v
            out["predicates"] = preds
        return out


def item_names(value):
    """Item ids of a loot table's item entries."""
    if isinstance(value, list):
        return [n for v in value for n in item_names(v)]
    if isinstance(value, dict):
        own = [value["name"]] if value.get("type") == "minecraft:item" and "name" in value else []
        return own + [n for v in value.values() for n in item_names(v)]
    return []
