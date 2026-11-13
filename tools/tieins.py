"""Tie-ins with other mods and NeoForge data maps (split out of backport.py; called from Port.run)."""
import json
import os
import sys
import zipfile
from pathlib import Path

from backport import COPPER_SETS, FULL_CHISEL, NS, OURS, RES, SOUND_SETS, STAGES, extras, gear, mobs, spear
from sources import asset


def tie_ins(port):
    for family, blocks in FULL_CHISEL.items():
        entries = [{"id": f"{NS}:{b}", "required": False} for b in blocks]
        for kind in ("block", "item"):
            port.write(RES / f"data/chipped/tags/{kind}/{family}.json", {"replace": False, "values": entries})
        port.write(RES / f"data/{NS}/chiseling_recipes/{family}.json", {
            "type": "rechiseled:chiseling", "overwrite": False,
            "entries": [{"item": f"minecraft:{family}"}] + [{"item": f"{NS}:{b}"} for b in blocks]})

def amendments(port):
    """Amendments hangs every lantern on walls through its own blockstates and bracket models; it ships them for
    other mods' copper lanterns (its "oxidized" set) and a bracket texture per copper stage. Ours reuse that exact
    structure from the Amendments jar, each with the bracket of its stage (waxed ones as their stage)."""
    mods = sys.argv[sys.argv.index("--mods") + 1] if "--mods" in sys.argv else os.environ.get("HAVEN_MODS")
    jar = next((Path(mods) / f for f in sorted(os.listdir(mods)) if f.startswith("amendments")), None) if mods else None
    if not jar:
        print("amendments: no --mods / HAVEN_MODS given, wall lantern tie-in left out")
        return
    z = zipfile.ZipFile(jar)
    base = "assets/amendments/blockstates/oxidized/wall_copper_lantern.json"
    state = json.loads(z.read(base))
    models = sorted({v["model"] for v in state["variants"].values()})
    bracket = {"": "wall_lantern_copper", "exposed_": "wall_lantern_copper_exposed",
               "weathered_": "wall_lantern_copper_weathered", "oxidized_": "wall_lantern_copper_oxidized"}
    for waxed in ("", "waxed_"):
        for st in STAGES:
            name = f"{waxed}{st}copper_lantern"
            ours = lambda m: m.replace("amendments:block/oxidized/wall_copper_lantern", f"amendments:block/{NS}/wall_{name}")
            port.write(RES / f"assets/amendments/blockstates/{NS}/wall_{name}.json",
                       {"variants": {k: {**v, "model": ours(v["model"])} for k, v in state["variants"].items()}})
            for m in models:
                data = json.loads(z.read("assets/amendments/models/block/" + m.split("block/", 1)[1] + ".json"))
                data["support"]["textures"]["1"] = f"amendments:block/wall_lanterns/{bracket[st]}"
                port.write(RES / "assets/amendments/models/block" / f"{ours(m).split('block/', 1)[1]}.json", data)

def copper_maps(port):
    """Mojang's WeatheringCopperCollection: unaffected -> exposed -> weathered -> oxidized, each with a waxed twin.
    NeoForge reads these data maps for random oxidation, honeycomb waxing and axe scraping."""
    oxidizables, waxables = {}, {}
    for s in COPPER_SETS + mobs.COPPER_SETS:
        for a, b in zip(STAGES, STAGES[1:]):
            oxidizables[f"{NS}:{a}{s}"] = {"next_oxidation_stage": f"{NS}:{b}{s}"}
        for st in STAGES:
            waxables[f"{NS}:{st}{s}"] = {"waxed": f"{NS}:waxed_{st}{s}"}
    port.write(RES / "data/neoforge/data_maps/block/oxidizables.json", {"values": oxidizables})
    port.write(RES / "data/neoforge/data_maps/block/waxables.json", {"values": waxables})


