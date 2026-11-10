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
STAGES = ["", "exposed_", "weathered_", "oxidized_"]
STATUES = [f"{w}{st}copper_golem_statue" for w in ("", "waxed_") for st in STAGES]
BLOCKS = {"ghast": ["dried_ghast"], "golem": STATUES}
CUSHIONS = [f"{c}_cushion" for c in COLORS]
ITEMS = [*HARNESSES, "happy_ghast_spawn_egg", "copper_golem_spawn_egg", *CUSHIONS]
ENTITIES = ["happy_ghast", "copper_golem", "cushion"]
COPPER_SETS = ["copper_golem_statue"]   # oxidation and waxing data maps (tieins.copper_maps)
ENTITY_TEXTURES = ["entity/ghast/happy_ghast", "entity/ghast/happy_ghast_baby", "entity/ghast/happy_ghast_ropes",
                   *[f"entity/equipment/happy_ghast_body/{h}" for h in HARNESSES],
                   *[f"entity/copper_golem/copper_golem{e}{s}" for e in ("", "_eyes") for s in ("", "_exposed", "_weathered", "_oxidized")],
                   *[f"entity/cushion/{c}" for c in CUSHIONS]]
# Keyframe animation classes converted to NeoForge JSON (animations.py): class -> (folder, field prefix)
ANIMATIONS = {"net/minecraft/client/animation/definitions/CopperGolemAnimation": ("copper_golem", "COPPER_GOLEM_")}
SOUNDS = [f"entity.happy_ghast.{s}" for s in ("ambient", "death", "hurt", "riding", "equip", "unequip",
                                               "harness_goggles_up", "harness_goggles_down")] \
    + [f"entity.ghastling.{s}" for s in ("ambient", "death", "hurt", "spawn")] \
    + [f"block.dried_ghast.{s}" for s in ("break", "step", "fall", "ambient", "ambient_water", "place",
                                          "place_in_water", "transition")] \
    + ["item.lead.tied", "item.lead.break"] \
    + [f"entity.copper_golem{st}.{s}" for st in ("", "_weathered", "_oxidized") for s in ("spin", "hurt", "death", "step")] \
    + [f"entity.copper_golem.{s}" for s in ("spawn", "shear", "no_item_get", "no_item_no_get", "item_drop", "item_no_drop")] \
    + ["entity.copper_golem_become_statue"] + [f"block.copper_golem_statue.{s}" for s in ("break", "step", "place", "hit", "fall")] \
    + [f"entity.cushion.{s}" for s in ("break", "get_up", "place", "sit")]
# 26.x tags our Java reads (written under minecraft:, with our ids; see tags.py)
CODE_TAGS = ["happy_ghast_food", "happy_ghast_tempt_items", "harnesses", "can_equip_harness", "followable_friendly_mobs",
             "triggers_ambient_dried_ghast_block_sounds", "happy_ghast_avoids",
             "copper", "copper_golem_statues", "shearable_from_copper_golem", "accepts_iron_golem_gift", "candidate_for_iron_golem_gift",
             "cushion_uses_collision_shape", "cushions"]


STATS = ["happy_ghast_one_cm"]   # custom stats (lang stat.<ns>.<id>)
# 26.x loot tables that name our items, taken whole (gear.take_loot): piglin bartering adds the dried ghast (weight 10).
LOOT = ["gameplay/piglin_bartering"]


def run(port, res, lang_new, lang):
    import gear
    import animations
    gear.take_loot(port, res, LOOT)
    port.n_animations = animations.run(port, res, NS, ANIMATIONS)
    for path in ENTITY_TEXTURES:
        port.copy_texture(f"assets/minecraft/textures/{path}.png", res / "assets" / NS / "textures" / f"{path}.png")
    for st in STATS:
        lang[f"stat.{NS}.{st}"] = lang_new[f"stat.minecraft.{st}"]
    for e in ENTITIES:
        lang[f"entity.{NS}.{e}"] = lang_new[f"entity.minecraft.{e}"]
        name = f"data/minecraft/loot_table/entities/{e}.json"
        if name not in port.new_names:
            continue   # noLootTable (the cushion drops itself)
        port.write(res / "data" / NS / "loot_table/entities" / f"{e}.json", port.ids(port.loot(port.read(name))))


def special_item(port, res, item, definition) -> bool:
    """26.x special item models 1.21.1 draws through a block entity renderer (builtin/entity): the copper golem statue
    (a select on its pose; Java draws the pose). The item model keeps the special model's base transforms."""
    if definition.get("type") != "minecraft:select" or definition.get("block_state_property") != "copper_golem_pose":
        return False
    import models
    base = definition["fallback"]["base"].split(":")[-1]
    data = models.convert(port.read(f"assets/minecraft/models/{base}.json"), base)
    data["parent"] = "minecraft:builtin/entity"
    data["textures"] = {k: port.texture(v) for k, v in data.get("textures", {}).items()}
    port.write(res / "assets" / NS / "models/item" / f"{item}.json", data)
    return True


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
