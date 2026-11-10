"""Vanilla loot tables as patches: 26.x's changes to a minecraft table, applied at load on top of the table that wins.

A whole-table file in data/minecraft/loot_table would replace any other mod's or datapack's version of that table. So
for each vanilla table the generator compares 26.x's converted table with 1.21.1's own and writes only the difference
to data/haven_backports/loot_patches/<table>.json. Java (loot/LootPatches.java) applies it in LootTableLoadEvent:

- `pools`: one item per 1.21.1 pool, in 1.21.1 order. `match` holds the pool's entry keys (type and name): the pool
  in the loaded table that shares the most keys is the target, so a mod may reorder pools or add its own. Then
  `remove` and `change` (`from` -> `to`) apply only to an entry equal to the 1.21.1 entry; `add` inserts an entry
  after the entry it follows in 26.x (`after`, null: first), or at the end when that entry is absent; `set` changes a
  pool field (`rolls`, `conditions`...) only when it still has its 1.21.1 value. When no pool matches, `fallback`
  is a pool of the added entries with 26.x's rolls and an empty entry for the rest of the pool's weight, so each
  roll gives our entries the same chance.
- `add_pools`: 26.x pools 1.21.1 lacks, each inserted after the pool `after` (an index into `pools`, -1: first).

Both sides are compared after `canon`: 1.21.1's files spell out defaults (`"add": false`, `"bonus_rolls": 0.0`,
`1.0` for `1`) that 26.x leaves out. Every patch is applied here to 1.21.1's table and must give 26.x's table, or
the run stops: a change the patch format cannot hold is never written half.
"""
import copy
import difflib
import json
import sys

NS = "haven_backports"
DEFAULTS = {"add": False, "bonus_rolls": 0, "weight": 1, "quality": 0, "functions": [], "conditions": []}
POOL_FIELDS = ["rolls", "bonus_rolls", "conditions", "functions"]
TABLE = [""]   # the table being diffed, for error messages
STATS = {}   # table -> change classes, for the run's report


def canon(v):
    if isinstance(v, float) and v.is_integer():
        return int(v)
    if isinstance(v, list):
        return [canon(x) for x in v]
    if isinstance(v, dict):
        if set(v) == {"type", "value"} and v["type"] == "minecraft:constant":
            return canon(v["value"])
        out = {k: canon(x) for k, x in sorted(v.items())}
        return {k: x for k, x in out.items() if not (k in DEFAULTS and x == DEFAULTS[k])}
    return v


def key(e):
    """An entry's identity for pool matching (LootPatches.key does the same on the codec's JSON)."""
    name = e.get("name", e.get("value"))
    own = e.get("type", "") + " " + (name if isinstance(name, str) else "")
    return own + "".join(f" ({key(c)})" for c in e.get("children", []))


def keys(pool):
    return sorted({key(e) for e in pool["entries"]})


def match(wanted, pools):
    """Patch pool index -> table pool index: highest shared-key count first, then nearest index (as LootPatches)."""
    pairs = []
    for i, w in enumerate(wanted):
        for t, p in enumerate(pools):
            shared = len(set(w) & set(keys(p)))
            if shared:
                pairs.append((-shared, abs(i - t), i, t))
    out, used = {}, set()
    for _, _, i, t in sorted(pairs):
        if i not in out and t not in used:
            out[i] = t
            used.add(t)
    return out


def weight(e):
    return e.get("weight", 1)


def pool_patch(old, new):
    ok, nk = [json.dumps(e, sort_keys=True) for e in old["entries"]], [json.dumps(e, sort_keys=True) for e in new["entries"]]
    p = {"match": keys(old)}
    common = [e for e in ok if e in nk]
    if common != [e for e in nk if e in ok]:
        sys.exit(f"lootpatch {TABLE[0]}: 26.x reorders kept entries of a pool; the patch format cannot hold that")
    # A replace block pairs entries 1.21.1 -> 26.x in place (saddle -> leather, map -> treasure map): a change.
    removed, change, add = [], [], []
    for op, a1, a2, b1, b2 in difflib.SequenceMatcher(None, ok, nk, autojunk=False).get_opcodes():
        n = min(a2 - a1, b2 - b1) if op == "replace" else 0
        change += [{"from": json.loads(ok[a1 + k]), "to": json.loads(nk[b1 + k])} for k in range(n)]
        if op in ("replace", "delete"):
            removed += ok[a1 + n:a2]
        if op in ("replace", "insert"):
            add += [{"after": json.loads(nk[j - 1]) if j else None, "entry": json.loads(nk[j])} for j in range(b1 + n, b2)]
    if removed:
        p["remove"] = [json.loads(e) for e in removed]
    if change:
        p["change"] = change
    if add:
        p["add"] = add
        ours = [a["entry"] for a in add]
        if any("weight" not in e and e.get("type") in ("minecraft:alternatives", "minecraft:group", "minecraft:sequence")
               for e in new["entries"]):
            sys.exit(f"lootpatch {TABLE[0]}: a composite entry in a pool with additions; no equal-chance fallback pool")
        rest = sum(weight(e) for e in new["entries"]) - sum(weight(e) for e in ours)
        fb = {k: new[k] for k in POOL_FIELDS if k in new}
        fb["entries"] = ours + ([{"type": "minecraft:empty", "weight": rest}] if rest > 0 else [])
        p["fallback"] = fb
    props = {k: {"from": old.get(k), "to": new.get(k)} for k in POOL_FIELDS if old.get(k) != new.get(k)}
    if props:
        p["set"] = props
    return p


