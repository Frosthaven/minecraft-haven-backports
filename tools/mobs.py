"""Mobs from newer Minecraft and the blocks and items that come with them (called from backport.py).

- BLOCKS, ITEMS, SOUNDS and CODE_TAGS join backport.py's lists, so blockstates, models, block loot, recipes, tags,
  sounds and lang come through its usual paths.
- run() writes what only mobs have: entity textures (1.21.1 has none of them; copied to our namespace at Mojang's
  path), entity names, entity loot tables and spawn egg names.
- layer(): 26.x picks a block's render layer from its textures' transparency; 1.21.1 needs it in the model. A block
  of ours whose textures hold transparent pixels gets cutout (only fully clear pixels) or translucent.
"""
import io
import json

import nautilus
from sources import COLORS

NS = "haven_backports"

HARNESSES = [f"{c}_harness" for c in COLORS]
STAGES = ["", "exposed_", "weathered_", "oxidized_"]
STATUES = [f"{w}{st}copper_golem_statue" for w in ("", "waxed_") for st in STAGES]
BLOCKS = {"ghast": ["dried_ghast"], "golem": STATUES, "straw_bed": ["straw_bed"]}
CUSHIONS = [f"{c}_cushion" for c in COLORS]
ITEMS = [*HARNESSES, "happy_ghast_spawn_egg", "copper_golem_spawn_egg", *CUSHIONS, "blue_egg", "brown_egg",
         "camel_husk_spawn_egg", "parched_spawn_egg", *nautilus.ITEMS]
ENTITIES = ["happy_ghast", "copper_golem", "cushion", "camel_husk", "parched", *nautilus.ENTITIES]
COPPER_SETS = ["copper_golem_statue"]   # oxidation and waxing data maps (tieins.copper_maps)
ENTITY_TEXTURES = ["entity/ghast/happy_ghast", "entity/ghast/happy_ghast_baby", "entity/ghast/happy_ghast_ropes",
                   *[f"entity/equipment/happy_ghast_body/{h}" for h in HARNESSES],
                   *[f"entity/copper_golem/copper_golem{e}{s}" for e in ("", "_eyes") for s in ("", "_exposed", "_weathered", "_oxidized")],
                   *[f"entity/cushion/{c}" for c in CUSHIONS],
                   "entity/camel/camel_husk", "entity/equipment/camel_husk_saddle/saddle", "entity/skeleton/parched",
                   *nautilus.ENTITY_TEXTURES]
# Keyframe animation classes converted to NeoForge JSON (animations.py): class -> (folder, field prefix)
ANIMATIONS = {"net/minecraft/client/animation/definitions/CopperGolemAnimation": ("copper_golem", "COPPER_GOLEM_"),
              **nautilus.ANIMATIONS}
SOUNDS = [f"entity.happy_ghast.{s}" for s in ("ambient", "death", "hurt", "riding", "equip", "unequip",
                                               "harness_goggles_up", "harness_goggles_down")] \
    + [f"entity.ghastling.{s}" for s in ("ambient", "death", "hurt", "spawn")] \
    + [f"block.dried_ghast.{s}" for s in ("break", "step", "fall", "ambient", "ambient_water", "place",
                                          "place_in_water", "transition")] \
    + ["item.lead.tied", "item.lead.break"] \
    + [f"entity.copper_golem{st}.{s}" for st in ("", "_weathered", "_oxidized") for s in ("spin", "hurt", "death", "step")] \
    + [f"entity.copper_golem.{s}" for s in ("spawn", "shear", "no_item_get", "no_item_no_get", "item_drop", "item_no_drop")] \
    + ["entity.copper_golem_become_statue"] + [f"block.copper_golem_statue.{s}" for s in ("break", "step", "place", "hit", "fall")] \
    + [f"entity.cushion.{s}" for s in ("break", "get_up", "place", "sit")] \
    + [f"block.straw_bed.{s}" for s in ("break", "step", "place", "hit", "fall", "break_leave")] \
    + [f"entity.camel_husk.{s}" for s in ("ambient", "dash", "dash_ready", "death", "eat", "hurt", "saddle", "sit", "stand",
                                          "step", "step_sand")] \
    + [f"entity.parched.{s}" for s in ("ambient", "death", "hurt", "step")] \
    + [f"entity.parrot.imitate.{m}" for m in ("camel_husk", "parched")] + nautilus.SOUNDS
