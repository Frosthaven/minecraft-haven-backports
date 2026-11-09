"""Backports content of Minecraft 26.4 snapshot 7 into Haven Backports' resources, from Mojang's own files only.

Downloads the snapshot client jar (sha1-checked, cached in .cache/) and, for every block and item in CONTENT:
- follows Mojang's references: blockstate -> block models -> parent models -> textures, item definition -> item model.
  A model 1.21.1 has with the same content, and every texture 1.21.1 has (even where 26.x retextured it: the owner
  keeps 1.21.1's look), stays vanilla (`minecraft:`); what 1.21.1 lacks becomes ours;
- converts the snapshot's newer formats to 1.21.1's: item definitions -> models/item, loot tables key for key,
  recipe ingredient shorthand -> {"item"/"tag"} objects;
- takes every snapshot recipe that makes one of ours, and EXTRA_RECIPES (new ways to make existing items) when 1.21.1
  can run them and has no equal recipe already;
- writes tags (those 1.21.1 has, or that a written recipe uses), lang, the Chipped / Rechiseled tie-ins, NeoForge
  oxidation and waxing data maps (Mojang's WeatheringCopperCollection order), and the merges for Haven Consolidated
  (META-INF/haven_consolidated/aliases.json).

Run: uv run --with pillow python -I tools/backport.py --mods <the pack's mods folder>   (or HAVEN_MODS; then ./gradlew build)
--mods lets the Amendments tie-in copy that mod's own wall-lantern models for our copper lanterns.
"""
import os
import json
import shutil
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))   # -I leaves the script folder off sys.path
import models   # noqa: E402
import modshelves   # noqa: E402
import gear   # noqa: E402
from sources import VERSION, asset, canon, models_in, old_format, pixels, snapshot   # noqa: E402

REPO = Path(__file__).resolve().parent.parent
RES = REPO / "src/main/resources"
NS = "haven_backports"
VANILLA_1211 = Path.home() / ".local/share/PrismLauncher/libraries/com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar"

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]
STAGES = ["", "exposed_", "weathered_", "oxidized_"]
COPPER_SETS = ["copper_bars", "copper_chain", "copper_lantern"]
# Mojang's shelf woods that 1.21.1 grows; pale oak and poplar shelves come with the Pale Garden and Dappled Forest.
SHELF_WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"]


def stairs_slab_wall(prefix):
    return [f"{prefix}_stairs", f"{prefix}_slab", f"{prefix}_wall"]


# Every block (with its item unless listed in NO_ITEM) and item we backport, in Mojang's creative tab order per family.
CONTENT = {
    "calcite": ["calcite_stairs", "calcite_slab", "calcite_wall", "calcite_pillar", "polished_calcite",
                *stairs_slab_wall("polished_calcite"), "polished_calcite_bricks", *stairs_slab_wall("polished_calcite_brick")],
    "snow": ["snow_bricks", *stairs_slab_wall("snow_brick")],
    "packed_ice": ["packed_ice_bricks", *stairs_slab_wall("packed_ice_brick"), "chiseled_packed_ice"],
    "wool": [f"{c}_wool_{s}" for c in COLORS for s in ("stairs", "slab")],
    "concrete": [f"{c}_concrete_{s}" for c in COLORS for s in ("stairs", "slab")],
    "copper": [f"{w}{st}{s}" for s in COPPER_SETS for w in ("", "waxed_") for st in STAGES] + ["copper_torch", "copper_wall_torch"],
    "shelf": [f"{w}_shelf" for w in SHELF_WOODS],
}
ITEMS_ONLY = ["copper_nugget"]
NO_ITEM = {"copper_wall_torch"}   # its item is the copper torch, as for the vanilla torch
FULL_CHISEL = {"calcite": ["polished_calcite", "polished_calcite_bricks", "calcite_pillar"]}
# New snapshot recipes for items 1.21.1 already has are taken when 1.21.1 can run them and has no equal one, except:
EXTRA_EXCLUDE = {
    "bundle": "1.21.1's bundle sits behind an experimental feature flag; enabling bundles is its own backport",
    "suspicious_stew_from_": "1.21.1's special suspicious stew recipe already makes every one of these",
}
# Snapshot recipes for our own items that a pack mod already provides in the same shape and counts (duplicates in JEI):
COVERED_BY_PACK = {
    "copper_nugget": "Create's crafting/materials/copper_nugget (1 c:ingots/copper -> 9; Create's nugget merges into ours)",
    "copper_ingot_from_nuggets": "Create's crafting/materials/copper_ingot (9 c:nuggets/copper -> 1)",
}
PARTICLES = ["copper_fire_flame"]
# New sound events 1.21.1 lacks: block sound sets, and the shelf's own (ShelfBlock's SoundEvents.SHELF_*).
SOUND_SETS = {"packed_ice": ["break", "fall", "hit", "place", "step"],
              "shelf": ["break", "fall", "hit", "place", "step",
                        "activate", "deactivate", "take_item", "single_swap", "place_item", "multi_swap"]}
