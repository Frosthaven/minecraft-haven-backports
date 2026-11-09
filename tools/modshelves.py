"""Shelves for the pack's modded woods (owner's choice 2026-11-09: every wood gets a shelf, recolored at build).

Mojang drew each vanilla shelf texture by hand (no region is copied from the log textures), so a modded wood has no
Mojang shelf art. Its texture is Mojang's shelf art from the vanilla wood whose stripped log looks closest, with every
color moved to the same place in the modded stripped log's light-to-dark range; Mojang's extra dark shades stay darker
by the same ratio. Blockstate, models, loot and recipe follow the vanilla shelf's files one for one.

Works without the wood's mod (owner's rule): the texture is ours and is also the particle (nothing of the other mod's
art is copied or referenced), the recipe has a mod_loaded condition, and Java reads the mod's planks only when present.
Names come from the mod's own name for its stripped log ("Stripped Fir Log" -> "Fir Shelf").
"""
import io
import json
import re
import zipfile
from pathlib import Path

NS = "haven_backports"
VANILLA = {"oak": "stripped_oak_log", "spruce": "stripped_spruce_log", "birch": "stripped_birch_log",
           "jungle": "stripped_jungle_log", "acacia": "stripped_acacia_log", "dark_oak": "stripped_dark_oak_log",
           "mangrove": "stripped_mangrove_log", "cherry": "stripped_cherry_log", "bamboo": "stripped_bamboo_block",
           "crimson": "stripped_crimson_stem", "warped": "stripped_warped_stem"}
# (mod id, wood, stripped block kind). One shelf per stripped log the mod gives a name (an item players can have).
# A wood a later mod adds needs one line here and a rebuild.
MOD_WOODS = [
    ("ad_astra", "glacian", "log"),
    ("ars_elemental", "yellow_archwood", "log"),
    ("ars_nouveau", "blue_archwood", "log"), ("ars_nouveau", "green_archwood", "log"),
    ("ars_nouveau", "purple_archwood", "log"), ("ars_nouveau", "red_archwood", "log"),
    *[("biomesoplenty", w, "log") for w in ("dead", "empyreal", "fir", "hellbark", "jacaranda", "magic", "mahogany",
                                          "maple", "palm", "pine", "redwood", "umbran", "willow")],
    ("garnished", "nut", "log"), ("garnished", "sepia", "stem"),
]


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def palette(*images):
    """Opaque colors, dark to light, each with its share of the pixels (a log's main color outweighs its specks)."""
    counts = {}
    for im in images:
        for p in im.getdata():
            if p[3] > 0:
                counts[p] = counts.get(p, 0) + 1
    cols = sorted(counts, key=lum)
    total, run, out = sum(counts.values()), 0, []
    for c in cols:
        out.append((c, (run + counts[c] / 2) / total))   # the color's middle place in the pixel distribution
        run += counts[c]
    return out


def colour_at(pal, q):
    """The color at place q (0..1) of a weighted palette."""
    for c, mid in pal:
        if mid >= q:
            return c
    return pal[-1][0]


def signature(pal, n=8):
    return [colour_at(pal, (i + 0.5) / n) for i in range(n)]


def distance(a, b):
    return sum((x[k] - y[k]) ** 2 for x, y in zip(signature(a), signature(b)) for k in range(3))


def recolor(shelf, src_pal, dst_pal):
    """Each shelf color to the modded palette at its place in the vanilla stripped log's pixel distribution."""
    from PIL import Image
    src_cols = [c for c, _ in src_pal]
    src_l = [lum(c) for c in src_cols]
    place = dict(src_pal)
    darkest = colour_at(dst_pal, 0.0)
    out = Image.new("RGBA", shelf.size)
    px, opx = shelf.load(), out.load()
    cache = {}
    for y in range(shelf.size[1]):
        for x in range(shelf.size[0]):
            c = px[x, y]
            if c[3] == 0:
                opx[x, y] = c
                continue
            if c not in cache:
                L = lum(c)
                if c in place:
                    t = colour_at(dst_pal, place[c])
                elif L < src_l[0]:      # Mojang's added shadow: darker than the log's darkest by the same ratio
                    f = L / max(1.0, src_l[0])
                    t = (round(darkest[0] * f), round(darkest[1] * f), round(darkest[2] * f), 255)
                elif L > src_l[-1]:
                    t = colour_at(dst_pal, 1.0)
                else:                   # between two log colors: its place by brightness
                    i = next(k for k in range(1, len(src_l)) if src_l[k] >= L)
                    f = (L - src_l[i - 1]) / max(1e-6, src_l[i] - src_l[i - 1])
                    t = colour_at(dst_pal, src_pal[i - 1][1] + f * (src_pal[i][1] - src_pal[i - 1][1]))
                cache[c] = (t[0], t[1], t[2], c[3])
            opx[x, y] = cache[c]
    return out


