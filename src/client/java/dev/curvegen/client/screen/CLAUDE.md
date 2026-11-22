# Client screens

## Controls and layout

- A number field's width and wheel hit area include its arrows. Let focused fields handle the wheel before scrolling a point list. Return after a right-click callback because it can rebuild the widget list.
- Put 3D colour controls under the preview to fit the extra tabs. Reserve room for the widest paint source so the Blocks tab row stays put. Use wheel steps for the three 3D point coordinates where arrows would not fit.
- Update only unfocused fields after a drag. Scroll to the dragged 3D point and keep its grab offset and starting camera depth. Scale torus edits from dimensions at focus entry to avoid accumulating rounding during typing. On unlocking a valid equation, copy its derived dimensions to keep the shape.
- Give compact fields without visible labels tooltips. Use the dialog title to narrate its field. A modal gets its own render stratum and owns input and hover.
- Keep dark bands behind text over the world. Paint status, image-drop hints and the radial footer allow two lines. Put clipped text in the tooltip and preserve filename extensions when shortening image names.

## Previews and counts

- Screens share a preview worker. Drop superseded solves, but allow one to finish during continuous editing. Update the ideal outline from live settings. Keep the original comparison settings apart from the 2D solver's mutable copy.
- Retain the completed preview during repaint. Capture the catalog and face colours on the client thread, and include the Chroma matcher version when checking whether paint is current. Use fresh paint for export.
- Use settled geometry for holes in every colour mode and leave holes out of counts. The 2D canvas reports the front layer. Match count icon colours and silhouettes to the preview. Keep the percentage when the full error line cannot fit.
- Give each preview its own texture ID because parents and children can both draw. Release covered screens' textures and recreate them when they draw again. A padding-only change keeps the main preview's view.
- Preview presets with the live orientation, depth and piece choices. The Blocks toggle determines whether saved block choices and paint apply. Include it in the preview cache key and cancel work when the target changes.

## Paint and libraries

- Keep the Pattern controls visible and its chooser usable when paint is off. Keep the material option in the same row with a separate value per source. Refresh the displayed default fit once the preview can tell whether the 2D shape is a line.
- Without Chroma Studio, skip Gradient in the source cycle. A saved Gradient selection stays visible with disabled controls and an explanation. Apply the same rule in the radial menu.
- Include the active item in a chooser even if it is absent from its library, including an unsaved pattern used to make a mix. Flag saved gradient blocks that cannot be placed because painting substitutes the closest placeable blocks.
- Use whole pixels per cell for small thumbnails. Apply a chosen pattern before rebuilding its parent. Release dropped image data when the import dialog closes. Keep the drop error until the next attempt.

## Radial input

- Keep the world live behind the menu and number box. Ignore repeat presses from the opening key and check its physical state for a release before the screen received events. Mouse bindings can use the next click because they do not repeat.
- In Hold mode a release selects the wedge. Submenus stay open for clicks. Return to the root when the hologram ends. Group consecutive wheel edits to one option into one undo entry.
