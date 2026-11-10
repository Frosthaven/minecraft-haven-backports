"""The golden dandelion age lock (26.x AgeableMob.setAgeLocked, makeAgeLockedParticle): its assets and data in
1.21.1's formats; called from Port.run before the sound and tag passes.

- SOUNDS join backport.py's EXTRA_SOUNDS (item.golden_dandelion.use / unuse, with their subtitles);
- CODE_TAGS join its CODE_TAGS (#minecraft:cannot_be_age_locked, read by AgeLock: zombie horse, skeleton horse,
  villager);
- PARTICLES: pause_mob_growth and reset_mob_growth (26.x particle definitions, Mojang's glint texture).
"""
import data

NS = "haven_backports"
SOUNDS = ["item.golden_dandelion.use", "item.golden_dandelion.unuse"]
CODE_TAGS = ["cannot_be_age_locked"]
PARTICLES = ["pause_mob_growth", "reset_mob_growth"]


def run(port, res, lang_new, lang):
    b = data.B
    b.EXTRA_SOUNDS += [s for s in SOUNDS if s not in b.EXTRA_SOUNDS]
    b.CODE_TAGS += [t for t in CODE_TAGS if t not in b.CODE_TAGS]
    for p in PARTICLES:
        d = port.read(f"assets/minecraft/particles/{p}.json")
        # particle definitions name textures without the particle/ folder (as backport.py's particle pass)
        d["textures"] = [port.texture("minecraft:particle/" + t.split(":")[-1]).replace("particle/", "") for t in d["textures"]]
        port.write(res / "assets" / NS / "particles" / f"{p}.json", d)
