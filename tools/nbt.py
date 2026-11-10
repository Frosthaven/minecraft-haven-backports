"""Minimal NBT (gzipped, big endian) reader and writer, enough for structure templates; and 26.x -> 1.21.1 template
conversion (tools/biomes.py calls convert_template for the structures a ported feature places).

26.x templates name palette states {"id", "properties"}; 1.21.1 reads {"Name", "Properties"}. 1.21.1 cannot load a
template saved by a newer version (DataFixers run forward only), so DataVersion becomes 1.21.1's 3955. Any key the
conversion does not know stops the run.
"""
import gzip
import io
import struct
import sys

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)
DATA_VERSION_1211 = 3955


class Tag:
    """A typed value: kind is the NBT tag id; for lists, elem is the element id."""
    def __init__(self, kind, value, elem=None):
        self.kind, self.value, self.elem = kind, value, elem


def _read(f, kind):
    if kind == BYTE:
        return struct.unpack(">b", f.read(1))[0]
    if kind == SHORT:
        return struct.unpack(">h", f.read(2))[0]
    if kind == INT:
        return struct.unpack(">i", f.read(4))[0]
    if kind == LONG:
        return struct.unpack(">q", f.read(8))[0]
    if kind == FLOAT:
        return struct.unpack(">f", f.read(4))[0]
    if kind == DOUBLE:
        return struct.unpack(">d", f.read(8))[0]
    if kind == BYTE_ARRAY:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}b", f.read(n)))
    if kind == STRING:
        n = struct.unpack(">H", f.read(2))[0]
        return f.read(n).decode("utf-8")
    if kind == LIST:
        elem = f.read(1)[0]
        n = struct.unpack(">i", f.read(4))[0]
        return Tag(LIST, [Tag(elem, _read(f, elem)) for _ in range(n)], elem)
    if kind == COMPOUND:
        out = {}
        while True:
            k = f.read(1)[0]
            if k == END:
                return out
            name = _read(f, STRING)
            out[name] = Tag(k, _read(f, k))
    if kind == INT_ARRAY:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}i", f.read(4 * n)))
    if kind == LONG_ARRAY:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}q", f.read(8 * n)))
    raise ValueError(f"tag kind {kind}")


def _write(f, tag):
    k, v = tag.kind, tag.value
    if k == BYTE:
        f.write(struct.pack(">b", v))
    elif k == SHORT:
        f.write(struct.pack(">h", v))
    elif k == INT:
        f.write(struct.pack(">i", v))
    elif k == LONG:
        f.write(struct.pack(">q", v))
    elif k == FLOAT:
        f.write(struct.pack(">f", v))
    elif k == DOUBLE:
        f.write(struct.pack(">d", v))
    elif k == BYTE_ARRAY:
        f.write(struct.pack(">i", len(v)) + struct.pack(f">{len(v)}b", *v))
    elif k == STRING:
        b = v.encode("utf-8")
        f.write(struct.pack(">H", len(b)) + b)
    elif k == LIST:
        items = v.value if isinstance(v, Tag) else v
        elem = v.elem if isinstance(v, Tag) else tag.elem
        f.write(bytes([elem if elem is not None else (items[0].kind if items else END)]) + struct.pack(">i", len(items)))
        for item in items:
            _write(f, item)
    elif k == COMPOUND:
        for name, child in v.items():
            f.write(bytes([child.kind]))
            _write(f, Tag(STRING, name))
            _write(f, child)
        f.write(bytes([END]))
    elif k == INT_ARRAY:
        f.write(struct.pack(">i", len(v)) + struct.pack(f">{len(v)}i", *v))
    elif k == LONG_ARRAY:
        f.write(struct.pack(">i", len(v)) + struct.pack(f">{len(v)}q", *v))


def load(data: bytes) -> Tag:
    f = io.BytesIO(gzip.decompress(data))
    kind = f.read(1)[0]
    _read(f, STRING)
    return Tag(kind, _read(f, kind))


def dump(tag: Tag) -> bytes:
    f = io.BytesIO()
    f.write(bytes([tag.kind]))
    _write(f, Tag(STRING, ""))
    _write(f, tag)
    return gzip.compress(f.getvalue(), mtime=0)


def convert_template(data: bytes, block_id, where: str) -> bytes:
    """A 26.x structure template in 1.21.1's form; block_id maps each block id (to ours where we port it)."""
    root = load(data)
    c = root.value
    known = {"DataVersion", "size", "palette", "palettes", "blocks", "entities"}
    if set(c) - known:
        sys.exit(f"{where}: template keys {sorted(set(c) - known)} need porting by hand")
    if c.get("entities") and c["entities"].value.value:
        sys.exit(f"{where}: template entities need porting by hand")
    palettes = [c["palette"].value] if "palette" in c else [p.value for p in c["palettes"].value.value]
    for palette in palettes:
        for entry in palette.value:
            s = entry.value
            if set(s) - {"id", "properties", "Name", "Properties"}:
                sys.exit(f"{where}: palette keys {sorted(set(s))}")
            name = s.pop("id", None) or s.pop("Name")
            props = s.pop("properties", None) or s.pop("Properties", None)
            s["Name"] = Tag(STRING, block_id(name.value))
            if props is not None:
                s["Properties"] = props
    for b in c.get("blocks", Tag(LIST, Tag(LIST, []))).value.value:
        if "nbt" in b.value:
            sys.exit(f"{where}: block entity data in templates needs porting by hand")
    c["DataVersion"] = Tag(INT, DATA_VERSION_1211)
    return dump(root)
