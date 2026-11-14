# Curve Generator

A Fabric and NeoForge mod for Minecraft Java 26.3. It turns 2D shapes (ellipses, equations and Bézier curves) and 3D shapes (ellipsoids, toruses, 3D equations, 3D Bézier curves and Bézier surfaces) into blocks, optionally painted from patterns, mixes or gradients, with a preview, a hologram the player edits in the world, operator-only placement and Litematica export.

## Commands

Needs JDK 25. Versions are in `gradle.properties`. Run `./gradlew build` after every change.

- `./gradlew build` builds both jars in `fabric/build/libs/` and `neoforge/build/libs/` and runs the tests. It must stay free of warnings. The IDE's own, such as unused imports, count too, so name a lambda or catch parameter that isn't used `_`.
- `./gradlew :fabric:test` runs the tests, which only Fabric hosts.
- `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient` starts a dev client. Use `runServer` for a server.

Both jars target 26.3 only. Minecraft is unobfuscated, so use its own names without mappings.

## Layout and boundaries

Both loaders compile the shared `src/main` and `src/client` code with their own entrypoints and metadata. Fabric splits common and client source sets and also compiles `src/test`. NeoForge combines them in one jar. Common code must never touch client classes.

Most classes say what they do in a comment at the top, and their fields and methods say the rest. Read those before changing a class, and keep them true. This file has only what no one class says.

- `dev/curvegen/core/`, with `core/edit/` and `core/paint/`, must have no Minecraft imports. `Pieces`, `Target` and `Solver` are the 2D solver. `Pieces3`, `Shape3`, `Shapes3`, `Bezier3` and `Solver3` are the 3D one. `core/edit/` has the editor's and menus' maths, and `core/paint/` painting's.
- Only `fabric/src/` and `neoforge/src/` may import their loader's classes. Shared client code goes through `ClientPlatform`. Each loader hooks the shared code into its own events, `Editor.render` among them, so a change to `ClientPlatform` or a new hook goes into both. NeoForge lists its one mixin in `curvegen.neoforge.mixins.json`.
- `client/edit/` is the in-world editor. `client/screen/` has the screens: `CurveScreen` is the main UI on G and `RadialScreen` the radial menu on V. `SettingsScreen` opens from the main UI's cogwheel, from Mod Menu on Fabric and from the mod list's Config button on NeoForge.
- Mod Menu is optional. Only `ModMenuIntegration`, which Mod Menu itself loads, may refer to it.
- Chroma Studio is optional too. Only `client/chroma/ChromaBridge` may refer to it, and `Chroma.init` loads that class only once the loader reports the mod and its API says version 2 (Chroma Studio 1.2) or later. Both loaders compile against `chromastudio_version` from its GitHub releases without bundling it, and `-PwithChroma` adds it to a dev client.
- Read `listing/README.md` before changing anything in `listing/`. After editing `listing/description.md`, run `listing/art/curseforge.py`, or the build fails. Run `listing/art/render.py` after a change to either solver or to the 3D preview's drawing, and expect only the pictures you meant to change to differ. The gallery is screenshots from the game, which it doesn't draw.

## Solvers

The 2D solver's results must not change when the 3D one does. The two share nothing but `Pieces.Family` and the settings' piece toggles and `fullConnects`. A pattern's holes in a 2D shape are the one exception: `Settle2D` lays the shape out as 3D states and settles it with `Solver3.settle`, after the solve. Run `GameRules2Test` after touching `Settle2D`, the 2D block states or `Solver3`'s rules. Without holes, settling must give exactly the states placed unsettled.

In both, a refinement pick must be judged on every cell whose state it can change. `Solver`'s affected sets and `Solver3`'s `Run.gather` list them. Leave a cell out and a pick can raise the total error, and then refinement can cycle.

2D:

- Ellipses solve one quarter and mirror it with `MX` and `MY`. Axis cells need pieces that are their own mirror image.
- The hollow penalty for a connector that looks like a full block must stay a cost, not a filter, or refinement can cycle.
- Grids are `byte[]`, so state indices must stay below 128.

3D:

- Run `GameRules3Test` after touching `Pieces3` or `Solver3`'s rules.
- To empty blocks after a solve and let the pieces beside them settle, a shape cuts them with `Shape3.cuts` and `cut`, and anything else calls `Solver3.settle` on the result. Neither solves again.
- A stair becomes an outer corner when the stair in front of it, on the same half, faces sideways, and an inner corner when the one behind does, unless the cell beside it holds a stair facing the same way.
- A wall side is tall when the bottom of the collision shape above covers that side's strip, 2 pixels wide from the edge to 1 past the centre.
- A wall has a post if the wall above has one, if it has no sides, or if a side lacks its opposite. Otherwise it has none when two opposite sides are both tall, and else has one only if the block above covers its centre.
- Every stage asks the cancel supplier at least once a level, so a 256-block solve stops within a few tens of milliseconds. Keep that up in a new stage, because the next solve can't start until the dropped one has returned.
- A hollow sphere takes about 20 ms to solve at 64 blocks across and 100 ms at 128. At 256 its preview is about 2 million faces, 80 ms to list and 17 ms to draw. `Shapes3Timing` and `Preview3Timing` print the rest.

