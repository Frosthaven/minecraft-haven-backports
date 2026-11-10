"""Bundles as 26.x has them (split out of backport.py; called through extras.run): the 16 dyed bundles, the open
bundle models the GUI shows around the selected item, the tooltip sprites, and the dye recipes.

- Item definitions: 26.x selects on the display context and, in the GUI with a selected item, composes
  <bundle>_open_back, the selected item and <bundle>_open_front. 1.21.1 has no item definitions, so the three models
  are written as plain models and Java (client/BundleRenderer) draws them in that order.
- 26.x's bundle model has no "filled" variant (it drew no bundle_filled art); minecraft:item/bundle is written as
  26.x's, dropping 1.21.1's filled override.
- Dye recipes are crafting_transmute (keep the bundle's contents): written for haven_backports:crafting_transmute,
  a port of 26.x's TransmuteRecipe.
"""
import sys

NS = "haven_backports"
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]
DYED = [f"{c}_bundle" for c in COLORS]
SOUNDS = ["item.bundle.insert_fail"]
LANG = ["item.minecraft.bundle.empty", "item.minecraft.bundle.empty.description", "item.minecraft.bundle.full"]
SPRITES = "assets/minecraft/textures/gui/sprites/container/bundle/"
LOOT = ["chests/village/village_snowy_house", "chests/village/village_plains_house", "chests/village/village_savanna_house",
        "chests/village/village_desert_house", "chests/village/village_taiga_house", "chests/village/village_cartographer",
        "chests/village/village_tannery"]


def run(port, res, lang_new, lang):
    for item in ["bundle"] + DYED:
        definition = port.read(f"assets/minecraft/items/{item}.json")["model"]
        if definition.get("type") != "minecraft:select" or definition.get("property") != "minecraft:display_context":
            sys.exit(f"{item}: 26.x item definition changed shape, port it by hand")
        (case,) = definition["cases"]
        layers = case["model"]["on_true"]["models"]
        if case["when"] != "gui" or [m["type"] for m in layers] != ["minecraft:model", "minecraft:bundle/selected_item", "minecraft:model"]:
            sys.exit(f"{item}: 26.x open bundle layers changed, port them by hand")
        for m in (layers[0], layers[2]):
            port.model(m["model"])   # ours: <bundle>_open_back / _open_front (1.21.1 has neither)
        fallback = definition["fallback"]["model"]
        if item == "bundle":
            data = port.read(f"assets/minecraft/models/{fallback.split(':')[1]}.json")
            data["textures"] = {k: port.texture(v) for k, v in data["textures"].items()}
            port.write(res / "assets/minecraft/models/item/bundle.json", data)
        else:
            if port.model(fallback) != f"{NS}:item/{item}":
                sys.exit(f"{item}: its model is not item/{item}")
            lang[f"item.{NS}.{item}"] = lang_new[f"item.minecraft.{item}"]
    for key in LANG:
        lang[key] = lang_new[key]
    for name in sorted(port.new_names):
        if name.startswith(SPRITES) and name.endswith(".png") and name not in port.old_names:
            port.copy_texture(name, res / "assets" / NS / "textures/gui/sprites/container/bundle" / name.rsplit("/", 1)[1])
    for c in COLORS:
        r = port.read(f"data/minecraft/recipe/{c}_bundle.json")
        if r["type"] != "minecraft:crafting_transmute" or set(r) - {"type", "category", "group", "input", "material", "result"}:
            sys.exit(f"recipe {c}_bundle: 26.x changed its shape")
        r["type"] = f"{NS}:crafting_transmute"
        r["input"] = port.ingredient(r["input"])
        r["material"] = port.ingredient(r["material"])
        r["result"] = port.ids(r["result"])
        port.write(res / f"data/{NS}/recipe/{c}_bundle.json", r)
