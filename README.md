# Curve Generator

<!-- TODO: preview GIF -->

A Fabric mod for Minecraft Java 1.21.1 that turns ellipses, equations and Bézier curves into blocks. It picks full blocks, slabs, stairs, trapdoors, fences, glass panes and walls to match the curve as closely as possible. You can then place the result in your world or export it as a Litematica schematic.

## Install

<!-- TODO: add the Modrinth and CurseForge links on release. Also add the Modrinth URL as "homepage" in fabric.mod.json. -->
Download the mod from Modrinth or CurseForge. Put the jar in your `mods` folder along with [Fabric API](https://modrinth.com/mod/fabric-api). You'll need Fabric Loader and Minecraft 1.21.1.

## Quick tour

1. Press **G** to open the generator.
2. Pick a tab: **Ellipse**, **Equation** or **Bézier**. Equations use Desmos-style syntax, like `y = 2sin(x)`, `x^2 + y^2 = 16` or `y < 9 - x^2/4`. On the Bézier tab, drag the numbered handles in the preview.
3. On the **Blocks** tab, choose a block for each piece type, or use **Match a colour…** to pick blocks whose textures are closest to a colour.
4. Choose **Upright** to build it like a wall or **Flat** to build it like a floor.
5. Check the **Count** tab to see how many of each block you'll need.
6. Press **Place** to position a hologram in the world, then press Enter to build it. Or press **Export** to save a `.litematic` file.

Placing needs operator permissions, the same as `/setblock`. In singleplayer, that means cheats must be on. Exporting works anywhere.

Right-click a button that steps through options to step backwards. You can rebind every key under Options → Controls → Key Binds → Curve Generator.

The [wiki](https://github.com/Neil-001/curvegen/wiki) covers the rest: placement keys, Replace and Carve, presets, exporting, and how resource packs and modded blocks work.

## Development

You'll need JDK 21.

```
./gradlew build       # builds the jar into build/libs/
./gradlew runClient   # starts a dev client with the mod
./gradlew test        # runs the tests for dev.curvegen.core
```

On Windows, use `gradlew.bat`. The Minecraft, Yarn, Loader and Fabric API versions are in `gradle.properties`.

- `dev.curvegen.core` holds the shape maths and the solver. It has no Minecraft imports, so the tests run without the game. Keep it that way.
- Run `./gradlew build` and `./gradlew test` before pushing.

---

License: MIT.