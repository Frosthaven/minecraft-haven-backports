"""Mobs from newer Minecraft and the blocks and items that come with them (called from backport.py).

- BLOCKS, ITEMS, SOUNDS and CODE_TAGS join backport.py's lists, so blockstates, models, block loot, recipes, tags,
  sounds and lang come through its usual paths.
- run() writes what only mobs have: entity textures (1.21.1 has none of them; copied to our namespace at Mojang's
  path), entity names, entity loot tables and spawn egg names.
- layer(): 26.x picks a block's render layer from its textures' transparency; 1.21.1 needs it in the model. A block
  of ours whose textures hold transparent pixels gets cutout (only fully clear pixels) or translucent.
"""
import io

from sources import COLORS

NS = "haven_backports"

HARNESSES = [f"{c}_harness" for c in COLORS]
BLOCKS = {"ghast": ["dried_ghast"]}
ITEMS = [*HARNESSES, "happy_ghast_spawn_egg"]
ENTITIES = ["happy_ghast"]
ENTITY_TEXTURES = ["entity/ghast/happy_ghast", "entity/ghast/happy_ghast_baby", "entity/ghast/happy_ghast_ropes",
                   *[f"entity/equipment/happy_ghast_body/{h}" for h in HARNESSES]]
SOUNDS = [f"entity.happy_ghast.{s}" for s in ("ambient", "death", "hurt", "riding", "equip", "unequip",
                                               "harness_goggles_up", "harness_goggles_down")] \
    + [f"entity.ghastling.{s}" for s in ("ambient", "death", "hurt", "spawn")] \
    + [f"block.dried_ghast.{s}" for s in ("break", "step", "fall", "ambient", "ambient_water", "place",
                                          "place_in_water", "transition")] \
    + ["item.lead.tied", "item.lead.break"]   # 26.x lead sounds the happy ghast leash plays
# 26.x tags our Java reads (written under minecraft:, with our ids; see tags.py)
CODE_TAGS = ["happy_ghast_food", "happy_ghast_tempt_items", "harnesses", "can_equip_harness", "followable_friendly_mobs",
             "triggers_ambient_dried_ghast_block_sounds", "happy_ghast_avoids"]


STATS = ["happy_ghast_one_cm"]   # custom stats (lang stat.<ns>.<id>)
# 26.x loot tables that name our items, taken whole (gear.take_loot): piglin bartering adds the dried ghast (weight 10).
LOOT = ["gameplay/piglin_bartering"]


def run(port, res, lang_new, lang):
    import gear
    gear.take_loot(port, res, LOOT)
    for path in ENTITY_TEXTURES:
        port.copy_texture(f"assets/minecraft/textures/{path}.png", res / "assets" / NS / "textures" / f"{path}.png")
    for st in STATS:
        lang[f"stat.{NS}.{st}"] = lang_new[f"stat.minecraft.{st}"]
    for e in ENTITIES:
        lang[f"entity.{NS}.{e}"] = lang_new[f"entity.minecraft.{e}"]
        name = f"data/minecraft/loot_table/entities/{e}.json"
        port.write(res / "data" / NS / "loot_table/entities" / f"{e}.json", port.ids(port.loot(port.read(name))))


def layer(port, block, state):
    """cutout or translucent for a mob block whose textures (ours, from Mojang) are not opaque; None otherwise."""
    if not any(block in family for family in BLOCKS.values()):
        return None
    from PIL import Image
    from sources import models_in
    alphas = set()
    for ref in models_in(state):
        for tex in textures_of(port, ref):
            alphas |= set(Image.open(io.BytesIO(port.jar.read(f"assets/minecraft/textures/{tex}.png"))).convert("RGBA").getchannel("A").getdata())
    if alphas <= {255}:
        return None
    return "minecraft:cutout" if alphas <= {0, 255} else "minecraft:translucent"


def textures_of(port, ref):
    """Texture paths a model (and its parents) names."""
    out = []
    while ref:
        path = ref.split(":")[-1]
        data = port.read(f"assets/minecraft/models/{path}.json")
        out += [v.split(":")[-1] for v in data.get("textures", {}).values() if not v.startswith("#")]
        ref = data.get("parent")
        if ref and ref.split(":")[-1].startswith("builtin/"):
            break
    return out
