"""The nautilus and the zombie nautilus (26.x), their armors and the breath_of_the_nautilus effect: lists joined into
mobs.py's (items, entities, textures, sounds, tags) and what only they need (called from mobs.run).

- Nautilus armor items (copper, iron, golden, diamond, netherite) are plain item models (backport.py's item path);
  their equipment textures (entity/equipment/nautilus_body/<asset>) and the nautilus saddle's are copied at Mojang's
  path, as are the inventory screen's background and slot sprites and the effect icon.
- The zombie nautilus's variant registry (zombie_nautilus_variant: temperate, warm with corals) has the farm variants'
  shape: variants.py writes it (FARM).
- Loot that names nautilus armor: buried treasure and the shipwreck supply and treasure chests here (lootfmt, taken
  whole once every item exists); shipwreck_map and the underwater ruins through maps.py (same rule).
- Smelting and blasting to nuggets: 26.x's iron and gold nugget recipes add the iron and golden spear and nautilus
  armor to 1.21.1's (written over 1.21.1's file, as mobs.OVERRIDE_RECIPES); the copper nugget ones are ours and come
  through backport.py's recipe path once their ingredients exist.
"""
NS = "haven_backports"
ARMORS = ["copper_nautilus_armor", "iron_nautilus_armor", "golden_nautilus_armor", "diamond_nautilus_armor",
          "netherite_nautilus_armor"]
ITEMS = ["nautilus_spawn_egg", "zombie_nautilus_spawn_egg", *ARMORS]
ENTITIES = ["nautilus", "zombie_nautilus"]
ENTITY_TEXTURES = [*[f"entity/nautilus/{t}" for t in ("nautilus", "nautilus_baby", "zombie_nautilus", "zombie_nautilus_coral")],
                   *[f"entity/equipment/nautilus_body/{m}" for m in ("copper", "iron", "gold", "diamond", "netherite")],
                   "entity/equipment/nautilus_saddle/saddle",
                   "gui/container/nautilus", "gui/sprites/container/slot/saddle", "gui/sprites/container/slot/nautilus_armor_inventory",
                   "mob_effect/breath_of_the_nautilus"]
ANIMATIONS = {"net/minecraft/client/animation/definitions/NautilusAnimation": ("nautilus", "")}
SOUNDS = [f"entity.{m}.{s}" for m in ("nautilus", "zombie_nautilus")
          for s in ("ambient", "ambient_land", "dash", "dash_land", "dash_ready", "dash_ready_land", "death", "death_land",
                    "eat", "hurt", "hurt_land", "riding", "swim")] \
    + [f"entity.baby_nautilus.{s}" for s in ("ambient", "ambient_land", "death", "death_land", "eat", "hurt", "hurt_land", "swim")] \
    + ["item.armor.equip_nautilus", "item.armor.unequip_nautilus", "item.nautilus_saddle_equip", "item.nautilus_saddle_underwater_equip",
       "entity.parrot.imitate.zombie_nautilus", "item.saddle.unequip"]
CODE_TAGS = ["nautilus_food", "nautilus_taming_items", "nautilus_bucket_food", "nautilus_hostiles", "can_wear_nautilus_armor",
             "immune_to_poison", "cannot_be_pushed_onto_boats", "spawns_coral_variant_zombie_nautilus"]
EFFECTS = ["breath_of_the_nautilus"]
PARROT = {"zombie_nautilus": "entity.parrot.imitate.zombie_nautilus"}
# 26.x tables naming nautilus armor that no other module takes (maps.py takes shipwreck_map and the underwater ruins)
LOOT = ["chests/buried_treasure", "chests/shipwreck_supply", "chests/shipwreck_treasure"]
OVERRIDE_RECIPES = ["iron_nugget_from_smelting", "iron_nugget_from_blasting", "gold_nugget_from_smelting", "gold_nugget_from_blasting"]


def run(port, res, lang_new, lang, conv):
    import extras
    for table in LOOT:
        extras.take_loot(port, res, conv, table)
    for e in EFFECTS:
        lang[f"effect.{NS}.{e}"] = lang_new[f"effect.minecraft.{e}"]
