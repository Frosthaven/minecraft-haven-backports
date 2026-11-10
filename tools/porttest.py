"""Offline port test, 26.x half: runs Mojang's own 26.x code from the pinned snapshot jar and writes what it produces
(build/porttest/26x.json) for `./gradlew test` (src/test: the same inputs through our 1.21.1 ports) to compare.

It writes: the overworld biome parameter list (OverworldBiomeBuilder.addBiomes), the blocks each ported tree and
fallen tree feature places for 50 seeds on a flat grass world (Mojang's feature JSON, decoded by Mojang's codecs),
and the shapes of the ported blocks with shape code 1.21.1 lacks. Libraries come from the version JSON (sha1-checked,
cached in .cache/libs/). Needs Java 25 or newer (`java` on PATH or JAVA_HOME).

Run: uv run python -I tools/porttest.py   (then ./gradlew test)
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sources import CACHE, MANIFEST, VERSION, get, snapshot   # noqa: E402

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "build/porttest"


def libraries() -> list[Path]:
    manifest = json.loads(get(MANIFEST))
    version = json.loads(get(next(v["url"] for v in manifest["versions"] if v["id"] == VERSION)))
    out = []
    for lib in version["libraries"]:
        art = lib.get("downloads", {}).get("artifact")
        if not art or ":natives-" in lib["name"] or any(r.get("os", {}).get("name") not in (None, "linux") for r in lib.get("rules", [])):
            continue
        path = CACHE / "libs" / art["path"]
        if not path.exists():
            data = get(art["url"])
            if hashlib.sha1(data).hexdigest() != art["sha1"]:
                sys.exit(f"{lib['name']}: sha1 mismatch")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        out.append(path)
    return out


def main():
    snapshot()
    jar = CACHE / f"minecraft-{VERSION}-client.jar"
    cp = os.pathsep.join(str(p) for p in [jar, *libraries()])
    java_home = os.environ.get("JAVA_HOME")
    javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    classes = OUT / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    r = subprocess.run([javac, "-d", str(classes), "-cp", cp, str(Path(__file__).parent / "porttest/Dump26.java")],
                       capture_output=True, text=True)
    if r.returncode:
        sys.exit(r.stderr[-4000:])
    r = subprocess.run([java, "-cp", os.pathsep.join([str(classes), cp]), "Dump26", str(OUT / "26x.json")],
                       cwd=OUT, capture_output=True, text=True)
    if r.returncode:
        sys.exit((r.stdout + r.stderr)[-4000:])
    print(f"wrote {OUT / '26x.json'}")


if __name__ == "__main__":
    main()
