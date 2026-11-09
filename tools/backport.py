"""Backports content of Minecraft 26.4 snapshot 7 into Haven Backports' resources, from Mojang's own files only.

Downloads the snapshot client jar from Mojang's version manifest (checked against its sha1, cached in .cache/), reads
the 12 new blocks' blockstates, block models, textures, loot tables, recipes and tags, and writes them under our
namespace in 1.21.1's formats:
- ids `minecraft:<new block>` become `haven_consolidated:<new block>`; vanilla ids (calcite, parent models) stay;
- recipe ingredients written as plain id strings (the snapshot's shorthand) become `{"item": ...}` objects;
- the snapshot's `assets/minecraft/items/*.json` item definitions become 1.21.1 item models (`models/item`);
- block and item tags keep only the tags 1.21.1 has (walls, stairs, slabs, mineable/pickaxe, ...).
It also writes the tie-ins: Chipped's `calcite` tags and a Rechiseled chiseling group (both merge with the mods' own).
Vanilla textures the snapshot changed (calcite's retexture) are shipped too, so the new shapes and the old block match as
in the snapshot. Loot tables are converted key for key from the snapshot's newer loot format; merges with other mods'
copies of the same block go to META-INF/haven_consolidated/aliases.json (Haven Consolidated carries them out).

Run: python3 -I tools/backport.py   (then ./gradlew build)
"""
import hashlib
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

VERSION = "26.4-snapshot-7"
REPO = Path(__file__).resolve().parent.parent
RES = REPO / "src/main/resources"
CACHE = REPO / ".cache"
NS = "haven_backports"
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
VANILLA_1211 = Path.home() / ".local/share/PrismLauncher/libraries/com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar"

BLOCKS = [
    "calcite_stairs", "calcite_slab", "calcite_wall", "calcite_pillar",
    "polished_calcite", "polished_calcite_stairs", "polished_calcite_slab", "polished_calcite_wall",
    "polished_calcite_bricks", "polished_calcite_brick_stairs", "polished_calcite_brick_slab",
    "polished_calcite_brick_wall",
]
FULL = ["polished_calcite", "polished_calcite_bricks", "calcite_pillar"]
CHANGED_VANILLA_TEXTURES = ["calcite"]   # retextured by the snapshot; its new shapes use the new look
# Other mods' copies of the same block: Mojang's look is the reference, so they become ours (Haven Consolidated).
ALIASES = [
    ("new_slab_variants:calcite_slab", "calcite_slab"),
    ("chipped:calcite_bricks", "polished_calcite_bricks"),
    ("chipped:calcite_pillar", "calcite_pillar"),
    ("create:calcite_pillar", "calcite_pillar"),
]
LOOT_KEYS = {"modifier": "functions"}   # chisel groups take full blocks only


