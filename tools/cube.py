"""The Sulfur Cube (26.x SulfurCube mob): its assets and data in 1.21.1's formats; called from Port.run.

backport.py folds SOUNDS into its sound pass, ITEMS into its item pass (bucket and spawn egg: Mojang's item models)
and ENTITIES into OURS (so 26.x data naming minecraft:sulfur_cube or minecraft:sulfur_cube_hot becomes ours: the
biome spawner, Mojang's damage type and entity type tags). run() writes the rest:
- entity textures and the sulfur_cube_goo particle;
- the 13 archetypes (26.x registry sulfur_cube_archetype -> NeoForge datapack registry
  haven_backports:sulfur_cube_archetype): new attribute, sound and damage type ids to ours, vanilla attribute ids
  to 1.21.1's (with their generic./player. prefix; an attribute 1.21.1 lacks stops the run), the cold archetype's
  location effect in 1.21.1's enchantment effect format (block state -> simple_state_provider, loot conditions and
  entity predicates in 1.21.1's keys);
- the damage type sulfur_cube_hot, the entity loot table, the husbandry advancement "Uh-Oh";
- the tags the cube's Java reads (sulfur_cube_food, sulfur_cube_swallowable, sulfur_cube_archetype/*,
  damage type sulfur_cube_with_block_immune_to). A member 1.21.1 lacks is left out; a tag reference 1.21.1 lacks is
  replaced by that 26.x tag's members (resolved the same way);
- lang (entity, content tooltip, attributes, death messages, advancement).
"""
import json
import sys

NS = "haven_backports"
ARCHETYPES = ["bouncy", "cold", "explosive", "fast_flat", "fast_sliding", "high_resistance", "hot", "light", "regular",
              "slow_bouncy", "slow_flat", "slow_sliding", "sticky"]
SOUNDS = ["item.bucket.empty_sulfur_cube", "item.bucket.fill_sulfur_cube", "entity.sulfur_cube.absorb", "entity.sulfur_cube.bounce",
          "entity.sulfur_cube.death", "entity.sulfur_cube.eject", "entity.sulfur_cube.hurt", "entity.sulfur_cube.jump",
          "entity.sulfur_cube.squish", "entity.small_sulfur_cube.death", "entity.small_sulfur_cube.hurt",
          "entity.small_sulfur_cube.jump", "entity.small_sulfur_cube.squish", "entity.small_sulfur_cube.eat",
          *[f"entity.sulfur_cube.{a}.{k}" for a in ARCHETYPES if a != "cold" for k in ("hit", "push")]]
ITEMS = ["sulfur_cube_bucket", "sulfur_cube_spawn_egg"]
CODE_TAGS: list[str] = []   # the cube's tags are written here (run), not by the shared tag pass
ENTITIES = ["sulfur_cube", "sulfur_cube_hot"]   # ids of ours in 26.x data: the entity type, the damage type
NEW_ATTRIBUTES = ["bounciness", "friction_modifier", "air_drag_modifier"]
TEXTURES = ["sulfur_cube_inner", "sulfur_cube_inner_small", "sulfur_cube_outer", "sulfur_cube_outer_small"]


def ours(v: str) -> str:
    """26.x minecraft: ids that are the cube's (sounds, attributes, damage type) -> haven_backports:."""
    if not isinstance(v, str) or not v.startswith("minecraft:"):
        return v
    p = v[10:]
    if p in NEW_ATTRIBUTES or p == "sulfur_cube_hot" or p.startswith(("entity.sulfur_cube.", "entity.small_sulfur_cube.")):
        return f"{NS}:{p}"
    return v


def entity_predicate(p):
    """26.x entity predicate (component-style keys) -> 1.21.1's keys."""
    out = {}
    for k, v in p.items():
        key = "type" if k == "minecraft:entity_type" else k.split(":")[-1]
        out[key] = v
    return out


def conditions(port, c):
    c = port.condition(c)

    def walk(v):
        if isinstance(v, dict):
            if v.get("condition") == "minecraft:entity_properties" and "predicate" in v:
                v["predicate"] = entity_predicate(v["predicate"])
            for x in v.values():
                walk(x)
        elif isinstance(v, list):
            for x in v:
                walk(x)
    walk(c)
    return port.ids(c)


def attribute_ids(port):
    """26.x attribute name -> 1.21.1 id. 1.21.1 keeps a group prefix (generic., player., zombie.) that 26.x dropped;
    the ids are the strings 1.21.1's classes register that also have a 1.21.1 attribute name in its lang file."""
    import re
    lang = port.old_lang()
    found = set()
    for n in port.old_names:
        if n.endswith(".class"):
            for m in re.finditer(rb"\x01(..)((?:generic|player|zombie|horse)\.[a-z_]+)", port.old.read(n), re.S):
                if int.from_bytes(m.group(1), "big") == len(m.group(2)) and f"attribute.name.{m.group(2).decode()}" in lang:
                    found.add(m.group(2).decode())
    out = {}
    for i in found:
        out.setdefault(i.split(".", 1)[1], []).append(i)
    return out


def attribute(port, v, ids):
    v = ours(v)
    if v.startswith(f"{NS}:"):
        return v
    name = v.split(":")[-1]
    if len(ids.get(name, [])) != 1:
        sys.exit(f"attribute {v}: 1.21.1 has {ids.get(name)} for it; map it by hand")
    return f"minecraft:{ids[name][0]}"


