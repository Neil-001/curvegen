# In-world editor

## Shape contract

- `EditShape` measures from the unpadded minimum corner in own axes. `Content` uses world-axis offsets from the box origin. Offset padded 3D blocks and wireframes when placing them in the box.
- Read the realized size after a resize. Point edits report shifts of the minimum corner so unchanged points stay fixed in the world. A null `bump` lets the editor resize the box.
- A 2D shape taking over a tipped 3D hologram needs two horizontal directions even if the old x or z is vertical. Assemble a 2D result with its solved floor mode and dimensions because the live box may have changed. Turning a 3D shape requires a solve in its new world orientation.
- Keep `curve` and `options` cheap because they run each frame. Trace the 3D equation wireframe on the worker and stretch the last trace to the live box while waiting.

## Editing and background work

- Apply every drag from its starting snapshot. Keep the grab offset and thin handles using the starting box. Consume the releases of presses taken from the game. End the drag before recording another edit so the next frame cannot restore the old settings.
- Consecutive `Editor.edit` calls with the same non-null group can share an undo entry. Record screen changes on close and rebase them when the editor records its own changes. Derive connection behavior from current block choices after undo.
- Solve with private settings on the worker. Keep a separate comparison snapshot because the 2D equation solver can resolve its locked height. Build blocks on the client thread. Pending blocks keep their solve origin, adjusted for subsequent translations.
- Keep the last blocks visible during a solve or repaint. Replace affects placement only. Switching Carve on needs a solve if the shape computes the clear set on request. Paint retains its block choices and matcher version. Check for pending settings before exporting a hologram from a screen.
- Cancel superseded solves, but let work finish after enough cancellations that a continuous drag would otherwise show no progress.
- Apply the first step on key-down. When the player types a number, retract the first step if it is still the latest edit. If another edit followed it, count that block towards the typed amount.

## Drawing and capture

- The collector draws submitted geometry later in the frame. Keep inputs scoped to that frame. Use box-relative coordinates for precision and clip line strips before the camera plane so they cannot fill the view.
- Collect carve operations before placements so placements win at shared positions. Draw only camera-facing faces to avoid blending a box twice.
- Draw geometry beyond the water surface before translucent terrain and geometry on the camera's side after it, when the collector supports both phases. Use a dim through-wall pass and a depth-tested pass for handles. The game supplies the see-through text render type without an access widener.
- Keep the aimed handle when opening the radial menu so Remove point retains its target. Give capture HUD hints priority and show errors and progress even with shape information disabled.
- Capture uses right clicks so a hologram retains its other controls. Check dimensions in the view at the second corner. The naming dialog receives the release. Cancelling it lets the player pick the second corner again.
