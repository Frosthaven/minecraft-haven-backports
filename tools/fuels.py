"""Furnace fuel and composting for our items, as 26.x's Items class gives them (Item.Properties.cookingFuel and
compostable, each naming a ContextIntProvider).

26.x reads a burn time from a context int provider (data/minecraft/context_int_provider/cooking/time_*.json: the base
time divided by 1 in a furnace, 2 in a fast-cooking one); 1.21.1 furnaces take the base time from NeoForge's
furnace_fuels data map and halve it in blast furnaces and smokers themselves, so the base time is written.
Composting: 26.x's compostable/*.json is a weighted list of 1 (a layer) and 0; NeoForge's compostables data map takes
the chance (the weight of 1 over the total). Items are matched by their Items field: a single item by name, a ColorCollection as <color>_<name>.
"""
import json
import re

from sources import COLORS

NS = "haven_backports"


def run(port, res, ours):
    from sources import decompile
    source = decompile(port.jar.filename, "net/minecraft/world/item/Items")
    fuels, compost = {}, {}
    for m in re.finditer(r"public static final [\w<>.]+ (\w+) = (.*?);\n", source, re.S):
        field, body, colored = m.group(1).lower(), m.group(2), "ColorCollection" in m.group(0)
        f = re.search(r"cookingFuel\(ContextIntProviders\.COOKING_(\w+)\)", body)
        if f:
            fuels[field] = (burn_time(port, f.group(1).lower()), colored)
        c = re.search(r"compostable\(ContextIntProviders\.COMPOSTABLE_(\w+)\)", body)
        if c:
            compost[field] = (chance(port, c.group(1).lower()), colored)
    port.write(res / "data/neoforge/data_maps/item/furnace_fuels.json", {"values": match(ours, fuels, "burn_time")})
    port.write(res / "data/neoforge/data_maps/item/compostables.json", {"values": match(ours, compost, "chance")})


def burn_time(port, name):
    data = json.loads(port.jar.read(f"data/minecraft/context_int_provider/cooking/{name}.json"))
    if data.get("type") != "minecraft:div" or not isinstance(data.get("left"), int):
        raise SystemExit(f"cooking/{name}: a cooking time form fuels.py does not read")
    return data["left"]


def chance(port, name):
    data = json.loads(port.jar.read(f"data/minecraft/context_int_provider/compostable/{name}.json"))
    if isinstance(data, int) or data.get("type") == "minecraft:constant":
        return 1.0
    dist = data["default"]["distribution"] if data.get("type") == "minecraft:number_dispatcher" else None
    if not dist:
        raise SystemExit(f"compostable/{name}: a form fuels.py does not read")
    return sum(d["weight"] for d in dist if d["data"] == 1) / sum(d["weight"] for d in dist)


def match(ours, table, key):
    values = {}
    for item in sorted(ours):
        if item in table and not table[item][1]:
            values[f"{NS}:{item}"] = {key: table[item][0]}
        color = next((c for c in sorted(COLORS, key=len, reverse=True) if item.startswith(c + "_")), None)
        rest = item[len(color) + 1:] if color else None
        if rest in table and table[rest][1]:
            values[f"{NS}:{item}"] = {key: table[rest][0]}
    return values
