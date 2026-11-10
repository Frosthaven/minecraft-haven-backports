"""Single items and the loot tables, data maps and registries that carry them (split out of backport.py; called from
Port.run). Every value is read from the snapshot jar; this file only lists which items are ours.

- Music discs (26.x JukeboxSongs and Items): the jukebox song files, Mojang's sound events (asset store, streamed),
  and the 26.x loot tables that drop them, as patches (lootpatch) when 1.21.1 has every item they name (lootfmt converts).
- Banner patterns Field Masoned and Bordure Indented: 26.x gives the loom's bricks and curly_border patterns an item
  each (pattern_item tags) and takes them out of #no_item_required (NeoForge's tag `remove`, so packs that add to
  the tag keep their entries).
- Chiseled Soulshale: its sound set (26.x block_sound_set/chiseled_soulshale) and the ancient city chests that hold it.
- Explorer maps and the cartographer's trades: tools/maps.py. Bundles: tools/bundles.py.
- Lightning rods in every copper stage (26.x WeatheringCopperCollection LIGHTNING_ROD, whose unaffected stage is still
  minecraft:lightning_rod): the vanilla rod heads the oxidation and waxing data maps, and 26.x's channeling, which
  names #minecraft:lightning_rods, replaces 1.21.1's (that one names minecraft:lightning_rod only).
"""
import json

import bundles
import lootfmt
import lootpatch
import maps

NS = "haven_backports"
STAGES = ["", "exposed_", "weathered_", "oxidized_"]
# Lightning rods: the vanilla one is the unaffected stage (minecraft:lightning_rod in 26.x too).
RODS = [f"{w}{st}lightning_rod" for w in ("", "waxed_") for st in STAGES if w or st]
# disc item -> its jukebox song (data/minecraft/jukebox_song/<song>.json). music_disc_flurry comes with the Ice Caves.
DISCS = {"music_disc_tears": "tears", "music_disc_lava_chicken": "lava_chicken", "music_disc_bounce": "bounce"}
# banner pattern item -> its pattern_item tag (data/minecraft/tags/banner_pattern/pattern_item/<tag>.json)
PATTERNS = {"field_masoned_banner_pattern": "field_masoned", "bordure_indented_banner_pattern": "bordure_indented"}
ITEMS_ONLY = list(DISCS) + list(PATTERNS) + maps.MAPS
OTHER_ITEMS = bundles.DYED   # ours, with item models written by their own module
SOUNDS = [f"music_disc.{s}" for s in DISCS.values()] + [f"block.chiseled_soulshale.{k}" for k in ("break", "fall", "hit", "place", "step")] + bundles.SOUNDS
CODE_TAGS = ["clonable_maps", "lightning_rods", "bundles"]
# 26.x tables that name our items. Patched (lootpatch) when every item in them exists on 1.21.1 or is ours.
LOOT = ["entities/ghast", "entities/zombie", "chests/abandoned_mineshaft", "chests/ancient_city", *bundles.LOOT]
# Biomes 26.x loot conditions name, and the file our biome port writes (absent: the entry waits for the biome).
BIOMES = {"minecraft:sulfur_caves": (f"{NS}:sulfur_caves", "data/haven_backports/worldgen/biome/sulfur_caves.json")}


def run(port, res, lang_new, lang):
    """Called before the lang file is written."""
    for disc, song in DISCS.items():
        data = port.read(f"data/minecraft/jukebox_song/{song}.json")
        data["sound_event"] = f"{NS}:{data['sound_event'].split(':')[1]}"
        port.write(res / f"data/{NS}/jukebox_song/{song}.json", data)
        key = data["description"]["translate"]
        lang[key] = lang_new[key]
    banner_patterns(port, res)
    bundles.run(port, res, lang_new, lang)
    biomes = {old: (new if (res / path).exists() else None) for old, (new, path) in BIOMES.items()}
    conv = lootfmt.Converter(port.ids, biomes)
    for table in LOOT:
        take_loot(port, res, conv, table)
    maps.run(port, res, conv)
    channeling(port, res, conv)
    for d in conv.dropped:
        print(f"loot entry left out until its source is backported: {d}")


