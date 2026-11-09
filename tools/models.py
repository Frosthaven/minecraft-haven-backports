"""Block and item model formats: 26.x model JSON -> 1.21.1 model JSON, and Mojang's shelf display transforms.

26.x model keys 1.21.1 lacks:
- element `shade_direction_override`: shades an element as if it faced that direction. Mojang uses "up" where 1.21.1
  has `"shade": false` (template_lantern, template_torch ...): "up" converts to that. Other directions have no 1.21.1
  form, so they stop the run for a decision.
- display `on_shelf`: the item transform for items standing on a shelf (ItemDisplayContext.ON_SHELF). 1.21.1 has no
  such context; Haven Backports adds one through a NeoForge enum extension, and NeoForge requires its name to carry
  the mod id, so the key becomes `haven_backports:on_shelf`. A model without one renders with no transform, as in 26.x
  (ItemTransforms.Deserializer -> ItemTransform.NO_TRANSFORM; the extension has no fallback context).

Vanilla models that 26.x gives an on_shelf transform (block/block, fence and wall inventory, anvil, skull, shield ...)
are written as 1.21.1's own file plus that one display entry, so every vanilla item sits on a shelf as in 26.x.
"""
import json

SHELF_KEY = "haven_backports:on_shelf"

# 26.x renders bed items from block models; 1.21.1 from the bed block entity renderer (item/template_bed, builtin/entity).
# Every display entry the two share differs by exactly 180 degrees on Y (checked in bed_shelf()), so the 1.21.1 geometry
# is the 26.x geometry turned 180 degrees and Mojang's on_shelf transform turns with it.
BED_26X = "block/template_bed"
BED_1211 = "item/template_bed"


def convert(model: dict, where: str) -> dict:
    """A 26.x model in 1.21.1's format (references left as they are)."""
    m = json.loads(json.dumps(model))
    for e in m.get("elements", []):
        if "shade_direction_override" in e:
            d = e.pop("shade_direction_override")
            if d != "up":
                raise SystemExit(f"{where}: shade_direction_override {d!r} has no 1.21.1 form; decide by hand")
            e["shade"] = False
        if "light_emission" in e:
            raise SystemExit(f"{where}: element light_emission needs a NeoForge form; decide by hand")
    display = m.get("display")
    if display and "on_shelf" in display:
        display[SHELF_KEY] = display.pop("on_shelf")
    return m


def plain(model: dict) -> dict:
    """For comparison: no namespace on vanilla references and no shelf transform."""
    m = json.loads(json.dumps(model).replace('"minecraft:', '"'))
    m.get("display", {}).pop(SHELF_KEY, None)
    if m.get("display") == {}:
        m.pop("display")
    return m


def with_shelf(old: dict, transform: dict) -> dict:
    out = json.loads(json.dumps(old))
    out.setdefault("display", {})[SHELF_KEY] = transform
    return out


def bed_shelf(new_bed: dict, old_bed: dict) -> dict:
    """1.21.1 bed's on_shelf: Mojang's, turned 180 degrees on Y, after checking that turn on every shared entry."""
    nd, od = new_bed["display"], old_bed["display"]
    shared = [k for k in od if k in nd]
    for k in shared:
        n, o = nd[k], od[k]
        if n.get("translation") != o.get("translation") or n.get("scale") != o.get("scale") \
                or n["rotation"][0] != o["rotation"][0] or n["rotation"][2] != o["rotation"][2] \
                or (n["rotation"][1] - o["rotation"][1]) % 360 != 180:
            raise SystemExit(f"bed display {k}: not the 180 degree turn; work out the shelf transform by hand")
    t = json.loads(json.dumps(nd["on_shelf"]))
    t["rotation"][1] = (t["rotation"][1] + 180) % 360
    return t


def vanilla_shelf_patches(jar, old) -> tuple[dict[str, dict], list[str]]:
    """{model path: 1.21.1 model + Mojang's on_shelf} for every 26.x model with an on_shelf that 1.21.1 also has, and
    the paths whose other content 26.x also changed (reported; 1.21.1's content is kept, only on_shelf is added)."""
    old_names = set(old.namelist())
    patches, notes = {}, []
    for name in sorted(jar.namelist()):
        if not (name.startswith("assets/minecraft/models/") and name.endswith(".json")):
            continue
        new = json.loads(jar.read(name))
        if "on_shelf" not in new.get("display", {}):
            continue
        path = name[len("assets/minecraft/models/"):-5]
        if path == BED_26X:
            bed_old = json.loads(old.read(f"assets/minecraft/models/{BED_1211}.json"))
            patches[BED_1211] = with_shelf(bed_old, bed_shelf(new, bed_old))
            continue
        if name not in old_names:
            continue   # 26.x-only model: ours if a backported block uses it
        prev = json.loads(old.read(name))
        conv = convert(new, name)
        if plain(conv) != plain(prev):
            # Same path, same renderer (builtin/entity in 1.21.1, the same special renderer in 26.x): the transform
            # applies in the same item space. Report what else 26.x changed, so it stays a visible decision.
            nd, od = conv.get("display", {}), prev.get("display", {})
            changed = sorted(k for k in set(nd) | set(od) if k != SHELF_KEY and nd.get(k) != od.get(k))
            notes.append(f"{path} (26.x also changed display {changed})" if changed else path)
        patches[path] = with_shelf(prev, new["display"]["on_shelf"])
    return patches, notes
