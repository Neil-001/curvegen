# Shared client

## Loader hooks and input

- Pass mouse buttons to `Capture` before `Editor`, and wheel movement to `Editor`, in the world with no screen open. Suppress a consumed event. Forward fresh in-world keyboard presses to `StepHold`. Submit the capture box and HUD alongside the editor's. Forward outgoing command text without the slash to `Placement.commandSent` and check incoming system messages with `Placement.hidesFeedback`.
- Block colour matching needs model parts and the particle material without a world or position. Each loader supplies its own lookup because NeoForge's world-aware methods require both.
- Keep shape settings across screen instances. Undo and redo target the active hologram or the last placement. Cancel gives pattern capture priority. Drain inapplicable key clicks so they cannot act on a future hologram.

## Blocks and paint

- Full block choices exclude falling blocks, which would move after placement, and game master blocks. Use Minecraft's connection exceptions for full blocks such as leaves and pumpkins. Set placed leaves persistent and leave placed states dry.
- For 2D states, drawing right is x. Forward is extrusion for upright shapes and drawing y for flat shapes. Upright layers run front to back and flat layers bottom to top. A stair faces its tall back. An open trapdoor and a shelf panel lie opposite their facing. In a flat stack an identical wall above makes the lower wall's sides tall. Use the solver's resolved joins and upright wall post data. Volumetric states already carry connections, corners, and wall heights.
- `ColorIndex` uses active resource pack textures and tints. Alpha coverage weights overlays so grass fringe contributes less than its opaque base. Use the particle texture when face textures yield no colour and the map colour when texture lookup fails. A tint that fails without a world contributes no tint. Use the same top or side selection for manual matching and painting.
- Build material families from the vanilla registry families first and infer missing variants by block id. Preserve the properties of captured full blocks and replace shaped blocks with their material's full block. Air is an empty cell. Captured blocks without a usable material use the Blocks tab's choice when painted. A gradient stop with a known colour can match by colour when its material is unavailable.
- Take the catalog and its fallback choices on the client thread. Resolve saved block ids against the registry, falling back to the selected family when a block is absent. Resolved paint entries store full states. On Wrap leave their orientation alone. Quarter turns are anticlockwise from the viewer, given by drawing right crossed with up. Use the world-independent state rotation about world up. NeoForge's replacement requires a world and position, which a pattern may lack. Turn horizontal projections' axis and direction properties only where the state supports the result.
- Use the settled joins from `Painted` next to 2D pattern holes. The 3D preview palette lists family colours first, then paint entries. An entry with no known colour uses its family's.

## Saved files and export

- Load Blocks tab choices on the first tick because NeoForge's client entrypoint can run before other mods' blocks are registered. Presets and `blocks.json` use the same encoding for block ids and piece toggles. Missing, unavailable, or incompatible choices and absent legacy toggles leave the current values in place.
- Call `ModSettings.save` after changing a setting. Settings reads use defaults for missing or invalid values and ignore unknown fields. The radial menu supports hold and toggle activation. Its oval uses equal wedges in stretched coordinates, while the circle uses those same wedge boundaries on a round ring.
- `PaintStore` batches writes on the client tick and saves about a second after it first detects unsaved settings.
- Apply a block preset's paint as a fresh snapshot so omitted defaults replace live values. Older block presets load unpainted. When Chroma Studio is unavailable, disable gradient paint on restore while keeping the gradient data, and tell the player.
- Read a preset's format version before binding its data. Leave unreadable or future pattern and preset files alone and reserve their filenames. Replacing an entry preserves its pin. Seed bundled entries in their declared order. Keep bundled pattern names as translation keys until renamed. Save through a temporary file and use atomic replacement where supported. Pattern files predating pin and save time load unpinned with time zero.
- Close `ImageFile` after using its pixels, including on cancellation. It owns the native buffer returned by the image decoder.
- Write one Litematica region using schema 6.1 for compatibility with readers that upgrade older schemas. Palette indices advance along x, then z, then y. Without a hologram, drawing x points east, and drawing y points up on an upright shape and north on a flat one. With a hologram, export its world orientation independently of the terrain.

## Placement fallback

- Recognize command replies from their component structure, including empty wrappers around errors and the suggested command on syntax context lines. Acknowledgement coordinates can arrive as numbers or as text. Match replies to the commands sent by the mod and give the player's own commands priority. Keep the first unexpected error visible.
- Check permission before enqueuing fallback commands to avoid flooding a server that will refuse them. Use `/setblock` strict mode for fallback undo so restored dependents survive without block updates. Blocks around those restored keep their state with this command fallback.
- Bound the search for dependents so adjacent vegetation cannot amplify the cost of a placement. Follow support chains vertically for sand, cane, and vines. Include snow layers even when they fill their block. Restore a dependent only into replaceable space. Redo merges new dependents into the original snapshot because an immediate redo can run before an undo has restored them.
- Keep overlay messages short enough for a single line at 427 pixels. The game's overlay listener also reads them to the narrator.
