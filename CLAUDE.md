# haven-backports (`haven_backports`)

NeoForge 1.21.1 mod, both sides, for the Haven pack: content from newer Minecraft, backported. Read README.md first.

## Rule: Mojang is the source of truth

Every look and behaviour comes from Mojang: assets and data from the snapshot jar, block properties and classes from
its `Blocks` class (`javap -p -c` on the unobfuscated jar). Do not redraw, approximate or invent a recipe. If a block
has no Mojang way to obtain it on 1.21.1 (e.g. it only generates in a new biome), backport the source (the biome) or
leave the block out. Engine-wide changes 1.21.1 lacks for every block (26.x `isViewBlocking` on stairs and slabs) are
noted, not emulated per block.

## Pipeline

`uv run --with pillow python -I tools/backport.py --mods <pack mods folder>` downloads the pinned snapshot client jar (sha1 checked, cached in `.cache/`) and writes
`src/main/resources`: blockstates, block and item models, textures 1.21.1 lacks (a texture 1.21.1 has stays
vanilla; the generator reports any whose pixels differ in 26.x, so a real retexture is a visible decision; for the
phase 1 families none do: 26.x only re-saved those files), lang, loot tables (snapshot loot format converted key for key), recipes (ingredient shorthand
converted to `{"item": ...}`), tags (only those 1.21.1 has, or that a backported recipe uses), the Chipped and
Rechiseled tie-ins, and `META-INF/haven_consolidated/aliases.json` (merges). Never hand-edit generated files.
`tools/models.py` converts 26.x model JSON (`shade_direction_override: up` -> `shade: false`; other values stop the
run; `on_shelf` -> `haven_backports:on_shelf`) and writes the vanilla models 26.x gives an on_shelf transform (1.21.1's
file plus that one entry). A model equal to 1.21.1's after conversion stays vanilla, so pack resource packs that
restyle vanilla (Better Lanterns + `haven-fixes`) apply to ours too. `tools/sources.py` holds the jar download and
format-neutral compares; `tools/data.py` sounds, recipes and tags; `tools/woods.py` wood sets (signs remapped from
26.x's block-model art to 1.21.1's entity layout, proven on every vanilla wood each run; boats; fuel, compost and
strip data maps from 26.x's data); `tools/biomes.py` biomes (environment attributes -> effects, surface rules into
1.21.1's noise settings, c: tags, dry foliage table) with `tools/worldgen.py` (26.x feature formats -> 1.21.1; stops
on any type 1.21.1's data does not use and we have not ported) and `tools/noise_math.py` (a 26.x noise is taken only
when its octave factors equal 1.21.1's); `tools/trades.py` wandering trader offers; `tools/nbt.py` structure templates
(26.x palette format -> 1.21.1); `tools/cube.py` the Sulfur Cube (archetype registry data, its tags, damage type); `tools/creaking.py` the creaking; `tools/frostbite.py` the frostbite, ice ball, freezing and the Flurry disc;
`tools/biome_mods.py` NeoForge biome modifiers for what 26.x's vanilla biomes have of ours (spawns now).
Tags: where 26.x moved vanilla blocks out of a 1.21.1 tag into a new family tag (`FAMILY_TAGS`: dirt lost
moss_block to #moss_blocks), ours in the family join the 1.21.1 tag. Code tags (`CODE_TAGS`, read by our Java or
worldgen) and every snapshot-only tag they reach are written whole, vanilla members too. Java registers the blocks the
way Mojang's helpers do (`registerStair` = `ofFullCopy(base)`, `registerSlab` = `ofLegacyCopy(base)`, `registerWall` =
`ofLegacyCopy(base).forceSolidOn()`).

## Ported classes

`block/` holds Mojang's 26.x classes ported to 1.21.1 APIs (ShelfBlock, ShelfBlockEntity, SideChainPartBlock,
SelectableSlotContainer); each says in its Javadoc where 1.21.1 forced a different form. Decompile with
`.cache/tools/vineflower-1.11.1.jar` (`-only=<class path>`). Mixins (`haven_backports.mixins.json`) only carry
behaviour 1.21.1 hard-codes: keep them to a minimum.

## Worldgen

Biome placement: OverworldBiomeBuilderMixin sets Mojang's 26.x table cells (`Biomes26`); nothing else moves.
Feature order: 1.21.1 stops chunk generation on any order cycle between biomes' feature lists, and 26.x reordered
some vanilla features; `biomes.py` puts the vanilla features of each step in 1.21.1's order and runs 1.21.1's cycle
test over every biome before it writes. Ported worldgen types live in `worldgen/` (registered in `Worldgen`), named as Mojang names them. Sapling growth uses
`TreeGrower26` (26.x's weighted tree lists; 1.21.1's TreeGrower cannot express them). The Sulfur Cube (`cube/`) carries
26.x's movement physics in its own base class (`Physics26Mob`): new physics never go into a global mixin. 26.x rule tests take the block position (`height_match`); ores that use them run `OreFeature26` (26.x's ore
feature), the others stay 1.21.1's. The frostbite (`frostbite/`) and the creaking and its heart (`creaking/`) port 26.x's Creaking, its brain and
the heart's block entity without mixins.

## Merges

Haven Consolidated reads `META-INF/haven_consolidated/aliases.json` from every mod jar at common setup and aliases
each old id to the new one (items, blocks, chunk palettes, recipes; duplicate recipes are dropped). Merge a mod's
block only when it is the same block as Mojang's; keep blocks of a mod's own cross-stone style (Create's cut family).

## Test

Offline port test: `uv run python -I tools/porttest.py` runs Mojang's 26.x code from the snapshot jar (biome
parameter list, every ported tree for 50 seeds on a flat world, ported block shapes) into `build/porttest/26x.json`;
then `./gradlew test` (src/test, NeoForge's unit test environment, mixins applied) runs our ports on the same inputs
and must match. If the default `java` is newer than Gradle supports (Java 27: "Unsupported class file major version
71" when the build script recompiles), run Gradle with `JAVA_HOME` set to a JDK 21. Dev server: `run/eula.txt`,
`./gradlew runServer`; enable RCON in `run/server.properties` to drive it (`/locate biome haven_backports:...`).

The throwaway proof mod in `../proof` (steps: `scene`, `cmd`, `look`, `calcitecheck`, `shot`) in the Prism instance
"Haven IP Test": a grass platform in a plains biome (`fillbiome`, under 32768 blocks), one grid per family. Check
inventory icons too (a hotbar shot), not only placed blocks. A server test: `just smoke --serve` in the pack test copy
with this jar in mods/, then the proof mod's `join`, `ccmd`, `useon` steps from the client.

## Release

Push to `main`: CI publishes `latest` and `v<mod_version>-<sha7>`; the pack pins one with `just bump-mod
haven-backports`. Commits: author `Frosthaven <shane@thedragon.dev>`, no Co-Authored-By trailer.
