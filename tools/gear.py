"""Copper tools and armor, copper and netherite horse armor (26.x), in 1.21.1's formats.

- Item definitions: a `select` on trim material becomes 1.21.1's `trim_type` overrides (each material's
  item_model_index from 1.21.1's trim_material files; a material 1.21.1 lacks, resin, is left out).
- Equipment textures: 26.x `entity/equipment/<layer>/<asset>.png` -> 1.21.1 `models/armor/<asset>_layer_1|2.png` and
  `entity/horse/armor/horse_armor_<asset>.png` (Haven Backports' armor materials are haven_backports:copper and
  haven_backports:netherite; the latter only for the horse: 1.21.1's netherite gives a horse 11 armor, 26.x 19).
- Copper trims on copper armor use Mojang's darker copper palette, as iron on iron does in 1.21.1: the palette,
  atlas permutations and the trim material's override.
- Loot tables that 26.x gives our items are written as Mojang's 26.x table when every item in it exists in 1.21.1 or
  is ours. 26.x's other changes to those tables come with them (saddle -> leather: saddles are craftable in 26.x, a
  recipe this mod backports); the run prints them. Tables naming items not backported yet stay at 1.21.1.
"""
import json

NS = "haven_backports"
TOOLS = ["copper_sword", "copper_shovel", "copper_pickaxe", "copper_axe", "copper_hoe"]
ARMOR = ["copper_helmet", "copper_chestplate", "copper_leggings", "copper_boots"]
HORSE = ["copper_horse_armor", "netherite_horse_armor"]
ITEMS = TOOLS + ARMOR + HORSE
SOUNDS = ["item.armor.equip_copper"]
LOOT = ["chests/nether_bridge", "chests/desert_pyramid", "chests/simple_dungeon", "chests/jungle_temple",
        "chests/end_city_treasure", "chests/stronghold_corridor", "chests/village/village_weaponsmith"]
# chests/abandoned_camp_common_chest: the abandoned camp is a 26.x structure 1.21.1 lacks.
TEXTURES = {  # 26.x equipment texture -> ours
    "entity/equipment/humanoid/copper": "models/armor/copper_layer_1",
    "entity/equipment/humanoid_leggings/copper": "models/armor/copper_layer_2",
    "entity/equipment/horse_body/copper": "entity/horse/armor/horse_armor_copper",
    "entity/equipment/horse_body/netherite": "entity/horse/armor/horse_armor_netherite",
}


def strip_ours(value, ours):
    """A loot table with our item entries removed (empty children lists dropped with them)."""
    if isinstance(value, list):
        return [strip_ours(v, ours) for v in value
                if not (isinstance(v, dict) and v.get("type") == "minecraft:item" and v.get("name") in ours)]
    if isinstance(value, dict):
        return {k: strip_ours(v, ours) for k, v in value.items()}
    return value


DEFAULTS = {"add": False, "bonus_rolls": 0}   # loot fields 1.21.1's files spell out and 26.x's leave out


def plain(value):
    if isinstance(value, list):
        return [plain(v) for v in value]
    if isinstance(value, dict):
        return {k: plain(v) for k, v in value.items() if not (k in DEFAULTS and v == DEFAULTS[k])}
    return value


def run(port, res, lang_new, lang):
    jar, old = port.jar, port.old
    trim_index = {}
    for n in port.old_names:
        if n.startswith("data/minecraft/trim_material/") and n.endswith(".json"):
            trim_index["minecraft:" + n.split("/")[-1][:-5]] = json.loads(old.read(n))["item_model_index"]

    for item in ITEMS:
        definition = json.loads(jar.read(f"assets/minecraft/items/{item}.json"))["model"]
        if definition["type"] == "minecraft:model":
            ref = port.model(definition["model"])
            if ref != f"{NS}:item/{item}":
                port.write(res / "assets" / NS / "models/item" / f"{item}.json", {"parent": ref})
        elif definition["type"] == "minecraft:select" and definition["property"] == "minecraft:trim_material":
            base = json.loads(jar.read(f"assets/minecraft/models/{definition['fallback']['model'].split(':')[1]}.json"))
            base["textures"] = {k: port.texture(v) for k, v in base["textures"].items()}
            overrides = []
            for case in definition["cases"]:
                if case["when"] in trim_index:
                    overrides.append((trim_index[case["when"]], port.model(case["model"]["model"])))
            base["overrides"] = [{"model": m, "predicate": {"trim_type": i}} for i, m in sorted(overrides)]
            port.write(res / "assets" / NS / "models/item" / f"{item}.json", base)
        else:
            raise SystemExit(f"{item}: item definition {definition['type']} needs porting by hand")
        lang[f"item.{NS}.{item}"] = lang_new[f"item.minecraft.{item}"]

    for src, dst in TEXTURES.items():
        out = res / "assets" / NS / "textures" / f"{dst}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(jar.read(f"assets/minecraft/textures/{src}.png"))

    # Darker copper trims: Mojang's palette (its iron_darker equals 1.21.1's, so the format is the same).
    from sources import pixels
    if pixels(jar.read("assets/minecraft/textures/palettes/trim/iron_darker.png")) != \
            pixels(old.read("assets/minecraft/textures/trims/color_palettes/iron_darker.png")):
        raise SystemExit("26.x trim palettes changed format; check copper_darker by hand")
    pal = res / "assets/minecraft/textures/trims/color_palettes/copper_darker.png"
    pal.parent.mkdir(parents=True, exist_ok=True)
    pal.write_bytes(jar.read("assets/minecraft/textures/palettes/trim/copper_darker.png"))
    for atlas in ("blocks", "armor_trims"):
        src = next(s for s in json.loads(old.read(f"assets/minecraft/atlases/{atlas}.json"))["sources"]
                   if s["type"] == "paletted_permutations")
        port.write(res / f"assets/minecraft/atlases/{atlas}.json", {"sources": [{
            "type": "paletted_permutations", "textures": src["textures"], "palette_key": src["palette_key"],
            "permutations": {"copper_darker": "trims/color_palettes/copper_darker"}}]})
    copper = json.loads(old.read("data/minecraft/trim_material/copper.json"))
    copper["override_armor_materials"] = {f"{NS}:copper": "copper_darker"}
    port.write(res / "data/minecraft/trim_material/copper.json", copper)

    waiting = {}
    for table in LOOT:
        name = f"data/minecraft/loot_table/{table}.json"
        new = port.ids(port.loot(json.loads(jar.read(name))))
        missing = sorted({n for n in names_in(new) if not port.exists_1211_item(n)})
        if missing:   # e.g. spears, until they are backported
            waiting[table] = missing
            continue
        port.write(res / name, new)
        port.loot_changes[table] = changes(plain(json.loads(old.read(name))), plain(new))
    if waiting:
        print(f"loot tables kept at 1.21.1 until these exist: {waiting}")


def names_in(value):
    """Item ids of a loot table's item entries."""
    if isinstance(value, list):
        return [n for v in value for n in names_in(v)]
    if isinstance(value, dict):
        own = [value["name"]] if value.get("type") == "minecraft:item" and "name" in value else []
        return own + [n for v in value.values() for n in names_in(v)]
    return []


def changes(old, new):
    """Item entries added and removed by 26.x, for the README and the report."""
    o, n = names_in(old), names_in(new)
    return {"added": sorted(set(n) - set(o)), "removed": sorted(set(o) - set(n))}
