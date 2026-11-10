"""Mojang's 26.x keyframe animation classes -> NeoForge 21.1 JSON entity animations (assets/<ns>/neoforge/animations/entity).

26.x keeps entity animations as Java data (client/animation/definitions/*Animation.java: AnimationDefinition.Builder
with AnimationChannel and Keyframe). 1.21.1's AnimationDefinition, AnimationChannel and Keyframe are the same API,
and NeoForge 21.1 loads the same data from JSON (AnimationLoader), so the data stays Mojang's and no hand-kept Java
copy is needed. The class is decompiled from the pinned jar (sources.decompile) and read back:
withLength, looping, addAnimation(bone, AnimationChannel(target, Keyframe(time, <vec>(x, y, z), interpolation)...)).
JSON keeps the vector helpers' arguments (degrees for rotation, raw position and scale), as NeoForge's parser expects.
A keyframe with separate pre and post targets (26.x Keyframe's 4-argument form) has no 1.21.1 form and stops the run.
"""
import re

from sources import decompile

TARGETS = {"ROTATION": ("minecraft:rotation", "degreeVec"), "POSITION": ("minecraft:position", "posVec"),
           "SCALE": ("minecraft:scale", "scaleVec")}
NUM = r"(-?[0-9.]+(?:E-?[0-9]+)?)F?"
KEYFRAME = re.compile(rf"new Keyframe\(\s*{NUM}\s*,\s*KeyframeAnimations\.(\w+)\(\s*{NUM}\s*,\s*{NUM}\s*,\s*{NUM}\s*\)\s*,\s*Interpolations\.(\w+)\s*\)")


def definitions(source: str) -> dict[str, dict]:
    """{FIELD_NAME: NeoForge animation JSON} for every AnimationDefinition in the class."""
    out = {}
    for m in re.finditer(r"public static final AnimationDefinition (\w+) = Builder\.withLength\(" + NUM + r"\)(.*?)\.build\(\);", source, re.S):
        name, length, body = m.group(1), float(m.group(2)), m.group(3)
        animations = []
        chunks = body.split(".addAnimation(")[1:]
        for chunk in chunks:
            a = re.match(r'\s*"(\w+)",\s*new AnimationChannel\(\s*Targets\.(\w+),', chunk)
            if not a:
                raise SystemExit(f"{name}: an addAnimation form this converter does not read")
            bone, target = a.group(1), a.group(2)
            json_target, helper = TARGETS[target]
            keyframes = []
            for k in KEYFRAME.finditer(chunk):
                time, fn, x, y, z, interp = k.groups()
                if fn != helper:
                    raise SystemExit(f"{name}/{bone}: {fn} on a {target} channel; decide by hand")
                keyframes.append({"timestamp": float(time), "target": [float(x), float(y), float(z)],
                                  "interpolation": "minecraft:" + interp.lower()})
            if len(keyframes) != chunk.count("new Keyframe("):
                raise SystemExit(f"{name}/{bone}: a keyframe form this converter does not read (pre/post targets?)")
            animations.append({"bone": bone, "target": json_target, "keyframes": keyframes})
        out[name] = {"length": length, "loop": ".looping()" in body, "animations": animations}
    return out


def run(port, res, ns, classes: dict[str, tuple[str, str]]):
    """classes: {class path: (output folder, field-name prefix to strip)}. Returns the number of animations written."""
    jar = port.jar.filename
    n = 0
    for class_path, (folder, prefix) in classes.items():
        for field, data in definitions(decompile(jar, class_path)).items():
            name = field.removeprefix(prefix).lower()
            port.write(res / "assets" / ns / "neoforge/animations/entity" / folder / f"{name}.json", data)
            n += 1
    return n