### Adding a 3D shape

Implement `Shape3`, whose comments describe each method, and add it to `Shape3.of`. Beyond those comments:

- `field` is called from several threads, so it must not share scratch state. The solver drops any voxel with a NaN among the eight samples around it.
- `Solver3Test.skippedCellsReallyAreUniform` and its namesake in `Shapes3Test` show how to test a field that may be steeper than the default `uniform` allows.
- An implicit surface f = 0 with a thickness is a band of f over its gradient's length. A filled side is `lo` or `hi` at infinity.
- The editor draws `wireframe()` every frame while a solve runs. Work it out once and keep it if it takes any time.

### The 3D shapes

Control points and equation ranges are in the shape's own box, in blocks from its lowest corner, before `pad()`. Each shape has its own preset kind in `PresetData`, and the file format is still version 1. With `q3Lock`, `Shape3.of` works out an equation's depth and height and leaves the settings alone, so read the size from the shape.

Removing one curve point takes it out of the list. The G menu's Remove uses `Bezier3.reduce` instead, which lowers the degree and keeps the ends.

Known limits the comments don't give:

- An equation's Surface mode never samples point by point, because that would take seconds. A wall loses up to a quarter of a block where it ends at a jump off the block grid, an asymptote or the edge of the equation's domain. A wall much thicker than the surface's bends are wide is only roughly that thick.
- Off the block grid, a surface's edge can land up to an eighth of a block out.
- A look ray that skims a surface's hump within a few thousandths of a block of its top can get the far crossing instead of the near one.

## Minecraft rules

`ConnectionRulesTest` covers the connection rules, and `Pieces`, `Solver` and `BlockChoices` state most of them where they apply them. The ones they leave out:

- Fences, panes and walls attach to sturdy full faces, including a stair's tall side, an open trapdoor's panel and a shelf's panel. They don't attach to slabs or closed trapdoors.
- At depth 1, an upright wall has a post unless both sides connect. A straight wall keeps its post only if the block above covers its centre and the sides aren't both tall, or the wall above has a post. `Solver` adds that last one after refinement, so a pick still changes nothing more than a row below. At any depth, `Solver.Result.placedWallPost` gives the post each layer places.
- A flat wall has no post when every side it joins has its opposite: straight or a crossing.
- A shelf's lips are 2 pixels deep and 4 tall. Only the panel's back is a sturdy face.
- `NativeImage.getPixel` and `setPixel` take ARGB.

## In-world editor

`Editor` owns the one hologram and knows nothing about any one shape. `EditShape` is all a shape supplies, with `Shape2D` for the 2D shapes and `Shape3D` for the 3D ones.

- Anything outside the editor that changes a hologram's settings calls `Editor.edit(group, change)`.
- Picking a handle ignores terrain.
- Handles show through terrain in the game's see-through text render type, because making an untextured type without a depth test needs an access widener.
- A 3D shape's box leaves out `Shape3.pad()`, so its blocks and wireframe are offset by that much. The G menu's picture is in the same blocks, so it doesn't jump when a thickness changes.
- With no hologram, Export writes the shape the G menu shows, which is never turned.

### G menu

- Opened mid-edit, `CurveScreen` shows the hologram's shape, and `Editor` takes up the changes when it closes.
- Add a 3D tab's control as the others are, with `bind`, and set `dirty` when it changes the shape.

### Radial menu

`RadialScreen` is a screen, so the game frees the cursor and both loaders get it without hooks of their own. Add a top-level wedge by adding its id and name to `Radial` and its action to `RadialScreen.top`.

## Painting

Painting chooses each block of a solved shape from the paint's source: a pattern, a mix or a gradient. The maths is in `core/paint/`, with `Paint` holding the settings and `Ladder` choosing each block. `PaintBlocks` gives it the game's blocks as a `Catalog` and turns its answers back into block states, and `PaintScreen` and the choosers beside it in `client/screen/` set it up.

- Paint never changes a solve. It runs after it, through `EditShape.paint`, and a paint change repaints the last solve on the worker thread without solving again. Each painter's `paintingNeverChangesThePieces` test holds every shape to that.
- Holes are the one exception. They take whole blocks out after the solve and settle the blocks beside them with `Solver3.settle` or `Settle2D`, and they place and clear nothing.
- `fullConnects` is a solver input, so it follows what most of the source's full blocks do (`PaintBlocks.fullConnects`). A paint change that flips it solves again. A full block they would join the other way gives way to the Blocks tab's, or to the closest one that joins as the solve assumed.
- The catalog is taken on the client thread and read on worker threads, so its matcher must be safe off the client thread. With Chroma Studio it is Chroma Studio's snapshot matcher, and otherwise `Catalog.lab`.
- `Paint2DTiming` and `Paint3Timing` set how fast a repaint must be to follow the mouse wheel.

