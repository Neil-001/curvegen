# Curve Generator (Fabric mod, Minecraft Java 1.21 to 1.21.11)

An in-game tool that turns ellipses, equation plots and Bézier curves into full blocks, slabs, stairs, trapdoors, fences, glass panes and walls. The player previews the result, then places it with a hologram (operator permission needed) or exports it as a Litematica schematic.

## Commands

- `./gradlew build` builds and tests all six jars. `./gradlew buildAndCollect` also copies them into `build/libs/`.
- `./gradlew :1.21.5:build`, `:1.21.5:test` or `:1.21.5:runClient` work on one version. Don't run `runClient` without a version: it would start six clients.

Needs JDK 21. Loom stays on 1.14.x, because newer Loom needs JDK 25 to run Gradle. The code uses Mojang's official mappings, so class and method names match the ones Minecraft uses (`Level`, `BlockState`, `GuiGraphics`), not Yarn's. Run build and test after every change.

## Versions (this is the `1.21.x/stable` branch)

This branch is for bug fixes only. New features go on `main`, which supports a single version.

[Stonecutter](https://stonecutter.kikugie.dev/) builds one jar per group of Minecraft versions that share the APIs the mod uses. Each jar is named after, and compiled against, the oldest version in its group, with the oldest Fabric API for it. The groups are in `settings.gradle.kts` and `stonecutter.properties.toml`:

| Node | Runs on | What changed at this point |
|---|---|---|
| `1.21` | 1.21, 1.21.1 | |
| `1.21.2` | 1.21.2 to 1.21.4 | `NativeImage` is ARGB, `blit` takes a render type |
| `1.21.5` | 1.21.5 | block models, `DynamicTexture` |
| `1.21.6` | 1.21.6 to 1.21.8 | GUI rendering (2D pose stack, strata, deferred tooltips), HUD registry |
| `1.21.9` | 1.21.9, 1.21.10 | input events, key categories, no Fabric world render events |
| `1.21.11` | 1.21.11 | permission sets, `Identifier`, render types, `CycleButton` builder |

- `src/` holds the 1.21.11 code. Code for other versions sits in `//? if <condition> {` comments next to it. Always commit with 1.21.11 active (`./gradlew "Reset active project"`).
- Put a version difference in `Compat` if more than one place needs it. Otherwise write the condition where the code is.
- Classes that only exist in some versions are written with their full name inside the condition, or imported inside a conditional import block, so the other versions still compile.
- `Identifier` is replaced by `ResourceLocation` before 1.21.11 (see `stonecutter.gradle.kts`).
- A jar must behave the same on every version in its group. A method the jar calls can be renamed inside a group even though the source compiles on both ends: `GuiGraphics.drawWordWrap` was, in 1.21.4, which is why `Compat.wordWrap` exists. After changing Minecraft-facing code, build the jar against the other versions of each group (add them to `versions(...)` temporarily) and check that the classes come out byte for byte the same.
- Only the `1.21.9` jar has a mixin, `LevelRendererMixin`, because Fabric API has no world render event there. `build.gradle.kts` leaves the mixin config out of the other jars.

## Layout

Loom's `splitEnvironmentSourceSets()` splits the code into `src/main` (common) and `src/client` (client-only).

- `dev/curvegen/core/` is pure Java with **no Minecraft imports**. Keep it that way, because the tests cover it.
  - `Pieces`: every piece state as a 16×16 silhouette. Holds indices, rectangles, masks, mirror maps (`MX`, `MY`), sturdy faces, colours and names.
  - `Target`: the ideal shape on a grid. Contains the ellipse, equation (marching squares plus bisection) and Bézier builders, and the carve regions.
  - `Solver`: picks a piece for each cell, including fence, pane and wall connections, symmetry and hollowing.
  - `Expr`: the Desmos-style equation parser. It compiles to lambdas, with no `eval`.
  - `ShapeSettings`: all user settings. The solver runs on a worker thread from a `copy()`.
  - `Layout`: the solved grid trimmed to non-empty cells, plus carve cells.
  - `PresetData`: preset capture and apply, default names, and the starting examples.
  - `LitematicBits`: Litematica's packed long array.
  - `Silhouette`: piece pixels and outlines, shared by the preview and the Count tab icons.
- `dev/curvegen/CurveGen.java` and `net/PlaceBlocksPayload.java` are the common entrypoint and the placement packet from client to server. The server checks operator level 2 through `CurveGen.canPlace`.
- `dev/curvegen/client/`:
  - `CurveGenClient`: keybinds (G opens the screen) and render hooks.
  - `Compat`: drawing, tooltip, texture and pixel calls that differ between Minecraft versions.
  - `mixin/LevelRendererMixin`: draws the hologram on 1.21.9 and 1.21.10.
  - `BlockChoices`: the block chosen for each piece family, candidate lists, and the mapping from piece to `BlockState` for upright and flat builds.
  - `ColorIndex`: average texture colours for the face you'll see, matched in CIELAB.
  - `Placement`: placement mode, hologram, Replace and Carve, undo, and the `/setblock` fallback.
  - `LitematicExporter`: writes the `.litematic` file.
  - `PresetStore`: reads and writes one JSON file per preset in `config/curvegen/presets/`, replacing each file atomically.
- `dev/curvegen/client/screen/`:
  - `CurveScreen`: the main UI.
  - `PreviewTexture`: renders a solved grid into a dynamic texture.
  - `BlockPickerScreen`, `ColorPickerScreen`, `PresetsScreen`, `NameDialogScreen`.

## How the solver works

1. **Target.** A builder marks each cell empty, full or mixed. For a mixed cell, it samples 16×16 pixels (Minecraft's own grid) and stores the pixel error of every piece state in `errTab`.
2. **First guess.** Each cell gets its lowest-error allowed piece. Fences, panes and walls start as type tokens (`FENCE`, `PANE`, `WALL`, `F_FENCE`, `F_PANE`, `F_WALL`). Their neighbours decide their exact state. The first guess assumes they connect wherever a neighbour will hold something.
3. **Refinement.** The solver re-picks each cell given its neighbours' current pieces, and repeats until nothing improves. A change can reshape the side neighbours (their connections). Upright, it can also reshape the row below (wall side heights). Flat, it can reshape the cells above and below. The affected sets in `Solver` must include all of these, or a refinement step can make the result worse.
4. **Symmetry.** The solver works on one quarter of an ellipse and mirrors it (`MX`, `MY`), so ellipses are exactly symmetric. Cells on a mirror axis only get self-symmetric pieces.
5. **Hollowing.** Thin ellipses lose full blocks with no exposed face. Hollowing never removes a block a connector attaches to. Upright, it also keeps any block that a wall below takes its height from.

**State indices.** Upright states are 0–55 (walls are 20–55, encoding left side ×3, right side ×3, covered and post). Flat-only states are 56–105. Grids are `byte[]`, so indices must stay below 128.

**Orientation.** Upright builds are drawn from the side. Flat builds are drawn from above, with the drawing's top pointing away from the player. From above, slabs, stairs and closed trapdoors look like full blocks, so flat builds don't use them. Colours come from a block's side face for upright builds and its top face for flat ones.

## Minecraft rules the code follows

`ConnectionRulesTest` covers these.

- **Connections.** Fences join only fences. Panes and walls join each other. All three attach to sturdy full faces: full blocks, a stair's tall side, and an open trapdoor's panel. They never attach to slabs, closed trapdoors, or blocks where `Block.isExceptionForConnection` is true (leaves, pumpkins, shulker boxes). `ShapeSettings.fullConnects` models that last case.
- **Wall sides (upright).** A side is tall when the block above covers that side's test region, taken from the bottom row of the block above.
- **Wall posts (upright), depth 1.** A wall has a post unless it's straight (both sides connected). A straight wall keeps its post only if the block above covers its centre and the sides aren't both tall.
- **Wall posts (upright), depth over 1.** The front and back walls connect on one side only, so they always have a post, and that's the post visible from the side.
- **Walls (flat).** A straight wall (exactly two opposite sides) has no post. Lower layers have tall sides, and the top layer has low sides.
- **Stairs.** `FACING` is the side of the tall back.
- **Open trapdoors.** The panel lies against the side *opposite* `FACING`.

## Conventions

- Grey out controls that don't apply, with a tooltip saying why. Don't hide them.
- Use `cycler(...)` or `toggle(...)` for anything that steps through options, so right-click steps backwards. Use `spin(...)` for number fields.
- Size buttons from their text (`tw(...)` and the fitting loops in `CurveScreen.init`). Nothing may overlap at 427 px wide (1280×720, GUI scale 3). New controls take space from existing ones rather than widening the UI.
- UI copy is plain and in sentence case, with no jargon.
- A visual fix must not change anything else. Prove it, for example with a pixel comparison across all pieces like `SilhouetteTest`.
- New core logic gets tests. Change the reference numbers in `SolverRegressionTest` only on purpose.

## Limits

- Client-to-server custom payloads must stay under 32 KiB, so placement sends 2,500 blocks per batch.
- The `/setblock` fallback sends 40 commands per tick.
- `NativeImage` colours are ABGR before 1.21.2 and ARGB from then on. `Compat.getPixel`, `setPixel` and `fill` always use ARGB.

## Releasing

Only `*/stable` branches release. `main` never does. The `Release` workflow (`.github/workflows/release.yml`) runs from `main` and publishes each branch to Modrinth, CurseForge and GitHub releases.

1. On this branch, open a PR that bumps `mod.version` in `stonecutter.properties.toml` and adds a `# <version>` section at the top of `CHANGELOG.md`. A suffix sets the release type: `1.1.0-beta.1` is a beta, `1.1.0-alpha.1` an alpha.
2. After it merges, run `gh workflow run release.yml -f dry_run=true` and read the run's summary. It lists the jars and the notes.
3. Run `gh workflow run release.yml` to publish.

The workflow tags each release `<version>+mc<line>`, such as `1.1.0+mc1.21.x`. It skips a branch whose tag exists, so the default `branches=all` only releases branches with a new version. It creates the tag last, after every site has the release.

If a site fails, re-run the failed jobs. A re-run skips versions already on Modrinth. CurseForge has no such check, so first look on CurseForge for the jar of each failed job, and re-run only if it isn't there. For the same reason, don't start a fresh run with CurseForge in `targets` after a partial release, because CurseForge has no duplicate check. To publish to fewer sites, pass `-f targets=curseforge,github`.

**Release notes.** The `CHANGELOG.md` section goes to all three sites as written. Start with a short paragraph that says what the release changes for players. Then list the merged PRs under `## Features` and `## Fixes`, one bullet each, ending in the PR number. Rewrite PR titles so a player understands them, and leave out changes players can't see (CI, refactors, docs).

```
# 1.1.0

Curve Generator 1.1.0 adds spirals and fixes two wall bugs.

## Features
- Spirals have their own tab (#21)

## Fixes
- Walls under a slab no longer show a post (#19)
```

The Modrinth and CurseForge project IDs are the repository variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`. The tokens are the secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`.
