"""Vanilla advancements that 26.x extends with our mobs and effects (called from mobs.run).

Each is 1.21.1's file with 26.x's criteria for our entities added (converted by lootfmt: entity predicates by
component -> 1.21.1 keys), and their requirement entries placed as 26.x places them: one shared "any of" list
(kill_a_mob) or one list each (kill_all_mobs, bred_all_animals). all_effects gets our effects in its effect map.
Criteria for 26.x mobs not backported (creaking, frostbite, zombie horse spawns ...) wait, as the mobs do.
"""
import json

import lootfmt

NS = "haven_backports"
CRITERIA = ["adventure/kill_a_mob", "adventure/kill_all_mobs", "husbandry/bred_all_animals"]
EFFECTS = ["nether/all_effects"]


def ours(port, criterion: str) -> bool:
    return criterion.startswith("minecraft:") and port.id(criterion) != criterion


def run(port, res, effects=()):
    conv = lootfmt.Converter(port.ids)
    for adv in CRITERIA:
        name = f"data/minecraft/advancement/{adv}.json"
        new, old = port.read(name), json.loads(port.old.read(name))
        added = [c for c in new["criteria"] if c not in old["criteria"] and ours(port, c)]
        if not added:
            continue
        for c in added:
            crit = json.loads(json.dumps(new["criteria"][c]))
            crit["conditions"] = {k: [conv.condition(v)] if isinstance(v, dict) else v for k, v in crit["conditions"].items()}
            old["criteria"][port.id(c)] = port.ids(crit)
        if len(old["requirements"]) == 1:
            old["requirements"][0] += [port.id(c) for c in added]
        else:
            old["requirements"] += [[port.id(c)] for c in added]
        port.write(res / name, old)
        print(f"advancement {adv}: 26.x criteria added {[port.id(c) for c in added]}")
    for adv in EFFECTS:
        name = f"data/minecraft/advancement/{adv}.json"
        new, old = port.read(name), json.loads(port.old.read(name))
        added = [e for e in new["criteria"]["all_effects"]["conditions"]["effects"] if e.split(":")[1] in effects
                 and e not in old["criteria"]["all_effects"]["conditions"]["effects"]]
        if not added:
            continue
        for e in added:
            old["criteria"]["all_effects"]["conditions"]["effects"][f"{NS}:{e.split(':')[1]}"] = {}
        port.write(res / name, old)
        print(f"advancement {adv}: 26.x effects added {[f'{NS}:' + e.split(':')[1] for e in added]}")
