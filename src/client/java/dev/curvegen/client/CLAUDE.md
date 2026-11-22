# Shared client

## Loader hooks

- Give capture priority over editor clicks. Forward fresh in-world presses to `StepHold` as key mapping clicks include repeats. Drain inapplicable clicks so they cannot act on the next hologram.
- The two loaders must observe outgoing commands in send order and filter incoming system replies. See the [core guide](../../../../../main/java/dev/curvegen/core/CLAUDE.md) for ambiguous replies.
- Block colour matching needs model parts and particle material without a world or position. Use the loader's `ClientPlatform` implementation.

## Blocks and paint

- Full block choices exclude falling and game master blocks. Preserve Minecraft's attachment exceptions, such as leaves and pumpkins. Set placed leaves persistent and leave placed states dry.
- Use the solver's resolved joins and upright wall posts. In a flat stack, an identical wall above makes the lower wall's sides tall. For 2D holes use the settled joins from `Painted`.
- Index colours from the active resource packs. Weight overlays by alpha so fringe contributes less than its opaque base. Use particle and map colours when face textures are unavailable. Match the same face in the picker and painter.
- Capture the catalog and fallback choices on the client thread. Its matcher must be safe on a worker. Preserve eligible captured full states and use the material's full block for shaped states.
- Use the world-independent rotation for full states. NeoForge's replacement requires a world and position, which a pattern may lack.

## Saved files and export

- Load Blocks tab choices on the first tick. NeoForge's client entrypoint can run before other mods register blocks. Presets and `blocks.json` share block and toggle encoding. Missing or incompatible choices and absent legacy toggles keep current values.
- Apply paint from a block preset as a fresh snapshot so omitted defaults replace live values. Older block presets load unpainted. Without Chroma Studio, disable restored gradient paint, keep its data and tell the player.
- Leave unreadable or future files alone and reserve their filenames. Replace files atomically where supported. Replacing a library entry keeps its pin. Bundled names stay as translation keys until renamed. Pattern files without pin and save time load unpinned with time zero.
- Close `ImageFile` on success and cancellation because it owns the native pixel buffer.
- Export a single Litematica region with schema 6.1 for compatibility. Palette indices advance along x, z, then y. Use the hologram's world orientation when present. Otherwise drawing x points east and drawing y points up for a wall or north for a floor.

## Placement fallback

- Match feedback by component structure. Acknowledgements can supply coordinates as text. Keep the first unexpected error visible and give the player's own commands priority. Leave `sendCommandFeedback` under server control.
- Check permission before queueing fallback commands. Use `/setblock` strict mode for undo so block updates leave restored dependents intact.
- Bound dependent searches so adjacent vegetation cannot amplify placement cost. Follow vertical support chains and include snow layers. Undo can restore a dependent even if its support is gone, provided the position is replaceable. Dropped items stay in the world. Redo merges new dependents into the original snapshot because it can run before undo finishes.
- Keep placement messages to a single overlay line at 427 pixels. The overlay listener also reads them to the narrator.
