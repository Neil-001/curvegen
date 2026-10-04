# Curve Generator

A Fabric and NeoForge mod for Minecraft Java 26.2. It turns ellipses, equations and Bézier curves into blocks, with a preview, operator-only placement and Litematica export.

## Commands

Needs JDK 25. Versions are in `gradle.properties`. Run build and test after every change.

- `./gradlew build` builds both jars in `fabric/build/libs/` and `neoforge/build/libs/` and runs the tests.
- `./gradlew :fabric:test` runs the pure-Java core's JUnit tests. Only Fabric hosts them, since they're loader-independent.
- `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient` starts a dev client. Use `runServer` for a server.

Both jars target 26.2 only. Minecraft is unobfuscated, so use its own names without mappings. Builds use the minimum supported Fabric API and NeoForge versions; CI also compiles against newer ones.

## Layout and boundaries

Both loaders compile the shared `src/main` and `src/client` code with their own entrypoints and metadata. Fabric also compiles `src/test`. Fabric splits common and client source sets with Loom's `splitEnvironmentSourceSets()`; NeoForge combines them in one jar. Common code must never touch client classes.

- `dev/curvegen/core/` must have no Minecraft imports. `Pieces` defines the 16×16 states, masks, mirrors and sturdy faces; `Target` builds the shapes and carve regions; `Solver` selects pieces. `Expr` compiles Desmos-style equations to lambdas without `eval`. Run the solver on a worker thread with a `ShapeSettings.copy()`. `Silhouette` shares piece pixels and outlines between the preview and Count icons.
- Only `fabric/src/` and `neoforge/src/` may import their loader's classes. Shared client code uses `ClientPlatform`; changes to that contract must go into both loaders.
- `CurveGen.java` handles server placement through `net/PlaceBlocksPayload.java` and checks `Permissions.COMMANDS_GAMEMASTER`.
- In `dev/curvegen/client/`, `BlockChoices` maps pieces to block states for each orientation; `ColorIndex` matches face texture colours in CIELAB; `Placement` handles the hologram, Replace, Carve, undo and `/setblock`. `PresetStore` atomically replaces one JSON file per preset in `config/curvegen/presets/`.
- `client/screen/CurveScreen` is the main UI; `PreviewTexture` draws the solved grid into a dynamic texture.

## Solver

1. `Target` marks cells empty, full or mixed. Mixed cells sample 16×16 pixels and store each state's pixel error in `errTab`.
2. Each cell starts with its lowest-error allowed piece. Fence, pane and wall tokens get their states from neighbours, initially assuming every supporting neighbour connects.
3. Refinement re-picks cells until nothing improves. `Solver`'s affected sets must include side neighbours, the row below for upright wall heights, and rows above and below for flat connections. Otherwise a step can worsen the result.
4. Ellipses solve one quarter and mirror it with `MX` and `MY`. Axis cells require self-symmetric pieces.
5. Upright connectors whose silhouette is a full block, such as a wall with two tall sides mirrored from one with two low sides, become full blocks unless that changes a neighbour.
6. Hollowing removes unexposed full blocks from thin ellipses. A block is unexposed when every neighbour's silhouette fills the shared edge, so `Pieces.EDGE` decides this rather than `STURDY`. Keep connector supports and, upright, blocks that determine a wall's height below.

Upright states are 0 to 55, with walls at 20 to 55 encoding three heights per side, covered and post. Flat-only states are 56 to 105. Grids use `byte[]`, so indices must stay below 128.

Upright builds show the side face; flat builds show the top, with the drawing's top pointing away from the player. Match colours to that face. Flat builds exclude slabs, stairs and closed trapdoors because they look like full blocks from above.

## Minecraft rules

`ConnectionRulesTest` covers these:

- Fences join only fences; panes and walls join each other. All attach to sturdy full faces, including a stair's tall side and an open trapdoor's panel. They don't attach to slabs, closed trapdoors or `Block.isExceptionForConnection` blocks such as leaves, pumpkins and shulker boxes. `ShapeSettings.fullConnects` models that exception.
- An upright wall side is tall when the bottom row of the block above covers its test region.
- At depth 1, an upright wall has a post unless both sides connect. A straight wall keeps its post only if the block above covers its centre and the sides aren't both tall.
- At depth over 1, the front and back walls connect on only one side, so the visible upright wall always has a post.
- Flat walls have no post with exactly two opposite connections. Lower layers have tall sides; the top layer has low sides.
- A stair's `FACING` points to its tall back. An open trapdoor's panel is opposite `FACING`.

## Conventions

- Grey out inapplicable controls with a tooltip explaining why. Don't hide them.
- Use `cycler(...)` or `toggle(...)` for options so right-click steps backwards, and `spin(...)` for numbers.
- Size buttons with `tw(...)` and the fitting loops in `CurveScreen.init`. Nothing may overlap at 427 px wide, as in 1280×720 at GUI scale 3. New controls must take space from existing ones.
- Use plain, sentence case UI copy without jargon.
- Visual fixes must preserve other behaviour. Prove it, for example by comparing pixels across all pieces as in `SilhouetteTest`.
- Test new core logic. Change `SolverRegressionTest` reference numbers only on purpose.

## Placement and rendering

- Keep custom payloads under 32 KiB. Placement sends 2,500 blocks per batch; the `/setblock` fallback sends 40 commands per tick.
- Undo restores blocks without updates, then the server updates every restored block's neighbours after the last batch. This prevents blocks breaking before their supports return. The fallback uses `strict` but can't update neighbours afterward, so surrounding blocks keep their state.
- Placement records nearby blocks that need support. Undo restores missing ones even if their support has since gone; it doesn't remove dropped items.
- Each loader passes the frame's `SubmitNodeCollector` to `Placement`. Submit hologram boxes after terrain to draw over water and glass, except boxes across a water surface from the camera, which go through normal submission to show through the water. `filledBox` must omit faces turned away from the camera because 26.2's filled-box render type doesn't cull them.
- `NativeImage.getPixel` and `setPixel` take ARGB.

## Releasing

Only `*/stable` branches release. Run `.github/workflows/release.yml` from `main` to publish to Modrinth, CurseForge and GitHub. It builds two-loader branches with Java 25 and older Fabric branches with Java 21, and reads loader and Minecraft ranges from jar metadata.

1. On the stable branch, open a PR bumping `mod_version` in `gradle.properties` and adding `# <version>` at the top of `CHANGELOG.md`. Stonecutter branches use `mod.version` in `stonecutter.properties.toml`. Use `-beta.1` or `-alpha.1` suffixes for prereleases.
2. After merge, run `gh workflow run release.yml -f dry_run=true` and check the summary's jars and notes.
3. Run `gh workflow run release.yml` to publish.

Tags use `<version>+mc<line>`, such as `1.1.0+mc1.21.x`. The default `branches=all` skips existing tags. The workflow creates the tag last, after publishing. Two-loader versions have distinct identifiers such as `1.0.0+mc26.1-fabric` and `1.0.0+mc26.1-neoforge`; GitHub holds both jars in one release.

After a partial failure, re-run failed jobs. Modrinth skips existing versions; CurseForge doesn't. Check CurseForge for each failed job's jar before retrying, and retry only if it's absent. Don't start a fresh run with CurseForge in `targets` after a partial release. To limit sites, pass `-f targets=curseforge,github`, for example.

The `CHANGELOG.md` section goes to all three sites as written. Start with a short paragraph about what changes for players. List merged PRs under `## Features` and `## Fixes`, one bullet per PR ending in `(#21)`, for example. Rewrite titles for players and omit CI, refactors and docs.

Project IDs are repository variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`; tokens are secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`.