# Snapshot tags our Java reads (ShelfBlock.isConnectable: #minecraft:wooden_shelves), written with our values.
CODE_TAGS = ["wooden_shelves", "incorrect_for_copper_tool", "repairs_copper_armor"]

# Other mods' copies of the same block or item: Mojang's look is the reference, so they become ours.
ALIASES = [
    ("new_slab_variants:calcite_slab", "calcite_slab", True),
    ("chipped:calcite_bricks", "polished_calcite_bricks", True),
    ("chipped:calcite_pillar", "calcite_pillar", True),
    ("create:calcite_pillar", "calcite_pillar", True),
    ("chipped:packed_ice_bricks", "packed_ice_bricks", True),
    ("create:copper_bars", "waxed_copper_bars", True),        # never oxidize: the waxed variant keeps that
    ("createdeco:copper_bars", "waxed_copper_bars", True),
    ("mcwlights:copper_chain", "waxed_copper_chain", True),
    ("create:copper_nugget", "copper_nugget", False),
    # Create Stuff 'N Additions' copper gear is Mojang's concept with its own stats; Mojang's version is the reference.
    *[(f"create_sa:copper_{p}", f"copper_{p}", False) for p in ("sword", "shovel", "pickaxe", "axe", "hoe",
                                                                 "helmet", "chestplate", "leggings", "boots")],
    *[(f"new_slab_variants:{c}_wool_slab", f"{c}_wool_slab", True) for c in COLORS],
    *[(f"new_slab_variants:{c}_concrete_slab", f"{c}_concrete_slab", True) for c in COLORS],
]
# Not merged: TFMG's colored concrete slabs and stairs belong to TFMG's own concrete (tfmg:<color>_concrete, its own
# textures), a different material from vanilla concrete.
# 26.x picks the render layer from texture transparency; 1.21.1 needs it set. Each takes its vanilla counterpart's layer
# from 1.21.1's ItemBlockRenderTypes: iron bars and chain cutout_mipped, torch, wall torch and lantern cutout.
RENDER_TYPES = {"copper_bars": "minecraft:cutout_mipped", "copper_chain": "minecraft:cutout_mipped",
                "copper_lantern": "minecraft:cutout", "copper_torch": "minecraft:cutout", "copper_wall_torch": "minecraft:cutout"}
RECIPE_TYPES_1211 = {"crafting_shaped", "crafting_shapeless", "stonecutting", "smelting", "blasting", "smoking",
                     "campfire_cooking", "smithing_transform"}

BLOCKS = [b for family in CONTENT.values() for b in family]
OURS = set(BLOCKS) | set(ITEMS_ONLY) | set(gear.ITEMS)


