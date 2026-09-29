# Curve Generator (Fabric mod for Minecraft Java 1.21.1)

Design ellipses, equation plots and Bézier curves in-game, built from full blocks, slabs, stairs, trapdoors, fences and glass panes. Then place them with a hologram preview, or export them to a Litematica schematic.

## Build

You need JDK 21.

```
./gradlew build          # Windows: gradlew.bat build
```

The mod jar ends up in `build/libs/curvegen-1.0.0.jar`. Put it in your `mods` folder together with [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1.

`./gradlew runClient` starts a development client with the mod loaded.

The versions in `gradle.properties` are for 1.21.1. For newer builds, check https://fabricmc.net/develop. Moving to a different Minecraft version will need some code changes: rendering, networking and screen APIs shift between releases.

## Use

Press **G** to open the generator. Every key can be rebound under Options, Controls, Key Binds, Curve Generator.

- **Ellipse, Equation, Bézier tabs** set up the shape, exactly like the web version.
  - Equations accept the Desmos-style syntax: `y = 2sin(x)`, `x^2 + y^2 = 16`, `y < 9 - x^2/4`.
  - On the Bézier tab, drag the numbered handles in the preview.
- **Blocks tab** controls which blocks are used.
  - Click a block to choose one yourself from a searchable list, sortable by name or by closeness in colour.
  - **Match a colour…** opens a colour picker. It picks, for every piece type, the block whose texture is closest to that colour (compared in CIELAB, so "close" means close to the eye).
  - Turning a piece type off means the solver won't use it.
- **Colour** (top right) switches the preview between plain stone, piece types, or the colours of your chosen blocks. **Curve** shows or hides the true curve.
- **Depth** extrudes the shape that many blocks deep, which is useful for tunnels and vaults. Fences and panes connect front to back as well.
- Scroll to zoom the preview, drag it to pan, and hover a block to see its piece and block.

### Place

**Place** closes the screen and shows a translucent preview where you're looking. The shape faces you; its middle-bottom block sits on the block your crosshair targets.

| Key | Action |
|---|---|
| Enter | Place it |
| R | Rotate by 90° |
| Page Up / Page Down | Move up or down |
| K | Lock the position (so you can walk around it), or unlock |
| Backspace | Cancel |
| Z | Undo the last placement |

Placing needs **operator permissions (level 2)**, the same as `/setblock`. In singleplayer, that means cheats must be on.

- **With the mod on the server** (or in singleplayer), blocks are sent in batches and placed at once. The server checks permissions itself.
- **On a server without the mod**, it falls back to `/setblock` commands, 40 per tick. This also needs operator rights.

Undo restores the previous block states. It doesn't restore the contents of chests or other block entities, so be careful placing over them.

### Export

**Export** writes `schematics/curvegen_<kind>_<date>.litematic` in your game folder. Load it with Litematica's Load Schematics menu. The drawing's width runs east, height runs up, and depth runs south; rotate it in Litematica as needed. Export works without any permissions.

## How it works

The shape maths lives in `dev.curvegen.core`, which is plain Java with no Minecraft dependencies. It's a direct port of the web generator and gives identical results.

1. Each cell is sampled at 16×16, Minecraft's own pixel grid.
2. Every allowed piece is scored by how many pixels it gets wrong.
3. Fences and panes are then refined together with their neighbours, since their shape depends on what's beside them. Their connections follow the game's rules: fences join fences, panes join panes, and both attach to full faces. They skip leaves, pumpkins and similar blocks that the game refuses to connect to.

The solver runs on a background thread, so dragging handles never stalls the game.

## Known limits

- Built and tested only for 1.21.1 Fabric.
- Preview colours come from each block's particle texture. Blocks with very different top and side textures, like grass blocks, are represented by that one texture.
- The hologram shows at most 30,000 blocks in detail. Bigger shapes show their bounding box only.
