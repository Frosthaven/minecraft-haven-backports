"""The frostbite, the ice ball, the freezing effect and its potions, and the music disc Flurry (26.x Frostbite, IceBall,
FreezingMobEffect, JukeboxSongs.FLURRY): their assets and data in 1.21.1's formats; called from Port.run.

backport.py folds ITEMS into its item pass (Mojang's own item models: the ice ball, the disc, the spawn egg with no
tints), SOUNDS into its sound pass (the disc's track is new: it ships here), PARTICLES into its particle pass (the
freezing snowflakes), CODE_TAGS into its tag pass (#minecraft:frostbite_preferred_weapons, read by Frostbite;
#minecraft:spawns_cold_variants_underground, read by ColdVariantSpawn) and ENTITIES into OURS (so 26.x data naming
minecraft:frostbite or minecraft:ice_ball becomes ours: zombies, powder_snow_walkable_mobs, no_anger_from_wind_charge,
impact_projectiles). run() writes the rest:
- the entity textures (frostbite, its frost overlay) and the effect icon;
- the entity loot table (rotten flesh 0-2, ice balls 0-3, looting +0-1 each);
- 26.x #immune_to_freezing as 1.21.1's #freeze_immune_entity_types (26.x renamed the tag and added the frostbite,
  zombie, husk and drowned: those convert in powder snow instead of freezing);
- the jukebox song Flurry (154 s, comparator 7);
- the parrot's imitation of the frostbite (26.x Parrot.MOB_SOUND_MAP), merged into the data map creaking.py writes;
- the advancement adventure/what_goes_around_comes_around (kill a frostbite with an ice ball);
- lang (entity, effect, potions, song, advancement).
Not ported: 26.x adds the frostbite to adventure/kill_a_mob and kill_all_mobs and the freezing effect to
nether/all_effects; editing those means replacing 1.21.1's own files, which no other backport here does.
"""
import json

NS = "haven_backports"
ITEMS = ["ice_ball", "frostbite_spawn_egg", "music_disc_flurry"]
SOUNDS = [*[f"entity.frostbite.{k}" for k in ("ambient", "death", "hurt", "step", "converted_to_zombie")],
          "entity.zombie.converted_to_frostbite", "entity.drowned.converted_to_zombie",
          "entity.ice_ball.break", "entity.ice_ball.throw", "event.mob_effect.freezing",
          "entity.parrot.imitate.frostbite", "music_disc.flurry"]
CODE_TAGS = ["frostbite_preferred_weapons", "spawns_cold_variants_underground"]
ENTITIES = ["frostbite", "ice_ball"]
PARTICLES = ["freezing"]
TEXTURES = ["frostbite", "frostbite_outer_layer"]   # 26.x frostbite_baby.png fits 26.x's baby model, which 1.21.1 lacks


def advancement(port, adv):
    """26.x player_killed_entity in 1.21.1's predicate format (entity predicates name "type", tag predicates no #)."""
    crit = adv["criteria"]["kill_frostbite_with_ice_ball"]
    cond = crit["conditions"]
    ent = cond["entity"]
    if set(cond) != {"entity", "killing_blow"} or ent["type"] != "minecraft:entity_properties" or set(ent["predicate"]) != {"minecraft:entity_type"}:
        raise SystemExit(f"what_goes_around_comes_around: conditions {cond} need porting by hand")
    blow = cond["killing_blow"]
    if set(blow) != {"direct_entity", "tags"} or set(blow["direct_entity"]) != {"minecraft:entity_type"}:
        raise SystemExit(f"what_goes_around_comes_around: killing_blow {blow} needs porting by hand")
    crit["conditions"] = {
        "entity": [{"condition": "minecraft:entity_properties", "entity": ent["entity"],
                    "predicate": {"type": port.id(ent["predicate"]["minecraft:entity_type"])}}],
        "killing_blow": {"direct_entity": {"type": port.id(blow["direct_entity"]["minecraft:entity_type"])},
                         "tags": [{"id": t["id"].lstrip("#"), "expected": t["expected"]} for t in blow["tags"]]}}
    adv["display"]["icon"] = {"id": port.id(adv["display"]["icon"]["id"])}
    return adv


def run(port, res, lang_new, lang):
    for t in TEXTURES:
        out = res / "assets" / NS / "textures/entity/zombie" / f"{t}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(port.jar.read(f"assets/minecraft/textures/entity/zombie/{t}.png"))
    port.copy_texture("assets/minecraft/textures/mob_effect/freezing.png", res / "assets" / NS / "textures/mob_effect/freezing.png")
    port.write(res / "data" / NS / "loot_table/entities/frostbite.json",
               port.ids(port.loot(port.read("data/minecraft/loot_table/entities/frostbite.json"))))

    old = set(json.loads(port.old.read("data/minecraft/tags/entity_type/freeze_immune_entity_types.json"))["values"])
    immune = [port.id(v) for v in port.read("data/minecraft/tags/entity_type/immune_to_freezing.json")["values"] if v not in old]
    port.write(res / "data/minecraft/tags/entity_type/freeze_immune_entity_types.json", {"replace": False, "values": immune})

    song = port.read("data/minecraft/jukebox_song/flurry.json")
    if song["sound_event"] != "minecraft:music_disc.flurry":
        raise SystemExit(f"jukebox song flurry plays {song['sound_event']}, which SOUNDS lacks")
    song["sound_event"] = f"{NS}:music_disc.flurry"
    song["description"] = {"translate": f"jukebox_song.{NS}.flurry"}
    port.write(res / "data" / NS / "jukebox_song/flurry.json", song)

    imitations = res / "data/neoforge/data_maps/entity_type/parrot_imitations.json"
    maps = json.loads(imitations.read_text()) if imitations.exists() else {"values": {}}
    maps["values"][f"{NS}:frostbite"] = {"sound": f"{NS}:entity.parrot.imitate.frostbite"}
    port.write(imitations, maps)

    port.write(res / "data" / NS / "advancement/adventure/what_goes_around_comes_around.json",
               advancement(port, port.read("data/minecraft/advancement/adventure/what_goes_around_comes_around.json")))

    lang[f"entity.{NS}.frostbite"] = lang_new["entity.minecraft.frostbite"]
    lang[f"entity.{NS}.ice_ball"] = lang_new["entity.minecraft.ice_ball"]
    lang[f"effect.{NS}.freezing"] = lang_new["effect.minecraft.freezing"]
    lang[f"jukebox_song.{NS}.flurry"] = lang_new["jukebox_song.minecraft.flurry"]
    for kind in ("potion", "splash_potion", "lingering_potion", "tipped_arrow"):   # 1.21.1 names potions by Potion.name
        key = f"item.minecraft.{kind}.effect.freezing"
        lang[key] = lang_new[key]
    for k in ("title", "description"):
        key = f"advancements.adventure.what_goes_around_comes_around.{k}"
        lang[key] = lang_new[key]
