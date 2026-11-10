"""The abandoned camp (26.x: one jigsaw structure per biome variant, all in one structure set), called from Port.run
after tools/mobs.py (the templates hold its blocks), with extras.run's loot converter: structure set, structures, template pools, templates, the biome and structure tags, the three loot
tables and the map names they give.

- Ids: the structures, the set, the pools and the templates are ours (haven_backports:abandoned_camp_<variant>,
  haven_backports:abandoned_camp/...). 26.x's tags (#has_structure/abandoned_camp_<variant>, #abandoned_camp,
  #on_abandoned_camp_<variant>) and its loot tables keep Mojang's minecraft: names: 1.21.1 has none of them.
- Every value is Mojang's (spacing, separation, salt, weights, sizes, heights, terrain adaptation). A structure,
  pool or element key not listed here stops the run.
- The tree pools place 26.x placed features through tools/worldgen.py (1.21.1's where it has the id, else ours). No
  biome lists them, so no biome's feature order changes.
- Templates (tools/nbt.py): a block that is neither 1.21.1's nor ours stops the run. Block entities: id mapped, the
  jigsaw's pool and final state mapped, LootTable kept; only keys 1.21.1 reads under the same name, empty components
  and items. Entities (cushions): 26.x's block_pos is 1.21.1's TileX/TileY/TileZ and fall_distance its FallDistance;
  StructureTemplateMixin sets TileX/Y/Z to the placed block, as 26.x placeEntities sets block_pos.
"""
import re
import sys

import nbt

NS = "haven_backports"
# 26.x's structure set order (checked against the jar); each is abandoned_camp_<variant>
VARIANTS = ["bamboo_jungle", "birch_forest", "cherry_grove", "dappled_forest", "flower_forest", "forest", "meadow",
            "old_growth_pine_taiga", "old_growth_birch_forest", "old_growth_spruce_taiga", "pale_garden", "savanna",
            "snowy_taiga", "sparse_jungle", "swamp", "taiga", "windswept_forest", "wooded_badlands"]
STRUCTURES = [f"abandoned_camp_{v}" for v in VARIANTS]
LOOT = ["barrels/abandoned_camp_barrel", "chests/abandoned_camp_common_chest", "chests/abandoned_camp_secret_chest"]
STRUCTURE_KEYS = {"type", "biomes", "max_distance_from_center", "project_start_to_heightmap", "size", "spawn_overrides",
                  "start_height", "start_pool", "step", "terrain_adaptation", "use_expansion_hack"}
ELEMENT_KEYS = {"minecraft:legacy_single_pool_element": {"element_type", "location", "processors", "projection"},
                "minecraft:feature_pool_element": {"element_type", "feature", "projection"}}
# 26.x block entity keys that 1.21.1 reads under the same name, per block entity type
BE_KEYS = {"minecraft:chest": {"LootTable", "Items"}, "minecraft:barrel": {"LootTable", "Items"},
           "minecraft:campfire": {"Items", "CookingTimes", "CookingTotalTimes"},
           "minecraft:jigsaw": {"name", "target", "pool", "final_state", "joint", "placement_priority", "selection_priority"},
           "minecraft:copper_golem_statue": set(), "minecraft:creaking_heart": set()}
# 26.x entity keys 1.21.1's Entity and our Cushion read under the same name
ENTITY_KEYS = {"id", "UUID", "Pos", "Motion", "Rotation", "Air", "Fire", "OnGround", "Invulnerable", "PortalCooldown", "color"}
ENTITIES = {"minecraft:cushion"}
PREFIX = "minecraft:abandoned_camp/"


def run(port, res, conv, lang_new, lang):
    import extras
    import maps
    Camp(port, res).run()
    for table in LOOT:
        extras.take_loot(port, res, conv, table)
        if not (res / f"data/minecraft/loot_table/{table}.json").exists():
            sys.exit(f"loot {table}: not written, so the camp's containers would be empty")
        for key in re.findall(r'"translate": "([^"]+)"', port.jar.read(f"data/minecraft/loot_table/{table}.json").decode()):
            lang[key] = lang_new[key]
    maps.structure_tags(port, res, LOOT)


