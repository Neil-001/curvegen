# Curve Generator

A Fabric and NeoForge mod for Minecraft Java 26.3. It turns ellipses, equations and Bézier curves into blocks, with a preview, operator-only placement and Litematica export.

## Commands

Needs JDK 25. Versions are in `gradle.properties`. Run build and test after every change.

- `./gradlew build` builds both jars in `fabric/build/libs/` and `neoforge/build/libs/` and runs the tests.
- `./gradlew :fabric:test` runs the JUnit tests for the pure-Java core and for client classes that don't need the game running, such as `ModSettings`. Only Fabric hosts them, since they're loader-independent. `GameRules3Test` is the one test that loads Minecraft's blocks, to check the 3D pieces against the game. It needs no world or client.
- `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient` starts a dev client. Use `runServer` for a server.

Both jars target 26.3 only. Minecraft is unobfuscated, so use its own names without mappings. Builds use the minimum supported Fabric API and NeoForge versions; CI also compiles against newer ones.

## Layout and boundaries

Both loaders compile the shared `src/main` and `src/client` code with their own entrypoints and metadata. Fabric also compiles `src/test`. Fabric splits common and client source sets with Loom's `splitEnvironmentSourceSets()`; NeoForge combines them in one jar. Common code must never touch client classes.

- `dev/curvegen/core/` must have no Minecraft imports. `Pieces` defines the 16×16 states, masks, mirrors and sturdy faces; `Target` builds the shapes and carve regions; `Solver` selects pieces. `Expr` compiles Desmos-style equations to lambdas without `eval`; only `parseEquation3` knows z, so 2D equations parse as they always did. Run the solver on a worker thread with a `ShapeSettings.copy()`. `Silhouette` shares piece pixels and outlines between the preview and Count icons. Those classes are 2D. 3D shapes have their own: `Pieces3`, `Shape3`, `Shapes3`, `Bezier3` and `Solver3`, described under "Volumetric solver".
- `core/Mesh3`, `Raster3` and `Count3` are the G menu's 3D preview and Count rows, and `core/edit/Orbit` its camera. See "G menu".
- `dev/curvegen/core/edit/` is the in-world editor's maths, also free of Minecraft imports: `HandleMath` (ray to axis, ray to plane, picking), `Box` (the 26 box handles, drag and fit rules), `Orient` (which way a shape's own axes point), `Edit2D` and `Edit3D` (what handles and keys do to a 2D or 3D shape's `ShapeSettings`) and `Turned` (a `Shape3` with its own axes pointing another way). `Radial` (which wedge the cursor points at, where labels go, wedge ids and their order), `HoldTimer` (tap or hold for the move keys) and `Option` (one entry of "Shape options") are there too.
- Only `fabric/src/` and `neoforge/src/` may import their loader's classes. Shared client code uses `ClientPlatform`; changes to that contract must go into both loaders. Each loader also passes in-world mouse buttons and scrolling to `Editor.mouseButton` and `Editor.mouseScroll` and cancels the event when they return true: NeoForge through `InputEvent`, Fabric through `MouseHandlerMixin`, because Fabric API has no such event.
- `CurveGen.java` handles server placement through `net/PlaceBlocksPayload.java` and checks `Permissions.COMMANDS_GAMEMASTER`.
- In `dev/curvegen/client/`, `BlockChoices` maps pieces to block states for each orientation, and 3D states through `stateFor3`, which sets every property `Pieces3.props` names on the chosen block; `ColorIndex` matches face texture colours in CIELAB; `Placement` sends blocks to the server or falls back to `/setblock`, and undoes the last placement. `PresetStore` atomically replaces one JSON file per preset in `config/curvegen/presets/`.
- `client/edit/` is the in-world editor. See "In-world editor" below.
- `client/screen/RadialScreen` is the radial menu on V, `InputScreen` the number box and one-line text box, and `WedgeOrderScreen` the settings screen's wedge order. See "Radial menu" below.
- `ModSettings` holds the mod's own options as static fields, loaded at client start and saved atomically to `config/curvegen/settings.json`. A missing, mistyped or out-of-range value gets its default.
- `client/screen/CurveScreen` is the main UI, described under "G menu"; `PreviewTexture` draws a solved 2D grid into a dynamic texture and `Preview3` a solved 3D shape. `SettingsScreen` edits `ModSettings` and opens from the main UI's cogwheel, from Mod Menu on Fabric and from the mod list's Config button on NeoForge. Both extend `ControlScreen`, which has the shared controls.
- Mod Menu is optional. Fabric compiles against it, and only `ModMenuIntegration`, which Mod Menu itself loads, may refer to it.
- `listing/` holds the Modrinth and CurseForge descriptions, which must say the same thing, and their artwork. `listing/art/render.py` redraws the artwork from the solver.

## Solver

This is the 2D solver, for ellipses, equations and Bézier curves. Its results must not change when the 3D one does.

1. `Target` marks cells empty, full or mixed. Mixed cells sample 16×16 pixels and store each state's pixel error in `errTab`.
2. Each cell starts with its lowest-error allowed piece. Fence, pane and wall tokens get their states from neighbours, initially assuming every supporting neighbour connects.
3. Refinement re-picks cells until nothing improves. `Solver`'s affected sets must include side neighbours, the row below for upright wall heights, and rows above and below for flat connections. Otherwise a step can worsen the result.
4. Ellipses solve one quarter and mirror it with `MX` and `MY`. Axis cells require self-symmetric pieces.
5. In hollow shapes, refinement adds a penalty for a connector whose silhouette is a full block, such as a wall with two tall sides mirrored from one with two low sides. The block above would have to stay to keep it tall. Keep the rule as a cost, not a filter, or refinement can cycle.
6. Hollowing removes unexposed full blocks from thin ellipses. A block is unexposed when every neighbour's silhouette fills the shared edge, so `Pieces.EDGE` decides this rather than `STURDY`. Keep connector supports and, upright, blocks that determine a wall's height below.

Upright states are 0 to 55, with walls at 20 to 55 encoding three heights per side, covered and post. Flat-only states are 56 to 105. Shelves follow: 106 and 107 upright, 108 to 111 flat. Chain and end rod states, 112 to 117, serve both orientations: a chain runs across or up the drawing and a rod points one of four ways. Neither connects to anything or uses the end-on view. They win a tie with air, the only tie not settled by candidate order. 2D grids use `byte[]`, so indices must stay below 128.

Upright builds show the side face; flat builds show the top, with the drawing's top pointing away from the player. Match colours to that face. Flat builds exclude slabs, stairs and closed trapdoors because they look like full blocks from above. Upright builds exclude shelves facing towards or away from the viewer for the same reason.

## Volumetric solver

`Solver3` solves 3D shapes (`ShapeSettings.is3d()`: ellipsoid, torus, 3D equation, 3D Bézier curve and Bézier surface) in the world's orientation: x east, y up, z south. It shares nothing with the 2D solver but `Pieces.Family` and the settings' piece toggles and `fullConnects`. The in-world editor runs it through `Shape3D`.

1. A `Shape3` is a box of blocks and a field, solid where the field lies from `lo()` to `hi()`. `uniform` marks runs of cells empty or solid without sampling them. A cell that may hold the surface is sampled at 16³ points, by interpolating the field from a 5×5×5 lattice, and stores its overlap with each of `Pieces3`'s 57 parts. Every state is a union of disjoint parts, so a state's error is the cell's volume plus the state's, less twice their overlap.
2. Each mixed cell starts with its lowest-error token. A token is a state, except that a straight stair stands for whichever corner shape its neighbours give it, and `FENCE`, `PANE` and `WALL_POST` for whichever connections, heights and post.
3. Refinement re-picks cells until nothing improves, for at most `MAX_SWEEPS` passes. A pick is judged on every cell whose state it can change: `Run.gather` lists them. A token reaches two steps on its own level, because a connector attaches to a stair by the stair's corner shape, and down any column of walls below those cells. Leaving a cell out lets a pick raise the total error, and then refinement can cycle.
4. A symmetric shape is sampled in one octant. Mirrored cells share a token, and only the quarter that x and z leave is scored, so the total being lowered stays one sum. Cells on a mirror plane may only hold tokens that are their own image.
5. `hollow()` shapes then lose every full block whose six neighbours fill the faces they share. No connector fills a face, so the blocks connectors attach to and the block above a wall always stay.

`Pieces3` states decode to every block state property, and `Pieces3.props` writes them in the game's names (`facing=north,half=top,shape=inner_left`), so the client maps a state to a `BlockState` without guessing. There are 257 states, so 3D grids are `short[]`, indexed `(y*nz + z)*nx + x`. Shapes are the game's voxel shapes, except that a fence's arms, a chain and an end rod are scored as drawn, like the 2D pieces. `GameRules3Test` builds every state with `BlockChoices.stateFor3` and compares its shape with the game's, then has the game update every stair, fence, pane and wall in a set of solved shapes and expects no change. Run it after touching `Pieces3` or `Solver3`'s rules.

Rules the 3D solver follows beyond those under "Minecraft rules":

- A stair becomes an outer corner when the stair in front of it, on the same half, faces sideways, and an inner corner when the one behind does, unless the cell beside it holds a stair facing the same way.
- A wall side is tall when the bottom of the collision shape above covers that side's strip, 2 pixels wide from the edge to 1 past the centre. A fence's collision arms are solid, so a fence above makes a wall's sides tall where the fence connects.
- A wall has a post if the wall above has one, if it has no sides, or if a side lacks its opposite. Otherwise it has none when two opposite sides are both tall, and else has one only if the block above covers its centre.

### Adding a 3D shape

Implement `Shape3` and add it to `Shape3.of`. Only the sizes, `field` and `wireframe` are required.

- `field(x, y, z)` takes block coordinates from the box's lowest corner. It's called from several threads, so it must not share scratch state. NaN means outside, and the solver drops any voxel with a NaN among the eight samples around it.
- The default `uniform` trusts the field to change by at most 1 per block, as a signed distance does. If it can be steeper, divide it by a bound on its slope or override `uniform`, or the solver will skip cells that hold the surface. `Solver3Test.skippedCellsReallyAreUniform` and its namesake in `Shapes3Test` show how to test that.
- Give a shell as a band of a smooth field, not as `abs(distance) - thickness/2`: the lattice can't follow a crease. An implicit surface f = 0 with a thickness is a band of f over its gradient's length; a filled side is `lo` or `hi` at infinity. A tube around a curve is the distance to it with `hi` at half the thickness. That field does have a crease, on the curve itself, and interpolating across it thins a tube under a block thick. Return false from `smooth()` then, or for any field the lattice can't follow: the solver evaluates all 4096 points instead, which is far slower. The ellipsoid and torus do this when they're only a few blocks thick, where their own creases come near the surface. A sheet has too many cells for that, so give it a signed distance, which has no crease on the sheet, and NaN past its edges.
- Three optional hooks help a field the lattice only sometimes follows. `follows(lattice)` gets a block's 125 samples and returns false to have that one block evaluated point by point. `inset()` moves the outermost samples just inside the block's faces, for a field that jumps on block boundaries. `lattice(i, j, k, out)` fills in a block's samples itself, for a field that's quicker to work out from a neighbouring sample's answer.
- `carve()` gives the band of field values that count as the space the shape encloses, for `Solver3.carve`. `pad()` is the room added around the size the player set. `symX`, `symY` and `symZ` must be true only if the field is exactly mirrored about the box's centre.
- `wireframe()` returns polylines as `{x0, y0, z0, x1, y1, z1, ...}` and must be cheap, since the editor draws it every frame while a solve runs. Work it out once and keep it if it takes any. The curve gives the curve and then its control polygon, the surface nine lines each way and then its control net, and the equation its box and then where the surface cuts a few slices.

Sizes cap at `Shape3.MAX_SIZE` (256) per axis before padding. `Solver3.solve` takes a `BooleanSupplier` and returns null once it reports true, so the caller can drop a solve when the settings change. Run it on a worker thread with a `ShapeSettings.copy()`. The solver only works in the world's orientation, so a turned or tipped shape is solved again as `Turned.of(shape, orient)`, never by moving solved blocks. `Turned` falls back to one block at a time for a shape that overrides `uniform`, whose runs are counted along its own x. It passes `lattice`, `follows` and `inset` on to the shape, with the samples reordered. Sampling runs on the common pool, a few rows of a level to a task. A hollow sphere takes about 20 ms at 64 blocks across and 100 ms at 128. `Shapes3Timing` prints the other shapes' times.

### The 3D shapes

`Shapes3` holds all five. Control points and equation ranges are in the shape's own box: x east, y up, z south, in blocks from its lowest corner, before `pad()`.

- **Equation** (`EQUATION3`). Anything in x, y and z with `=`, `<` or `>`; a bare expression means z = expression. z is height. x runs east and y north, so the shape isn't mirrored: the range's highest y is at the box's lowest z. Modes are Surface with a thickness, Below and Above, and `<` or `>` in the equation overrides the mode, as in 2D. With `q3Lock` the depth and height follow the width; `Shape3.of` works them out and leaves the settings alone, so read the size from the shape. `error()` carries the parser's message.
- Below and Above use the equation's sign and interpolate its value. `follows` sends blocks where it bends too sharply, as at a pole or a jump, to point-by-point sampling. Surface uses the distance to where the two sides meet: it walks there the way the equation changes fastest and measures straight back. A sign change with no zero, at a jump or an asymptote, isn't surface, so `z = floor(x)` is treads without risers and `z = tan(x)` has no walls at its asymptotes.
- An equation that only touches 0 at its surface, as `x^2 = 0` or a squared sphere does, builds the same wall. `Equation.lattice` gives the two sides of such a surface opposite signs within a block.
- No slope bound holds for an equation, so `uniform` looks at each cell's corners and centre and skips it only when they agree by a margin. It's a judgement: a feature much smaller than a block that touches none of those points is missed. Surface mode never samples point by point, because that would take seconds, so a wall loses up to a quarter of a block where it ends at a jump off the block grid, an asymptote or the edge of the equation's domain. A wall much thicker than the surface's bends are wide is only roughly that thick.
- **Curve** (`BEZIER3`). One Bézier through all of `pts3`, 2 to `Bezier3.MAX_POINTS`, as a round tube with round ends. It's cut into straight pieces within a hundredth of a block of it, and `Near3` buckets them so a distance looks at a handful.
- **Surface** (`SURFACE`). One patch of `sRows` × `sCols` points, 2 to 6 each way, row by row in `sPts`. The thickness is measured straight out from the patch and the edges are cut square, so a level patch on the block grid, a block thick, is exactly a layer of blocks. The field is a signed distance and NaN past an edge. The patch is sampled about once a block with its slopes and bends. `Near3` finds the nearest sample, and a point's distance comes from Newton's method on that sample's own bent sheet. Where the patch folds back or bends sharply, the other stretches near the point are tried too. Within a block the samples are worked out one from the next, unless more than one stretch is near. Off the block grid an edge can land up to an eighth of a block out.
- `Bezier3` has what the editor needs, with rays in the control points' own coordinates: `insert` adds a curve point by raising the degree, which leaves the curve alone and moves every inner point; `addRow` and `addColumn` do the same to a surface; `removeRow` and `removeColumn` lower the degree by least squares, which keeps the edges and undoes an add exactly; `nearestToRay` and `patchNearestToRay` find where a look ray meets or passes closest. A ray within a twentieth of a block of the patch counts as hitting it. A ray that skims a hump within a few thousandths of a block of its top can get the far crossing instead of the near one. Removing a curve point is just taking it out of the list.
- Each has its own preset kind in `PresetData`, with three numbers to a Bézier point. That needed no change to the file format, which is still version 1.

## Minecraft rules

`ConnectionRulesTest` covers these:

- Fences join only fences; panes and walls join each other. All attach to sturdy full faces, including a stair's tall side, an open trapdoor's panel and a shelf's panel. They don't attach to slabs, closed trapdoors or `Block.isExceptionForConnection` blocks such as leaves, pumpkins and shulker boxes. `ShapeSettings.fullConnects` models that exception.
- An upright wall side is tall when the bottom row of the block above covers its test region.
- At depth 1, an upright wall has a post unless both sides connect. A straight wall keeps its post only if the block above covers its centre and the sides aren't both tall.
- At depth over 1, the front and back walls connect on only one side, so the visible upright wall always has a post.
- Flat walls have no post with exactly two opposite connections. Lower layers have tall sides; the top layer has low sides.
- A stair's `FACING` points to its tall back. An open trapdoor's panel is opposite `FACING`, and so is a shelf's.
- A shelf is a panel 3 pixels thick with lips 2 deep and 4 tall along its top and bottom. Only the panel's back is a sturdy face.

## In-world editor

Place in the G menu spawns one hologram, locked at the block the player looks at. `Editor` owns it: lock, handles, drags, keys, undo and redo, the HUD and the solver job. It knows nothing about any one shape.

- `Editor` keeps a `Box` in world axes and an `Orient`. Edits change the live `CurveGenClient.SETTINGS`. Undo snapshots are `ShapeSettings.copy()` plus the box and orientation, and `ShapeSettings.set` restores one. `ShapeSettingsTest` fails if a new field is missing from `set` or `same`.
- A solve gets a cancel supplier. When the shape changes while one is running, `Editor` drops it, so a slow solve doesn't hold up the latest shape. After 400 ms of dropping, the next one runs to its end, so a long drag of a slow shape still shows blocks.
- A drag restores the settings and box from when it started before every update, so a drag depends only on where the player looks now. Shape edits needn't be reversible.
- The box and ideal curve are drawn from the live settings every frame. Blocks come from `EditShape.solve` on a worker thread with a `ShapeSettings.copy()`, then `EditShape.build` on the client thread. `Hologram` takes that list of offsets and block states, drops faces hidden against a whole-cube neighbour once per solve, and then only checks the world. Above `ModSettings.hologramBlockLimit` it draws nothing, so only the wireframe shows.
- Handles are drawn twice: dimmed through terrain with `Hologram.throughWalls()`, then as they are where nothing hides them. The first is the game's see-through text render type on `textures/white.png`, because the game has no untextured type without a depth test and making one needs an access widener. Picking ignores terrain.
- Along an axis where the box is too small to tell three layers of handles apart (`Box.handles(size, gap)`), only the middle layer shows, with that axis's own two face handles.
- Face handles move along one axis, edge handles in the plane across their edge, and corners in the axis-aligned plane facing the camera, with scrolling for the third axis. Sneak resizes about the centre. `Editor.mouseButton` returns true only for a click on a highlighted handle or during a drag.
- Nudge and bump keys are `CurveGenClient.STEP_KEYS`, each with an action that takes a number of blocks. Hold-to-type calls that action with the typed amount.
- Turning rounds when width and depth differ in parity, so `Editor` turns about a pivot kept from before the first turn (`Box.about`). Anything else that resizes the box clears it.
- `Editor.export()` writes the hologram's blocks to a Litematica schematic as they stand in the world. The radial menu's Export uses it. The G menu's Export writes the shape it shows, unturned, whether or not a hologram is out.
- Opening a screen mid-edit keeps the hologram. When it closes, `Editor` re-reads the settings and records one undo step if they changed. Changes `Editor` recorded itself while the screen was open, such as the radial menu's, don't count towards that step.
- Anything outside the editor that changes the settings of a hologram calls `Editor.edit(group, change)`. It records the undo step, takes the shape and orientation the settings now call for and fits the box. Edits in a row with the same non-null `group` share one undo step, which is how scrolling a value several notches is one step.
- `StepHold` runs hold-to-type for every key in `STEP_KEYS`: the press acts at once with 1, `Editor.renderHud` draws the progress bar after `barDelaySeconds`, and at `holdSeconds` the number box opens. The typed amount replaces the press's step through `Editor.lastStep` and `Editor.retract`, so the two are one undo step. Undo and redo call a hold in progress off. A held key repeats, so `InputScreen.ignoring` keeps the repeats out of the box.
- The HUD hides while any screen is open.

### `EditShape`

`client/edit/EditShape` is all a shape supplies. `Shape2D` implements it for the ellipse, equation and 2D Bézier curve, and `Shape3D` for the 3D shapes; `EditShape.of` picks the implementation and `EditShape.supports` says whether a kind has one, which the radial menu greys a shape's wedge out by. Everything is in the shape's own axes, in blocks from its own minimum corner, except `Content`, which is in world axes.

- `size()` and `resize(want, dragged)`: the box. `resize` applies what its limits allow and may change undragged axes, as an equation's same-scale lock does. `Editor` reads `size()` back and fits the box.
- `bump(axis, side, amount)`: optional. A curve stretches its control points' bounding box here; returning null has `Editor` resize the box.
- `orient(current)` returns the orientation the settings allow; `tip(current, forward)` tips a quarter turn and may change settings. A 2D shape switches `floor` instead of rotating.
- `points()`, `pointPlane()`, `movePoint`, `removePoint`, `insertPoint`, `duplicatePoint`: control points. `pointPlane()` is the own axis points can't move along, or -1 for free 3D points, which drag like corners. `movePoint` returns how far the box's own minimum corner moved when the shape grew to keep the point inside.
- `curve(solved)`: the ideal curve as segments, cheap enough for every frame.
- `solve(copy, orient, cancelled)` runs on the worker thread and mustn't touch the game. It may return null once `cancelled` reports true. A 3D shape only works out what Carve clears while Carve is on (`solveUsesCarve()`), so switching it on solves again. `build(solved, orient)` returns `Content`: `Placed(dx, dy, dz, state)` offsets from the world box's minimum corner, carve offsets, whether to tint by top texture, and an error message. Return false from `solveUsesOrient()` if turning only needs `build` again.
- `options()`: the shape's own settings for the radial menu's "Shape options", as `Option.Number`, `Cycler` (or `Option.toggle`), `Text` and `Action`. Each has a label of at most about 13 characters and `off()`, which is null or the reason it's greyed out. Getters read the live settings and setters only change them: `Editor.edit` does the rest. A number gives its limits, step and whether it's whole. The menu calls this every frame, so build the list from the live settings. `Editor.options()` adds "Add point" and "Remove point" for a shape with `points()`, so a shape doesn't list them. `Edit2D.options` has the 2D shapes'. `Shape3D` has none yet.
- `describe()`: the HUD's first line.

A 3D shape's box leaves out the room an outwards shell adds (`Shape3.pad()`), so its blocks and wireframe are offset by that much. A torus's handles and bump keys change only its box (`tW`, `tH`, `tD`): the ring and tube sizes describe the round ring that is stretched to fill it. `Edit3D.setRing` and `setTube` change those and scale the box with them.

`ColorIndex.of(Block)` follows the colour face (`ShapeSettings.topColours`) for 3D shapes and `floor` for 2D ones.

### G menu

`CurveScreen` has two sets of shape tabs, 2D and 3D, with a button to switch. `mode3d` always agrees with `ShapeSettings.is3d()`, and switching goes back to the shape that set was last on. Blocks and Count belong to both. Opened mid-edit, it shows the hologram's shape, and `Editor` takes up the changes when it closes.

- The 3D shapes have two more tabs than fit beside the view options, so their Colour button and the colour face switch sit in a row under the picture (`py1()`).
- A 3D tab's rows come from `rows3`, which closes them up on a short screen, and its fields from `whole`, `thickness` and `sizes3`. Those `bind` each field to the setting it shows: every frame an unfocused field takes the setting's value, and a field that doesn't apply is greyed out with its reason. That is how a dragged point, an equation's locked sizes and a torus's box reach their fields. Add a control the same way and set `dirty` when it changes the shape.
- A torus's ring and tube fields scale its box, so each keystroke starts again from the sizes as they were before the field was clicked. Otherwise typing 25 would pass through 2 and lose the tube.
- The 3D picture is drawn without the graphics card. `Mesh3` lists the faces of a solve that aren't against a neighbour filling the face they share, once per solve, on the worker thread. `Raster3` draws them into an array of pixels with a depth for each, lit by which way they face, and `Preview3` uploads that to a texture, only when the camera, shape or colours changed. `Orbit` is the camera. It has no perspective, so every face looking one way is the same parallelogram and a dragged point stays under the cursor. A hollow sphere 256 across is about 2 million faces: 80 ms to list and 17 ms to draw. `Preview3Timing` prints those.
- The picture is in blocks from the lowest corner of the box the player set, without `Shape3.pad()`, so it doesn't jump when a thickness changes. `Preview3` shifts the mesh and the outline by their own padding. The outline (`Shape3.wireframe`) comes from the live settings, so it moves at once while the blocks catch up.
- A solve of a superseded shape is cancelled, and after 400 ms of that the next one runs to its end, as in `Editor`.
- Dragging empty space turns the picture, a right or middle drag slides it, scrolling zooms about the cursor and a double click fits it again. On the Bézier and Surface tabs a left drag on a point moves it in the plane through it that faces the camera (`Orbit.unproject` at the point's depth), kept inside the box and on half blocks with Snap on.
- "Add point" on a 3D curve raises its degree and "Remove" lowers it, so the curve stays put. The × beside a point takes that one out. A surface has Rows and Columns instead.
- `Count3` groups `Pieces3`'s states into the Count tab's rows for a 3D shape.
- Place is greyed out for a 3D shape the radial menu doesn't offer (`RadialScreen.offers`).
- `PresetsScreen` shows a 3D preset through `Preview3` too, and dragging that picture turns it.

### Radial menu

`RadialScreen` is a screen, so the game frees the cursor and both loaders get it without hooks of their own. It draws no background, and `Editor` keeps the handles (and the one the player was looking at) while it's open.

- The menu is an oval, because labels need more room sideways. `Radial.wedgeAt` picks the wedge from the cursor's direction in the oval's proportions, wedge 0 at the top and the rest clockwise, and `Radial.labels` puts each label inside its own wedge. A menu holds at most `Radial.MAX_WEDGES` (12) wedges, "Back" included, and labels are 80 px wide. `RadialTest` checks that labels don't overlap each other, the ring or the text at the bottom on a 427 by 240 screen.
- Held open (the default), letting go of the key chooses the wedge, or closes the menu from the centre. A wedge that opens a submenu latches the menu open: after that, and from the start with `ModSettings.radialToggle`, a click chooses and the key closes. A click always chooses, a right-click goes back, and scrolling steps a number, cycler or piece type at once.
- A wedge is greyed out by giving it a reason, which shows along the bottom while the cursor points at it.
- The top-level wedges have ids in `Radial.START` (no hologram) and `Radial.EDIT`. `ModSettings.radialOrder` is one list for both: `Radial.order` applies it to a menu, with unlisted wedges after the listed ones, and `Radial.merge` builds it. Add a top-level wedge by adding its id and name to `Radial` and its action to `RadialScreen.top`.
- `RadialScreen.KINDS_2D` and `KINDS_3D` list the shapes. A kind with a null `Gen` is a shape that doesn't exist yet, shown greyed out. Give it its `Gen` when it does.

## Conventions

- Grey out inapplicable controls with a tooltip explaining why. Don't hide them.
- Use `ControlScreen`'s `cycler(...)` or `toggle(...)` for options so right-click steps backwards, and `spin(...)` for numbers.
- Size buttons with `tw(...)` and the fitting loops in `CurveScreen.init`. Nothing may overlap at 427 px wide, as in 1280×720 at GUI scale 3. New controls must take space from existing ones.
- Use plain, sentence case UI copy without jargon.
- Visual fixes must preserve other behaviour. Prove it, for example by comparing pixels across all pieces as in `SilhouetteTest`.
- Test new core logic. `OrbitTest` and `Preview3Test` cover the 3D preview's camera, picking, faces and drawing. Change `SolverRegressionTest`, `Solver3Test` and `Shapes3Test` reference numbers only on purpose.

## Placement and rendering

- Keep custom payloads under 32 KiB. Placement sends 2,500 blocks per batch; the `/setblock` fallback sends 40 commands per tick.
- Undo restores blocks without updates, then the server updates every restored block's neighbours after the last batch. This prevents blocks breaking before their supports return. The fallback uses `strict` but can't update neighbours afterward, so surrounding blocks keep their state.
- `Placement.place` records nearby blocks that need support. Undo restores missing ones even if their support has since gone; it doesn't remove dropped items.
- Each loader passes the frame's `SubmitNodeCollector` to `Editor.render`. Submit hologram boxes after terrain to draw over water and glass, except boxes across a water surface from the camera, which go through normal submission to show through the water. `Hologram` must omit faces turned away from the camera because 26.2's filled-box render type doesn't cull them.
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
