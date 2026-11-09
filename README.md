# Haven Backports

Content from newer Minecraft versions, backported to 1.21.1 (NeoForge) for the Haven pack. Required on the server and
on every client.

Everything comes from Mojang's own files: textures, models, blockstates, recipes, loot tables, tags and block
properties are read from the newest snapshot's client jar (`tools/backport.py`), and block behaviour follows Mojang's
code in that jar (it ships unobfuscated). Nothing is redrawn or invented. Vanilla textures that newer versions
retextured (wool, concrete, copper, calcite...) keep their 1.21.1 look, so new blocks match the blocks around them. Where another mod in the pack already adds the
same block in its own art, it is merged into the backported one (Haven Consolidated carries out the merge), so there is
one block per concept and it looks the way Mojang made it.

## Content (26.4 snapshot 7)

| Set | Blocks and items |
|---|---|
| Calcite | Calcite Pillar, Stairs, Slab, Wall; Polished Calcite with Stairs, Slab, Wall; Polished Calcite Bricks with Stairs, Slab, Wall |
| Snow and ice | Snow Bricks with Stairs, Slab, Wall; Packed Ice Bricks with Stairs, Slab, Wall; Chiseled Packed Ice (new packed ice sounds) |
| Wool and concrete | Stairs and Slab for all 16 colors of each; wool ones re-dye with any dye and burn like wool |
| Copper | Copper Bars, Chain and Lantern in every oxidation stage, waxed or not (honeycomb, axe, rain); Copper Torch; Copper Nugget |
| Recipes | Name Tag (paper + metal nugget), Saddle; stonecutter: cobblestone set from stone, every deepslate variant from deepslate |

Merged into the backported ones (Haven Consolidated): New Slab Variants' calcite, wool and concrete slabs; TFMG's
concrete slabs and stairs; Chipped's calcite bricks, calcite pillar and packed ice bricks; Create's calcite pillar;
Create's and Create Deco's copper bars and Macaw's Lights' copper chain (into the waxed ones: they never oxidized);
Create's copper nugget (its nugget and ingot recipes stay, making ours).

Not taken: recipes 1.21.1 cannot run or already has (bundle, which 1.21.1 keeps behind a feature flag; the per-flower
suspicious stews 1.21.1's special recipe already makes; dyes and stews from flowers 1.21.1 lacks).

## Download

The Haven pack delivers it (`mods/haven-backports.pw.toml`).