def diff(old, new):
    if {k: v for k, v in old.items() if k != "pools"} != {k: v for k, v in new.items() if k != "pools"}:
        sys.exit(f"lootpatch {TABLE[0]}: 26.x changes a table field other than pools; the patch format cannot hold that")
    olds, news = old["pools"], new["pools"]
    pairs = match([keys(p) for p in news], olds)   # 26.x pool -> 1.21.1 pool
    # a 1.21.1 pool none of whose entries 26.x keeps (shipwreck_map's map) pairs with the 26.x pool left in its place
    left = [t for t in range(len(olds)) if t not in pairs.values()]
    for j in range(len(news)):
        if j not in pairs and left and all(pairs[k] < left[0] for k in pairs if k < j):
            pairs[j] = left.pop(0)
    if len(pairs) != len(olds):
        sys.exit(f"lootpatch {TABLE[0]}: 26.x drops a 1.21.1 pool; the patch format cannot hold that")
    if [pairs[j] for j in sorted(pairs)] != sorted(pairs.values()):
        sys.exit(f"lootpatch {TABLE[0]}: 26.x reorders 1.21.1's pools; the patch format cannot hold that")
    back = {t: j for j, t in pairs.items()}
    patch = {"pools": [pool_patch(olds[t], news[back[t]]) for t in range(len(olds))], "add_pools": []}
    last = -1
    for j, p in enumerate(news):
        if j in pairs:
            last = pairs[j]
        else:
            patch["add_pools"].append({"after": last, "pool": p})
    if not patch["add_pools"]:
        del patch["add_pools"]
    return patch


def apply(patch, table):
    """The Python twin of LootPatches.apply (canon JSON in, canon JSON out), for the self-check on 1.21.1's table."""
    pools = copy.deepcopy(table["pools"])
    where = match([p["match"] for p in patch["pools"]], pools)
    for i, pp in enumerate(patch["pools"]):
        if i not in where:
            continue
        pool = pools[where[i]]
        entries = pool["entries"]
        for e in pp.get("remove", []):
            if e in entries:
                entries.remove(e)
        for c in pp.get("change", []):
            if c["from"] in entries:
                entries[entries.index(c["from"])] = c["to"]
        for a in pp.get("add", []):
            if a["entry"] in entries:
                continue
            at = 0 if a["after"] is None else (entries.index(a["after"]) + 1 if a["after"] in entries else len(entries))
            entries.insert(at, a["entry"])
        for k, s in pp.get("set", {}).items():
            if pool.get(k) == s["from"]:
                if s["to"] is None:
                    pool.pop(k, None)
                else:
                    pool[k] = s["to"]
    adds = patch.get("add_pools", [])
    out = [a["pool"] for a in adds if a["after"] == -1]
    for t, p in enumerate(pools):
        out.append(p)
        out += [a["pool"] for i, x in where.items() if x == t for a in adds if a["after"] == i]
    out += [a["pool"] for a in adds if a["after"] != -1 and a["after"] not in where]   # its pool is gone: at the end
    return {**table, "pools": out}


def write(port, res, table, new):
    """Writes the patch that turns 1.21.1's `table` into `new` (26.x's, converted), checked by applying it."""
    old = canon(json.loads(port.old.read(f"data/minecraft/loot_table/{table}.json")))
    new = canon(new)
    TABLE[0] = table
    patch = diff(old, new)
    if canon(apply(patch, old)) != new:
        sys.exit(f"lootpatch: the patch for {table} does not give 26.x's table")
    port.write(res / "data" / NS / "loot_patches" / f"{table}.json", patch)
    STATS[table] = {
        "added": sum(len(p.get("add", [])) for p in patch["pools"]),
        "removed": sum(len(p.get("remove", [])) for p in patch["pools"]),
        "changed": sum(len(p.get("change", [])) for p in patch["pools"]),
        "pool fields": sum(len(p.get("set", {})) for p in patch["pools"]),
        "new pools": len(patch.get("add_pools", [])),
    }


def report():
    total = {}
    for table, s in sorted(STATS.items()):
        print(f"loot patch {table}: " + ", ".join(f"{v} {k}" for k, v in s.items() if v))
        for k, v in s.items():
            total[k] = total.get(k, 0) + v
    print(f"loot patches: {len(STATS)} vanilla tables, " + ", ".join(f"{v} {k}" for k, v in total.items()))
