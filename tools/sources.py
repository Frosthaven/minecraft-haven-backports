"""Mojang's files: the pinned snapshot jar and asset store (sha1-checked, cached), and format-neutral comparisons."""
import hashlib
import json
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

VERSION = "26.4-snapshot-7"
CACHE = Path(__file__).resolve().parent.parent / ".cache"
# Mojang's DyeColor order (ColorCollection, item and block registration order).
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"


def get(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=300) as r:
        return r.read()


def snapshot() -> tuple[zipfile.ZipFile, dict]:
    jar_path, index_path = CACHE / f"minecraft-{VERSION}-client.jar", CACHE / f"{VERSION}-assets.json"
    if not jar_path.exists() or not index_path.exists():
        manifest = json.loads(get(MANIFEST))
        version = json.loads(get(next(v["url"] for v in manifest["versions"] if v["id"] == VERSION)))
        client = version["downloads"]["client"]
        data = get(client["url"])
        if hashlib.sha1(data).hexdigest() != client["sha1"]:
            sys.exit(f"{VERSION} client jar: sha1 mismatch")
        CACHE.mkdir(exist_ok=True)
        jar_path.write_bytes(data)
        index = get(version["assetIndex"]["url"])
        if hashlib.sha1(index).hexdigest() != version["assetIndex"]["sha1"]:
            sys.exit(f"{VERSION} asset index: sha1 mismatch")
        index_path.write_bytes(index)
    return zipfile.ZipFile(jar_path), json.loads(index_path.read_text())["objects"]


def old_sounds() -> dict:
    """1.21.1's sounds.json (its asset store index, sha1-checked, cached): the sound events 1.21.1 already has."""
    path = CACHE / "1.21.1-assets.json"
    if not path.exists():
        manifest = json.loads(get(MANIFEST))
        version = json.loads(get(next(v["url"] for v in manifest["versions"] if v["id"] == "1.21.1")))
        index = get(version["assetIndex"]["url"])
        if hashlib.sha1(index).hexdigest() != version["assetIndex"]["sha1"]:
            sys.exit("1.21.1 asset index: sha1 mismatch")
        path.write_bytes(index)
    return json.loads(asset(json.loads(path.read_text())["objects"], "minecraft/sounds.json"))


def asset(objects: dict, name: str) -> bytes:
    """A file from Mojang's asset store (sounds live there, not in the jar), checked against the index hash."""
    entry = objects[name]
    path = CACHE / "objects" / entry["hash"]
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        data = get(f"https://resources.download.minecraft.net/{entry['hash'][:2]}/{entry['hash']}")
        if hashlib.sha1(data).hexdigest() != entry["hash"]:
            sys.exit(f"{name}: sha1 mismatch")
        path.write_bytes(data)
    return path.read_bytes()


def pixels(png: bytes) -> bytes:
    """Decoded RGBA pixels: file bytes differ when Mojang only re-saves a PNG."""
    import io
    from PIL import Image
    return Image.open(io.BytesIO(png)).convert("RGBA").tobytes()


def models_in(value) -> list[str]:
    if isinstance(value, dict):
        return [value["model"]] if "model" in value else [m for v in value.values() for m in models_in(v)]
    if isinstance(value, list):
        return [m for v in value for m in models_in(v)]
    return []


def old_format(r):
    """1.21.1's recipe JSON in the same shape canon() reads."""
    return r


def canon(r: dict) -> str:
    """Type, result and the ingredient ids of a recipe, in either jar's format, as one comparable string."""
    def ing(v):
        if isinstance(v, list):
            return sorted((ing(x) for x in v), key=lambda x: json.dumps(x))
        if isinstance(v, dict):
            return ("#" + v["tag"]) if "tag" in v else v.get("item", json.dumps(v, sort_keys=True))
        return v
    res = r.get("result", {})
    res = (res.get("id") or res.get("item"), res.get("count", 1)) if isinstance(res, dict) else (res, 1)
    parts = {"type": r.get("type"), "result": res}
    if "key" in r:
        parts["pattern"] = r.get("pattern")
        parts["key"] = {k: ing(v) for k, v in sorted(r["key"].items())}
    for k in ("ingredient", "ingredients", "base", "addition", "template"):
        if k in r:
            parts[k] = json.dumps(sorted((ing(x) for x in r[k]), key=lambda x: json.dumps(x)) if k == "ingredients" else ing(r[k]))
    return json.dumps(parts, sort_keys=True, default=str)


VINEFLOWER = CACHE / "tools/vineflower-1.11.1.jar"


def decompile(jar_path, class_path: str) -> str:
    """Source of one class from the jar (vineflower, cached by class)."""
    out = CACHE / "decompiled" / class_path.rsplit("/", 1)[0]
    src = CACHE / "decompiled" / f"{class_path}.java"
    if not src.exists():
        if not VINEFLOWER.exists():
            sys.exit(f"{VINEFLOWER} is missing (vineflower 1.11.1, see CLAUDE.md)")
        out.mkdir(parents=True, exist_ok=True)
        tmp = CACHE / "decompiled/_run"
        subprocess.run(["java", "-jar", str(VINEFLOWER), f"-only={class_path}", str(jar_path), str(tmp)], check=True,
                       stdout=subprocess.DEVNULL)
        found = next((p for p in tmp.rglob("*.java") if p.name == class_path.rsplit("/", 1)[1] + ".java"), None)
        if found is None:
            raw = next(tmp.rglob("*.jar"), None)
            if raw is None:
                raise SystemExit(f"vineflower wrote no source for {class_path}")
            with zipfile.ZipFile(raw) as z:
                src.write_text(z.read(f"{class_path}.java").decode())
        else:
            src.write_text(found.read_text())
        shutil.rmtree(tmp, ignore_errors=True)
    return src.read_text()
