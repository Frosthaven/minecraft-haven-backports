"""The creaking and the creaking heart (26.x Creaking, CreakingHeartBlock): their assets and data in 1.21.1's formats;
called from Port.run.

backport.py folds BLOCKS into its block pass (blockstate, models, loot, lang; the recipe and the mineable/axe tag come
from its recipe and tag passes), ITEMS into its item pass (the spawn egg: Mojang's own item model, no tints), SOUNDS
into its sound pass, CODE_TAGS into its tag pass (#minecraft:gaze_disguise_equipment, read by creaking/Gaze26) and
ENTITIES into OURS (so 26.x data naming minecraft:creaking becomes ours). run() writes the rest:
- the entity textures (body and the emissive eyes);
- the entity loot table (empty, as 26.x's);
- the parrot's imitation of the creaking (26.x Parrot.MOB_SOUND_MAP) as NeoForge's data map neoforge:parrot_imitations;
- the advancement adventure/heart_transplanter (26.x placed_block location: one condition; 1.21.1: a list);
- lang (entity, advancement).
Not ported: 26.x adds the creaking to the vanilla advancements adventure/kill_a_mob and kill_all_mobs; editing
those means replacing 1.21.1's own files, which no other backport here does.
"""
NS = "haven_backports"
BLOCKS = ["creaking_heart"]
ITEMS = ["creaking_spawn_egg"]
SOUNDS = [*[f"entity.creaking.{k}" for k in ("activate", "ambient", "attack", "deactivate", "death", "freeze", "spawn", "step",
                                             "sway", "twitch", "unfreeze")],
          "entity.parrot.imitate.creaking",
          *[f"block.creaking_heart.{k}" for k in ("hurt", "idle", "spawn",
                                                  "break", "fall", "hit", "place", "step")]]   # the last five: block_sound_set
CODE_TAGS = ["gaze_disguise_equipment"]
ENTITIES = ["creaking"]
PARTICLES: list[str] = []   # block_crumble draws the block's own texture: no particle definition
TEXTURES = ["creaking", "creaking_eyes"]


def run(port, res, lang_new, lang):
    sound_set = port.read("data/minecraft/block_sound_set/creaking_heart.json")
    for event in sound_set.values():
        if event.split(":")[-1] not in SOUNDS:
            raise SystemExit(f"creaking_heart sound set names {event}, which SOUNDS lacks")
    for t in TEXTURES:
        out = res / "assets" / NS / "textures/entity/creaking" / f"{t}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(port.jar.read(f"assets/minecraft/textures/entity/creaking/{t}.png"))
    port.write(res / "data" / NS / "loot_table/entities/creaking.json",
               port.ids(port.loot(port.read("data/minecraft/loot_table/entities/creaking.json"))))
    port.write(res / "data/neoforge/data_maps/entity_type/parrot_imitations.json",
               {"values": {f"{NS}:creaking": {"sound": f"{NS}:entity.parrot.imitate.creaking"}}})

    adv = port.read("data/minecraft/advancement/adventure/heart_transplanter.json")
    for crit in adv["criteria"].values():
        loc = crit["conditions"].get("location")
        if isinstance(loc, dict):
            crit["conditions"]["location"] = [port.ids(port.condition(loc))]
    adv["display"]["icon"] = port.ids(adv["display"]["icon"])
    port.write(res / "data" / NS / "advancement/adventure/heart_transplanter.json", adv)

    lang[f"entity.{NS}.creaking"] = lang_new["entity.minecraft.creaking"]
    for k in ("title", "description"):
        lang[f"advancements.adventure.heart_transplanter.{k}"] = lang_new[f"advancements.adventure.heart_transplanter.{k}"]
