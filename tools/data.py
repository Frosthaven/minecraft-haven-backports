"""Port methods that write data from Mojang's files: modded shelves, shelf displays, recipes, tags (tools/tags.py).

Split out of backport.py (each file stays under 500 lines); Port inherits them. Constants come from backport.
"""
import json
import os
import sys
from pathlib import Path

import models
import modshelves
from sources import canon, old_format


B = None   # the backport module (set by backport.py: it holds the constants)


class DataPort:
    def mod_shelves(self, lang):
        mods = sys.argv[sys.argv.index("--mods") + 1] if "--mods" in sys.argv else os.environ.get("HAVEN_MODS")
        if not mods:
            sys.exit("modded shelves need --mods / HAVEN_MODS (the pack's mods folder)")
        self.modded_shelves, names, base = modshelves.run(self, B.RES, Path(mods))
        lang.update(names)
        print(f"modded shelves: {len(self.modded_shelves)}, art from: {base}")
        modshelves.java(B.REPO, self.modded_shelves)

    def shelf_displays(self):
        patches, notes = models.vanilla_shelf_patches(self.jar, self.old)
        for path, data in patches.items():
            self.write(B.RES / "assets/minecraft/models" / f"{path}.json", data)
        self.n_shelf_patches = len(patches)
        if notes:
            print(f"on_shelf added to vanilla models 26.x also changed otherwise (1.21.1 content kept): {notes}")

    def sound_id(self, event: str) -> str:
        """A sound event id: ours when this mod ships the event (1.21.1 lacks it), else vanilla."""
        name = event.split(":")[-1]
        return f"{B.NS}:{name}" if name in B.EXTRA_SOUNDS else event

    def exists_1211_id(self, vid, tag_file) -> bool:
        """A vanilla id a new tag may hold: what the tag's registry has in 1.21.1."""
        ns, path = vid.split(":")
        if ns != "minecraft":
            return False
        if "/tags/block/" in tag_file:
            return f"assets/minecraft/blockstates/{path}.json" in self.old_names
        if "/tags/entity_type/" in tag_file:
            return f"entity.minecraft.{path}" in self.old_lang()
        return self.exists_1211_item(vid)

    def old_lang(self):
        if not hasattr(self, "_old_lang"):
            self._old_lang = json.loads(self.old.read("assets/minecraft/lang/en_us.json"))
        return self._old_lang

    def recipes(self):
        self.old_recipe_keys = set()
        for n in self.old_names:
            if n.startswith("data/minecraft/recipe/") and n.endswith(".json"):
                self.old_recipe_keys.add(canon(old_format(json.loads(self.old.read(n)))))
        self.n_recipes = self.n_extra = 0
        for n in sorted(self.new_names):
            if not (n.startswith("data/minecraft/recipe/") and n.endswith(".json")):
                continue
            rid = n.split("/")[-1][:-5]
            raw = self.read(n)
            result = raw.get("result", {})
            result_id = (result.get("id") if isinstance(result, dict) else result) or ""
            ours = result_id.split(":")[-1] in B.OURS
            if rid in B.COVERED_BY_PACK:
                continue
            if not ours:
                if n in self.old_names or ":" not in result_id or not self.exists_1211_item(result_id):
                    continue
                if any(rid == x or (x.endswith("_") and rid.startswith(x)) for x in B.EXTRA_EXCLUDE):
                    continue
            r = self.recipe(raw)
            if r is None or self.equal_in_1211(r):
                continue
            self.write(B.RES / "data" / B.NS / "recipe" / f"{rid}.json", r)
            self.n_recipes += 1
            self.n_extra += 0 if ours else 1

    def tags(self):
        import tags   # imported here: it reads backport's constants
        tags.run(self)