# 26.x tags our Java reads (written under minecraft:, with our ids; see tags.py)
CODE_TAGS = ["happy_ghast_food", "happy_ghast_tempt_items", "harnesses", "can_equip_harness", "followable_friendly_mobs",
             "triggers_ambient_dried_ghast_block_sounds", "happy_ghast_avoids",
             "copper", "copper_golem_statues", "shearable_from_copper_golem", "accepts_iron_golem_gift", "candidate_for_iron_golem_gift",
             "cushion_uses_collision_shape", "cushions",
             "spawns_cold_variant_farm_animals", "spawns_warm_variant_farm_animals", "eggs",
             "camel_husk_food", "immune_to_weakness", *nautilus.CODE_TAGS]
# 26.x recipes for items 1.21.1 has that take the new eggs (#eggs), taken whole over 1.21.1's
OVERRIDE_RECIPES = ["cake", "pumpkin_pie", *nautilus.OVERRIDE_RECIPES]


STATS = ["happy_ghast_one_cm", "sleep_in_straw_bed"]   # custom stats (lang stat.<ns>.<id>)
# 26.x loot tables that name our items, patched (gear.take_loot): piglin bartering adds the dried ghast (weight 10).
LOOT = ["gameplay/piglin_bartering"]
# 26.x tables that name our entities, patched through lootfmt (extras.take_loot): a husk riding a camel husk drops
# a rabbit's foot.
ENTITY_LOOT = ["entities/husk"]
# NeoForge's parrot_imitations data map: the 26.x Parrot.MOB_SOUND_MAP entries of our mobs (entity -> sound event).
PARROT = {"camel_husk": "entity.parrot.imitate.camel_husk", "parched": "entity.parrot.imitate.parched", **nautilus.PARROT}


def run(port, res, lang_new, lang):
    import gear
    import animations
    gear.take_loot(port, res, LOOT)
    import extras
    import lootfmt
    conv = lootfmt.Converter(port.ids)
    for table in ENTITY_LOOT:
        extras.take_loot(port, res, conv, table)
    nautilus.run(port, res, lang_new, lang, conv)
    port.write(res / "data/neoforge/data_maps/entity_type/parrot_imitations.json",
               {"values": {f"{NS}:{e}": {"sound": f"{NS}:{s}"} for e, s in PARROT.items()}})
    import spawns
    spawns.run(port, res, ENTITIES)
    import advancements
    advancements.run(port, res, nautilus.EFFECTS)
    port.n_animations = animations.run(port, res, NS, ANIMATIONS)
    import variants
    variants.run(port, res, port.old_sound_events)
    for r in OVERRIDE_RECIPES:
        recipe = port.recipe(port.read(f"data/minecraft/recipe/{r}.json"))
        if recipe is None:
            raise SystemExit(f"recipe {r}: 1.21.1 cannot run it")
        port.write(res / "data/minecraft/recipe" / f"{r}.json", recipe)
    for path in ENTITY_TEXTURES:
        port.copy_texture(f"assets/minecraft/textures/{path}.png", res / "assets" / NS / "textures" / f"{path}.png")
    for st in STATS:
        lang[f"stat.{NS}.{st}"] = lang_new[f"stat.minecraft.{st}"]
    for e in ENTITIES:
        lang[f"entity.{NS}.{e}"] = lang_new[f"entity.minecraft.{e}"]
        name = f"data/minecraft/loot_table/entities/{e}.json"
        if name not in port.new_names:
            continue   # noLootTable (the cushion drops itself)
        port.write(res / "data" / NS / "loot_table/entities" / f"{e}.json", conv.table(port.read(name)))


