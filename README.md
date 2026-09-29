# Curve Generator (Fabric mod for Minecraft Java 1.21.1)

Design ellipses, equation plots and Bézier curves in-game, built from full blocks, slabs, stairs, trapdoors, fences, glass panes and walls. Then place them with a hologram preview, or export them to a Litematica schematic.

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
  - On the Bézier tab, drag the numbered handles in the preview, or type exact x and y positions in the point list. Remove a point with its × button, and scroll the list when there are more points than fit.
- **Blocks tab** controls which blocks are used.
  - Click a block to choose one yourself from a searchable list, sortable by name or by closeness in colour.
  - **Match a colour…** opens a colour picker. It picks, for every piece type, the block whose texture is closest to that colour (compared in CIELAB, so "close" means close to the eye).
  - Turning a piece type off means the solver won't use it.
- **Upright / Flat** (bottom left) builds the shape upright like a wall, drawn from the side, or flat like a floor, drawn from above with the drawing's top pointing away from you.
  - **Flat builds use the pieces that differ from above:** full blocks, open trapdoors against any side, and fences, panes and walls connecting in all four directions. Slabs and stairs look like full blocks from above, so flat builds don't use them.
  - **Colours follow the face you'll see:** block colours use the top face for floors and the side face for walls, so logs show their rings and grass blocks are green.
- **Right-click** any button that steps through options (Shape, the examples, Colour and the rest) to step backwards.
- With an inequality typed in, the Shape button is greyed out because the inequality decides the filled side.
- **Count tab** lists how many of each block the shape uses, like the web version's materials list.
  - Grouped by piece type, with one row per orientation or state. Each row's icon is drawn exactly as in the preview, in the current colouring.
  - With Depth or Height above 1, counts cover every layer.
  - Hover a row to see the full name and which block it uses.
- **Colour** (top right) switches the preview between plain stone, piece types, or the colours of your chosen blocks. **Curve** shows or hides the true curve.
- **Replace** (bottom bar): when off, the shape only goes into air and replaceable blocks like grass, water and snow layers, leaving existing builds alone.
- **Carve** (bottom bar): clears existing blocks from the space the shape encloses. That's inside a thin or thick ellipse, above a "fill under" equation, or below a "fill over" one. Filled ellipses, lines and Bézier curves don't carve. The preview shows blocks that will be cleared in red.
- **Depth** (upright) extrudes the shape that many blocks deep, which is useful for tunnels and vaults. **Height** (flat) stacks the floor that many layers up. Fences, panes and walls connect through the extra layers too, and walls follow the game's rules as they do. For example, a straight wall more than one block deep gets posts, because its front and back blocks each connect on one side only.
- Scroll to zoom the preview, drag it to pan, and hover a block to see its piece and block.

### Place

**Place** closes the screen and shows a translucent preview where you're looking. An upright shape faces you, with its middle-bottom block on the block your crosshair targets. A flat shape lies centred on that block.

| Key | Action |
|---|---|
| Enter | Place it |
| R | Rotate by 90° |
| Page Up / Page Down | Move up or down |
| K | Lock the position and direction, so you can walk around it without it turning (R still rotates it), or unlock |
| Backspace | Cancel |
| Z | Undo the last placement |

Placing needs **operator permissions (level 2)**, the same as `/setblock`. In singleplayer, that means cheats must be on.

- **With the mod on the server** (or in singleplayer), blocks are sent in batches and placed at once. The server checks permissions itself.
- **On a server without the mod**, it falls back to `/setblock` commands, 40 per tick. This also needs operator rights.

Undo restores everything the placement changed, including carved blocks. It restores the previous block states. It doesn't restore the contents of chests or other block entities, so be careful placing over them.

### Export

**Export** writes `schematics/curvegen_<kind>_<date>.litematic` in your game folder (with `_floor` added for flat builds). Load it with Litematica's Load Schematics menu. Upright shapes run east (width), up (height) and south (depth). Flat shapes run east and north, stacking up; rotate them in Litematica as needed. Export works without any permissions.

## How it works

The shape maths lives in `dev.curvegen.core`, which is plain Java with no Minecraft dependencies. It's a direct port of the web generator and gives identical results.

1. Each cell is sampled at 16×16, Minecraft's own pixel grid.
2. Every allowed piece is scored by how many pixels it gets wrong.
3. Fences, panes and walls are then refined together with their neighbours, since their shape depends on what's around them. Their connections follow the game's rules: fences join fences, panes and walls join each other, and all of them attach to full faces. They skip leaves, pumpkins and similar blocks that the game refuses to connect to. A wall's sides also turn tall when the block above covers them, and a straight wall drops its post unless something rests on it.

The solver runs on a background thread, so dragging handles never stalls the game.

## Resource packs and modded blocks

**Resource packs:** block colours are read from the textures of whatever packs are active, and they're recomputed whenever you change packs or press F3+T.

**Modded blocks:** they're included automatically when they're built on the vanilla block types.
- Slabs, stairs, trapdoors, fences, panes and walls appear in the lists when their mod uses Minecraft's own slab, stair, trapdoor, fence, pane and wall classes, which most mods do.
- Full blocks are recognised by their shape.
- A modded block's colour comes from its texture like any other.
- Blocks that imitate these shapes with their own code, like some decorative mods' custom stairs, won't show up.

## Known limits

- Built and tested only for 1.21.1 Fabric.
- Preview colours come from each block's particle texture. Blocks with very different top and side textures, like grass blocks, are represented by that one texture.
- The hologram shows at most 30,000 blocks in detail. Bigger shapes show their bounding box only.
- Exported schematics contain the shape only; carving isn't included.