def sulfur_dust(port):
    """Mojang's sulfur blocks crush into the pack's shared sulfur dust, as the pack crushes sulfur ore: create_mf's
    sulfur ore crushing (its results and processing time, read from its jar) with the output named
    haven_consolidated:sulfur_dust (the dust Haven Consolidated merges create_mf's sulfur into). Only the four full
    blocks; slabs, stairs and walls do not crush. Each recipe needs Create and Haven Consolidated. The pack has no
    milling or Mekanism recipe for sulfur ore, so neither is added."""
    mods = sys.argv[sys.argv.index("--mods") + 1] if "--mods" in sys.argv else os.environ.get("HAVEN_MODS")
    jar = next((Path(mods) / f for f in sorted(os.listdir(mods)) if f.startswith("create_mf")), None) if mods else None
    if not jar:
        print("sulfur dust: no create_mf jar in --mods / HAVEN_MODS, crushing tie-in left out")
        return
    ore = json.loads(zipfile.ZipFile(jar).read("data/create_mf/recipe/sulfurfromorerecipe.json"))
    if ore["type"] != "create:crushing" or any(r["id"] != "create_mf:sulfur" for r in ore["results"]):
        sys.exit("create_mf's sulfur ore crushing changed shape; check the sulfur dust tie-in by hand")
    for block in ("sulfur", "polished_sulfur", "sulfur_bricks", "chiseled_sulfur"):
        port.write(RES / f"data/{NS}/recipe/crushing/{block}.json", {
            "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "create"},
                                    {"type": "neoforge:mod_loaded", "modid": "haven_consolidated"}],
            "type": "create:crushing",
            "ingredients": [{"item": f"{NS}:{block}"}],
            "processing_time": ore["processing_time"],
            "results": [{**r, "id": "haven_consolidated:sulfur_dust"} for r in ore["results"]]})


OUR_WOODS = ("pale_oak", "poplar")
# the vanilla overworld woods with the same item set as ours: a recipe is "per wood" when a mod ships it for each
TWIN_WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")


def wood_recipes(port):
    """Other mods' per-wood recipes, for our woods (Mekanism's sawing, Farmer's Delight's cutting: 1.21.1 mods list
    the vanilla woods one by one, so ours had none). A recipe is taken when its mod ships the same recipe for every
    vanilla overworld wood and it names wood only through vanilla ids: ours is that recipe with the wood swapped,
    each id checked (an item of ours, a tag we write). Recipes that name a mod's own wood item or a wood type in a
    component (Sophisticated Storage, furniture mods) need that mod's blocks and are left out. Create is not here:
    it builds its saw recipes for every mod's wood at run time."""
    mods = sys.argv[sys.argv.index("--mods") + 1] if "--mods" in sys.argv else os.environ.get("HAVEN_MODS")
    if not mods:
        print("wood recipes: no --mods / HAVEN_MODS given, other mods' per-wood recipes left out")
        return
    import re
    wood_id = re.compile(r"^minecraft:(stripped_)?acacia_(\w+)$")
    n = 0
    for jar in sorted(Path(mods).glob("*.jar")):
        try:
            z = zipfile.ZipFile(jar)
        except zipfile.BadZipFile:
            continue
        recipes = {}
        for name in z.namelist():
            parts = name.split("/")
            if len(parts) > 3 and parts[0] == "data" and parts[2] == "recipe" and name.endswith(".json") and parts[1] != NS:
                text = z.read(name).decode("utf-8", "replace")
                if any(f"minecraft:{w}_" in text or f"minecraft:stripped_{w}_" in text for w in TWIN_WOODS):
                    try:
                        recipes[name] = json.loads(text)
                    except ValueError:
                        pass
        if not recipes:
            continue
        known = {json.dumps(r, sort_keys=True) for r in recipes.values()}

        def swap(value, wood, ours, key=None):
            """The recipe with acacia swapped for `wood`; None when it names something we cannot give."""
            if isinstance(value, dict):
                out = {k: swap(v, wood, ours, k) for k, v in value.items()}
                return None if any(v is None for v in out.values()) else out
            if isinstance(value, list):
                out = [swap(v, wood, ours, key) for v in value]
                return None if any(v is None for v in out) else out
            if not isinstance(value, str) or "acacia" not in value:
                return value
            m = wood_id.match(value)
            if not m:
                return None   # a mod's own acacia item, a wood type name
            name = f"{m.group(1) or ''}{wood}_{m.group(2)}"
            if not ours:
                return f"minecraft:{name}"
            if key == "tag":
                return f"minecraft:{name}" if (RES / f"data/minecraft/tags/item/{name}.json").exists() else None
            return f"{NS}:{name}" if name in OURS else None

        for name, recipe in sorted(recipes.items()):
            if "acacia" not in json.dumps(recipe):
                continue
            twins = [swap(recipe, w, False) for w in TWIN_WOODS]
            if any(t is None or json.dumps(t, sort_keys=True) not in known for t in twins):
                continue
            ns, rid = name.split("/")[1], name.split("/", 3)[3][:-5]
            for wood in OUR_WOODS:
                ours = swap(recipe, wood, True)
                if ours is None:
                    print(f"wood recipes: {ns}:{rid} left out for {wood} (names an item or tag we do not have)")
                    continue
                conditions = [{"type": "neoforge:mod_loaded", "modid": ns}] + ours.pop("neoforge:conditions", [])
                port.write(RES / f"data/{NS}/recipe/wood/{ns}/{rid.replace('acacia', wood)}.json",
                           {"neoforge:conditions": conditions, **ours})
                n += 1
    print(f"wood recipes: {n} per-wood recipes of other mods written for {', '.join(OUR_WOODS)}")