## Conventions

- Grey out inapplicable controls with a tooltip explaining why. Don't hide them.
- Make options, numbers and mouse wheel steps through `ControlScreen`'s `cycler`, `toggle`, `spin`, `wheel` and `wheelNeedsFocus`.
- Size buttons with `tw(...)` and the fitting loops in `CurveScreen.init`. Nothing may overlap at 427 px wide, as in 1280×720 at GUI scale 3. New controls must take space from existing ones.
- Use plain, sentence case UI copy without jargon.
- Visual fixes must preserve other behaviour. Prove it, for example by comparing pixels across all pieces as in `SilhouetteTest`.
- Test new core logic. Change a test's reference numbers only on purpose.

## Placement

`Placement`, `CurveGen`, `LastPlacement` and `CommandReplies` say how placing, undo, redo and the `/setblock` fallback work. Beyond their comments:

- Undo restores a nearby block that broke even if its support has since gone. It doesn't remove dropped items.
- Hiding the fallback's replies never touches the server's `sendCommandFeedback`.

## Branch workflow

`main` is the development branch. Open feature, fix and documentation PRs against `main`, and don't duplicate them against `26.3/stable`. That branch receives one release PR with all changes ready to publish for Minecraft 26.3.

Prepare the version and changelog on `main`, then cut `release/26.3-<version>` from the tested commit and open its PR against `26.3/stable`. Keep that release branch at the chosen version while development continues on `main`. Merge the release PR with a merge commit, never squash or rebase it, because later promotions need the original commits in the stable branch's ancestry. Feature PRs into `main` may still be squashed.

If the release PR conflicts with stable, merge `origin/26.3/stable` into the release branch, resolve the conflicts and rerun the checks. Keep the intended release contents, including its version and changelog. Bring any release fixes back to `main` before the next promotion. A hotfix that must ship before the next promotion can target stable, but bring that fix back to `main` too.

Each supported Minecraft line has its own `<line>/stable` branch: `1.21.1/stable` (for 1.21 and 1.21.1), `1.21.11/stable`, `26.2/stable` and `26.3/stable`. An older line gets a tested `main` release ported down as one commit on top of that release. Open the backport PR against that line's stable branch and merge it with a merge commit. Once `main` targets a newer Minecraft version, maintain `26.3/stable` the same way.

`1.21.x/stable` is the retired Stonecutter branch, Fabric only, that released 1.0.0. It stays as history.

## Releasing

Only `*/stable` branches release. Run `.github/workflows/release.yml` from `main` to publish to Modrinth, CurseForge and GitHub. Its comments and those in `.github/scripts/` say how it works.

Those files name no mod, and Chroma Studio's repository has the same ones, so make a change to them in both. Chroma Studio sets the repository variable `RELEASE_MAIN` to `true` and releases from `main`. This repository must not set it.

1. For the current Minecraft version, bump `mod_version` in `gradle.properties` and add `# <version>` at the top of `CHANGELOG.md` on `main`, then promote through the release PR above. For an older line, prepare those changes in a PR against its branch. Use `-beta.1` or `-alpha.1` suffixes for prereleases.
2. After the release PR merges and stable's checks pass, run `gh workflow run release.yml --ref main -f branches=26.3/stable -f dry_run=true` and check the summary's jars and notes.
3. Run it again without `dry_run` to publish.
4. When the description or gallery changed, run `gh workflow run listing.yml --ref main`, with `-f dry_run=true` first. CurseForge has no API for its page, so paste `listing/curseforge.html` and upload the gallery by hand.

Tags use `<version>+mc<line>`, such as `2.1.0+mc1.21.1`, and a branch whose tag exists is skipped. Each loader's jar has its own version on the sites, such as `1.0.0+mc26.1-fabric`.

After a partial failure, re-run failed jobs or start a fresh run. Neither uploads a jar twice. Delete the artifact `curseforge-<version>` to upload a version to CurseForge again. If a publish job failed at its CurseForge step or while saving that artifact, the upload may have gone through, so check CurseForge's files page and retry only if the jar is absent. To limit sites, pass `-f targets=curseforge,github`, for example.

The `CHANGELOG.md` section goes to all three sites as written. Start with a short paragraph about what changes for players. List merged PRs under `## Features` and `## Fixes`, one bullet per PR ending in `(#21)`, for example. Rewrite titles for players and omit CI, refactors and docs.

Project IDs are repository variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`. Tokens are secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`.
