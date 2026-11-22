# Editor geometry and input

## Boxes and shape edits

- Preserve the doubled x and z pivot across quarter turns. A turn that swaps odd and even sizes rounds the box origin. Recomputing the pivot from that origin would move the box after four turns.
- Keep both face handles on thin axes even when thinning the other handles. A symmetric resize changes size in pairs to preserve the centre and parity. When a lock resizes an undragged axis, anchor its lateral centre or its vertical bottom.
- Facing a player forward puts the shape's x to their right, y up and z away. On a reversed axis a point maps to size minus coordinate, and a cell maps to size minus one minus index. Keep the cell transform valid outside the box for carve cells.
- Solve `Turned` shapes in world axes so pieces settle with the correct up direction. Remap the quarter-block lattice, with x changing fastest, then z, then y, for both `lattice` and `follows`. A `uniform` run is valid along own +x. Keep the full run when that axis points east or the shape guarantees the bound in all directions. Otherwise use just the current cell.
- Keep equation scale locks consistent with the solver. A 2D resize uses width when both dimensions move. A 3D resize uses the first dragged own axis. Equation x, z and y supply width, height and depth. Keep the ranges fixed when stretching the box. Disable fill or thickness controls made inapplicable by an inequality.
- Torus resize handles stretch the box. Changes to the ring or tube setting preserve its stretch by scaling the affected dimensions together. Keep the tube within the ring diameter when the ring shrinks.
- Point edits return shifts of the box minimum in own axes. Pass them to `Box.regrown` to keep unaffected geometry fixed in the world. Limit a stretch before scaling so bounds preserve proportions. Preserve the opposite bound on a bump. After reducing a surface, refit because the control points can move outside the box. Allow for arithmetic roundoff near integer boundaries during refitting and keep direct drags at their actual targets.
- 2D Bézier insertion follows the nearest curve parameter. 3D insertion elevates the degree. For surfaces, point actions operate on rows by default and columns with Alt.

## Picking and key holds

- Reject near-parallel drag intersections to avoid jumps. Measure ray distance from its origin. Use the original control point plus the ray intersection's displacement so a stationary ray preserves the point.
- Match in-world pick radii to rendered sizes. Compare the miss distance as a fraction of the radius and slightly prefer nearer handles when they overlap. In the preview, the hit area is square and depth breaks equal cursor distances.
- `Orbit` is orthographic. It looks north at zero yaw with depth growing away from the camera. Preserve the starting depth during a drag and anchor zoom under the cursor.
- Feed `HoldTimer` fresh in-world key-downs. Vanilla queues repeats even when a held key outlives a screen. A fresh press starts its own hold and emits `STEP`, including a release and press between ticks. Cancel holds on undo and redo because the number box would replace a different edit.

## Radial menu

- Shape option setters change the setting. The editor handles undo and fitting. Keep fixed labels within 72 pixels and twelve wedges within 427 by 240, with room for two lines below them.
- Keep pattern controls in fixed slots across sources, including disabled slots. The client inserts Choose in its own slot. For a shape without wrapping, show the projection actually in use even if the saved direction wraps.
- Use the label ellipse's proportions for wedge picks in both ring styles. Apply those proportions to the dead zone only for the stretched ring. Keep its half-pixel cursor offset on odd screens. The round ring picks from the integer centre it draws around and leaves pixels whose corners cross a wedge boundary empty.
- Store both top-level orders in one list. `presets` is their only shared ID, which lets both menus retain their order. Insert an unsaved wedge after its default predecessor so it stays near related actions in a custom order.