# Vanilla items newer Minecraft renamed (id kept: changing it would break every mod naming the old id). Mojang's new
# name from its own lang file is written over 1.21.1's key.
RENAMED = {"block.minecraft.chain": "block.minecraft.iron_chain"}


def renames(port, lang_new):
    port.write(RES / "assets/minecraft/lang/en_us.json", {old: lang_new[new] for old, new in RENAMED.items()})


def sounds(port, lang, lang_new):
    """Every sound event of ours, Mojang's entries and files; variants.EXTRA_SOUNDS is filled by variants.collect."""
    import variants
    mojang = json.loads(asset(port.objects, "minecraft/sounds.json"))
    out = {}
    import backport
    events = ([f"block.{sset}.{kind}" for sset, kinds in SOUND_SETS.items() for kind in kinds] + gear.SOUNDS + spear.SOUNDS + extras.SOUNDS +
              mobs.SOUNDS + variants.EXTRA_SOUNDS)
    for sset in backport.DATA_SOUND_SETS:   # 26.x sound sets: the events their data names
        events += [e.split(":")[-1] for e in port.read(f"data/minecraft/block_sound_set/{sset}.json").values()]
    events += [e for e in backport.EXTRA_SOUNDS if e not in events]
    events = list(dict.fromkeys(events))
    # sound files 1.21.1 ships with the same hash stay vanilla; so do music tracks 1.21.1 ships (26.x re-encoded the
    # same recordings smaller: the cave music's 11 shared tracks play 1.21.1's files, its 3 new ones ship here)
    old_index = Path.home() / ".local/share/PrismLauncher/assets/indexes/17.json"
    old_objects = json.loads(old_index.read_text())["objects"] if old_index.exists() else {}
    if True:
        for event in events:
            entry = json.loads(json.dumps(mojang[event]))
            for s in entry["sounds"]:
                name = s["name"] if isinstance(s, dict) else s
                if isinstance(s, dict) and s.get("type") == "event":   # a reference to another sound event
                    name = name.split(":")[-1]
                    if name in events:
                        s["name"] = f"{NS}:{name}"
                    elif name not in port.old_sound_events:
                        raise SystemExit(f"sound {event}: refers to {name}, which is neither ours nor in 1.21.1")
                    continue
                path = name.split(":")[-1]
                key = f"minecraft/sounds/{path}.ogg"
                if key in old_objects and (old_objects[key]["hash"] == port.objects[key]["hash"] or path.startswith("music/")):
                    if isinstance(s, dict):
                        s["name"] = f"minecraft:{path}"
                    else:
                        entry["sounds"][entry["sounds"].index(s)] = f"minecraft:{path}"
                    continue
                data = asset(port.objects, key)
                out_file = RES / "assets" / NS / "sounds" / f"{path}.ogg"
                out_file.parent.mkdir(parents=True, exist_ok=True)
                out_file.write_bytes(data)
                if isinstance(s, dict):
                    s["name"] = f"{NS}:{path}"
                else:
                    entry["sounds"][entry["sounds"].index(s)] = f"{NS}:{path}"
            if "subtitle" in entry and entry["subtitle"] not in port.old_lang():
                lang[entry["subtitle"]] = lang_new[entry["subtitle"]]   # Mojang's subtitle text, in our lang
            out[event] = entry
    port.write(RES / "assets" / NS / "sounds.json", out)
