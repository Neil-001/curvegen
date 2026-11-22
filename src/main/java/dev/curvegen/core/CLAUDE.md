# Core geometry

## 3D shape contract

- Implement `Shape3` and register it in `Shape3.of`. Keep settings private to the solve and make the field and cancel supplier safe to call from several threads. Field coordinates are relative to the padded grid's low corner. Controls and equation ranges are relative to the unpadded box. Read the realized size from the shape, especially for a locked equation.
- Use a signed distance band for a shell so interpolation can resolve a thin wall. The band includes its endpoints. `carve()` tests a separate open interval at block centres. A NaN at any of a voxel's eight corners drops that voxel.
- The default `uniform` requires a field that changes by at most one per block. It classifies a run along +x. Override it for steeper fields and check every skipped cell as in `Solver3Test.skippedCellsReallyAreUniform` and `Shapes3Test`. Use `inset` at jumps on block boundaries so each cell samples its own side. Disable interpolation only where needed. Direct sampling evaluates 4096 centres per block.
- Cache expensive wireframes because the editor draws them while the solve runs. Keep shared traversal order for the field lattice and the `Turned` remapping in the [editor geometry guide](edit/CLAUDE.md).

## Refinement and placement

- Score every cell whose state a refinement pick can change, or total error can rise and refinement can cycle. In 2D, upright picks include both side neighbours and three cells in the row below. Apply wall posts inherited down the column after refinement to keep that dependency local. Keep ellipse axis pieces symmetric, the hollow connector penalty a cost, and byte-grid state indices below 128. A chain or rod may win an equal-cost pick against air to keep a thin stroke continuous. Keep the tie one-way.
- In 3D, `Run.gather` reaches two steps across the plane through stair-dependent joins, and down through wall columns. Check cancellation in every stage at least once per level, and between refinement picks because a tall wall column can make one pick expensive. The next solve waits for the cancelled one to return.
- Reflect 3D tokens before settling their states. A mirror in y preserves the field but can change wall states through support from above. Keep visible geometry separate from wall coverage. A fence's bars score as drawn, while its collision arms cover the wall strips at the bottom. Chains and rods cover a wall's centre and leave its side strips clear.
- Test for hidden blocks before applying a shape cut so the cut leaves no cap. Sparse settling assumes a settled source and must agree with dense settling on it. Keep the solve's error, volume and sweep count when settling holes.
- Keep `Settle2D`'s token conversion aligned with `BlockChoices.stateFor`. Settling an intact shape must give the same states as direct placement. For sparse holes, settle the intact extrusion first. Two layers at each face and one middle layer cover an upright extrusion. Reuse floor layers only after adjacent lower layers agree because walls can transmit state down through the stack.

## Shape algorithms

- For ellipsoid shells use true distance. On a symmetry plane, dropping a zero coordinate from the nearest-point equation can hide the closest point off the plane. Recover the missing coordinate at the dropped pole.
- An implicit equation band uses field values scaled by gradient length. Surface mode estimates distance to a root. Require convergence to zero to reject jumps and poles. Preserve the side of approach at a tangent root. Check the lattice before skipping when all corners and the centre are undefined. A wall can lose up to a quarter block at a jump, pole or domain edge off the block grid. Thickness is approximate around bends narrower than the wall.
- Extend an open hollow tube along the end tangents when finding cuts so its ends follow block boundaries. Measure wrapping on the actual curve. Carry the seam between cross-sections and align it toward down without introducing a twist at each segment.
- A patch extends its field slightly beyond the edge to compensate for NaN corner samples. Off-grid edges can be out by an eighth of a block. Test other nearby sheets when finding a nearest sample on a fold. Seed neighbouring lattice queries only when the corners and centre agree on a single sheet. A ray grazing a hump can select the far crossing. Overlap mesh triangles slightly so their shared edges remain pickable.
- `Near3` uses the triangle inequality to prune bucket candidates. A bucket can omit the true nearest candidate beyond the requested reach.
- Bézier insertion raises the degree and preserves the shape. Reduction fixes the ends and fits the remaining points by least squares. The resulting normal matrix is positive definite and tridiagonal. Picking on a self-crossing curve needs to account for more than one local minimum.

## Preview and data contracts

- Use occupied-block indices in grid order for `Mesh3` paint lookup. `Silhouette`'s rectangles must cover the same pixels as its outlined raster. Hidden wireframe pixels stay faint even where several lines overlap.
- Keep `ShapeSettings.copy`, `set` and `same` complete for worker snapshots and undo, including independent point arrays. Keep translated diagnostics as `Message` values until rendering.
- Presets store a tab's settings separately from a build's orientation and extrusion depth. Keep a surface's grid and dimensions together. A legacy curve without a hollow flag is solid. An older complete block list implies that piece families added since it was written were disabled.
- Litematica uses at least two bits per palette entry in a contiguous stream. Entries can cross a long boundary.
- `CommandReplies` matches successes by position and errors by send order because vanilla sends no command ID. Show ambiguous replies and resume hiding errors after a matching success restores order. An identifiable nested `run setblock` stays queued until timeout because it can emit multiple replies. Preserve the first actionable error and associate its syntax context.
