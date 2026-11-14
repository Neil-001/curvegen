# 3.0.0

Curve Generator 3.0.0 paints shapes. A shape no longer has to be one block for each piece type: it can take its blocks from a pattern, a mix or a gradient, and the mod still picks slabs, stairs and the other pieces in the same material, so painting never changes the shape. Patterns come with the mod, from blocks you've built or from a picture, and a pattern's empty spots can be left open as holes. 3D Bézier curves can now be hollow. This version is for Minecraft 26.2 on Fabric and NeoForge.

Minecraft 26.2 has no concrete or wool slabs and stairs, so where a painted shape needs those pieces in concrete or wool, they take the closest colour instead.

Chroma Studio 1.2 or newer is optional. With it installed, you can paint with the gradients you save in it, mix blocks from its palettes and make patterns from pictures, and painting and "Match a colour…" choose blocks by colour the way Chroma Studio does. Without it, everything else works.

## Features

- Hollow 3D Bézier curves, with open or capped ends: a pipe, an arch to walk under or, with Carve on, a tunnel through a hill (#86)
- Painting keeps every piece where it was. A painted block's slabs, stairs and walls come from its own material, such as stone brick stairs for stone bricks, or else from the closest colour. A pattern runs along a line and round a loop without a seam, and a turned stripe becomes a spiral (#85)
- Paint 2D shapes with a pattern from the new Paint screen, opened from the Blocks tab. Choose how the pattern fits, its size, turn, mirror and where it starts, and watch the shape's picture change. The hologram, placing, undo, the block count and Litematica export all use the painted blocks (#87)
- Mix: scatter blocks over a shape in set shares, one at a time or in patches, and shuffle for a new arrangement (#88)
- The Blocks tab's block choices are kept when you restart the game, a preset's picture shows its paint, and the G menu's buttons no longer overlap at 1280×720 in any language (#89)
- Paint 3D shapes: wrap a pattern round an ellipsoid, a torus, a tube or a surface, or project it from above, the front or the side. Mixes work on 3D shapes too (#90)
- A pattern library to keep your own patterns, pin, rename or delete them, and try one on your shape before choosing it. Capture a new pattern from blocks in the world by clicking two corners, from the library or the radial menu's Capture wedge (#91)
- A Paint wedge in the radial menu changes the source, pattern, turn, slide, size and mirror while you look at the hologram. The Blocks tab's Use toggles are kept when you restart the game too (#92)
- Leave a pattern's empty spots open as holes, for a lattice, a screen or a dome you can see through. Holes place and clear nothing, the blocks beside them settle as the game would settle them, and a lattice pattern comes with the mod (#93)
- With Chroma Studio 1.2 or newer: paint with its saved gradients, bottom to top, along or across the shape, or outwards from the middle, with sharp or ragged edges. Mix blocks from its palettes, and match colours its way (#94)
- With Chroma Studio 1.2 or newer, drop a picture onto the pattern library to make a pattern up to 128 blocks across, chosen as soon as you save it. Clear pixels become empty spots (#95)
- Shorter tooltips, and every greyed-out control says why (#84)

## Fixes

- Repainting a large shape with holes is faster, and the line above the G menu's picture no longer counts holes as blocks (#96)
- The Paint screen, the pattern library and the radial menu's top and Paint pages no longer cut or hide text at 1280×720 in any language (#97)
- Screen readers name the pattern name fields after their dialog instead of reading "Preset name" (#100)
- Walls in thick shapes now get a post wherever the wall above them has one, so the game no longer changes them after placing (#102)
- A pattern made from a picture no longer reads like a warning on the Paint screen: its status line says how many blocks were matched to its colours. The gradient chooser marks blocks painting can't place, such as a chain or concrete powder, and says they take the closest colour (#104)

# 2.1.0

Curve Generator 2.1.0 adds translations for nine languages. Menus, tooltips, editor hints and messages now follow Minecraft's language setting. This version is for Minecraft 26.2 on Fabric and NeoForge.

## Features

- Make the mod's menus, hints and messages translatable (#66)
- Add German, Spanish, French, Japanese, Korean, Polish, Brazilian Portuguese, Russian and Simplified Chinese translations (#67)

# 2.0.0

Curve Generator 2.0.0 adds 3D shapes and an editor in the world. A shape now appears as a hologram with handles you drag to resize it and points you drag to bend it, and the blocks update as you go. The new shapes are ellipsoids, toruses, 3D equations, 3D Bézier curves and Bézier surfaces. A radial menu on V starts shapes and changes their options without leaving the world. This version is for Minecraft 26.3 on Fabric and NeoForge.

The hologram now starts locked at the block you're looking at, with its handles showing. Before, it followed your view until you pressed K. K still unlocks it to follow you.

## Features

- Edit 2D shapes in the world: drag the box's handles to resize, drag Bézier points, move with Page Up and Page Down, resize with Home and End, rotate with R, tip with U, and undo and redo edits with Z and Y. With no hologram out, Z undoes the last placement and Y puts it back (#46)
- A radial menu on V that starts a shape, changes its options and blocks, and places, exports or cancels it. You can put its wedges in your own order. Hold a move or resize key to type an exact number of blocks (#47)
- Ellipsoids and toruses, filled or hollow, built by a new 3D solver that uses every piece type and Minecraft's own rules for stair corners, fences, panes and walls (#45)
- Edit ellipsoids and toruses in the world, and turn or tip them a quarter turn at a time (#48)
- 3D equations in x, y and z, 3D Bézier curves as round tubes, and Bézier surfaces of 2 to 6 rows and columns of points (#49)
- Edit 3D equations, 3D Bézier curves and Bézier surfaces in the world, with a handle on every control point (#50)
- 3D tabs in the G menu, with a preview you can turn, zoom and drag points in, and block counts, presets and Litematica export for every 3D shape (#51)
- A settings screen for hold times, the radial menu, handle size, the hologram's opacity and block limit, and whether the key hints and shape information show on screen. Open it from the cogwheel in the G menu, from Mod Menu on Fabric or from the mod list on NeoForge (#44)
- Scroll over a number field in the G menu or the settings to change it (#56)

## Fixes

- The line above the G menu's picture no longer runs off its edge on a narrow screen (#53)
- On a server without the mod, placing or undoing a shape no longer fills chat with a line for every block (#54)

# 1.3.1

Curve Generator 1.3.1 shows mod messages above the hotbar instead of in chat.

## Features

- Show placement, undo and export messages above the hotbar (#41)

# 1.3.0

Curve Generator 1.3.0 adds shelves, chains and end rods as block choices for curves on Fabric and NeoForge.

## Features

- Use shelves to build curves (#32)
- Include chains and end rods in curves with optional block choices (#34)

# 1.2.0

Curve Generator 1.2.0 brings the mod to Minecraft 26.3 with Fabric and NeoForge.

## Features

- Support for Minecraft 26.3 (#29)

# 1.1.0

Curve Generator 1.1.0 brings the mod to Minecraft 26.2 with Fabric and NeoForge. It adds more ways to move the placement preview, fixes Carve and undo, and removes stray blocks inside thin ellipses.

## Features

- Support for Minecraft 26.2 (#18)
- Support for NeoForge alongside Fabric (#17)
- Move the placement preview along any axis or in the direction you are looking (#13)
- A new bass clef mod icon (#25)

## Fixes

- Thin ellipses no longer have stray blocks inside the curve beside walls (#23)
- Red Carve boxes show on solid blocks, holograms show through water surfaces, and undo restores nearby blocks that lost their support (#19)
- The Count tab draws its block icons more efficiently (#14)
- Wrapped hint text no longer has a shadow (#6)
