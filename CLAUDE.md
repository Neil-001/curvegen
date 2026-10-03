# Curve Generator (Fabric and NeoForge mod, Minecraft Java 26.2)

An in-game tool that turns ellipses, equation plots and Bézier curves into full blocks, slabs, stairs, trapdoors, fences, glass panes and walls. The player previews the result, then places it with a hologram (operator permission needed) or exports it as a Litematica schematic.

## Commands

- `./gradlew build` builds both jars, into `fabric/build/libs/` and `neoforge/build/libs/`, and runs the tests.
- `./gradlew :fabric:runClient` and `./gradlew :neoforge:runClient` start a dev client with the mod. `runServer` works the same way.
- `./gradlew :fabric:test` runs the JUnit tests for the pure-Java core. They live in the Fabric project only, because they don't depend on the loader.

Needs JDK 25. Versions are in `gradle.properties`. Minecraft isn't obfuscated from 26.1 on, so the build has no mappings and the code uses Minecraft's own names (`Level`, `BlockState`, `GuiGraphicsExtractor`). Each loader's jar runs on 26.2 only. The Fabric jar is compiled against the first Fabric API built after 26.2 was released, and the NeoForge jar against the first stable NeoForge for 26.2. CI also compiles each against a newer one. The last commit that builds for 26.1 to 26.1.2 is `49acdf6`. Run build and test after every change.

## Layout

The shared code is in the root `src/` folder: `src/main` (common), `src/client` (client-only) and `src/test`. The `fabric/` and `neoforge/` projects each compile it together with their own entrypoints and metadata. Fabric keeps the two source sets apart with Loom's `splitEnvironmentSourceSets()`. NeoForge puts both in one jar, so common code must still never touch a client class.

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
- `dev/curvegen/CurveGen.java` and `net/PlaceBlocksPayload.java` are the server-side placement handler and the placement packet from client to server. The server checks the gamemaster permission level (`Permissions.COMMANDS_GAMEMASTER`).
- `fabric/src/` and `neoforge/src/` hold each loader's entrypoints (`dev/curvegen/fabric`, `dev/curvegen/client/fabric`, `dev/curvegen/neoforge`, `dev/curvegen/client/neoforge`) and its metadata file. They are the only code that may use that loader's classes. Everything else reaches the loader through `ClientPlatform`. A change to what the mod needs from the loader goes into both.
- `dev/curvegen/client/`:
  - `CurveGenClient`: the keys (G opens the screen) and what they do each tick.
  - `ClientPlatform`: what the client needs from the mod loader: sending the placement packet, and the config and game folders.
  - `BlockChoices`: the block chosen for each piece family, candidate lists, and the mapping from piece to `BlockState` for upright and flat builds.
  - `ColorIndex`: average texture colours for the face you'll see, matched in CIELAB.
  - `Placement`: placement mode, hologram, Replace and Carve, undo, and the `/setblock` fallback. The hologram is submitted to the frame's `SubmitNodeCollector`, which each loader hands over in its own event, and the game draws it later in the frame. The boxes go into the after-terrain phase so they draw over water and glass, and `filledBox` leaves out the faces turned away from the camera, because 26.2's filled-box render type doesn't cull them.
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
- `NativeImage.getPixel` and `setPixel` take ARGB.

## Releasing

Only `*/stable` branches release. `main` never does. The release workflow still expects the single-loader layout of `1.21.x/stable`: jars in `build/libs/` with a `fabric.mod.json`, published as Fabric. It needs updating before a 26.x stable branch with two loaders can release. The `Release` workflow (`.github/workflows/release.yml`) runs from `main` and publishes each branch to Modrinth, CurseForge and GitHub releases.

1. On the stable branch, open a PR that bumps `mod.version` in `stonecutter.properties.toml` and adds a `# <version>` section at the top of `CHANGELOG.md`. A suffix sets the release type: `1.1.0-beta.1` is a beta, `1.1.0-alpha.1` an alpha.
2. After it merges, run `gh workflow run release.yml -f dry_run=true` and read the run's summary. It lists the jars and the notes.
3. Run `gh workflow run release.yml` to publish.

The workflow tags each release `<version>+mc<line>`, such as `1.1.0+mc1.21.x`. It skips a branch whose tag exists, so the default `branches=all` only releases branches with a new version. It creates the tag last, after every site has the release.

If a site fails, re-run the failed jobs. A re-run skips versions already on Modrinth. CurseForge has no such check, so first look on CurseForge for the jar of each failed job, and re-run only if it isn't there. For the same reason, don't start a fresh run with CurseForge in `targets` after a partial release. To publish to fewer sites, pass `-f targets=curseforge,github`.

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
