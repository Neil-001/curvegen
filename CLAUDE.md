# Curve Generator

Curve Generator builds 2D and 3D shapes in Minecraft Java 26.3. It ships for Fabric and NeoForge and supports previews, in-world editing, painting, operator-only placement and Litematica export.

## Build

Use JDK 25 and run `./gradlew build` after every change. It builds both jars and runs the Fabric tests. Keep the build and IDE free of warnings. Use `_` for unused lambda and catch parameters. Use Minecraft's unobfuscated names.

Run focused tests with `./gradlew :fabric:test --tests '<class>'`. Use `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient` for a dev client, and `runServer` for a dev server.

## Boundaries

- Read the package guide before editing code in a package that has one. Keep decisions and their reasons in `CLAUDE.md`, with a relative `AGENTS.md` symlink. Leave details the code already makes clear in the code.
- Both loaders compile the shared `src/main` and `src/client` trees. Keep client classes out of common code and Minecraft imports out of `core`, including `core/edit` and `core/paint`. Keep loader imports in their own source trees. Shared client code uses `ClientPlatform`. Update both loaders when changing it or adding a hook.
- Keep optional Mod Menu references in `ModMenuIntegration` and Chroma Studio references in `ChromaBridge`. Use `-PwithChroma` to add Chroma Studio to a dev client.
- Keep the 2D and 3D solvers independent. `Settle2D` uses `Solver3.settle` only after a 2D solve to settle around pattern holes. Run `GameRules2Test` after changes to `Settle2D`, 2D block states or `Solver3`'s game rules. Run `GameRules3Test` after changes to `Pieces3` or `Solver3`'s game rules. The [core guide](src/main/java/dev/curvegen/core/CLAUDE.md) covers refinement and new 3D shapes.
- Paint reuses the last solve unless it changes the solver input `fullConnects`. Holes open after the solve and settle their neighbours. Placement leaves the holes alone. See the [paint guide](src/main/java/dev/curvegen/core/paint/CLAUDE.md).
- Route external hologram edits through `Editor.edit(group, change)`. See the [editor guide](src/client/java/dev/curvegen/client/edit/CLAUDE.md) for snapshots and background work.
- Read [the listing guide](listing/README.md) before changing listing files. Regenerate the CurseForge HTML after editing the description. Rerun `listing/art/render.py` after changing either solver or the 3D preview drawing, and inspect the resulting image diff.

## UI and tests

- Grey out inapplicable controls and explain why in a tooltip. Use plain, sentence case copy. Use translation keys for player-facing text.
- Use `ControlScreen`'s controls for options and numeric input. Size buttons with `tw(...)` and the fitting loops in `CurveScreen.init`. The UI must fit at 427 pixels wide, as at 1280×720 with GUI scale 3. Make room within the layout for new controls.
- Test new core logic. Keep reference values unless the intended output changes. For a visual fix, check that other cases still render as before. `SilhouetteTest` compares pixels across pieces.

## Branch workflow

Open feature, fix and documentation PRs against `main`. Prepare the 26.3 version and changelog there, then cut `release/26.3-<version>` from the tested commit and open one promotion PR against `26.3/stable`. Keep that branch at the chosen version as development continues on `main`. Use a merge commit for promotions so stable retains the ancestry needed for the next release. Feature PRs may be squashed.

To resolve a promotion conflict, merge `origin/26.3/stable` into the release branch, preserve the intended release contents and rerun the checks. Bring release fixes back to `main`. Hotfixes may target stable when they must ship before the next promotion. Bring those fixes back too.

Port a tested release to each older line as one commit on top of that release, then open a PR to the line's stable branch and merge it with a merge commit. The older lines are `1.21.1/stable` for Minecraft 1.21 and 1.21.1, `1.21.11/stable` and `26.2/stable`. Use the same process for 26.3 once `main` moves to a newer line. Keep the retired `1.21.x/stable` as history.

## Releasing

Only `*/stable` branches publish. Keep `RELEASE_MAIN` unset here. Chroma Studio shares the release workflows and scripts but publishes from `main`. Apply changes to those files in both repositories.

1. Bump `mod_version` in `gradle.properties` and add `# <version>` at the top of `CHANGELOG.md`. Use `-beta.1` or `-alpha.1` for prereleases. Prepare current releases on `main` and older releases in a PR to their stable branch.
2. After the release PR merges and stable's checks pass, run `gh workflow run release.yml --ref main -f branches=26.3/stable -f dry_run=true`. Check the summary's jars and notes, then run it again without `dry_run`.
3. If the description or gallery changed, follow the [listing release steps](listing/README.md).

The changelog supplies the notes for Modrinth, CurseForge and GitHub. Start with a short paragraph for players. Under `## Features` and `## Fixes`, give each player-facing PR one bullet ending in its number, such as `(#21)`. Omit CI, refactors and docs.

For a partial failure, retry failed jobs or start a fresh run. If the CurseForge upload or receipt failed, check the files page first. A jar can have uploaded before its receipt was saved. Delete the corresponding `curseforge-<jar-version>` artifact only to upload a jar again.
