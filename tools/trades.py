"""26.x wandering trader trades that give one of ours -> data/haven_backports/wandering_trades.json (read by Java).

26.x draws the trader's offers from three trade sets: buying (2), common (5) and uncommon (2), each a tag of
villager_trade files. 1.21.1 has two pools: generic (5 picks) and rare (1 pick). Common maps to generic (same pick
count); uncommon maps to rare, the closest 1.21.1 pool (1 pick in place of 2: such offers show up about half as
often). Defaults are 26.x VillagerTrade's: max_uses 4, xp 1, reputation_discount 0.
"""
import json
import sys

NS = "haven_backports"
POOLS = {"common": "generic", "uncommon": "rare"}


def run(port, res):
    out = []
    for pool in ("buying", "common", "uncommon"):
        tag = port.read(f"data/minecraft/tags/villager_trade/wandering_trader/{pool}.json")
        for ref in tag["values"]:
            trade = port.read(f"data/minecraft/villager_trade/{ref.split(':')[-1]}.json")
            gives = port.id(trade["gives"]["id"])
            if not gives.startswith(f"{NS}:"):
                continue
            if pool not in POOLS:
                sys.exit(f"trade {ref}: 26.x pool {pool} has no 1.21.1 form")
            extra = set(trade) - {"gives", "wants", "max_uses", "xp", "reputation_discount"}
            if extra or not all(isinstance(trade.get(k, 0), (int, float)) for k in ("max_uses", "xp", "reputation_discount")):
                sys.exit(f"trade {ref}: {sorted(extra)} or a provider value needs porting by hand")
            wants = trade["wants"]
            out.append({"trade": ref, "pool": POOLS[pool],
                        "wants": {"id": port.id(wants["id"]), "count": wants.get("count", 1)},
                        "gives": {"id": gives, "count": trade["gives"].get("count", 1)},
                        "max_uses": trade.get("max_uses", 4), "xp": trade.get("xp", 1),
                        "reputation_discount": trade.get("reputation_discount", 0.0)})
    port.write(res / "data" / NS / "wandering_trades.json", out)
    return out
