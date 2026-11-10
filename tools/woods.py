"""Wood sets (26.x poplar, pale oak) in 1.21.1's forms; called from Port.run.

- Signs: 26.x draws signs as block models from a 32x32 `block/<wood>_sign.png` / `<wood>_hanging_sign.png`; 1.21.1
  draws them with SignRenderer / HangingSignRenderer from 64x32 `entity/signs[/hanging]/<wood>.png`. Each 26.x model
  face is a rectangle of the 32x32 file that 1.21.1's cube UVs put at one place (and orientation) of the 64x32 file;
  SIGN_MAP / HANGING_MAP list them (found by searching every vanilla wood's two files for a placement that matches all
  of them). Every run proves the maps: remapping each 26.x vanilla wood must give 1.21.1's own entity texture pixel for
  pixel, except STICK_TOP (the standing sign stick's top face, inside the board: 26.x has no face for it, it stays
  transparent). Blockstates and the particle model are 1.21.1's sign form (one model with the particle texture).
- Boats: 26.x boat entity textures keep 1.21.1's layout (checked each run: 26.x oak equals 1.21.1's oak in every
  visible pixel; the files differ only under alpha 0).
- Fuel and compost values: 26.x Items gives each item a ContextIntProvider key (ITEM_DATA, from Items.java); the
  values are read from those data files (cooking time, compost chance), into NeoForge's furnace_fuels / compostables
  data maps. Stripping comes from 26.x's block_transformer/axe.json, into NeoForge's strippables data map.
"""
import io
import json
import sys

from PIL import Image

NS = "haven_backports"
VANILLA = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"]
FLIP = [None, Image.FLIP_LEFT_RIGHT, Image.FLIP_TOP_BOTTOM, Image.ROTATE_180]
# 26.x 32x32 rectangle (x0, y0, x1, y1) -> (flip, x, y) in 1.21.1's 64x32 file.
SIGN_MAP = {(0, 0, 24, 2): (0, 2, 0), (0, 2, 24, 14): (0, 2, 2), (0, 16, 24, 28): (0, 28, 2), (0, 28, 24, 30): (2, 26, 0),
            (24, 2, 26, 14): (0, 26, 2), (24, 16, 26, 28): (0, 0, 2), (28, 0, 30, 14): (0, 2, 16),
            (28, 16, 30, 30): (0, 6, 16), (28, 30, 30, 32): (2, 4, 14), (30, 0, 32, 14): (0, 4, 16),
            (30, 16, 32, 30): (0, 0, 16)}
HANGING_MAP = {(0, 0, 16, 4): (0, 4, 0), (0, 4, 16, 6): (0, 4, 4), (0, 7, 16, 9): (0, 24, 4), (0, 16, 2, 26): (0, 0, 14),
               (2, 14, 16, 16): (0, 2, 12), (2, 16, 16, 26): (0, 2, 14), (2, 26, 16, 28): (2, 16, 12),
               (16, 4, 20, 6): (0, 20, 4), (16, 7, 20, 9): (0, 0, 4), (0, 9, 16, 13): (3, 20, 0),
               (16, 16, 18, 26): (0, 16, 14), (18, 16, 32, 26): (0, 18, 14), (20, 0, 32, 6): (0, 14, 6),
               (22, 7, 25, 13): (0, 0, 6), (28, 8, 31, 12): (0, 6, 7)}
STICK_TOP = {(2, 14), (3, 14), (2, 15), (3, 15)}

# 26.x Items.java: (item suffix or id) -> (cookingFuel key, compostable key)
ITEM_DATA = {
    "planks": ("time_wood_blocks", None), "log": ("time_wood_blocks", None), "wood": ("time_wood_blocks", None),
    "fence": ("time_wood_blocks", None), "fence_gate": ("time_wood_blocks", None), "stairs": ("time_wood_blocks", None),
    "pressure_plate": ("time_wood_blocks", None), "trapdoor": ("time_wood_blocks", None),
    "shelf": ("time_wood_blocks", None), "slab": ("time_wood_slabs", None), "button": ("time_wood_items_extra_small", None),
    "door": ("time_wood_items_large", None), "sign": ("time_wood_items_large", None),
    "hanging_sign": ("time_hanging_signs", None), "boat": ("time_boats", None), "chest_boat": ("time_boats", None),
    "sapling": ("time_dry_plants", "low"), "leaves": (None, "low"),
    "leaf_litter": ("time_dry_plants", "low"), "red_shrub": (None, "low"), "shelf_mushroom": (None, "medium"),
    "pale_moss_block": (None, "medium"), "pale_moss_carpet": (None, "low"), "pale_hanging_moss": (None, "low"),
    "eyeblossom": (None, "medium"),
    "bush": (None, "low"), "firefly_bush": (None, "low"), "wildflowers": (None, "low"), "cactus_flower": (None, "low"),
    "short_dry_grass": ("time_dry_plants", "low"), "tall_dry_grass": ("time_dry_plants", "low"),   # golden dandelion: none
}
# 26.x has no name for the three poplar leaves (no lang key); its only poplar leaves name is "Poplar Leaves".
LANG_FALLBACK = {"red_poplar_leaves": "block.minecraft.poplar_leaves", "orange_poplar_leaves": "block.minecraft.poplar_leaves",
                 "yellow_poplar_leaves": "block.minecraft.poplar_leaves"}


def wood_set(w, leaves):
    """The 26.x blocks of a wood, in the order Blocks.java registers them (sign/wall/hanging included)."""
    return [f"{w}_planks", f"{w}_sapling", f"{w}_log", f"stripped_{w}_log", f"{w}_wood", f"stripped_{w}_wood", *leaves,
            f"{w}_sign", f"{w}_wall_sign", f"{w}_hanging_sign", f"{w}_wall_hanging_sign", f"{w}_pressure_plate",
            f"{w}_trapdoor", f"potted_{w}_sapling", f"{w}_button", f"{w}_stairs", f"{w}_slab", f"{w}_fence_gate",
            f"{w}_fence", f"{w}_door"]


