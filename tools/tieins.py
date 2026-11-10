"""Tie-ins with other mods and NeoForge data maps (split out of backport.py; called from Port.run)."""
import json
import os
import sys
import zipfile
from pathlib import Path

from backport import COPPER_SETS, FULL_CHISEL, NS, RES, SOUND_SETS, STAGES, extras, gear, mobs, spear
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
    events = ([f"block.{sset}.{kind}" for sset, kinds in SOUND_SETS.items() for kind in kinds] + gear.SOUNDS + spear.SOUNDS + extras.SOUNDS +
              mobs.SOUNDS + variants.EXTRA_SOUNDS)
    if True:
        for event in events:
            entry = json.loads(json.dumps(mojang[event]))
            for s in entry["sounds"]:
                name = s["name"] if isinstance(s, dict) else s
                if isinstance(s, dict) and s.get("type") == "event":   # a reference to another sound event
                    if name in events:
                        s["name"] = f"{NS}:{name}"
                    elif name not in port.old_sound_events:
                        raise SystemExit(f"sound {event}: refers to {name}, which is neither ours nor in 1.21.1")
                    continue
                path = name.split(":")[-1]
                data = asset(port.objects, f"minecraft/sounds/{path}.ogg")
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