class Camp:
    def __init__(self, port, res):
        self.port, self.res, self.wg = port, res, port.wg
        self.pools: dict[str, bool] = {}
        self.templates: set[str] = set()

    def write(self, path, data):
        self.port.write(self.res / path, data)

    def run(self):
        sset = self.port.read("data/minecraft/worldgen/structure_set/abandoned_camp.json")
        if [s["structure"] for s in sset["structures"]] != [f"minecraft:{s}" for s in STRUCTURES]:
            sys.exit("structure_set abandoned_camp: 26.x's structures differ from camp.VARIANTS")
        sset["structures"] = [{**s, "structure": self.port.id(s["structure"])} for s in sset["structures"]]
        self.write(f"data/{NS}/worldgen/structure_set/abandoned_camp.json", sset)
        for name in STRUCTURES:
            self.structure(name)
        self.structure_tag("abandoned_camp")
        print(f"abandoned camp: {len(STRUCTURES)} structures, {len(self.pools)} pools, {len(self.templates)} templates")

    def structure(self, name):
        s = self.port.read(f"data/minecraft/worldgen/structure/{name}.json")
        if set(s) - STRUCTURE_KEYS or s["type"] != "minecraft:jigsaw":
            sys.exit(f"structure {name}: keys {sorted(set(s) - STRUCTURE_KEYS)} or type {s['type']} need porting")
        if not s["biomes"].startswith("#"):
            sys.exit(f"structure {name}: biomes {s['biomes']} is not a tag")
        self.biome_tag(s["biomes"][1:])
        s["start_pool"] = self.pool(s["start_pool"])
        self.write(f"data/{NS}/worldgen/structure/{name}.json", s)

    def biome_tag(self, tag):
        path = f"data/minecraft/tags/worldgen/biome/{tag.split(':')[1]}.json"
        if path in self.port.old_names:
            sys.exit(f"biome tag {tag}: 1.21.1 has it; it would need a merge, not a new file")
        values = []
        for v in self.port.read(path)["values"]:
            b = self.port.id(v)
            ours = (self.res / f"data/{NS}/worldgen/biome/{b.split(':')[1]}.json").exists()
            if not (b.startswith(f"{NS}:") and ours) and f"data/minecraft/worldgen/biome/{b.split(':')[1]}.json" not in self.port.old_names:
                sys.exit(f"biome tag {tag}: biome {v} is neither 1.21.1's nor backported")
            values.append(b)
        self.write(path, {"replace": False, "values": values})

    def structure_tag(self, tag):
        path = f"data/minecraft/tags/worldgen/structure/{tag}.json"
        values = [self.port.id(v) for v in self.port.read(path)["values"]]
        if any(not v.startswith(f"{NS}:") for v in values):
            sys.exit(f"structure tag {tag}: {values} holds structures that are not the camp's")
        self.write(path, {"replace": False, "values": values})

    # ---------- pools and templates ----------
    def pool(self, pid):
        """A 26.x camp pool (written once) -> our id."""
        if pid == "minecraft:empty":
            return pid
        if not pid.startswith(PREFIX):
            sys.exit(f"pool {pid} is not the camp's")
        ours = f"{NS}:{pid.split(':')[1]}"
        if pid in self.pools:
            return ours
        self.pools[pid] = True
        p = self.port.read(f"data/minecraft/worldgen/template_pool/{pid.split(':')[1]}.json")
        if set(p) != {"elements", "fallback"}:
            sys.exit(f"pool {pid}: keys {sorted(p)}")
        p["fallback"] = self.pool(p["fallback"])
        for e in p["elements"]:
            el = e["element"]
            kind = el["element_type"]
            if kind not in ELEMENT_KEYS or set(el) != ELEMENT_KEYS[kind] or set(e) != {"element", "weight"}:
                sys.exit(f"pool {pid}: element {el} needs porting")
            if kind == "minecraft:feature_pool_element":
                el["feature"] = self.wg.placed_ref(el["feature"])
            else:
                if el["processors"] != {"processors": []}:
                    sys.exit(f"pool {pid}: processors {el['processors']} need porting")
                el["location"] = self.template(el["location"])
        self.write(f"data/{NS}/worldgen/template_pool/{pid.split(':')[1]}.json", p)
        return ours

    def template(self, tid):
        if not tid.startswith(PREFIX):
            sys.exit(f"template {tid} is not the camp's")
        name = tid.split(":")[1]
        if name not in self.templates:
            self.templates.add(name)
            data = nbt.convert_template(self.port.jar.read(f"data/minecraft/structure/{name}.nbt"), self.block_id,
                                        f"structure {tid}", self.block_entity, self.entity)
            out = self.res / "data" / NS / "structure" / f"{name}.nbt"
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(data)
        return f"{NS}:{name}"

    def block_id(self, v):
        b = self.port.id(v)
        path = b.split(":")[1]
        if b.startswith(f"{NS}:"):
            if not (self.res / f"assets/{NS}/blockstates/{path}.json").exists():
                sys.exit(f"camp template block {v}: ours by name, but no block of ours is written")
        elif f"assets/minecraft/blockstates/{path}.json" not in self.port.old_names:
            sys.exit(f"camp template block {v}: neither 1.21.1 nor Haven Backports has it")
        return b

    def block_entity(self, c):
        kind = c["id"].value
        if kind not in BE_KEYS or set(c) - BE_KEYS[kind] - {"id", "components"}:
            sys.exit(f"camp block entity {kind}: keys {sorted(c)} need porting")
        if c["components"].value or ("Items" in c and c["Items"].value.value):
            sys.exit(f"camp block entity {kind}: components or items need porting")
        c["id"] = nbt.Tag(nbt.STRING, self.port.id(kind))
        if kind == "minecraft:jigsaw":
            c["pool"] = nbt.Tag(nbt.STRING, self.pool(c["pool"].value))
            state = c["final_state"].value
            block, _, props = state.partition("[")
            c["final_state"] = nbt.Tag(nbt.STRING, self.block_id(block) + (f"[{props}" if props else ""))

    def entity(self, info):
        c = info["nbt"].value
        kind = c["id"].value
        if kind not in ENTITIES or set(c) - ENTITY_KEYS - {"block_pos", "fall_distance"}:
            sys.exit(f"camp entity {kind}: keys {sorted(c)} need porting")
        c["id"] = nbt.Tag(nbt.STRING, self.port.id(kind))
        if not c["id"].value.startswith(f"{NS}:"):
            sys.exit(f"camp entity {kind}: not ours")
        if "block_pos" in c:
            for axis, v in zip("XYZ", c.pop("block_pos").value):
                c[f"Tile{axis}"] = nbt.Tag(nbt.INT, v)
        if "fall_distance" in c:
            c["FallDistance"] = nbt.Tag(nbt.FLOAT, c.pop("fall_distance").value)