def get(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read()


def snapshot_jar() -> zipfile.ZipFile:
    path = CACHE / f"minecraft-{VERSION}-client.jar"
    if not path.exists():
        manifest = json.loads(get(MANIFEST))
        url = next(v["url"] for v in manifest["versions"] if v["id"] == VERSION)
        client = json.loads(get(url))["downloads"]["client"]
        data = get(client["url"])
        if hashlib.sha1(data).hexdigest() != client["sha1"]:
            sys.exit(f"{VERSION} client jar: sha1 mismatch")
        CACHE.mkdir(exist_ok=True)
        path.write_bytes(data)
    return zipfile.ZipFile(path)


def ours(name: str) -> bool:
    """A model, texture or block id that the snapshot adds (ours now), as opposed to a vanilla 1.21.1 one."""
    return any(name == b or name.startswith(b + "_") for b in BLOCKS) or name in NEW_TEXTURES


def rewrite(value):
    """minecraft:<new thing> -> haven_consolidated:<new thing>, recursively; everything else unchanged."""
    if isinstance(value, dict):
        return {k: rewrite(v) for k, v in value.items()}
    if isinstance(value, list):
        return [rewrite(v) for v in value]
    if isinstance(value, str) and value.startswith("minecraft:"):
        path = value[len("minecraft:"):]
        leaf = path.split("/")[-1]
        if ours(leaf) and (path.startswith("block/") or "/" not in path):
            return f"{NS}:{path}"
    return value


def ingredient(value):
    """1.21.1 needs {"item": id} / {"tag": id}; the snapshot writes "id" / "#id" (or a list of them)."""
    if isinstance(value, list):
        return [ingredient(v) for v in value]
    if isinstance(value, str):
        return {"tag": value[1:]} if value.startswith("#") else {"item": rewrite(value)}
    return rewrite(value)


def loot(value):
    """The snapshot's loot format -> 1.21.1's: modifier -> functions, "type" of a function -> "function", a single
    "condition" -> a "conditions" list, "type" of a condition -> "condition", match_block (blocks, state) ->
    block_state_property (block, properties). Same meaning, older spelling."""
    if isinstance(value, list):
        return [loot(v) for v in value]
    if not isinstance(value, dict):
        return value
    out = {}
    for k, v in value.items():
        if k == "modifier":
            out["functions"] = [function(f) for f in v]
        elif k == "condition" and isinstance(v, dict):
            out["conditions"] = [condition(v)]
        elif k == "conditions":
            out["conditions"] = [condition(c) for c in v]
        else:
            out[k] = loot(v)
    return out


def function(f: dict) -> dict:
    f = loot(f)
    f["function"] = f.pop("type")
    return f


def condition(c: dict) -> dict:
    c = loot(c)
    kind = c.pop("type")
    if kind == "minecraft:match_block":
        c = {"block": c["blocks"], "properties": c.get("state", {})}
        kind = "minecraft:block_state_property"
    c["condition"] = kind
    return c


def write(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def merge_tag(path: Path, values: list) -> None:
    """Adds values to a tag file that other generators (gen.py's mineable/pickaxe) also write, keeping theirs."""
    current = json.loads(path.read_text()) if path.exists() else {"replace": False, "values": []}
    for v in values:
        if v not in current["values"]:
            current["values"].append(v)
    write(path, current)


def main() -> None:
    jar = snapshot_jar()
    names = jar.namelist()
    read = lambda n: json.loads(jar.read(n))
    global NEW_TEXTURES
    NEW_TEXTURES = {n.split("/")[-1][:-4] for n in names
                    if n.startswith("assets/minecraft/textures/block/") and n.endswith(".png") and "calcite" in n} - {"calcite"}
    old = zipfile.ZipFile(VANILLA_1211)
    old_tags = {n for n in old.namelist() if n.startswith("data/minecraft/tags/")}

    assets = RES / "assets" / NS
    for t in sorted(NEW_TEXTURES):
        out = assets / "textures/block" / f"{t}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(jar.read(f"assets/minecraft/textures/block/{t}.png"))
    for vanilla in CHANGED_VANILLA_TEXTURES:
        out = RES / f"assets/minecraft/textures/block/{vanilla}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(jar.read(f"assets/minecraft/textures/block/{vanilla}.png"))

    lang = read("assets/minecraft/lang/en_us.json")
    our_lang = {}
    for b in BLOCKS:
        write(assets / "blockstates" / f"{b}.json", rewrite(read(f"assets/minecraft/blockstates/{b}.json")))
        item_def = read(f"assets/minecraft/items/{b}.json")
        write(assets / "models/item" / f"{b}.json", {"parent": rewrite(item_def["model"]["model"])})
        our_lang[f"block.{NS}.{b}"] = lang[f"block.minecraft.{b}"]
        write(RES / "data" / NS / "loot_table/blocks" / f"{b}.json", loot(rewrite(read(f"data/minecraft/loot_table/blocks/{b}.json"))))
    for n in names:
        if n.startswith("assets/minecraft/models/block/") and ours(n.split("/")[-1][:-5]):
            write(assets / "models/block" / n.split("/")[-1], rewrite(read(n)))

    recipes = 0
    for n in names:
        if not (n.startswith("data/minecraft/recipe/") and n.endswith(".json")):
            continue
        r = read(n)
        if not ours(r.get("result", {}).get("id", "").split(":")[-1]):
            continue
        if "key" in r:
            r["key"] = {k: ingredient(v) for k, v in r["key"].items()}
        if "ingredient" in r:
            r["ingredient"] = ingredient(r["ingredient"])
        if "ingredients" in r:
            r["ingredients"] = ingredient(r["ingredients"])
        r["result"] = rewrite(r["result"])
        write(RES / "data" / NS / "recipe" / n.split("/")[-1], r)
        recipes += 1

    tags = 0
    for n in names:
        if not (n.startswith("data/minecraft/tags/") and n.endswith(".json")) or n not in old_tags:
            continue
        values = [v for v in read(n).get("values", []) if isinstance(v, str) and v.startswith("minecraft:") and ours(v[10:])]
        if values:
            merge_tag(RES / n, rewrite(values))
            tags += 1

    entries = [{"id": f"{NS}:{b}", "required": False} for b in FULL]
    for kind in ("block", "item"):
        merge_tag(RES / f"data/chipped/tags/{kind}/calcite.json", entries)
    write(RES / f"data/{NS}/chiseling_recipes/calcite.json", {
        "type": "rechiseled:chiseling", "overwrite": False,
        "entries": [{"item": "minecraft:calcite"}] + [{"item": f"{NS}:{b}"} for b in FULL]})

    lang_path = assets / "lang/en_us.json"
    lang_path.parent.mkdir(parents=True, exist_ok=True)
    merged = json.loads(lang_path.read_text()) if lang_path.exists() else {}
    merged.update(our_lang)
    lang_path.write_text(json.dumps(dict(sorted(merged.items())), indent=2) + "\n")
    write(RES / "META-INF/haven_consolidated/aliases.json",
          {"aliases": [{"old": old_id, "new": f"{NS}:{new}", "block": True} for old_id, new in ALIASES]})
    print(f"{VERSION}: {len(BLOCKS)} blocks, {len(NEW_TEXTURES)} textures, {recipes} recipes, {tags} tags")


NEW_TEXTURES: set = set()

if __name__ == "__main__":
    main()