class Port:
    def __init__(self):
        self.jar, self.objects = snapshot()
        self.old = zipfile.ZipFile(VANILLA_1211)
        self.old_names = set(self.old.namelist())
        self.new_names = set(self.jar.namelist())
        self.ours_models: set[str] = set()     # paths like "block/copper_bars_post"
        self.ours_textures: set[str] = set()   # paths like "block/copper_bars"
        self.changed_vanilla: set[str] = set()
        self.recipe_tags: set[str] = set()
        self.loot_changes: dict = {}

    def read(self, name):
        return json.loads(self.jar.read(name))

    def same_as_1211(self, name) -> bool:
        return name in self.old_names and self.old.read(name) == self.jar.read(name)

    # ---------- references ----------
    def model(self, ref: str) -> str:
        """Walks a model reference; returns the id to write (ours or vanilla)."""
        ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
        if ns != "minecraft" or path.startswith("builtin/"):
            return ref
        name = f"assets/minecraft/models/{path}.json"
        if path in self.ours_models:
            return f"{NS}:{path}"
        if name not in self.new_names:
            sys.exit(f"model {ref} is in neither jar")
        data = models.convert(self.read(name), name)
        if name in self.old_names and models.plain(data) == models.plain(json.loads(self.old.read(name))):
            return f"minecraft:{path}"   # the same model; its on_shelf (if any) comes from the vanilla patches
        self.ours_models.add(path)
        if "parent" in data:
            data["parent"] = self.model(data["parent"])
        if "textures" in data:
            data["textures"] = {k: (v if v.startswith("#") else self.texture(v)) for k, v in data["textures"].items()}
        self.write(RES / "assets" / NS / "models" / f"{path}.json", data)
        return f"{NS}:{path}"

    def texture(self, ref: str) -> str:
        ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
        if ns != "minecraft" or path.startswith("trims/items/"):
            return f"{ns}:{path}"   # trim textures are atlas permutations (gear.py adds copper_darker), not files
        name = f"assets/minecraft/textures/{path}.png"
        if name in self.old_names:
            # 1.21.1 has this texture: keep its look (owner's choice 2026-11-09), even where 26.x retextured it, so a
            # backported block matches the vanilla blocks it is made from and nothing already placed changes look.
            if self.old.read(name) != self.jar.read(name) and pixels(self.old.read(name)) != pixels(self.jar.read(name)):
                self.changed_vanilla.add(path)   # a real retexture, not only a re-saved file
            return f"minecraft:{path}"
        if path not in self.ours_textures:
            self.ours_textures.add(path)
            self.copy_texture(name, RES / "assets" / NS / "textures" / f"{path}.png")
        return f"{NS}:{path}"

    def copy_texture(self, name, out: Path):
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(self.jar.read(name))
        if name + ".mcmeta" in self.new_names:
            Path(str(out) + ".mcmeta").write_bytes(self.jar.read(name + ".mcmeta"))

    def blockstate(self, value):
        if isinstance(value, dict):
            return {k: (self.model(v) if k == "model" else self.blockstate(v)) for k, v in value.items()}
        if isinstance(value, list):
            return [self.blockstate(v) for v in value]
        return value

    # ---------- ids ----------
    @staticmethod
    def id(value: str) -> str:
        """minecraft:<one of ours> -> haven_backports:<it>."""
        if value.startswith("minecraft:") and value[10:] in OURS:
            return f"{NS}:{value[10:]}"
        return value

    def ids(self, value):
        if isinstance(value, dict):
            return {k: self.ids(v) for k, v in value.items()}
        if isinstance(value, list):
            return [self.ids(v) for v in value]
        return self.id(value) if isinstance(value, str) else value

    def exists_1211_item(self, item_id: str) -> bool:
        ns, path = item_id.split(":")
        if ns == NS:
            return True
        return ns == "minecraft" and (f"assets/minecraft/models/item/{path}.json" in self.old_names)

    # ---------- formats ----------
    def loot(self, value):
        if isinstance(value, list):
            return [self.loot(v) for v in value]
        if not isinstance(value, dict):
            return value
        out = {}
        for k, v in value.items():
            if k == "modifier":   # one function or a list of them
                out["functions"] = [self.function(f) for f in (v if isinstance(v, list) else [v])]
            elif k == "condition" and isinstance(v, dict):
                out["conditions"] = [self.condition(v)]
            elif k == "conditions":
                out["conditions"] = [self.condition(c) for c in v]
            else:
                out[k] = self.loot(v)
        return out

    def function(self, f):
        f = self.loot(f)
        f["function"] = f.pop("type")
        return f

    def condition(self, c):
        c = self.loot(c)
        kind = c.pop("type")
        if kind == "minecraft:match_block":
            c = {"block": c["blocks"], "properties": c.get("state", {})}
            kind = "minecraft:block_state_property"
        c["condition"] = kind
        return c

    def ingredient(self, value):
        """Returns the 1.21.1 ingredient, or None when it names something 1.21.1 cannot provide."""
        if isinstance(value, list):
            parts = [self.ingredient(v) for v in value]
            return None if any(p is None for p in parts) else parts
        if isinstance(value, str):
            if value.startswith("#"):
                self.recipe_tags.add(value[1:])
                return {"tag": value[1:]}
            item = self.id(value)
            return {"item": item} if self.exists_1211_item(item) else None
        return self.ids(value)

    def recipe(self, r: dict):
        if r.get("type", "").split(":")[-1] not in RECIPE_TYPES_1211:
            return None
        r = dict(r)
        for key in ("key",):
            if key in r:
                converted = {k: self.ingredient(v) for k, v in r[key].items()}
                if any(v is None for v in converted.values()):
                    return None
                r[key] = converted
        for key in ("ingredient", "base", "addition", "template"):
            if key in r:
                r[key] = self.ingredient(r[key])
                if r[key] is None:
                    return None
        if "ingredients" in r:
            r["ingredients"] = [self.ingredient(v) for v in r["ingredients"]]
            if any(v is None for v in r["ingredients"]):
                return None
        r["result"] = self.ids(r["result"])
        return r

    def equal_in_1211(self, r: dict) -> bool:
        """1.21.1 already has a recipe with this type, result and ingredients (renamed in the snapshot)."""
        key = canon(r)
        return key in self.old_recipe_keys

    # ---------- output ----------
    @staticmethod
    def write(path: Path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + "\n")

    def run(self):
        for d in (RES / "assets", RES / "data", RES / "META-INF/haven_consolidated"):   # META-INF also holds hand-written files
            shutil.rmtree(d, ignore_errors=True)
        lang_new = self.read("assets/minecraft/lang/en_us.json")
        lang = {}
        for b in BLOCKS:
            state = self.blockstate(self.read(f"assets/minecraft/blockstates/{b}.json"))
            self.write(RES / "assets" / NS / "blockstates" / f"{b}.json", state)
            layer = next((t for k, t in RENDER_TYPES.items() if b.endswith(k)), None)
            if layer:
                for ref in models_in(state):
                    if ref.startswith(f"{NS}:"):
                        f = RES / "assets" / NS / "models" / f"{ref.split(':')[1]}.json"
                        m = json.loads(f.read_text())
                        m["render_type"] = layer
                        self.write(f, m)
            loot_name = f"data/minecraft/loot_table/blocks/{b}.json"
            if loot_name in self.new_names:   # wall variants have none: they drop like their standing block (dropsLike)
                self.write(RES / "data" / NS / "loot_table/blocks" / f"{b}.json", self.ids(self.loot(self.read(loot_name))))
            lang[f"block.{NS}.{b}"] = lang_new[f"block.minecraft.{b}"]
        for i in [b for b in BLOCKS if b not in NO_ITEM] + ITEMS_ONLY:
            item_def = self.read(f"assets/minecraft/items/{i}.json")["model"]
            if item_def.get("type") != "minecraft:model":
                sys.exit(f"item {i}: item model type {item_def.get('type')} needs porting by hand")
            ref = self.model(item_def["model"])
            if ref != f"{NS}:item/{i}":   # an item model of the same name was just written by model(): it is the model
                self.write(RES / "assets" / NS / "models/item" / f"{i}.json", {"parent": ref})
            if i in ITEMS_ONLY:
                lang[f"item.{NS}.{i}"] = lang_new[f"item.minecraft.{i}"]

        for p in PARTICLES:
            data = self.read(f"assets/minecraft/particles/{p}.json")
            # particle definitions name textures without the particle/ folder
            data["textures"] = [self.texture("minecraft:particle/" + t.split(":")[-1]).replace("particle/", "") for t in data["textures"]]
            self.write(RES / "assets" / NS / "particles" / f"{p}.json", data)

        self.sounds(lang, lang_new)
        self.mod_shelves(lang)
        gear.run(self, RES, lang_new, lang)
        for table, change in self.loot_changes.items():
            print(f"loot {table}: 26.x adds {change['added']}, removes {change['removed']}")
        self.write(RES / "assets" / NS / "lang/en_us.json", dict(sorted(lang.items())))
        self.shelf_displays()
        self.recipes()
        self.tags()
        import tieins   # imported here: it reads this module's constants
        tieins.tie_ins(self)
        tieins.copper_maps(self)
        tieins.amendments(self)
        self.write(RES / "META-INF/haven_consolidated/aliases.json",
                   {"aliases": [{"old": o, "new": f"{NS}:{n}", "block": blk} for o, n, blk in ALIASES]})
        print(f"{VERSION}: {len(BLOCKS)} blocks, {len(ITEMS_ONLY)} items, {len(self.ours_models)} models, "
              f"{len(self.ours_textures)} textures, {self.n_shelf_patches} vanilla models given on_shelf, {self.n_recipes} recipes ({self.n_extra} extra), {self.n_tags} tags, "
              f"vanilla textures 26.x really retextured (pixels differ), kept at 1.21.1: {sorted(self.changed_vanilla)}")

    def mod_shelves(self, lang):
        mods = sys.argv[sys.argv.index("--mods") + 1] if "--mods" in sys.argv else os.environ.get("HAVEN_MODS")
        if not mods:
            sys.exit("modded shelves need --mods / HAVEN_MODS (the pack's mods folder)")
        self.modded_shelves, names, base = modshelves.run(self, RES, Path(mods))
        lang.update(names)
        print(f"modded shelves: {len(self.modded_shelves)}, art from: {base}")
        java = REPO / "src/main/java/dev/frosthaven/havenbackports/ModdedShelves.java"
        rows = ",\n".join(f'            new Wood("{sid}", "{ns}", "{planks}", {str(nether).lower()})'
                           for sid, ns, planks, nether in self.modded_shelves)
        java.write_text(f"""package dev.frosthaven.havenbackports;

import java.util.List;

/** Generated by tools/backport.py (tools/modshelves.py): shelves for the pack's modded woods. Do not edit by hand. */
final class ModdedShelves {{
    /** id, the wood's mod, that mod's planks (map color), nether wood (not flammable, like crimson and warped). */
    record Wood(String id, String mod, String planks, boolean nether) {{}}

    static final List<Wood> WOODS = List.of(
{rows});

    private ModdedShelves() {{}}
}}
""")

    def shelf_displays(self):
        patches, notes = models.vanilla_shelf_patches(self.jar, self.old)
        for path, data in patches.items():
            self.write(RES / "assets/minecraft/models" / f"{path}.json", data)
        self.n_shelf_patches = len(patches)
        if notes:
            print(f"on_shelf added to vanilla models 26.x also changed otherwise (1.21.1 content kept): {notes}")

    def sounds(self, lang, lang_new):
        mojang = json.loads(asset(self.objects, "minecraft/sounds.json"))
        out = {}
        events = [f"block.{sset}.{kind}" for sset, kinds in SOUND_SETS.items() for kind in kinds] + gear.SOUNDS
        if True:
            for event in events:
                entry = json.loads(json.dumps(mojang[event]))
                for s in entry["sounds"]:
                    name = s["name"] if isinstance(s, dict) else s
                    path = name.split(":")[-1]
                    data = asset(self.objects, f"minecraft/sounds/{path}.ogg")
                    out_file = RES / "assets" / NS / "sounds" / f"{path}.ogg"
                    out_file.parent.mkdir(parents=True, exist_ok=True)
                    out_file.write_bytes(data)
                    if isinstance(s, dict):
                        s["name"] = f"{NS}:{path}"
                    else:
                        entry["sounds"][entry["sounds"].index(s)] = f"{NS}:{path}"
                if "subtitle" in entry and entry["subtitle"] not in self.old_lang():
                    lang[entry["subtitle"]] = lang_new[entry["subtitle"]]   # Mojang's subtitle text, in our lang
                out[event] = entry
        self.write(RES / "assets" / NS / "sounds.json", out)

    def old_lang(self):
        if not hasattr(self, "_old_lang"):
            self._old_lang = json.loads(self.old.read("assets/minecraft/lang/en_us.json"))
        return self._old_lang

    def recipes(self):
        self.old_recipe_keys = set()
        for n in self.old_names:
            if n.startswith("data/minecraft/recipe/") and n.endswith(".json"):
                self.old_recipe_keys.add(canon(old_format(json.loads(self.old.read(n)))))
        self.n_recipes = self.n_extra = 0
        for n in sorted(self.new_names):
            if not (n.startswith("data/minecraft/recipe/") and n.endswith(".json")):
                continue
            rid = n.split("/")[-1][:-5]
            raw = self.read(n)
            result = raw.get("result", {})
            result_id = (result.get("id") if isinstance(result, dict) else result) or ""
            ours = result_id.split(":")[-1] in OURS
            if rid in COVERED_BY_PACK:
                continue
            if not ours:
                if n in self.old_names or ":" not in result_id or not self.exists_1211_item(result_id):
                    continue
                if any(rid == x or (x.endswith("_") and rid.startswith(x)) for x in EXTRA_EXCLUDE):
                    continue
            r = self.recipe(raw)
            if r is None or self.equal_in_1211(r):
                continue
            self.write(RES / "data" / NS / "recipe" / f"{rid}.json", r)
            self.n_recipes += 1
            self.n_extra += 0 if ours else 1

    def tags(self):
        """Tags 1.21.1 has, or a written recipe uses, with our ids; plus the snapshot sub-tags they reference
        (slabs -> #wool_slabs, #concrete_slabs ...) when those hold ours."""
        tag_files = {n: self.read(n) for n in self.new_names if n.startswith("data/minecraft/tags/") and n.endswith(".json")}

        def own(n, seen=()):
            """Our values of a tag file, following #references into other snapshot tags (written as references)."""
            values = []
            for v in tag_files[n].get("values", []):
                if not isinstance(v, str):
                    continue
                if v.startswith("#") and n.split("/")[-1][:-5] in CODE_TAGS:
                    values.append(v)   # a tag only our Java reads keeps Mojang's references as they are
                    continue
                if v.startswith("#"):
                    ref = n.rsplit("/tags/", 1)[0] + "/tags/" + n.split("/tags/", 1)[1].split("/", 1)[0] + "/" + v[1:].split(":")[-1] + ".json"
                    if ref in tag_files and ref not in self.old_names and ref not in seen and holds_ours(ref, seen + (n,)):
                        values.append(v)
                    continue
                vid = self.id(v)
                recipe_tag = n.startswith("data/minecraft/tags/item/") and "minecraft:" + n.split("/", 4)[4][:-5] in self.recipe_tags
                # ours always; vanilla entries only in a tag a written recipe needs (metal_nuggets), never in a tag
                # 1.21.1 has or a sub-tag it would reach (that would change 1.21.1's own tag behaviour)
                code_tag = n.split("/")[-1][:-5] in CODE_TAGS and n not in self.old_names
                if vid.startswith(f"{NS}:") or ((recipe_tag or code_tag) and n not in self.old_names and self.exists_1211_item(vid)):
                    values.append(vid)
            return values

        def holds_ours(n, seen=()):
            for v in tag_files[n].get("values", []):
                if isinstance(v, str) and v.startswith("#"):
                    ref = n.split("/tags/", 1)[0] + "/tags/" + n.split("/tags/", 1)[1].split("/", 1)[0] + "/" + v[1:].split(":")[-1] + ".json"
                    if ref in tag_files and ref not in seen and holds_ours(ref, seen + (n,)):
                        return True
                elif isinstance(v, str) and self.id(v).startswith(f"{NS}:"):
                    return True
            return False

        wanted = [n for n in tag_files if n in self.old_names or n.split("/")[-1][:-5] in CODE_TAGS or
                  (n.startswith("data/minecraft/tags/item/") and "minecraft:" + n.split("/", 4)[4][:-5] in self.recipe_tags)]
        written, queue = set(), list(wanted)
        while queue:
            n = queue.pop()
            if n in written:
                continue
            values = own(n)
            if not values:
                continue
            written.add(n)
            self.write(RES / n, {"replace": False, "values": values})
            for v in values:
                if v.startswith("#"):
                    queue.append(n.split("/tags/", 1)[0] + "/tags/" + n.split("/tags/", 1)[1].split("/", 1)[0] + "/" + v[1:].split(":")[-1] + ".json")
        for kind in ("block", "item"):
            f = RES / f"data/minecraft/tags/{kind}/wooden_shelves.json"
            data = json.loads(f.read_text())
            data["values"] += [f"{NS}:{sid}" for sid, *_ in self.modded_shelves]
            self.write(f, data)
        nether = [f"{NS}:{sid}" for sid, _, _, n in self.modded_shelves if n]
        if nether:
            f = RES / "data/minecraft/tags/item/non_flammable_wood.json"
            data = json.loads(f.read_text())
            data["values"] += nether
            self.write(f, data)
        self.n_tags = len(written)



if __name__ == "__main__":
    Port().run()
