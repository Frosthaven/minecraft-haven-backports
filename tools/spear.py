"""Spears and Lunge (26.x) in 1.21.1's formats. Called from backport.py (Port.run, after gear.run).

- Item definitions: 26.x selects the flat model for gui, ground, fixed and on_shelf and the in-hand model otherwise.
  1.21.1 has no item definitions; NeoForge's `separate_transforms` loader does the same per display context (base =
  the in-hand model, one perspective per listed context, `on_shelf` -> haven_backports:on_shelf). The flat model is
  written inline: as a file it would share models/item/<spear>.json with the item model itself.
  `swap_animation_scale` (1.95) has no 1.21.1 form; Java carries it (client/spear/SpearClient), and the run checks it here.
- Lunge (enchantment/lunge.json): term for term in 1.21.1's format. The 26.x effect component and effects 1.21.1 lacks
  are Haven Backports' own registry entries (Java: spear/Lunge): post_piercing_attack, apply_exhaustion,
  apply_impulse, play_sound (26.x's sound list form); change_item_damage is 1.21.1's damage_item (both call
  hurtAndBreak with the owner). Entity predicate components become 1.21.1's fields; the 26.x flags 1.21.1 lacks
  (is_in_water, is_fall_flying) and the player's food are type_specific sub-predicates of ours.
- Six vanilla enchantments move from 1.21.1's #enchantable/sword to 26.x's #enchantable/melee_weapon (supported or
  primary items, as 26.x has them); the files stay 1.21.1's otherwise. #enchantable/melee_weapon also lists
  #enchantable/sword, the tag those six named in 1.21.1, so items other mods add to it keep those enchantments
  (Farmer's Delight knives and skillet, Iron's Spellbooks staves). Sweeping Edge stays on #enchantable/sword (26.x:
  #enchantable/sweeping = swords), so spears never take it.
- Damage type minecraft:spear and advancement adventure/spear_many_mobs become ours (trigger haven_backports:spear_mobs).
"""
import json

NS = "haven_backports"
TIERS = ["wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"]
ITEMS = [f"{t}_spear" for t in TIERS]
# Other 26.x ids that are ours: minecraft:<id> becomes haven_backports:<id> wherever 26.x names them (tags).
IDS = ["lunge", "spear"]
SOUNDS = ["item.spear.attack", "item.spear.hit", "item.spear.use", "item.spear.lunge_1", "item.spear.lunge_2",
          "item.spear.lunge_3", "item.spear_wood.attack", "item.spear_wood.hit", "item.spear_wood.use"]
# Tags our Java reads: #spears (arm pose, pickup rules), the tool materials (repair, as 26.x's ToolMaterial);
# #enchantable/lunge (the enchantment). The entity type tag Java reads is written by tags().
CODE_TAGS = ["spears", "lunge", "wooden_tool_materials", "iron_tool_materials", "gold_tool_materials",
             "diamond_tool_materials", "netherite_tool_materials"]
SWAP_ANIMATION_SCALE = 1.95   # every 26.x spear item definition; Java's SpearClient.SWAP_ANIMATION_SCALE
RETARGETED = ["sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting"]
MELEE_MERGE = "#minecraft:enchantable/sword"

COMPONENTS = {"minecraft:post_piercing_attack": f"{NS}:post_piercing_attack"}
EFFECTS = {"minecraft:all_of": "minecraft:all_of", "minecraft:change_item_damage": "minecraft:damage_item",
           "minecraft:apply_exhaustion": f"{NS}:apply_exhaustion", "minecraft:apply_impulse": f"{NS}:apply_impulse",
           "minecraft:play_sound": f"{NS}:play_sound"}
FLAGS_1211 = {"is_on_ground", "is_on_fire", "is_sneaking", "is_sprinting", "is_swimming", "is_flying", "is_baby"}
PLAYER_1211 = {"level", "gamemode", "stats", "recipes", "advancements", "looking_at"}