def banner_patterns(port, res):
    for tag in PATTERNS.values():
        name = f"data/minecraft/tags/banner_pattern/pattern_item/{tag}.json"
        port.write(res / name, {"replace": False, "values": port.read(name)["values"]})
    name = "data/minecraft/tags/banner_pattern/no_item_required.json"
    gone = [v for v in json.loads(port.old.read(name))["values"] if v not in port.read(name)["values"]]
    port.write(res / name, {"replace": False, "values": [], "remove": gone})


def take_loot(port, res, conv, table, patch=None):
    """Writes Mojang's 26.x table when 1.21.1 has every item it names. Otherwise, with `patch` (old item id -> our
    item), only those entries of 1.21.1's table become 26.x's; without it the table stays at 1.21.1."""
    name = f"data/minecraft/loot_table/{table}.json"
    new = conv.table(port.read(name))
    missing = sorted({n for n in lootfmt.item_names(new) if not port.exists_1211_item(n)})
    if not missing:
        old = json.loads(port.old.read(name)) if name in port.old_names else {}
        added = sorted(set(lootfmt.item_names(new)) - set(lootfmt.item_names(old)))
        removed = sorted(set(lootfmt.item_names(old)) - set(lootfmt.item_names(new)))
        if old and not added and not removed:   # e.g. its only new entry waits for a biome
            print(f"loot {table}: 26.x adds nothing 1.21.1 can have yet, kept at 1.21.1")
            return
        write_loot(port, res, table, new)
        print(f"loot {table}: 26.x table taken (adds {added}, removes {removed})")
        return
    if not patch:
        print(f"loot {table}: kept at 1.21.1 until these exist: {missing}")
        return
    old = json.loads(port.old.read(name))
    ours = {}
    for pool in new["pools"]:
        for e in pool["entries"]:
            if e.get("name") in patch.values():
                ours[e["name"]] = e
    for pool in old["pools"]:
        pool["entries"] = [ours[patch[key]] if (key := entry_key(e)) in patch else e for e in pool["entries"]]
    write_loot(port, res, table, old)
    print(f"loot {table}: 1.21.1 table with 26.x's {sorted(set(patch.values()))} entries "
          f"(the rest waits for: {missing})")


def write_loot(port, res, table, data):
    """A table 1.21.1 has becomes a patch (lootpatch: other mods' versions keep their entries); one it lacks is ours
    alone and is written whole."""
    name = f"data/minecraft/loot_table/{table}.json"
    if name in port.old_names:
        lootpatch.write(port, res, table, data)
    else:
        port.write(res / name, data)


def entry_key(e):
    """An entry's item id, with `+exploration_map` when 1.21.1 turns it into an explorer map."""
    name = e.get("name", "")
    if any(f.get("function") == "minecraft:exploration_map" for f in e.get("functions", [])):
        return name + "+exploration_map"
    return name


def channeling(port, res, conv):
    """26.x's channeling enchantment: the trident's hit_block effect asks for #minecraft:lightning_rods."""
    name = "data/minecraft/enchantment/channeling.json"
    data = port.read(name)
    for effects in data["effects"].values():
        for e in effects:
            e["requirements"] = conv.condition(e["requirements"])
    port.write(res / name, data)


def finish(port, res):
    """Called at the end of the run, after the copper data maps are written."""
    for kind, key, pairs in (
            ("oxidizables", "next_oxidation_stage", [(rod(a), rod(b)) for a, b in zip(STAGES, STAGES[1:])]),
            ("waxables", "waxed", [(rod(st), rod("waxed_" + st)) for st in STAGES])):
        f = res / f"data/neoforge/data_maps/block/{kind}.json"
        data = json.loads(f.read_text())
        data["values"].update({a: {key: b} for a, b in pairs})
        port.write(f, data)
    if any(not (res / path).exists() for _, path in BIOMES.values()):
        print("biomes not backported yet (their loot entries wait): "
              f"{[b for b, (_, p) in BIOMES.items() if not (res / p).exists()]}")



def rod(stage):
    return "minecraft:lightning_rod" if stage == "" else f"{NS}:{stage}lightning_rod"