def special_item(port, res, item, definition) -> bool:
    """26.x item definitions 1.21.1 has no form for. A composite of block models (the straw bed: head and foot, each
    turned and moved) becomes one item model holding both models' elements, turned and moved the same way. The copper
    golem statue (a select on its pose over special models) is drawn through a block entity renderer
    (builtin/entity; Java draws the pose); the item model keeps the special model's base transforms."""
    if definition.get("type") == "minecraft:composite":
        composite_item(port, res, item, definition)
        return True
    if definition.get("type") != "minecraft:select" or definition.get("block_state_property") != "copper_golem_pose":
        return False
    import models
    base = definition["fallback"]["base"].split(":")[-1]
    data = models.convert(port.read(f"assets/minecraft/models/{base}.json"), base)
    data["parent"] = "minecraft:builtin/entity"
    data["textures"] = {k: port.texture(v) for k, v in data.get("textures", {}).items()}
    port.write(res / "assets" / NS / "models/item" / f"{item}.json", data)
    return True


SWAP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "up", "down": "down"}


def composite_item(port, res, item, definition):
    """Composite of plain models, each with a transformation of a half turn about Y (or none) and a translation."""
    import models
    out = None
    for part in definition["models"]:
        if part.get("type") != "minecraft:model":
            raise SystemExit(f"{item}: composite part {part.get('type')} needs porting by hand")
        ref = part["model"].split(":")[-1]
        data = models.convert(port.read(f"assets/minecraft/models/{ref}.json"), ref)
        t = part.get("transformation", {})
        rot = [round(v, 6) for v in t.get("left_rotation", [0, 0, 0, 1])]
        if t.get("right_rotation", [0, 0, 0, 1]) != [0.0, 0.0, 0.0, 1.0] or t.get("scale", [1, 1, 1]) != [1.0, 1.0, 1.0] \
                or rot not in ([0, 0, 0, 1], [0, 1, 0, 0]):
            raise SystemExit(f"{item}: composite transformation {t} needs porting by hand")
        half_turn = rot == [0, 1, 0, 0]
        tx, ty, tz = (v * 16 for v in t.get("translation", [0, 0, 0]))
        elements = [element(e, half_turn, tx, ty, tz) for e in data.get("elements", [])]
        if out is None:
            out = {k: v for k, v in data.items() if k != "elements"}
            out["textures"] = {k: (v if v.startswith("#") else port.texture(v)) for k, v in data.get("textures", {}).items()}
            out["elements"] = []
        out["elements"] += elements
    if f"assets/minecraft/blockstates/{item}.json" in port.new_names:
        lay = layer(port, item, port.read(f"assets/minecraft/blockstates/{item}.json"))
        if lay:
            out["render_type"] = lay   # the block's layer, as its block models have it
    port.write(res / "assets" / NS / "models/item" / f"{item}.json", out)


def element(e, half_turn, tx, ty, tz):
    e = json.loads(json.dumps(e))
    def point(p):
        return [(-p[0] if half_turn else p[0]) + tx, p[1] + ty, (-p[2] if half_turn else p[2]) + tz]
    a, b = point(e["from"]), point(e["to"])
    e["from"], e["to"] = [min(a[i], b[i]) for i in range(3)], [max(a[i], b[i]) for i in range(3)]
    if "rotation" in e:
        r = e["rotation"]
        r["origin"] = point(r["origin"])
        if half_turn and r["axis"] in ("x", "z"):
            r["angle"] = -r["angle"]
    if half_turn:
        faces = {}
        for side, face in e.get("faces", {}).items():
            if "cullface" in face:
                face["cullface"] = SWAP[face["cullface"]]
            if side in ("up", "down"):
                face["rotation"] = (face.get("rotation", 0) + 180) % 360
            faces[SWAP[side]] = face
        e["faces"] = faces
    return e


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
