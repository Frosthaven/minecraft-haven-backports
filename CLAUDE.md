# haven-backports (`haven_backports`)

NeoForge 1.21.1 mod, both sides, for the Haven pack: content from newer Minecraft, backported. Read README.md first.

## Rule: Mojang is the source of truth

Every look and behaviour comes from Mojang: assets and data from the snapshot jar, block properties and classes from
its `Blocks` class (`javap -p -c` on the unobfuscated jar). Do not redraw, approximate or invent a recipe. If a block
has no Mojang way to obtain it on 1.21.1 (e.g. it only generates in a new biome), backport the source (the biome) or
leave the block out. Port every way Mojang gives an item, also the ones in code (mob spawn
equipment, structure pieces), not only data (recipes, loot, trades, tags). Engine-wide changes 1.21.1 lacks for every block (26.x `isViewBlocking` on stairs and slabs) are
noted, not emulated per block.

## Pipeline

`uv run --with pillow python -I tools/backport.py --mods <pack mods folder>` downloads the pinned snapshot client jar (sha1 checked, cached in `.cache/`) and writes
`src/main/resources`: blockstates, block and item models, textures 1.21.1 lacks (a texture 1.21.1 has stays
vanilla; the generator reports any whose pixels differ in 26.x, so a real retexture is a visible decision; for the
phase 1 families none do: 26.x only re-saved those files), lang, loot tables (snapshot loot format converted key for key), recipes (ingredient shorthand
converted to `{"item": ...}`), tags (only those 1.21.1 has, or that a backported recipe uses), the Chipped and
Rechiseled tie-ins, and `META-INF/haven_consolidated/aliases.json` (merges). Never hand-edit generated files.
`tools/gear.py` (copper gear, the whole-table loot rule) and `tools/spear.py` (spears, Lunge, the six retargeted
enchantments) follow the same rules; Lunge's JSON is converted term for term, each 26.x key 1.21.1 lacks mapped to a
registry entry of ours (`spear/Lunge`). `tools/models.py` converts 26.x model JSON (`shade_direction_override: up` -> `shade: false`; other values stop the
run; `on_shelf` -> `haven_backports:on_shelf`) and writes the vanilla models 26.x gives an on_shelf transform (1.21.1's
file plus that one entry). A model equal to 1.21.1's after conversion stays vanilla, so pack resource packs that
restyle vanilla (Better Lanterns + `haven-fixes`) apply to ours too. `tools/sources.py` holds the jar download and
format-neutral compares. `tools/mobs.py` adds the mobs' blocks, items, sounds, tags, entity textures, names and loot
to the same paths; `tools/tags.py` writes tags (a reference to a 26.x-only tag is inlined in a tag our Java reads and
left out elsewhere when it holds nothing of ours); `tools/animations.py` converts 26.x keyframe animation classes to
NeoForge JSON and `tools/fuels.py` writes fuel and compost data maps from 26.x's Items class, both reading code
through `sources.decompile` (vineflower, cached in `.cache/decompiled`). Java registers the blocks the way Mojang's helpers do (`registerStair` = `ofFullCopy(base)`, `registerSlab` =
`ofLegacyCopy(base)`, `registerWall` = `ofLegacyCopy(base).forceSolidOn()`).

## Ported classes

`block/` holds Mojang's 26.x classes ported to 1.21.1 APIs (ShelfBlock, ShelfBlockEntity, SideChainPartBlock,
SelectableSlotContainer), `spear/` the spear's (KineticWeapon, PiercingWeapon, AttackRange, stab attacks, SpearUseGoal,
the piglin spear behaviors) and `client/spear/` its animations (SpearAnimations, Ease); each says in its Javadoc where
1.21.1 forced a different form. 26.x item components 1.21.1 lacks become fields of the item class (SpearItem). Decompile with
`.cache/tools/vineflower-1.11.1.jar` (`-only=<class path>`). Mixins (`haven_backports.mixins.json`) only carry
behaviour 1.21.1 hard-codes: keep them to a minimum.

Mobs (`ghast/`, `entity/`, `client/ghast/`) are Mojang's 26.x classes the same way; 26.x engine helpers they need
(`entity/Traversal`, `entity/Leash26`, `entity/Navigations`) are copied once and shared. 1.21.1 lacks
TEMPT_RANGE and CAMERA_DISTANCE attributes: the values are constants. A test run of the dev server (`./gradlew
runServer`) loads every mixin target at start only when a temporary audit hook is added; do not commit one.

## Merges

Haven Consolidated reads `META-INF/haven_consolidated/aliases.json` from every mod jar at common setup and aliases
each old id to the new one (items, blocks, chunk palettes, recipes; duplicate recipes are dropped). Merge a mod's
block only when it is the same block as Mojang's; keep blocks of a mod's own cross-stone style (Create's cut family).

## Test

The throwaway proof mod in `../proof` (steps: `scene`, `cmd`, `look`, `calcitecheck`, `shot`) in the Prism instance
"Haven IP Test": a grass platform in a plains biome (`fillbiome`, under 32768 blocks), one grid per family. Check
inventory icons too (a hotbar shot), not only placed blocks. Mob screenshots: the proof mod's `mobscene <ghast|golem|decor|variants> <x> <y> <z>` builds a 40 x 40 plains scene
(noon, clear, frozen) with the subjects still (`mobscene2 <desert|nautilus> <x> <y> <z>` the same for the desert mobs and
nautiluses, faces to the south: shoot from the south; a nautilus's face is at its back, so it is summoned at yaw 180); then `look near <eye x y z> <target x y z>` and `shot`. A server test:
`just smoke --serve` in the pack test copy
with this jar in mods/, then the proof mod's `join`, `ccmd`, `useon` steps from the client.

## Release

Push to `main`: CI publishes `latest` and `v<mod_version>-<sha7>`; the pack pins one with `just bump-mod
haven-backports`. Commits: author `Frosthaven <shane@thedragon.dev>`, no Co-Authored-By trailer.

## Player page

Players read about this mod on the Haven Codex, tab `#tab-backports` (https://claude.ai/artifact/QG2xPPxSAM2BQiovWVoGiF#tab-backports).
Update that tab when a change alters what players see; publish to that URL in place (never a copy) when the
maintainer asks. The pack repo's CLAUDE.md, "Haven's own mods and pages", lists what to check on a pack change.