def run(port, res, lang_new, lang):
    for item in ITEMS:
        definition = port.read(f"assets/minecraft/items/{item}.json")
        model = definition["model"]
        if definition.get("swap_animation_scale") != SWAP_ANIMATION_SCALE or model.get("property") != "minecraft:display_context" \
                or len(model["cases"]) != 1 or model["fallback"]["type"] != "minecraft:model":
            raise SystemExit(f"{item}: item definition changed shape; port it by hand")
        case = model["cases"][0]
        flat_name = case["model"]["model"].split(":")[1]
        flat = port.read(f"assets/minecraft/models/{flat_name}.json")
        flat = {"parent": port.model(flat["parent"]), "textures": {k: port.texture(v) for k, v in flat["textures"].items()}}
        port.write(res / "assets" / NS / "models/item" / f"{item}.json", {
            "loader": "neoforge:separate_transforms",
            "base": {"parent": port.model(model["fallback"]["model"])},
            "perspectives": {(f"{NS}:on_shelf" if c == "on_shelf" else c): flat for c in case["when"]}})
        lang[f"item.{NS}.{item}"] = lang_new[f"item.minecraft.{item}"]

    lunge = port.read("data/minecraft/enchantment/lunge.json")
    lunge["effects"] = {COMPONENTS[k]: [conditional(port, e) for e in v] for k, v in lunge["effects"].items()}
    port.write(res / "data" / NS / "enchantment/lunge.json", lunge)
    for name in RETARGETED:
        old = json.loads(port.old.read(f"data/minecraft/enchantment/{name}.json"))
        new = port.read(f"data/minecraft/enchantment/{name}.json")
        other = {k for k in set(old) | set(new) if k not in ("effects", "supported_items", "primary_items") and old.get(k) != new.get(k)}
        if other:
            raise SystemExit(f"enchantment {name}: 26.x also changed {sorted(other)}; decide by hand")
        for key in ("supported_items", "primary_items"):
            if key in new:
                old[key] = new[key]
        port.write(res / "data/minecraft/enchantment" / f"{name}.json", old)

    # Items.WOODEN_SPEAR cookingFuel(COOKING_TIME_WOOD_ITEMS_LARGE): 26.x's context provider is left / reduction factor
    # (a fast-cooking furnace's factor applies where 1.21.1 halves burn time itself); 1.21.1 reads NeoForge's data map.
    fuel = port.read("data/minecraft/context_int_provider/cooking/time_wood_items_large.json")
    factor = port.read(f"data/minecraft/context_int_provider/{fuel['right']['on_false'].split(':')[1]}.json")
    if fuel["type"] != "minecraft:div" or not isinstance(factor, int):
        raise SystemExit("wooden spear fuel: 26.x context provider changed shape; port it by hand")
    port.write(res / "data/neoforge/data_maps/item/furnace_fuels.json",
               {"values": {f"{NS}:wooden_spear": {"burn_time": fuel["left"] // factor}}})
    port.write(res / "data" / NS / "damage_type/spear.json", port.read("data/minecraft/damage_type/spear.json"))
    adv = port.read("data/minecraft/advancement/adventure/spear_many_mobs.json")
    adv["criteria"]["spear_many_mobs"]["trigger"] = f"{NS}:spear_mobs"
    adv["display"]["icon"]["id"] = port.id(adv["display"]["icon"]["id"])
    port.write(res / "data" / NS / "advancement/adventure/spear_many_mobs.json", adv)
    for key in ("enchantment.minecraft.lunge", "death.attack.spear", "death.attack.spear.item",
                "advancements.adventure.spear_many_mobs.title", "advancements.adventure.spear_many_mobs.description"):
        lang[key] = lang_new[key]


def tags(port, res):
    """After Port.tags: 26.x's #enchantable/melee_weapon whole (its #swords is a 1.21.1 tag, which Port.tags leaves
    out of new tags 1.21.1 tags reach), plus the merge of 1.21.1's #enchantable/sword."""
    values = port.read("data/minecraft/tags/item/enchantable/melee_weapon.json")["values"]
    port.write(res / "data/minecraft/tags/item/enchantable/melee_weapon.json", {"replace": False, "values": values + [MELEE_MERGE]})
    # An entity type tag (Port.tags checks values as items): 26.x's, whose one entry (interaction) 1.21.1 has.
    tag = "data/minecraft/tags/entity_type/cannot_be_dismounted_by_item_usage.json"
    port.write(res / tag, {"replace": False, "values": port.read(tag)["values"]})


def conditional(port, effect):
    return {"effect": entity_effect(port, effect["effect"]), "requirements": condition(effect["requirements"])}


def entity_effect(port, e):
    if e["type"] not in EFFECTS:
        raise SystemExit(f"lunge: effect {e['type']} has no 1.21.1 form here")
    out = dict(e, type=EFFECTS[e["type"]])
    if "effects" in out:
        out["effects"] = [entity_effect(port, x) for x in out["effects"]]
    if e["type"] == "minecraft:play_sound":
        sounds = e["sound"] if isinstance(e["sound"], list) else [e["sound"]]
        out["sound"] = [f"{NS}:{s.split(':')[1]}" if s.split(":")[1] in SOUNDS else s for s in sounds]
    return out


def condition(c):
    """26.x loot condition (type) -> 1.21.1 (condition), entity predicates converted."""
    out = {("condition" if k == "type" else k): v for k, v in c.items()}
    if "term" in out:
        out["term"] = condition(out["term"])
    if "terms" in out:
        out["terms"] = [condition(t) for t in out["terms"]]
    if "predicate" in out and c["type"] == "minecraft:entity_properties":
        out["predicate"] = predicate(out["predicate"])
    return out


def predicate(p):
    """26.x entity predicate (component map) -> 1.21.1 EntityPredicate fields."""
    out, specific = {}, None
    for key, value in p.items():
        if key == "minecraft:vehicle":
            out["vehicle"] = predicate(value)
        elif key == "minecraft:flags":
            old = {k: v for k, v in value.items() if k in FLAGS_1211}
            new = {k: v for k, v in value.items() if k not in FLAGS_1211}
            if old:
                out["flags"] = old
            if new:
                specific = merge(specific, {"type": f"{NS}:flags", **new})
        elif key == "minecraft:type_specific/player":
            old = {k: v for k, v in value.items() if k in PLAYER_1211}
            new = {k: v for k, v in value.items() if k not in PLAYER_1211}
            if new.keys() - {"food"}:
                raise SystemExit(f"lunge: player predicate fields {sorted(new)} have no 1.21.1 form here")
            if old or not new:
                specific = merge(specific, {"type": "minecraft:player", **old})
            if new:
                specific = merge(specific, {"type": f"{NS}:player_food", **new})
        else:
            raise SystemExit(f"lunge: entity predicate {key} has no 1.21.1 form here")
    if specific:
        out["type_specific"] = specific
    return out


def merge(current, new):
    if current is not None:
        raise SystemExit("lunge: two type_specific predicates in one entity predicate; 1.21.1 holds one")
    return new