def find_jar(mods: Path, ns: str, block: str):
    for j in sorted(mods.glob("*.jar")):
        try:
            z = zipfile.ZipFile(j)
        except zipfile.BadZipFile:
            continue
        if f"assets/{ns}/blockstates/{block}.json" in z.namelist():
            return z
    raise SystemExit(f"modded shelf: no jar in {mods} has {ns}:{block}")


def run(port, res: Path, mods: Path):
    """Writes the modded shelves next to the vanilla ones; returns [(id, mod id, planks id, nether)] for Java."""
    from PIL import Image
    jar, old = port.jar, port.old

    def img(z, name):
        return Image.open(io.BytesIO(z.read(name))).convert("RGBA")

    vanilla = {}
    for w, log in VANILLA.items():
        side = img(old if f"assets/minecraft/textures/block/{log}.png" in port.old_names else jar,
                   f"assets/minecraft/textures/block/{log}.png")
        top = img(old if f"assets/minecraft/textures/block/{log}_top.png" in port.old_names else jar,
                  f"assets/minecraft/textures/block/{log}_top.png")
        vanilla[w] = (palette(side, top), img(jar, f"assets/minecraft/textures/block/{w}_shelf.png"))
    lang, shelves, base_used = {}, [], {}
    oak_state = json.loads(jar.read("assets/minecraft/blockstates/oak_shelf.json"))
    oak_loot = json.loads(jar.read("data/minecraft/loot_table/blocks/oak_shelf.json"))
    oak_recipe = json.loads(jar.read("data/minecraft/recipe/oak_shelf.json"))
    parts = sorted({m.split("oak_shelf", 1)[1] for m in re.findall(r"minecraft:block/(oak_shelf[a-z_]*)", json.dumps(oak_state))}
                   | {"_inventory"})
    for ns, wood, kind in MOD_WOODS:
        log = f"stripped_{wood}_{kind}"
        z = find_jar(mods, ns, log)
        names = set(z.namelist())
        tex = [f"assets/{ns}/textures/block/{log}.png", f"assets/{ns}/textures/block/{log}_top.png"]
        pal = palette(*[img(z, t) for t in tex if t in names])
        base = min(vanilla, key=lambda w: distance(vanilla[w][0], pal))
        base_used[wood] = base
        sid = f"{wood}_shelf"
        out = res / "assets" / NS / "textures/block" / f"{sid}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        recolor(vanilla[base][1], vanilla[base][0], pal).save(out)
        tex_id = f"{NS}:block/{sid}"
        for part in parts:   # the vanilla shelf models, with our texture (also the particle: see the module doc)
            m = json.loads(jar.read(f"assets/minecraft/models/block/oak_shelf{part}.json"))
            m["parent"] = port.model(m["parent"])
            m["textures"] = {"all": tex_id, "particle": tex_id}
            port.write(res / "assets" / NS / "models/block" / f"{sid}{part}.json", m)
        port.write(res / "assets" / NS / "blockstates" / f"{sid}.json",
                   json.loads(json.dumps(oak_state).replace("minecraft:block/oak_shelf", f"{NS}:block/{sid}")))
        port.write(res / "assets" / NS / "models/item" / f"{sid}.json", {"parent": f"{NS}:block/{sid}_inventory"})
        port.write(res / "data" / NS / "loot_table/blocks" / f"{sid}.json",
                   port.loot(json.loads(json.dumps(oak_loot).replace("minecraft:oak_shelf", f"{NS}:{sid}")
                                        .replace("minecraft:blocks/oak_shelf", f"{NS}:blocks/{sid}"))))
        recipe = json.loads(json.dumps(oak_recipe))
        recipe["key"] = {"#": {"item": f"{ns}:{log}"}}
        recipe["result"]["id"] = f"{NS}:{sid}"
        recipe["neoforge:conditions"] = [{"type": "neoforge:mod_loaded", "modid": ns}]
        port.write(res / "data" / NS / "recipe" / f"{sid}.json", recipe)
        mod_lang = json.loads(z.read(f"assets/{ns}/lang/en_us.json"))
        log_name = mod_lang[f"block.{ns}.{log}"]
        lang[f"block.{NS}.{sid}"] = re.sub(r"^Stripped (.*) (Log|Stem|Block)$", r"\1 Shelf", log_name)
        planks = "ars_nouveau:archwood_planks" if wood.endswith("archwood") else f"{ns}:{wood}_planks"
        nft = f"data/minecraft/tags/item/non_flammable_wood.json"
        nether = nft in names and f"{planks}" in json.dumps(json.loads(z.read(nft)))
        shelves.append((sid, ns, planks, nether))
    return shelves, lang, base_used
