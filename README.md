# Haven Backports

Content from newer Minecraft versions, backported to 1.21.1 (NeoForge) for the Haven pack. Required on the server and
on every client.

Everything comes from Mojang's own files: textures, models, blockstates, recipes, loot tables, tags and block
properties are read from the newest snapshot's client jar (`tools/backport.py`), and block behaviour follows Mojang's
code in that jar (it ships unobfuscated). Nothing is redrawn or invented. Where another mod in the pack already adds the
same block in its own art, it is merged into the backported one (Haven Consolidated carries out the merge), so there is
one block per concept and it looks the way Mojang made it.

## Content

| Set | From | Blocks |
|---|---|---|
| Calcite | 26.4 snapshot 7 | Calcite Stairs, Slab, Wall, Pillar; Polished Calcite with Stairs, Slab, Wall; Polished Calcite Bricks with Stairs, Slab, Wall. Calcite takes the snapshot's retexture. |

Merged into the backported blocks: New Slab Variants' calcite slab, Chipped's calcite bricks and calcite pillar,
Create's calcite pillar. Our polished calcite, polished calcite bricks and pillar join Chipped's calcite group and
Rechiseled's calcite chiseling group; every recipe is also a stonecutter recipe (and so a Mechanical Saw recipe).

## Download

The Haven pack delivers it (`mods/haven-backports.pw.toml`).
