# Paint

## Sources and block choices

- Resolve shape-specific fallbacks when reading settings so switching shapes preserves the player's choices. A missing fit defaults to Repeat Along on lines and Repeat elsewhere. Edge requires depth greater than one. A 3D equation uses projection when the saved direction requires wrapping.
- Embed source snapshots so presets survive library changes. Keep missing settings and incomplete sources at their current values. Store pattern rows top first and address the grid from the bottom. Preserve the distinction between `-` for empty cells that may open into holes and `?` for unusable captured blocks that fill.
- Keep gradient ideal colours, block states, stops and manual swaps, including missing IDs. Ignore colour-only and empty entries in the `fullConnects` vote. With no votes, use the Blocks tab choice. A full block that joins differently from the solve uses the Blocks tab choice or the closest compatible option.
- Try a material variant before a colour match. Keep fences in the Blocks tab fence's connection group. Include the colours of disallowed source blocks so they can still match by colour. One material per colour considers the slabs and stairs the solve uses. Other families retain their usual fallbacks.

## Coordinates and layouts

- `Wrapping.place` gives distances along and across the shape. Map through the nearest point to align a pattern through a thick wall, and mark closed axes so repeats can meet at the seam. The painter can call the mapping from several threads. A null mapping leaves projection available.
- On closed strips, Stretch keeps its seam at the start and repeating fits use whole repeats. For a strip closed only along its length, retain scale across it when the along correction is feasible and within a factor of two. A closed cross-section may need shear. Keep the loop windings nonzero and the determinant positive to prevent collapse or mirroring.
- `Wrap2D` follows the centre line or the filled outline for Edge. A closed chain runs anticlockwise from the bottom, or the back on a floor, so the outside stays at low `v`. Match the equation range edges to the contour's interpolation and traversal so wrapping can join them.
- Equation contours may have duplicate edges, tiny gaps or collapsed segments. Mend open ends in chain order and keep short chains because a stroke can make one cover a block. Filled outlines use an exact nearest search near the edge. Interior sweeps avoid searching many nearly equidistant segments during repaint.
- Wrap leaves captured states oriented as saved. Projection turns states anticlockwise when viewed from drawing right crossed with up. Reverse `u` for right-handed axes so patterns read correctly in previews and exports.
- Hash positions in unpadded shape coordinates so mixes and ragged gradients stay in place through turns and thickness changes. Limit gradient jitter to adjacent bands. Along and Across give each 2D strip its own span.
- Preserve grid order across parallel 3D mapping and compact paint slots after settling holes. Limit the retained mapping to two million blocks to cap the float arrays at about 16 MB. 2D slots retain full grid indices and can share one face across layers. Keep the 3D preview palette's family entries before painted entries.

## Capture and import

- Prefer a one-block-thick capture axis, then the view. Use the eye's side of the box, or the look direction inside its depth span. Derive right from up crossed with the direction toward the viewer so the image reads without mirroring. Take the nearest non-air cell even when unusable. A torch in front of a wall fills that cell.
- Image import reads ARGB. Weight by alpha and coverage to suppress hidden colours, and average in linear light to avoid darkening fine black and white detail. A wholly empty image makes no pattern.
- Use bundled translation keys for file stems so language changes keep the same filenames.