def archetype(port, name, ids):
    a = port.read(f"data/minecraft/sulfur_cube_archetype/{name}.json")
    for m in a["attribute_modifiers"]:
        m["attribute"] = attribute(port, m["attribute"], ids)
        m["id"] = f"{NS}:{m['id'].split(':')[-1]}"
    s = a["sound_settings"]
    for k in ("hit_sound", "push_sound"):
        if k in s:
            s[k] = ours(s[k])
    if "contact_damage" in a:
        a["contact_damage"]["damage_type"] = ours(a["contact_damage"]["damage_type"])
    effects = []
    for e in a.get("location_based_effects", []):
        eff = dict(e["effect"])
        if eff["type"] != "minecraft:replace_disk":
            sys.exit(f"archetype {name}: location effect {eff['type']} needs porting by hand")
        st = eff["block_state"]
        state = {"Name": port.id(st["id"])}
        if st.get("properties"):
            state["Properties"] = st["properties"]
        eff["block_state"] = {"type": "minecraft:simple_state_provider", "state": state}
        out = {"effect": eff}
        if "requirements" in e:
            out["requirements"] = conditions(port, e["requirements"])
        effects.append(out)
    if effects:
        a["location_based_effects"] = effects
    return a


ITEM_TAGS = ["sulfur_cube_food", "sulfur_cube_swallowable", *[f"sulfur_cube_archetype/{a}" for a in ARCHETYPES]]


def resolve_tag(port, kind, name, seen=()):
    """Members of a 26.x tag for 1.21.1: ours and 1.21.1's ids, 1.21.1's tags and the tags written here, other 26.x
    tags expanded."""
    values = []
    for v in port.read(f"data/minecraft/tags/{kind}/{name}.json")["values"]:
        if v.startswith("#"):
            ref = v[1:].split(":")[-1]
            if f"data/minecraft/tags/{kind}/{ref}.json" in port.old_names or (kind == "item" and ref in ITEM_TAGS):
                values.append(v)
            elif ref not in seen:
                values += resolve_tag(port, kind, ref, seen + (name,))
            continue
        vid = ours(port.id(v))
        path = vid.split(":")[-1]
        if vid.startswith(f"{NS}:"):
            values.append(vid)
        elif kind == "item" and port.exists_1211_item(vid):
            values.append(vid)
        elif kind == "damage_type" and f"data/minecraft/damage_type/{path}.json" in port.old_names:
            values.append(vid)
    return list(dict.fromkeys(values))


def run(port, res, lang_new, lang):
    for t in TEXTURES:
        out = res / "assets" / NS / "textures/entity/sulfur_cube" / f"{t}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(port.jar.read(f"assets/minecraft/textures/entity/sulfur_cube/{t}.png"))
    part = port.read("assets/minecraft/particles/sulfur_cube_goo.json")
    part["textures"] = [port.texture("minecraft:particle/" + t.split(":")[-1]).replace("particle/", "") for t in part["textures"]]
    port.write(res / "assets" / NS / "particles/sulfur_cube_goo.json", part)

    ids = attribute_ids(port)
    for name in ARCHETYPES:
        port.write(res / "data" / NS / NS / "sulfur_cube_archetype" / f"{name}.json", archetype(port, name, ids))
    dt = port.read("data/minecraft/damage_type/sulfur_cube_hot.json")
    port.write(res / "data" / NS / "damage_type/sulfur_cube_hot.json", dt)
    port.write(res / "data" / NS / "loot_table/entities/sulfur_cube.json",
               port.ids(port.loot(port.read("data/minecraft/loot_table/entities/sulfur_cube.json"))))

    adv = port.read("data/minecraft/advancement/husbandry/uh_oh.json")
    for crit in adv["criteria"].values():
        ent = crit["conditions"].get("entity")
        if ent:
            crit["conditions"]["entity"] = [conditions(port, ent)] if isinstance(ent, dict) else ent
        item = crit["conditions"].get("item")
        if item:
            crit["conditions"]["item"] = item   # "#minecraft:sulfur_cube_archetype/explosive", written below
    port.write(res / "data" / NS / "advancement/husbandry/uh_oh.json", adv)

    for tag in ITEM_TAGS:
        port.write(res / f"data/minecraft/tags/item/{tag}.json", {"replace": False, "values": resolve_tag(port, "item", tag)})
    port.write(res / "data/minecraft/tags/damage_type/sulfur_cube_with_block_immune_to.json",
               {"replace": False, "values": resolve_tag(port, "damage_type", "sulfur_cube_with_block_immune_to")})
    port.write(res / "data/minecraft/tags/block/suppresses_bounce.json",
               {"replace": False, "values": [port.id(v) for v in port.read("data/minecraft/tags/block/suppresses_bounce.json")["values"]]})

    lang[f"entity.{NS}.sulfur_cube"] = lang_new["entity.minecraft.sulfur_cube"]
    lang[f"entity.{NS}.sulfur_cube.content"] = lang_new["entity.minecraft.sulfur_cube.content"]
    for a in NEW_ATTRIBUTES:
        lang[f"attribute.name.{NS}.{a}"] = lang_new[f"attribute.name.{a}"]
    for k in ("", ".item", ".player"):
        lang[f"death.attack.{dt['message_id']}{k}"] = lang_new[f"death.attack.{dt['message_id']}{k}"]
    for k in ("title", "description"):
        lang[f"advancements.husbandry.uh_oh.{k}"] = lang_new[f"advancements.husbandry.uh_oh.{k}"]