def no_item(w):
    return {f"{w}_wall_sign", f"{w}_wall_hanging_sign", f"potted_{w}_sapling"}


def sign_kind(b):
    if b.endswith("_hanging_sign"):
        return "hanging"
    return "sign" if b.endswith("_sign") else None


def remap(png: bytes, table) -> Image.Image:
    img = Image.open(io.BytesIO(png)).convert("RGBA")
    out = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for (x0, y0, x1, y1), (t, ox, oy) in table.items():
        c = img.crop((x0, y0, x1, y1))
        out.paste(c.transpose(FLIP[t]) if FLIP[t] is not None else c, (ox, oy))
    return out


def prove_sign_maps(port):
    def norm(im):
        return [p if p[3] else (0, 0, 0, 0) for p in im.get_flattened_data()]
    for kind, new, old, table, allowed in (("sign", "block/{}_sign", "entity/signs/{}", SIGN_MAP, STICK_TOP),
                                           ("hanging", "block/{}_hanging_sign", "entity/signs/hanging/{}", HANGING_MAP, set())):
        for w in VANILLA:
            a = norm(remap(port.jar.read(f"assets/minecraft/textures/{new.format(w)}.png"), table))
            b = norm(Image.open(io.BytesIO(port.old.read(f"assets/minecraft/textures/{old.format(w)}.png"))).convert("RGBA"))
            bad = {(i % 64, i // 64) for i in range(len(a)) if a[i] != b[i]} - allowed
            if bad:
                sys.exit(f"{kind} remap fails on {w}: pixels {sorted(bad)[:8]}; signs need porting by hand")


def sign_block(port, res, b):
    """1.21.1 sign blockstate and particle model; the entity texture (remapped) and the hanging GUI texture."""
    kind = sign_kind(b)
    standing = b.replace("_wall_", "_")
    wood = standing[:-len("_hanging_sign")] if kind == "hanging" else standing[:-len("_sign")]
    m26 = port.read(f"assets/minecraft/models/block/{standing}_rot_0.json")
    particle = port.texture(m26["textures"]["particle"])
    port.write(res / "assets" / NS / "models/block" / f"{standing}.json", {"textures": {"particle": particle}})
    port.write(res / "assets" / NS / "blockstates" / f"{b}.json", {"variants": {"": {"model": f"{NS}:block/{standing}"}}})
    if b != standing:
        return
    src = f"assets/minecraft/textures/block/{standing}.png"
    out = res / "assets" / NS / "textures/entity/signs" / ("hanging/" if kind == "hanging" else "") / f"{wood}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    remap(port.jar.read(src), HANGING_MAP if kind == "hanging" else SIGN_MAP).save(out)
    if kind == "hanging":
        gui = res / "assets" / NS / "textures/gui/hanging_signs" / f"{wood}.png"
        gui.parent.mkdir(parents=True, exist_ok=True)
        gui.write_bytes(port.jar.read(f"assets/minecraft/textures/gui/hanging_signs/{wood}.png"))


def visible(png: bytes):
    """RGBA pixels with every fully transparent pixel the same (Mojang re-saves differ only there)."""
    return [p if p[3] else (0, 0, 0, 0) for p in Image.open(io.BytesIO(png)).convert("RGBA").get_flattened_data()]


def boats(port, res, wood):
    for kind in ("boat", "chest_boat"):
        if visible(port.jar.read(f"assets/minecraft/textures/entity/{kind}/oak.png")) != \
                visible(port.old.read(f"assets/minecraft/textures/entity/{kind}/oak.png")):
            sys.exit(f"26.x {kind} textures changed layout; port boats by hand")
        out = res / "assets" / NS / f"textures/entity/{kind}" / f"{wood}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(port.jar.read(f"assets/minecraft/textures/entity/{kind}/{wood}.png"))


def item_data(name):
    for suffix in sorted(ITEM_DATA, key=len, reverse=True):
        if name == suffix or name.endswith("_" + suffix):
            return ITEM_DATA[suffix]
    return None, None


def data_maps(port, res, items):
    fuels, compost = {}, {}
    for i in items:
        fuel, comp = item_data(i)
        if fuel:
            fuels[f"{NS}:{i}"] = {"burn_time": port.read(f"data/minecraft/context_int_provider/cooking/{fuel}.json")["left"]}
        if comp:
            dist = port.read(f"data/minecraft/context_int_provider/compostable/{comp}.json")["default"]["distribution"]
            chance = sum(d["weight"] for d in dist if d["data"] == 1) / sum(d["weight"] for d in dist)
            compost[f"{NS}:{i}"] = {"chance": chance}
    strip = {}
    for transformer in port.read("data/minecraft/block_transformer/axe.json"):
        if transformer["sound"] != "minecraft:item.axe.strip":
            continue   # scraping and wax removal: the copper data maps (tieins.copper_maps)
        for rule in transformer["block_state_provider"].get("rules", []):
            src = port.id(rule["if_true"].get("blocks", ""))
            then = rule["then"]
            if src.startswith(f"{NS}:") and then.get("type") == "minecraft:copy_properties":
                strip[src] = {"stripped_block": port.id(then["source"]["id"])}
    port.write(res / "data/neoforge/data_maps/item/furnace_fuels.json", {"values": fuels})
    port.write(res / "data/neoforge/data_maps/item/compostables.json", {"values": compost})
    port.write(res / "data/neoforge/data_maps/block/strippables.json", {"values": strip})
