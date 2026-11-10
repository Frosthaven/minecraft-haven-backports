"""Climate variants of pigs, cows and chickens and the wolf's sound variants (26.x data registries), for Variants.java.

- pig_variant, cow_variant, chicken_variant: Mojang's files, under data/haven_backports/haven_backports/<registry>/.
  The temperate variant keeps 1.21.1's texture (the look already in every world; its asset id names 1.21.1's file);
  warm and cold textures are Mojang's, copied to our namespace. baby_asset_id is left out: 1.21.1 draws babies as small
  adults, so the baby textures are not used (README).
- wolf_sound_variant: Mojang's files; a sound event 1.21.1 has stays a reference, one it lacks becomes an inline event
  of ours (its sounds written to sounds.json by backport.sounds through EXTRA_SOUNDS).
- The spawn biome tags (spawns_cold/warm_variant_farm_animals) are CODE_TAGS.
"""
import json

NS = "haven_backports"
FARM = ["pig_variant", "cow_variant", "chicken_variant"]
EXTRA_SOUNDS: list[str] = []   # filled by collect(), read by backport.sounds


def collect(port):
    """The new sound events the wolf sound variants name (before backport.sounds runs)."""
    from sources import old_sounds
    old = old_sounds()
    for n in sorted(port.new_names):
        if n.startswith("data/minecraft/wolf_sound_variant/") and n.endswith(".json"):
            for group in port.read(n).values():
                for event in group.values():
                    e = event.split(":")[-1]
                    if e not in old and e not in EXTRA_SOUNDS:
                        EXTRA_SOUNDS.append(e)
    return old


def run(port, res, old):
    for reg in FARM:
        for n in sorted(port.new_names):
            if n.startswith(f"data/minecraft/{reg}/") and n.endswith(".json"):
                data = port.read(n)
                data.pop("baby_asset_id", None)
                data["asset_id"] = asset(port, res, data["asset_id"])
                port.write(res / "data" / NS / NS / reg / n.rsplit("/", 1)[1], data)
    for n in sorted(port.new_names):
        if n.startswith("data/minecraft/wolf_sound_variant/") and n.endswith(".json"):
            data = port.read(n)
            for group in data.values():
                for k, event in group.items():
                    e = event.split(":")[-1]
                    group[k] = event if e in old else {"sound_id": f"{NS}:{e}"}
            port.write(res / "data" / NS / NS / "wolf_sound_variant" / n.rsplit("/", 1)[1], data)


def asset(port, res, asset_id):
    path = asset_id.split(":")[-1]
    if path.endswith("_temperate"):
        base = path[: -len("_temperate")]
        for old in (base, base.rsplit("/", 1)[0]):   # entity/pig/pig, entity/cow/cow; entity/chicken
            if f"assets/minecraft/textures/{old}.png" in port.old_names:
                return f"minecraft:{old}"
        raise SystemExit(f"{asset_id}: no 1.21.1 texture for the temperate variant")
    port.copy_texture(f"assets/minecraft/textures/{path}.png", res / "assets" / NS / "textures" / f"{path}.png")
    return f"{NS}:{path}"
