"""Tags: those 1.21.1 has, or that a written recipe or our Java (CODE_TAGS) uses, with our ids (called from Port.tags).

Snapshot sub-tags they reference (slabs -> #wool_slabs, #concrete_slabs ...) are written too when they hold ours. A tag
only our Java reads keeps Mojang's references to tags 1.21.1 has; a reference to a 26.x-only tag (happy_ghast_avoids ->
#speleothems) is replaced by that tag's values that exist in 1.21.1, since 1.21.1 cannot resolve the reference.
"""
import json

from backport import CODE_TAGS, NS, RES


def exists_1211(port, kind: str, vid: str) -> bool:
    """A block, item or entity type 1.21.1 has (or one of ours)."""
    ns, path = vid.split(":")
    if ns == NS:
        return True
    if ns != "minecraft":
        return False
    if kind == "block":
        return f"assets/minecraft/blockstates/{path}.json" in port.old_names
    if kind == "entity_type":
        return f"entity.minecraft.{path}" in port.old_lang()
    return port.exists_1211_item(vid)


def run(port):
    tag_files = {n: port.read(n) for n in port.new_names if n.startswith("data/minecraft/tags/") and n.endswith(".json")}

    def kind_of(n):
        return n.split("/tags/", 1)[1].split("/", 1)[0]

    def ref_file(n, v):
        return n.split("/tags/", 1)[0] + "/tags/" + kind_of(n) + "/" + v[1:].split(":")[-1] + ".json"

    def name(n):
        return n.split("/")[-1][:-5]

    def own(n, seen=()):
        """Our values of a tag file, following #references into other snapshot tags (written as references)."""
        values = []
        code_tag = name(n) in CODE_TAGS and n not in port.old_names
        for v in tag_files[n].get("values", []):
            if not isinstance(v, str):
                continue
            if v.startswith("#") and name(n) in CODE_TAGS:
                ref = ref_file(n, v)
                if ref in port.old_names or name(ref) in CODE_TAGS or ref not in tag_files or ref in seen:
                    values.append(v)   # a tag only our Java reads keeps Mojang's references as they are
                else:
                    values += [x for x in own_inline(ref, seen + (n,)) if x not in values]
                continue
            if v.startswith("#"):
                ref = ref_file(n, v)
                if ref in tag_files and ref not in port.old_names and ref not in seen and holds_ours(ref, seen + (n,)):
                    values.append(v)
                continue
            vid = port.id(v)
            recipe_tag = n.startswith("data/minecraft/tags/item/") and "minecraft:" + n.split("/", 4)[4][:-5] in port.recipe_tags
            # ours always; vanilla entries only in a tag a written recipe needs (metal_nuggets), never in a tag
            # 1.21.1 has or a sub-tag it would reach (that would change 1.21.1's own tag behaviour)
            if vid.startswith(f"{NS}:") or ((recipe_tag or code_tag) and n not in port.old_names and exists_1211(port, kind_of(n), vid)):
                values.append(vid)
        return values

    def own_inline(n, seen):
        """Every value of a 26.x-only tag that 1.21.1 has, its own references expanded."""
        out = []
        for v in tag_files[n].get("values", []):
            if not isinstance(v, str):
                continue
            if v.startswith("#"):
                ref = ref_file(n, v)
                if ref in port.old_names:
                    out.append(v)
                elif ref in tag_files and ref not in seen:
                    out += own_inline(ref, seen + (n,))
            elif exists_1211(port, kind_of(n), port.id(v)):
                out.append(port.id(v))
        return out

    def holds_ours(n, seen=()):
        for v in tag_files[n].get("values", []):
            if isinstance(v, str) and v.startswith("#"):
                ref = ref_file(n, v)
                if ref in tag_files and ref not in seen and holds_ours(ref, seen + (n,)):
                    return True
            elif isinstance(v, str) and port.id(v).startswith(f"{NS}:"):
                return True
        return False

    wanted = [n for n in tag_files if n in port.old_names or name(n) in CODE_TAGS or
              (n.startswith("data/minecraft/tags/item/") and "minecraft:" + n.split("/", 4)[4][:-5] in port.recipe_tags)]
    results, queue = {}, list(wanted)
    while queue:
        n = queue.pop()
        if n in results:
            continue
        values = own(n)
        if not values:
            continue
        results[n] = values
        for v in values:
            if v.startswith("#"):
                queue.append(ref_file(n, v))
    # A reference to a 26.x-only tag that holds nothing of ours here (sharp_weapon -> #melee_weapon while spears are
    # missing) would make 1.21.1 drop the whole tag: leave that reference out, and any tag left empty with it.
    pruned = True
    while pruned:
        pruned = False
        for n in list(results):
            keep = [v for v in results[n] if not (v.startswith("#") and ref_file(n, v) not in port.old_names and ref_file(n, v) not in results)]
            if keep != results[n]:
                pruned = True
                if keep:
                    results[n] = keep
                else:
                    del results[n]
    for n, values in results.items():
        port.write(RES / n, {"replace": False, "values": values})
    written = set(results)
    for kind in ("block", "item"):
        f = RES / f"data/minecraft/tags/{kind}/wooden_shelves.json"
        data = json.loads(f.read_text())
        data["values"] += [f"{NS}:{sid}" for sid, *_ in port.modded_shelves]
        port.write(f, data)
    nether = [f"{NS}:{sid}" for sid, _, _, n in port.modded_shelves if n]
    if nether:
        f = RES / "data/minecraft/tags/item/non_flammable_wood.json"
        data = json.loads(f.read_text())
        data["values"] += nether
        port.write(f, data)
    port.n_tags = len(written)
